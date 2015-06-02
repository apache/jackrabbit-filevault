/*************************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ************************************************************************/
package org.apache.jackrabbit.vault.fs.impl.io;

import java.security.Principal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.jcr.ValueFactory;
import javax.jcr.security.AccessControlEntry;
import javax.jcr.security.AccessControlManager;
import javax.jcr.security.AccessControlPolicy;
import javax.jcr.security.AccessControlPolicyIterator;
import javax.jcr.security.Privilege;

import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.JackrabbitAccessControlList;
import org.apache.jackrabbit.api.security.principal.PrincipalManager;
import org.apache.jackrabbit.vault.fs.io.AccessControlHandling;
import org.apache.jackrabbit.vault.util.DocViewNode;
import org.apache.jackrabbit.vault.util.DocViewProperty;
import org.slf4j.Logger;
import org.xml.sax.SAXException;

/**
 * Implements a doc view adapter that reads the ACL information of the docview hierarchy and applies it to the
 * underlying repository, based on the {@link org.apache.jackrabbit.vault.fs.io.AccessControlHandling}
 */
public class JackrabbitACLImporter implements DocViewAdapter {

    /**
     * default logger
     */
    private static final Logger log = DocViewSAXImporter.log;

    private final JackrabbitSession session;

    private final AccessControlHandling aclHandling;

    private final AccessControlManager acMgr;

    private final PrincipalManager pMgr;

    private final String accessControlledPath;

    private Map<String, List<ACE>> aceMap = new LinkedHashMap<String, List<ACE>>();

    private ACE currentACE;

    private static enum State {
        INITIAL,
        ACL,
        ACE,
        RESTRICTION,
        ERROR,
    }

    private final Stack<State> states = new Stack<State>();

    public JackrabbitACLImporter(Node accessControlledNode, AccessControlHandling aclHandling)
            throws RepositoryException {
        this(accessControlledNode.getSession(), accessControlledNode.getPath(), aclHandling);
    }

    public JackrabbitACLImporter(Session session, AccessControlHandling aclHandling)
            throws RepositoryException {
        this(session, null, aclHandling);
    }

    private JackrabbitACLImporter(Session session, String path, AccessControlHandling aclHandling)
            throws RepositoryException {
        if (aclHandling == AccessControlHandling.CLEAR || aclHandling == AccessControlHandling.IGNORE) {
            throw new RepositoryException("Error while reading access control content: unsupported AccessControlHandling: " + aclHandling);
        }
        this.accessControlledPath = path;
        this.session = (JackrabbitSession) session;
        this.acMgr = this.session.getAccessControlManager();
        this.pMgr = this.session.getPrincipalManager();
        this.aclHandling = aclHandling;
        this.states.push(State.INITIAL);
    }


    public void startNode(DocViewNode node) throws SAXException {
        State state = states.peek();
        switch (state) {
            case INITIAL:
                if ("rep:ACL".equals(node.primary)) {
                    state = State.ACL;
                } else {
                    log.error("Error while reading access control content: Expected rep:ACL but was: {}", node.primary);
                    state = State.ERROR;
                }
                break;

            case ACL:
                try {
                    currentACE = addACE(aceMap, new ACE(node));
                    state = State.ACE;
                } catch (IllegalArgumentException e) {
                    log.error("Error while reading access control content: {}", e);
                    state = State.ERROR;
                }
                break;
            case ACE:
                currentACE.addRestrictions(node);
                state = State.RESTRICTION;
                break;
            case RESTRICTION:
                log.error("Error while reading access control content: Unexpected node: {} for state {}", node.primary, state);
                state = State.ERROR;
                break;
            case ERROR:
                // stay in error
                break;
        }
        states.push(state);
    }

    private static ACE addACE(Map<String, List<ACE>> map, ACE ace) {
        List<ACE> list = map.get(ace.principalName);
        if (list == null) {
            list = new ArrayList<ACE>();
            map.put(ace.principalName, list);
        }
        list.add(ace);
        return ace;
    }

    public void endNode() throws SAXException {
        State state = states.pop();
        if (state == State.ACE) {
            currentACE = null;
        }
    }

    public List<String> close() throws SAXException {
        if (states.peek() != State.INITIAL) {
            log.error("Unexpected end state: {}", states.peek());
        }
        List<String> paths = new ArrayList<String>();
        try {
            apply();

            // currently cheat a little here
            if (accessControlledPath == null) {
                addPathIfExists(paths, "/rep:repoPolicy");
            } else if ("/".equals(accessControlledPath)) {
                addPathIfExists(paths, "/rep:policy");
            } else {
                addPathIfExists(paths, accessControlledPath + "/rep:policy");
            }
        } catch (RepositoryException e) {
            log.error("Error while applying access control content.", e);
        }
        return paths;
    }

    private void addPathIfExists(List<String> paths, String path) throws RepositoryException {
        if (session.nodeExists(path)) {
            paths.add(path);
        }
    }

    private void apply() throws RepositoryException {
        final ValueFactory valueFactory = session.getValueFactory();

        // find principals of existing ACL
        JackrabbitAccessControlList acl = null;
        Set<String> existingPrincipals = new HashSet<String>();
        for (AccessControlPolicy p: acMgr.getPolicies(accessControlledPath)) {
            if (p instanceof JackrabbitAccessControlList) {
                acl = (JackrabbitAccessControlList) p;
                for (AccessControlEntry ace: acl.getAccessControlEntries()) {
                    existingPrincipals.add(ace.getPrincipal().getName());
                }
            }
        }

        // remove existing policy for 'overwrite'
        if (aclHandling == AccessControlHandling.OVERWRITE && acl != null) {
            acMgr.removePolicy(accessControlledPath, acl);
            acl = null;
        }

        if (acl == null) {
            AccessControlPolicyIterator iter = acMgr.getApplicablePolicies(accessControlledPath);
            while (iter.hasNext()) {
                AccessControlPolicy p = iter.nextAccessControlPolicy();
                if (p instanceof JackrabbitAccessControlList) {
                    acl = (JackrabbitAccessControlList) p;
                    break;
                }
            }
        }
        if (acl == null) {
            throw new RepositoryException("not JackrabbitAccessControlList applicable on " +
                    (accessControlledPath == null ? "'root'" : accessControlledPath));
        }

        // clear all ACEs of the package principals for merge (VLT-94), otherwise the `acl.addEntry()` below
        // might just combine the privileges.
        if (aclHandling == AccessControlHandling.MERGE) {
            for (String name: aceMap.keySet()) {
                for (AccessControlEntry ace : acl.getAccessControlEntries()) {
                    if (ace.getPrincipal().getName().equals(name)) {
                        acl.removeAccessControlEntry(ace);
                    }
                }
            }
        }

        // apply ACEs of package
        for (Map.Entry<String, List<ACE>> entry: aceMap.entrySet()) {
            final String principalName = entry.getKey();
            if (aclHandling == AccessControlHandling.MERGE_PRESERVE && existingPrincipals.contains(principalName)) {
                // skip principal if it already has an ACL
                continue;
            }
            Principal principal = pMgr.getPrincipal(principalName);
            if (principal == null) {
                principal = new Principal(){
                    public String getName() {
                        return principalName;
                    }
                };
            }

            for (ACE ace: entry.getValue()) {
                Privilege[] privileges = new Privilege[ace.privileges.length];
                for (int i = 0; i < privileges.length; i++) {
                    privileges[i] = acMgr.privilegeFromName(ace.privileges[i]);
                }
                Map<String, Value> svRestrictions = new HashMap<String, Value>();
                Map<String, Value[]> mvRestrictions = new HashMap<String, Value[]>();
                for (String restName : acl.getRestrictionNames()) {
                    DocViewProperty restriction = ace.restrictions.get(restName);
                    if (restriction != null) {
                        Value[] values = new Value[restriction.values.length];
                        int type = acl.getRestrictionType(restName);
                        for (int i=0; i<values.length; i++) {
                            values[i] = valueFactory.createValue(restriction.values[i], type);
                        }
                        if (restriction.isMulti) {
                            mvRestrictions.put(restName, values);
                        } else {
                            svRestrictions.put(restName, values[0]);
                        }
                    }
                }
                acl.addEntry(principal, privileges, ace.allow, svRestrictions, mvRestrictions);
            }
        }
        acMgr.setPolicy(accessControlledPath, acl);
    }

    private static class ACE {

        private final boolean allow;
        private final String principalName;
        private final String[] privileges;
        private final Map<String, DocViewProperty> restrictions = new HashMap<String, DocViewProperty>();

        private ACE(DocViewNode node) {
            if ("rep:GrantACE".equals(node.primary)) {
                allow = true;
            } else if ("rep:DenyACE".equals(node.primary)) {
                allow = false;
            } else {
                throw new IllegalArgumentException("Unexpected node ACE type: " + node.primary);
            }
            principalName = node.getValue("rep:principalName");
            privileges = node.getValues("rep:privileges");
            addRestrictions(node);
        }

        public void addRestrictions(DocViewNode node) {
            restrictions.putAll(node.props);
        }
    }
}
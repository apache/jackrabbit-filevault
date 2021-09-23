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
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.jcr.ValueFactory;
import javax.jcr.security.AccessControlEntry;
import javax.jcr.security.AccessControlException;
import javax.jcr.security.AccessControlManager;
import javax.jcr.security.AccessControlPolicy;
import javax.jcr.security.AccessControlPolicyIterator;
import javax.jcr.security.Privilege;

import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.JackrabbitAccessControlList;
import org.apache.jackrabbit.api.security.JackrabbitAccessControlManager;
import org.apache.jackrabbit.api.security.authorization.PrincipalAccessControlList;
import org.apache.jackrabbit.api.security.authorization.PrincipalSetPolicy;
import org.apache.jackrabbit.api.security.principal.PrincipalManager;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.commons.jackrabbit.authorization.AccessControlUtils;
import org.apache.jackrabbit.spi.Name;
import org.apache.jackrabbit.spi.commons.conversion.DefaultNamePathResolver;
import org.apache.jackrabbit.spi.commons.conversion.NamePathResolver;
import org.apache.jackrabbit.spi.commons.conversion.NameResolver;
import org.apache.jackrabbit.spi.commons.name.NameConstants;
import org.apache.jackrabbit.spi.commons.name.NameFactoryImpl;
import org.apache.jackrabbit.vault.fs.io.AccessControlHandling;
import org.apache.jackrabbit.vault.util.DocViewNode2;
import org.apache.jackrabbit.vault.util.DocViewProperty2;
import org.slf4j.Logger;

/**
 * Implements a doc view adapter that reads the ACL information of the docview hierarchy and applies it to the
 * underlying repository, based on the {@link org.apache.jackrabbit.vault.fs.io.AccessControlHandling}
 */
public class JackrabbitACLImporter implements DocViewAdapter {

    private static final Name NAME_REP_EFFECTIVE_PATH = NameFactoryImpl.getInstance().create(Name.NS_REP_URI, "effectivePath");
    private static final Name NAME_REP_PRINCIPAL_NAMES = NameFactoryImpl.getInstance().create(Name.NS_REP_URI, "principalNames");

    /**
     * default logger
     */
    private static final Logger log = DocViewSAXHandler.log;

    private final JackrabbitSession session;

    private final AccessControlHandling aclHandling;

    private final AccessControlManager acMgr;

    private final PrincipalManager pMgr;

    private final String accessControlledPath;

    private final  NamePathResolver resolver;

    private ImportedPolicy<? extends AccessControlPolicy> importPolicy;

    private enum State {
        INITIAL,
        ACL,
        ACE,
        RESTRICTION,
        ERROR,
        PRINCIPAL_SET_POLICY
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
        this.resolver = new DefaultNamePathResolver(session);
    }

    public void startNode(DocViewNode2 node) {
        State state = states.peek();
        switch (state) {
            case INITIAL:
                String primaryType = node.getPrimaryType().orElseThrow(() -> new IllegalStateException("Error while reading access control content: Missing 'jcr:primaryType'"));
                if ("rep:ACL".equals(primaryType)) {
                    importPolicy = new ImportedAcList();
                    state = State.ACL;
                } else if ("rep:CugPolicy".equals(primaryType)) {
                    importPolicy = new ImportedPrincipalSet(node);
                    state = State.PRINCIPAL_SET_POLICY;
                } else if ("rep:PrincipalPolicy".equals(primaryType)) {
                    importPolicy = new ImportedPrincipalAcList(node);
                    state = State.ACL;
                } else {
                    log.error("Error while reading access control content: Expected rep:ACL or rep:CugPolicy but was: {}", node.getPrimaryType());
                    state = State.ERROR;
                }
                break;
            case ACL:
            case ACE:
            case RESTRICTION:
                state = importPolicy.append(state, node);
                break;
            case PRINCIPAL_SET_POLICY:
                state = importPolicy.append(state, node);
                break;
            case ERROR:
                // stay in error
                break;
        }
        states.push(state);
    }

    public void endNode() {
        State state = states.pop();
        importPolicy.endNode(state);
    }

    public List<String> close() throws RepositoryException {
        if (states.peek() != State.INITIAL) {
            log.error("Unexpected end state: {}", states.peek());
        }
        List<String> paths = new ArrayList<>();
        importPolicy.apply(paths, resolver);
        return paths;
    }

    private void addPathIfExists(List<String> paths, String path) throws RepositoryException {
        if (session.nodeExists(path)) {
            paths.add(path);
        }
    }

    private abstract class ImportedPolicy<T extends AccessControlPolicy> {

        abstract State append(State state, DocViewNode2 node);

        abstract void endNode(State state);

        abstract void apply(List<String> paths, NameResolver resolver) throws RepositoryException;

        Principal getPrincipal(final String principalName) {
            Principal principal = new Principal() {
                public String getName() {
                    return principalName;
                }
            };
            return principal;
        }

        T getPolicy(Class<T> clz) throws RepositoryException {
            for (AccessControlPolicy p : acMgr.getPolicies(accessControlledPath)) {
                if (clz.isAssignableFrom(p.getClass())) {
                    return clz.cast(p);
                }
            }
            return null;
        }

        T getPolicy(Class<T> clz, Principal principal) throws RepositoryException {
            if (acMgr instanceof JackrabbitAccessControlManager) {
                for (AccessControlPolicy p : ((JackrabbitAccessControlManager) acMgr).getPolicies(principal)) {
                    if (clz.isAssignableFrom(p.getClass())) {
                        return clz.cast(p);
                    }
                }
            }
            return null;
        }

        T getApplicablePolicy(Class<T> clz) throws RepositoryException {
            AccessControlPolicyIterator iter = acMgr.getApplicablePolicies(accessControlledPath);
            while (iter.hasNext()) {
                AccessControlPolicy p = iter.nextAccessControlPolicy();
                if (clz.isAssignableFrom(p.getClass())) {
                    return clz.cast(p);
                }
            }

            // no applicable policy
            throw new RepositoryException("no applicable AccessControlPolicy of type "+ clz + " on " +
                    (accessControlledPath == null ? "'root'" : accessControlledPath));
        }

        T getApplicablePolicy(Class<T> clz, Principal principal) throws RepositoryException {
            if (acMgr instanceof JackrabbitAccessControlManager) {
                for (AccessControlPolicy p : ((JackrabbitAccessControlManager) acMgr).getApplicablePolicies(principal)) {
                    if (clz.isAssignableFrom(p.getClass())) {
                        return clz.cast(p);
                    }
                }
            }

            // no applicable policy
            throw new AccessControlException("no applicable AccessControlPolicy of type "+ clz + " for " + principal.getName());
        }
    }

    private final class ImportedAcList extends ImportedPolicy<JackrabbitAccessControlList> {

        private List<ACE> aceList = new ArrayList<>();
        private ACE currentACE;

        private ImportedAcList() {
        }

        @Override
        State append(State state, DocViewNode2 childNode) {
            if (state == State.ACL) {
                try {
                    currentACE = new ACE(childNode);
                    aceList.add(currentACE);
                    return State.ACE;
                } catch (IllegalArgumentException e) {
                    log.error("Error while reading access control content: {}", e);
                    return State.ERROR;
                }
            } else if (state == State.ACE) {
                currentACE.addRestrictions(childNode);
                return State.RESTRICTION;
            } else {
                log.error("Error while reading access control content: Unexpected node: {} for state {}", childNode.getPrimaryType(), state);
                return State.ERROR;
            }
        }

        @Override
        void endNode(State state) {
            if (state == State.ACE) {
                currentACE = null;
            }
        }

        @Override
        void apply(List<String> paths, NameResolver resolver) throws RepositoryException {
            // find principals of existing ACL
            JackrabbitAccessControlList acl = getPolicy(JackrabbitAccessControlList.class);
            Set<String> existingPrincipals = new HashSet<String>();
            if (acl != null) {
                for (AccessControlEntry ace: acl.getAccessControlEntries()) {
                    existingPrincipals.add(ace.getPrincipal().getName());
                }

                // remove existing policy for 'overwrite'
                if (aclHandling == AccessControlHandling.OVERWRITE) {
                    acMgr.removePolicy(accessControlledPath, acl);
                    acl = null;
                }
            }

            if (acl == null) {
                acl = getApplicablePolicy(JackrabbitAccessControlList.class);
            }

            // clear all ACEs of the package principals for merge (VLT-94), otherwise the `acl.addEntry()` below
            // might just combine the privileges.
            if (aclHandling == AccessControlHandling.MERGE) {
                for (ACE entry : aceList) {
                    for (AccessControlEntry ace : acl.getAccessControlEntries()) {
                        if (ace.getPrincipal().getName().equals(entry.principalName)) {
                            acl.removeAccessControlEntry(ace);
                        }
                    }
                }
            }

            // apply ACEs of package
            for (ACE ace : aceList) {
                final String principalName = ace.principalName;
                if (aclHandling == AccessControlHandling.MERGE_PRESERVE && existingPrincipals.contains(principalName)) {
                    // skip principal if it already has an ACL
                    continue;
                }
                Principal principal = getPrincipal(principalName);

                Map<String, Value> svRestrictions = new HashMap<String, Value>();
                Map<String, Value[]> mvRestrictions = new HashMap<String, Value[]>();
                ace.convertRestrictions(acl, session.getValueFactory(), resolver, svRestrictions, mvRestrictions);
                acl.addEntry(principal, ace.getPrivileges(acMgr), ace.allow, svRestrictions, mvRestrictions);
            }
            acMgr.setPolicy(accessControlledPath, acl);

            if (accessControlledPath == null) {
                addPathIfExists(paths, "/rep:repoPolicy");
            } else if ("/".equals(accessControlledPath)) {
                addPathIfExists(paths, "/rep:policy");
            } else {
                addPathIfExists(paths, accessControlledPath + "/rep:policy");
            }
        }
    }

    private final class ImportedPrincipalSet extends ImportedPolicy<PrincipalSetPolicy> {

        private final Collection<String> principalNames;

        private ImportedPrincipalSet(DocViewNode2 node) {
            // don't change the status as a cug policy may not have child nodes.
            // just collect the rep:principalNames property
            // any subsequent state would indicate an error
            principalNames = node.getPropertyValues(NAME_REP_PRINCIPAL_NAMES);
        }

        @Override
        State append(State state, DocViewNode2 childNode) {
            log.error("Error while reading access control content: Unexpected node: {} for state {}", childNode.getPrimaryType(), state);
            return State.ERROR;
        }

        @Override
        void endNode(State state) {
            // nothing to do
        }

        @Override
        void apply(List<String> paths, NameResolver resolver) throws RepositoryException {
            PrincipalSetPolicy psPolicy = getPolicy(PrincipalSetPolicy.class);
            if (psPolicy != null) {
                Set<Principal> existingPrincipals = psPolicy.getPrincipals();
                // remove existing policy for 'overwrite'
                if (aclHandling == AccessControlHandling.OVERWRITE) {
                    psPolicy.removePrincipals(existingPrincipals.toArray(new Principal[existingPrincipals.size()]));
                }
            } else {
                psPolicy = getApplicablePolicy(PrincipalSetPolicy.class);
            }

            // TODO: correct behavior for MERGE and MERGE_PRESERVE?
            Principal[] principals = principalNames.stream().map(name -> getPrincipal(name)).toArray(Principal[]::new);

            psPolicy.addPrincipals(principals);
            acMgr.setPolicy(accessControlledPath, psPolicy);

            if ("/".equals(accessControlledPath)) {
                addPathIfExists(paths, "/rep:cugPolicy");
            } else {
                addPathIfExists(paths, accessControlledPath + "/rep:cugPolicy");
            }
        }
    }

    private final class ImportedPrincipalAcList extends ImportedPolicy<PrincipalAccessControlList> {

        private final Principal principal;
        private final List<PrincipalEntry> entries = new ArrayList<>();
        private PrincipalEntry currentEntry;

        private ImportedPrincipalAcList(DocViewNode2 node) {
             String principalName = node.getPropertyValue(NameConstants.REP_PRINCIPAL_NAME).orElseThrow(() -> new IllegalStateException("mandatory property 'rep:principalName' missing on principal policy node"));
             Principal p = pMgr.getPrincipal(principalName);
             if (p == null) {
                 try {
                     Authorizable a = session.getUserManager().getAuthorizableByPath(accessControlledPath);
                     if (a != null) {
                         p = a.getPrincipal();
                     }
                 } catch (RepositoryException e) {
                     log.debug("Error while trying to retrieve user/group from access controlled path {}, {}", accessControlledPath, e.getMessage());
                 }
                 if (p == null) {
                     p = getPrincipal(principalName);
                 }
             }
             principal = p;
        }

        @Override
        State append(State state, DocViewNode2 childNode) {
            if (state == State.ACL) {
                if (!"rep:PrincipalEntry".equals(childNode.getPrimaryType().orElseThrow(() -> new IllegalStateException("mandatory property 'jcr:primaryType' missing on principal policy node")))) {
                    log.error("Unexpected node type of access control entry: {}", childNode.getPrimaryType());
                    return State.ERROR;
                }
                currentEntry = new PrincipalEntry(childNode);
                entries.add(currentEntry);
                return State.ACE;
            } else if (state == State.ACE) {
                currentEntry.addRestrictions(childNode);
                return State.RESTRICTION;
            } else {
                log.error("Error while reading access control content: Unexpected node: {} for state {}", childNode.getPrimaryType(), state);
                return State.ERROR;
            }
        }

        @Override
        void endNode(State state) {
            if (state == State.ACE) {
                currentEntry = null;
            }
        }

        @Override
        void apply(List<String> paths, NameResolver resolver) throws RepositoryException {
            if (aclHandling == AccessControlHandling.MERGE_PRESERVE) {
                log.debug("MERGE_PRESERVE for principal-based access control list is equivalent to IGNORE.");
                return;
            }

            PrincipalAccessControlList acl = getPolicy(PrincipalAccessControlList.class, principal);
            if (acl != null && aclHandling == AccessControlHandling.OVERWRITE) {
                // remove existing policy for 'OVERWRITE'
                acMgr.removePolicy(acl.getPath(), acl);
                acl = null;
            }

            if (acl == null) {
                acl = getApplicablePolicy(PrincipalAccessControlList.class, principal);
            }

            // apply ACEs of package for MERGE and OVERWRITE
            for (PrincipalEntry entry : entries) {
                Map<String, Value> svRestrictions = new HashMap<>();
                Map<String, Value[]> mvRestrictions = new HashMap<String, Value[]>();
                entry.convertRestrictions(acl, session.getValueFactory(), resolver, svRestrictions, mvRestrictions);
                acl.addEntry(entry.effectivePath, entry.getPrivileges(acMgr), svRestrictions, mvRestrictions);
            }
            acMgr.setPolicy(acl.getPath(), acl);

            if (accessControlledPath == null) {
                addPathIfExists(paths, "/rep:repoPolicy");
            } else if ("/".equals(accessControlledPath)) {
                addPathIfExists(paths, "/rep:policy");
            } else {
                addPathIfExists(paths, accessControlledPath + "/rep:policy");
            }
        }
    }

    private static class AbstractEntry {

        private final Collection<String> privileges;
        private final Map<Name, DocViewProperty2> restrictions;

        private AbstractEntry(DocViewNode2 node) {
            privileges = node.getPropertyValues(NameConstants.REP_PRIVILEGES);
            restrictions = new HashMap<>();
            addRestrictions(node);
        }

        void addRestrictions(DocViewNode2 node) {
            restrictions.putAll(node.getProperties().stream().collect(Collectors.toMap(DocViewProperty2::getName, Function.identity())));
        }

        void convertRestrictions(JackrabbitAccessControlList acl, ValueFactory vf, NameResolver resolver, Map<String, Value> svRestrictions, Map<String, Value[]> mvRestrictions) throws RepositoryException {
            for (String restName : acl.getRestrictionNames()) {
                DocViewProperty2 restriction = restrictions.get(resolver.getQName(restName));
                if (restriction != null) {
                    Value[] values = new Value[restriction.getStringValues().size()];
                    int type = acl.getRestrictionType(restName);
                    for (int i=0; i<values.length; i++) {
                        values[i] = vf.createValue(restriction.getStringValues().get(i), type);
                    }
                    if (restriction.isMultiValue()) {
                        mvRestrictions.put(restName, values);
                    } else {
                        svRestrictions.put(restName, values[0]);
                    }
                }
            }
        }

        Privilege[] getPrivileges(AccessControlManager acMgr) throws RepositoryException {
            return AccessControlUtils.privilegesFromNames(acMgr, privileges.toArray(new String[0]));
        }
    }

    private static class ACE extends AbstractEntry {

        private final boolean allow;
        private final String principalName;

        private ACE(DocViewNode2 childNode) {
            super(childNode);
            String primaryType = childNode.getPrimaryType().orElseThrow(() -> new IllegalStateException("mandatory property 'jcr:primaryType' missing on ace node"));
            if ("rep:GrantACE".equals(primaryType)) {
                allow = true;
            } else if ("rep:DenyACE".equals(primaryType)) {
                allow = false;
            } else {
                throw new IllegalArgumentException("Unexpected node ACE type: " + childNode.getPrimaryType());
            }
            principalName = childNode.getPropertyValue(NameConstants.REP_PRINCIPAL_NAME).orElseThrow(() -> new IllegalStateException("mandatory property 'rep:principalName' missing"));
        }
    }

    private static class PrincipalEntry extends AbstractEntry {

        private final String effectivePath;

        private PrincipalEntry(DocViewNode2 node) {
            super(node);
            String v = node.getPropertyValue(NAME_REP_EFFECTIVE_PATH).orElseThrow(() -> new IllegalStateException("mandatory property 'rep:effectivePath ' missing on principal entry node"));
            if (v.isEmpty()) {
                effectivePath = null;
            } else {
                effectivePath = v;
            }
        }
    }
}
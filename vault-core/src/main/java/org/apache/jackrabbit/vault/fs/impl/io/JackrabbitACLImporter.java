/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.jackrabbit.vault.fs.impl.io;

import javax.jcr.NamespaceException;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.jcr.ValueFormatException;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.jackrabbit.spi.Name;
import org.apache.jackrabbit.spi.commons.conversion.DefaultNamePathResolver;
import org.apache.jackrabbit.spi.commons.conversion.NamePathResolver;
import org.apache.jackrabbit.spi.commons.name.NameConstants;
import org.apache.jackrabbit.spi.commons.name.NameFactoryImpl;
import org.apache.jackrabbit.vault.fs.io.AccessControlHandling;
import org.apache.jackrabbit.vault.fs.spi.impl.jcr20.JackrabbitACLManagement;
import org.apache.jackrabbit.vault.fs.spi.impl.jcr20.accesscontrol.AbstractAccessControlEntry;
import org.apache.jackrabbit.vault.fs.spi.impl.jcr20.accesscontrol.JackrabbitAccessControlEntryBuilder;
import org.apache.jackrabbit.vault.fs.spi.impl.jcr20.accesscontrol.JackrabbitAccessControlPolicy;
import org.apache.jackrabbit.vault.fs.spi.impl.jcr20.accesscontrol.JackrabbitAccessControlPolicyBuilder;
import org.apache.jackrabbit.vault.fs.spi.impl.jcr20.accesscontrol.PrincipalBasedAccessControlEntry;
import org.apache.jackrabbit.vault.fs.spi.impl.jcr20.accesscontrol.PrincipalBasedAccessControlList;
import org.apache.jackrabbit.vault.fs.spi.impl.jcr20.accesscontrol.PrincipalSetAccessControlPolicy;
import org.apache.jackrabbit.vault.fs.spi.impl.jcr20.accesscontrol.ResourceBasedAccessControlEntry;
import org.apache.jackrabbit.vault.fs.spi.impl.jcr20.accesscontrol.ResourceBasedAccessControlList;
import org.apache.jackrabbit.vault.util.DocViewNode2;
import org.apache.jackrabbit.vault.util.DocViewProperty2;
import org.apache.jackrabbit.vault.util.UncheckedRepositoryException;
import org.apache.jackrabbit.vault.util.UncheckedValueFormatException;
import org.slf4j.Logger;

/**
 * Implements a doc view adapter that reads the ACL information of the docview hierarchy and applies it to the
 * underlying repository, based on the {@link org.apache.jackrabbit.vault.fs.io.AccessControlHandling}
 */
public class JackrabbitACLImporter implements DocViewAdapter {

    // TODO: move to repository impl specific package
    private static final Name NAME_REP_EFFECTIVE_PATH =
            NameFactoryImpl.getInstance().create(Name.NS_REP_URI, "effectivePath");
    private static final Name NAME_REP_PRINCIPAL_NAMES =
            NameFactoryImpl.getInstance().create(Name.NS_REP_URI, "principalNames");

    /**
     * default logger
     */
    private static final Logger log = DocViewImporter.log;

    private final Session session;

    private final AccessControlHandling aclHandling;

    private final String accessControlledPath;

    private final NamePathResolver resolver;

    /**
     * The state representing the level of the last evaluated node (i.e. the parent)
     *
     */
    private enum State {
        INITIAL,
        RESOURCE_BASED_ACL,
        PRINCIPAL_BASED_ACL,
        PRINCIPAL_SET_POLICY,
        RESOURCE_BASED_ACE,
        PRINCIPAL_BASED_ACE,
        RESTRICTION
    }

    /** all property names on either rep:GrantACE/rep:DenyACE or rep:Restrictions which don't represent an access control restriction */
    private static final Set<Name> NON_RESTRICTION_PROPERTY_NAMES = new HashSet<>(Arrays.asList(
            NameConstants.REP_PRINCIPAL_NAME,
            NameConstants.JCR_PRIMARYTYPE,
            NameConstants.JCR_MIXINTYPES,
            NameConstants.REP_PRIVILEGES));

    private JackrabbitAccessControlPolicyBuilder<?> policyBuilder;
    private JackrabbitAccessControlEntryBuilder<? extends AbstractAccessControlEntry> entryBuilder;

    private final Deque<State> states = new LinkedList<>();

    public JackrabbitACLImporter(Node accessControlledNode, AccessControlHandling aclHandling)
            throws RepositoryException {
        this(accessControlledNode.getSession(), accessControlledNode.getPath(), aclHandling);
    }

    public JackrabbitACLImporter(Session session, AccessControlHandling aclHandling) throws RepositoryException {
        this(session, null, aclHandling);
    }

    private JackrabbitACLImporter(Session session, String path, AccessControlHandling aclHandling)
            throws RepositoryException {
        if (aclHandling == AccessControlHandling.CLEAR || aclHandling == AccessControlHandling.IGNORE) {
            throw new RepositoryException(
                    "Error while reading access control content: unsupported AccessControlHandling: " + aclHandling);
        }
        this.accessControlledPath = path;
        this.session = session;
        this.aclHandling = aclHandling;
        this.states.push(State.INITIAL);
        this.resolver = new DefaultNamePathResolver(session);
    }

    public void startNode(DocViewNode2 node) throws RepositoryException, IOException {
        State state = states.peek();
        try {
            switch (state) {
                case INITIAL:
                    String primaryType = node.getPrimaryType()
                            .orElseThrow(() -> new IllegalStateException(
                                    "Error while reading access control content: Missing 'jcr:primaryType'"));
                    if (JackrabbitACLManagement.NT_REP_ACL.equals(primaryType)) {
                        policyBuilder = new ResourceBasedAccessControlList.Builder();
                        state = State.RESOURCE_BASED_ACL;
                    } else if (JackrabbitACLManagement.NT_REP_CUG_POLICY.equals(primaryType)) {
                        // just collect the rep:principalNames property
                        Collection<String> principalNames = node.getPropertyValues(NAME_REP_PRINCIPAL_NAMES);
                        policyBuilder = new PrincipalSetAccessControlPolicy.Builder(principalNames);
                        state = State.PRINCIPAL_SET_POLICY;
                    } else if (JackrabbitACLManagement.NT_REP_PRINCIPAL_POLICY.equals(primaryType)) {
                        String principalName = node.getPropertyValue(NameConstants.REP_PRINCIPAL_NAME)
                                .orElseThrow(() -> new IllegalStateException(
                                        "mandatory property 'rep:principalName' missing on principal policy node"));
                        policyBuilder = new PrincipalBasedAccessControlList.Builder(principalName);
                        state = State.PRINCIPAL_BASED_ACL;
                    } else {
                        throw new IOException(
                                "Error while reading access control content: Expected rep:ACL, rep:PrincipalPolicy or rep:CugPolicy primary type but found: "
                                        + node.getPrimaryType().toString());
                    }
                    break;
                case RESOURCE_BASED_ACL:
                case PRINCIPAL_BASED_ACL:
                case RESOURCE_BASED_ACE:
                case PRINCIPAL_BASED_ACE:
                case RESTRICTION:
                    state = startEntryNode(node, state);
                    break;
                case PRINCIPAL_SET_POLICY:
                    throw new IOException("Error while reading access control content: Unexpected node: "
                            + node.getPrimaryType().orElse("") + " for state " + state);
            }
        } catch (UncheckedRepositoryException e) {
            throw e.getCause();
        }
        states.push(state);
    }

    /**
     * Extracts all information from rep:GrantACE/rep:DenyACE and children.
     * This is used for both resource-based and principal based access control entries.
     *
     * @param node
     * @param state
     * @return
     * @see <a href="https://jackrabbit.apache.org/oak/docs/security/accesscontrol/default.html#representation-in-the-repository">Oak Access Control Management : The Default Implementation</a>
     * @see <a href="https://jackrabbit.apache.org/oak/docs/security/authorization/principalbased.html#representation-in-the-repository">Oak Principal Based Access Control Management</a>
     * @see <a href="https://jackrabbit.apache.org/oak/docs/security/authorization/restriction.html#representation-in-the-repository">Oak Restrictions</a>
     */
    private State startEntryNode(DocViewNode2 node, State state) throws IOException {
        final State newState;
        switch (state) {
            case RESOURCE_BASED_ACL: {
                final boolean allow;
                final String primaryType = node.getPrimaryType()
                        .orElseThrow(() ->
                                new IllegalStateException("mandatory property 'jcr:primaryType' missing on ace node"));
                if (JackrabbitACLManagement.NT_REP_GRANT_ACE.equals(primaryType)) {
                    allow = true;
                } else if (JackrabbitACLManagement.NT_REP_DENY_ACE.equals(primaryType)) {
                    allow = false;
                } else {
                    throw new IOException(
                            "Unexpected node ACE type inside resource based ACL: " + node.getPrimaryType());
                }
                final String principalName = node.getPropertyValue(NameConstants.REP_PRINCIPAL_NAME)
                        .orElseThrow(() -> new IllegalStateException("mandatory property 'rep:principalName' missing"));
                Collection<String> privileges = node.getPropertyValues(NameConstants.REP_PRIVILEGES);
                entryBuilder = new ResourceBasedAccessControlEntry.Builder(privileges, allow, principalName);
                extractRestrictions(node).entrySet().stream()
                        .forEach(entry -> entryBuilder.addRestriction(entry.getKey(), entry.getValue()));
                newState = State.RESOURCE_BASED_ACE;
                break;
            }
            case PRINCIPAL_BASED_ACL: {
                if (!"rep:PrincipalEntry"
                        .equals(node.getPrimaryType()
                                .orElseThrow(() -> new IllegalStateException(
                                        "mandatory property 'jcr:primaryType' missing on principal policy node")))) {
                    throw new IOException(
                            "Unexpected node ACE type inside principal based ACL: " + node.getPrimaryType());
                }
                Collection<String> privileges = node.getPropertyValues(NameConstants.REP_PRIVILEGES);
                String v = node.getPropertyValue(NAME_REP_EFFECTIVE_PATH)
                        .orElseThrow(() -> new IllegalStateException(
                                "mandatory property 'rep:effectivePath ' missing on principal entry node"));
                final String effectivePath;
                if (v.isEmpty()) {
                    effectivePath = null;
                } else {
                    effectivePath = v;
                }
                entryBuilder = new PrincipalBasedAccessControlEntry.Builder(privileges, effectivePath);
                newState = State.PRINCIPAL_BASED_ACE;
                break;
            }
            case RESOURCE_BASED_ACE:
            case PRINCIPAL_BASED_ACE: {
                if (!JackrabbitACLManagement.NT_REP_RESTRICTIONS.equals(node.getPrimaryType()
                        .orElseThrow(() -> new IllegalStateException(
                                "mandatory property 'jcr:primaryType' missing on principal policy node")))) {
                    throw new IllegalArgumentException(
                            "Unexpected restriction type inside principal or resource based ACE: "
                                    + node.getPrimaryType());
                }
                extractRestrictions(node).entrySet().stream()
                        .forEach(entry -> entryBuilder.addRestriction(entry.getKey(), entry.getValue()));
                newState = State.RESTRICTION;
                break;
            }
            case RESTRICTION:
                throw new IOException(
                        "Restriction nodes are not supposed to have any children but found " + node.toString());
            default:
                throw new IllegalArgumentException("This method must not be called with state " + state);
        }
        return newState;
    }

    private Map<String, Value[]> extractRestrictions(DocViewNode2 node) {
        return node.getProperties().stream()
                .filter(p -> (!NON_RESTRICTION_PROPERTY_NAMES.contains(p.getName())))
                .collect(Collectors.<DocViewProperty2, String, Value[]>toMap(
                        p -> {
                            try {
                                return resolver.getJCRName(p.getName());
                            } catch (NamespaceException e) {
                                // should not happen
                                throw new IllegalStateException(
                                        "Cannot retrieve qualified name for "
                                                + p.getName().toString(),
                                        e);
                            }
                        },
                        p -> {
                            try {
                                return p.getValues(session.getValueFactory()).toArray(new Value[0]);
                            } catch (ValueFormatException e) {
                                throw new UncheckedValueFormatException(e);
                            } catch (RepositoryException e) {
                                throw new UncheckedRepositoryException(e);
                            }
                        }));
    }

    public void endNode() {
        State state = states.pop();
        switch (state) {
            case RESOURCE_BASED_ACE:
            case PRINCIPAL_BASED_ACE: {
                policyBuilder.addEntry(entryBuilder.build());
                break;
            }
            default:
            // nothing happens in all other states
        }
    }

    public List<String> close() throws RepositoryException {
        if (states.peek() != State.INITIAL) {
            log.error("Unexpected end state: {}", states.peek());
        }
        JackrabbitAccessControlPolicy policy = policyBuilder.build();
        return policy.apply(session, aclHandling, accessControlledPath);
    }
}

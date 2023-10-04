/*
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
 */

package org.apache.jackrabbit.vault.fs.spi.impl.jcr20;

import java.security.Principal;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.security.AccessControlManager;
import javax.jcr.security.AccessControlPolicy;

import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.JackrabbitAccessControlManager;
import org.apache.jackrabbit.api.security.JackrabbitAccessControlPolicy;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.jackrabbit.commons.JcrUtils;
import org.apache.jackrabbit.vault.fs.spi.ACLManagement;
import org.apache.jackrabbit.vault.fs.spi.UserManagement;
import org.apache.jackrabbit.vault.util.UncheckedRepositoryException;
import org.jetbrains.annotations.NotNull;

/**
 * This is Jackrabbit/Oak specific as it is not defined by JCR 2.0 how access control policies are persisted.
 */
public class JackrabbitACLManagement implements ACLManagement {

    // Constants copied from Oak classes
    
    /**
     * The primary node type name of the CUG policy node.
     */
    public static final String NT_REP_CUG_POLICY = "rep:CugPolicy";

    /**
     * The name of the CUG policy node.
     */
    public static final String REP_CUG_POLICY = "rep:cugPolicy";
    
    /** the name of the ACL policy node (resource based) */
    public static final String REP_POLICY = "rep:policy";

    /** the name of the ACL policy node (principal based) */
    public static final String REP_PRINCIPAL_POLICY = "rep:principalPolicy";
    /**
     * The primary node type name of the principal based access control policy node.
     */
    public static final String NT_REP_PRINCIPAL_POLICY = "rep:PrincipalPolicy";

    /**
     * The primary node type name of intermediate folders within which authorizables might be found.
     */
    public static final String NT_REP_AUTHORIZABLE_FOLDER = "rep:AuthorizableFolder";
   
    /** the name of the repository wide ACL policy node (both principal and resource based) */
    public static final String REP_REPO_POLICY = "rep:repoPolicy";
    
    /**
     * Node type name of ancestor for both {@link #NT_REP_PRINCIPAL_POLICY} and {@link #NT_REP_ACL}
     */
    public static final String NT_REP_POLICY = "rep:Policy";
    
    /**
     * The primary node type name of the resource based access control policy node.
     */
    public static final String NT_REP_ACL = "rep:ACL";
    public static final String NT_REP_ACE = "rep:ACE";
    public static final String NT_REP_GRANT_ACE = "rep:GrantACE";
    public static final String NT_REP_DENY_ACE = "rep:DenyACE";
    public static final String NT_REP_RESTRICTIONS = "rep:Restrictions";

    public static final String MIX_REP_ACCESS_CONTROLLABLE = "rep:AccessControllable";
    public static final String MIX_REP_REPO_ACCESS_CONTROLLABLE = "rep:RepoAccessControllable";
    /**
     * The name of the mixin type that defines the CUG policy node.
     */
    public static final String MIX_REP_CUG_MIXIN = "rep:CugMixin";

    /**
     * The name of the mixin type that defines the principal based access control policy node.
     */
    public static final String MIX_REP_PRINCIPAL_BASED_MIXIN = "rep:PrincipalBasedMixin";

    private final UserManagement userManagement;

    public JackrabbitACLManagement() {
        userManagement = new JackrabbitUserManagement();
    }

    /**
     * {@inheritDoc}
     */
    public boolean isACLNodeType(String name) {
        return name.equals(NT_REP_ACL) || name.equals(NT_REP_CUG_POLICY) || name.equals(NT_REP_PRINCIPAL_POLICY);
    }

    /**
     * {@inheritDoc}
     */
    public boolean isAccessControllableMixin(String name) {
        return name.equals(MIX_REP_ACCESS_CONTROLLABLE)
                || name.equals(MIX_REP_REPO_ACCESS_CONTROLLABLE)
                || name.equals(MIX_REP_CUG_MIXIN)
                || name.equals(MIX_REP_PRINCIPAL_BASED_MIXIN);
    }

    /**
     * {@inheritDoc}
     */
    public boolean isACLNode(Node node) throws RepositoryException {
        return node.isNodeType(NT_REP_POLICY);
    }

    /**
     * {@inheritDoc}
     */
    public boolean ensureAccessControllable(Node node, String policyPrimaryType) throws RepositoryException {
        boolean modified = false;
        if (NT_REP_ACL.equals(policyPrimaryType)) {
            if (!node.isNodeType(MIX_REP_ACCESS_CONTROLLABLE)) {
                node.addMixin(MIX_REP_ACCESS_CONTROLLABLE);
                modified = true;
            }
            if (isRootNode(node) && !node.isNodeType(MIX_REP_REPO_ACCESS_CONTROLLABLE)) {
                node.addMixin(MIX_REP_REPO_ACCESS_CONTROLLABLE);
                modified = true;
            }
        } else if (NT_REP_CUG_POLICY.equals(policyPrimaryType)) {
            if (!node.isNodeType(MIX_REP_CUG_MIXIN)) {
                node.addMixin(MIX_REP_CUG_MIXIN);
                modified = true;
            }
        } else if (NT_REP_PRINCIPAL_POLICY.equals(policyPrimaryType)) {
            if (!node.isNodeType(MIX_REP_PRINCIPAL_BASED_MIXIN)) {
                node.addMixin(MIX_REP_PRINCIPAL_BASED_MIXIN);
                modified = true;
            }
        }
        return modified;
    }

    /**
     * {@inheritDoc}
     */
    public void clearACL(Node node) throws RepositoryException {
        AccessControlManager ac = node.getSession().getAccessControlManager();
        String pPath = node.getPath();
        for (AccessControlPolicy p: ac.getPolicies(pPath)) {
            ac.removePolicy(pPath, p);
        }
        if (isRootNode(node)) {
            for (AccessControlPolicy p: ac.getPolicies(null)) {
                ac.removePolicy(null, p);
            }
        }
    }

    //--------------------------------------------------------------------------
    private static boolean isRootNode(Node node) throws RepositoryException {
        return node.getDepth() == 0;
    }

    private boolean isAuthorizable(Node node) throws RepositoryException {
        return userManagement.isAuthorizableNodeType(node.getPrimaryNodeType().getName());
    }

    private boolean isAuthorizableFolder(Node node) throws RepositoryException {
        return node.isNodeType(NT_REP_AUTHORIZABLE_FOLDER);
    }

    private boolean areAuthorizablesAllowed(Node node) throws RepositoryException {
        return isAuthorizableFolder(node) || isAuthorizable(node);
    }

    @Override
    public @NotNull Map<String, List<? extends AccessControlPolicy>> getPrincipalAcls(Node node) throws RepositoryException {
        // first do a quick check if path may contain principal ACLs at all before triggering expensive traversal
        if (!areAuthorizablesAllowed(node)) {
            return Collections.emptyMap();
        }
        JackrabbitSession jrSession = (JackrabbitSession)node.getSession();
        AccessControlManager acMgr = jrSession.getAccessControlManager();
        if (!(acMgr instanceof JackrabbitAccessControlManager)) {
            throw new RepositoryException("The access control manager returned is no JackrabbitAccessControlManager, this is probably not a Jackrabbit/Oak repository");
        }
        JackrabbitAccessControlManager jrAcMgr = (JackrabbitAccessControlManager) acMgr;
        PrincipalAccessControlPolicyCollector policiesCollector = new PrincipalAccessControlPolicyCollector(jrAcMgr);
        try {
            findPrincipalsRecursively(jrSession.getUserManager(), node, policiesCollector);
            return policiesCollector.getPoliciesPerPrincipal();
        } catch (UncheckedRepositoryException e) {
            throw e.getCause();
        }
    }

    private static final class PrincipalAccessControlPolicyCollector implements Consumer<Principal> {

        private final JackrabbitAccessControlManager jrAcMgr;
        private final Map<String, List<? extends AccessControlPolicy>> policiesPerPrincipal;

        public PrincipalAccessControlPolicyCollector(JackrabbitAccessControlManager jrAcMgr) {
            super();
            this.jrAcMgr = jrAcMgr;
            this.policiesPerPrincipal = new HashMap<>();
        }

        public Map<String, List<? extends AccessControlPolicy>> getPoliciesPerPrincipal() {
            return policiesPerPrincipal;
        }

        @Override
        public void accept(Principal principal) {
            try {
                List<JackrabbitAccessControlPolicy> policies = Arrays.asList(jrAcMgr.getPolicies(principal));
                if (!policies.isEmpty()) {
                    policiesPerPrincipal.put(principal.getName(), policies);
                }
            } catch (RepositoryException e) {
                throw new UncheckedRepositoryException(e);
            }
        }
    }

    private void findPrincipalsRecursively(UserManager userMgr, Node node, Consumer<Principal> principalConsumer) throws RepositoryException {
        if (isAuthorizable(node)) {
            Authorizable authorizable = userMgr.getAuthorizableByPath(node.getPath());
            if (authorizable != null) {
                principalConsumer.accept(authorizable.getPrincipal());
            }
        } else if (isAuthorizableFolder(node)) {
            for (Node child : JcrUtils.in(((Iterator<Node>)node.getNodes()))) {
                findPrincipalsRecursively(userMgr, child, principalConsumer);
            }
        }
    }

}
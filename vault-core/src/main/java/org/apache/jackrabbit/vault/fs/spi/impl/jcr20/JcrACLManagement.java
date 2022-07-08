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

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.security.AccessControlManager;
import javax.jcr.security.AccessControlPolicy;

import org.apache.jackrabbit.vault.fs.spi.ACLManagement;

/**
 * {@code JcrACLManagement}...
 * This is Jackrabbit/Oak specific as it is not defined by JCR 2.0 how access control policies are persisted.
 */
public class JcrACLManagement implements ACLManagement {

    /**
     * {@inheritDoc}
     */
    public boolean isACLNodeType(String name) {
        // all those inherit from rep:Policy
        return name.equals("rep:ACL") || name.equals("rep:CugPolicy") || name.equals("rep:PrincipalPolicy");
    }

    /**
     * {@inheritDoc}
     */
    public boolean isAccessControllableMixin(String name) {
        return name.equals("rep:AccessControllable")
                || name.equals("rep:RepoAccessControllable")
                || name.equals("rep:CugMixin")
                || name.equals("rep:PrincipalBasedMixin");
    }

    /**
     * {@inheritDoc}
     */
    public boolean isACLNode(Node node) throws RepositoryException {
        return node.isNodeType("rep:Policy");
    }

    /**
     * {@inheritDoc}
     */
    public boolean ensureAccessControllable(Node node, String policyPrimaryType) throws RepositoryException {
        boolean modified = false;
        if ("rep:ACL".equals(policyPrimaryType)) {
            if (!node.isNodeType("rep:AccessControllable")) {
                node.addMixin("rep:AccessControllable");
                modified = true;
            }
            if (isRootNode(node) && !node.isNodeType("rep:RepoAccessControllable")) {
                node.addMixin("rep:RepoAccessControllable");
                modified = true;
            }
        } else if ("rep:CugPolicy".equals(policyPrimaryType)) {
            if (!node.isNodeType("rep:CugMixin")) {
                node.addMixin("rep:CugMixin");
                modified = true;
            }
        } else if ("rep:PrincipalPolicy".equals(policyPrimaryType)) {
            if (!node.isNodeType("rep:PrincipalBasedMixin")) {
                node.addMixin("rep:PrincipalBasedMixin");
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
}
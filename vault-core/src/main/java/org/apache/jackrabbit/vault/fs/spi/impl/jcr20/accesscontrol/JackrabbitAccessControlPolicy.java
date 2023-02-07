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
package org.apache.jackrabbit.vault.fs.spi.impl.jcr20.accesscontrol;

import java.security.Principal;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.security.AccessControlException;
import javax.jcr.security.AccessControlManager;
import javax.jcr.security.AccessControlPolicy;
import javax.jcr.security.AccessControlPolicyIterator;

import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.JackrabbitAccessControlEntry;
import org.apache.jackrabbit.api.security.JackrabbitAccessControlList;
import org.apache.jackrabbit.api.security.JackrabbitAccessControlManager;
import org.apache.jackrabbit.api.security.authorization.PrincipalAccessControlList;
import org.apache.jackrabbit.api.security.authorization.PrincipalSetPolicy;
import org.apache.jackrabbit.api.security.principal.PrincipalManager;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.jackrabbit.vault.fs.io.AccessControlHandling;
import org.apache.jackrabbit.vault.fs.spi.impl.jcr20.SimplePrincipal;
import org.apache.jackrabbit.vault.util.UncheckedRepositoryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 
 * Abstraction on top of a JCR Access control policies (not bound to a JCR)
 */
public abstract class JackrabbitAccessControlPolicy {

    /** default logger */
    protected static final Logger log = LoggerFactory.getLogger(JackrabbitAccessControlPolicy.class);

    public static JackrabbitAccessControlPolicy fromAccessControlPolicy(AccessControlPolicy policy) throws RepositoryException {
        final JackrabbitAccessControlPolicyBuilder<? extends JackrabbitAccessControlPolicy> builder;
        if (policy instanceof PrincipalAccessControlList) {
            PrincipalAccessControlList principalAcl = (PrincipalAccessControlList)policy;
            builder = new PrincipalBasedAccessControlList.Builder(principalAcl.getPrincipal().getName());
            Arrays.stream(principalAcl.getAccessControlEntries()).map(PrincipalAccessControlList.Entry.class::cast).map(t -> {
                try {
                    return new PrincipalBasedAccessControlEntry(t);
                } catch (RepositoryException e) {
                    throw new UncheckedRepositoryException(e);
                }
            }).forEach(builder::addEntry);
        } else if (policy instanceof PrincipalSetPolicy) {
            PrincipalSetPolicy principalSetPolicy = (PrincipalSetPolicy)policy;
            builder = new PrincipalSetAccessControlPolicy.Builder(principalSetPolicy.getPrincipals().stream().map(Principal::getName).collect(Collectors.toList()));
        } else if (policy instanceof JackrabbitAccessControlList) {
            JackrabbitAccessControlList acl = (JackrabbitAccessControlList)policy;
            builder = new ResourceBasedAccessControlList.Builder();
            Arrays.stream(acl.getAccessControlEntries()).map(JackrabbitAccessControlEntry.class::cast).map(t -> {
                try {
                    return new ResourceBasedAccessControlEntry(t);
                } catch (RepositoryException e) {
                    throw new UncheckedRepositoryException(e);
                }
            }).forEach(builder::addEntry);
        } else {
            throw new RepositoryException("Unsupported policy type " + policy);
        }
        return builder.build();
    }

    JackrabbitAccessControlPolicy() {
    }

    Principal getPrincipal(Session session, final String principalName, final String accessControlledPath) throws RepositoryException {
        PrincipalManager pMgr = getPrincipalManager(session);
        Principal p = pMgr.getPrincipal(principalName);
        if (p == null) {
            try {
                Authorizable a = getUserManager(session).getAuthorizableByPath(accessControlledPath);
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
        return p;
    }

    Principal getPrincipal(final String principalName) {
        return new SimplePrincipal(principalName);
    }

    protected static final JackrabbitAccessControlManager getAccessControlManager(Session session) throws RepositoryException {
        AccessControlManager acMgr = session.getAccessControlManager();
        if (!(acMgr instanceof JackrabbitAccessControlManager)) {
            throw new IllegalStateException("The access control manager exposed by the given session is no JackrabbitAccessControlManager");
        }
        return (JackrabbitAccessControlManager)acMgr;
    }

    protected static final PrincipalManager getPrincipalManager(Session session) throws RepositoryException {
        if(!(session instanceof JackrabbitSession)) {
            throw new IllegalStateException("This session is not a JackrabbitSession");
        }
        return ((JackrabbitSession)session).getPrincipalManager();
    }

    protected static final UserManager getUserManager(Session session) throws RepositoryException {
        if(!(session instanceof JackrabbitSession)) {
            throw new IllegalStateException("This session is not a JackrabbitSession");
        }
        return ((JackrabbitSession)session).getUserManager();
    }

    <T> T getPolicy(JackrabbitAccessControlManager acMgr, Class<T> clz, final String accessControlledPath) throws RepositoryException {
        for (AccessControlPolicy p : acMgr.getPolicies(accessControlledPath)) {
            if (clz.isAssignableFrom(p.getClass())) {
                return clz.cast(p);
            }
        }
        return null;
    }

    <T> T getPolicy(JackrabbitAccessControlManager acMgr, Class<T> clz, Principal principal) throws RepositoryException {
        for (AccessControlPolicy p : acMgr.getPolicies(principal)) {
            if (clz.isAssignableFrom(p.getClass())) {
                return clz.cast(p);
            }
        }
        return null;
    }

    <T> T getApplicablePolicy(JackrabbitAccessControlManager acMgr, Class<T> clz, final String accessControlledPath) throws RepositoryException {
        AccessControlPolicyIterator iter = acMgr.getApplicablePolicies(accessControlledPath);
        while (iter.hasNext()) {
            AccessControlPolicy p = iter.nextAccessControlPolicy();
            if (clz.isAssignableFrom(p.getClass())) {
                return clz.cast(p);
            }
        }

        // no applicable policy
        throw new RepositoryException("no applicable AccessControlPolicy of type " + clz + " on " +
                (accessControlledPath == null ? "'root'" : accessControlledPath));
    }

    <T> T getApplicablePolicy(JackrabbitAccessControlManager acMgr, Class<T> clz, Principal principal) throws RepositoryException {
        for (AccessControlPolicy p : acMgr.getApplicablePolicies(principal)) {
            if (clz.isAssignableFrom(p.getClass())) {
                return clz.cast(p);
            }
        }

        // no applicable policy
        throw new AccessControlException("no applicable AccessControlPolicy of type " + clz + " for " + principal.getName());
    }

    /**
     * Imports the policy into the repository according to the rules from {@code aclHandling}.
     * @param session
     * @param aclHandling
     * @param accessControlledPath the path under which the policy is supposed to be added/imported (not necessarily equal to the path the policy affects)
     * @return the paths which have been modified or added
     * 
     * @throws RepositoryException
     */
    public abstract List<String> apply(Session session, AccessControlHandling aclHandling, String accessControlledPath) throws RepositoryException;
}

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
package org.apache.jackrabbit.vault.fs.spi.impl.jcr20.accesscontrol;

import javax.jcr.RepositoryException;
import javax.jcr.Session;

import java.security.Principal;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.jackrabbit.api.security.JackrabbitAccessControlManager;
import org.apache.jackrabbit.api.security.authorization.PrincipalSetPolicy;
import org.apache.jackrabbit.vault.fs.io.AccessControlHandling;
import org.apache.jackrabbit.vault.fs.spi.impl.jcr20.JackrabbitACLManagement;

public class PrincipalSetAccessControlPolicy extends JackrabbitAccessControlPolicy {

    public static final class Builder implements JackrabbitAccessControlPolicyBuilder<JackrabbitAccessControlPolicy> {
        private final Collection<String> principalNames;

        public Builder(Collection<String> principalNames) {
            this.principalNames = new HashSet<>(principalNames);
        }

        @Override
        public void addEntry(AbstractAccessControlEntry entry) {
            throw new UnsupportedOperationException("This policy type does not support entries");
        }

        @Override
        public PrincipalSetAccessControlPolicy build() {
            return new PrincipalSetAccessControlPolicy(principalNames);
        }
    }

    private final Collection<String> principalNames;

    public PrincipalSetAccessControlPolicy(Collection<String> principalNames) {
        this.principalNames = new HashSet<>(principalNames);
    }

    @Override
    public List<String> apply(Session session, AccessControlHandling aclHandling, String accessControlledPath)
            throws RepositoryException {
        if (aclHandling == AccessControlHandling.IGNORE) {
            return Collections.emptyList();
        }
        JackrabbitAccessControlManager acMgr = getAccessControlManager(session);
        PrincipalSetPolicy psPolicy = getPolicy(acMgr, PrincipalSetPolicy.class, accessControlledPath);
        if (psPolicy != null) {
            Set<Principal> existingPrincipals = psPolicy.getPrincipals();
            // remove existing policy for 'overwrite'
            if (aclHandling == AccessControlHandling.OVERWRITE) {
                psPolicy.removePrincipals(existingPrincipals.toArray(new Principal[existingPrincipals.size()]));
            }
        } else {
            psPolicy = getApplicablePolicy(acMgr, PrincipalSetPolicy.class, accessControlledPath);
        }

        // TODO: correct behavior for MERGE and MERGE_PRESERVE?
        Principal[] principals =
                principalNames.stream().map(name -> getPrincipal(name)).toArray(Principal[]::new);

        psPolicy.addPrincipals(principals);
        acMgr.setPolicy(accessControlledPath, psPolicy);

        final String path;
        if ("/".equals(accessControlledPath)) {
            path = "/" + JackrabbitACLManagement.REP_CUG_POLICY;
        } else {
            path = accessControlledPath + "/" + JackrabbitACLManagement.REP_CUG_POLICY;
        }
        return Collections.singletonList(path);
    }
}

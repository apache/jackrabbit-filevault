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
import javax.jcr.Value;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.jackrabbit.api.security.JackrabbitAccessControlManager;
import org.apache.jackrabbit.api.security.authorization.PrincipalAccessControlList;
import org.apache.jackrabbit.vault.fs.io.AccessControlHandling;
import org.apache.jackrabbit.vault.fs.spi.impl.jcr20.JackrabbitACLManagement;

public class PrincipalBasedAccessControlList extends JackrabbitAccessControlPolicy {

    public static final class Builder implements JackrabbitAccessControlPolicyBuilder<PrincipalBasedAccessControlList> {
        private final List<PrincipalBasedAccessControlEntry> entries = new ArrayList<>();
        private final String principalName;

        public Builder(String principalName) {
            this.principalName = principalName;
        }

        @Override
        public void addEntry(AbstractAccessControlEntry entry) {
            if (!(entry instanceof PrincipalBasedAccessControlEntry)) {
                throw new IllegalStateException("Only entries of type PrincipalBasedAccessControlEntry are supported");
            }
            entries.add((PrincipalBasedAccessControlEntry) entry);
        }

        @Override
        public PrincipalBasedAccessControlList build() {
            return new PrincipalBasedAccessControlList(principalName, entries);
        }
    }

    private final String principalName;
    private final List<PrincipalBasedAccessControlEntry> entries = new ArrayList<>();

    private PrincipalBasedAccessControlList(String principalName, List<PrincipalBasedAccessControlEntry> entries) {
        this.entries.addAll(entries);
        this.principalName = principalName;
    }

    @Override
    public List<String> apply(Session session, final AccessControlHandling aclHandling, String accessControlledPath)
            throws RepositoryException {
        if (aclHandling == AccessControlHandling.IGNORE) {
            return Collections.emptyList();
        }
        if (aclHandling == AccessControlHandling.MERGE_PRESERVE) {
            log.debug("MERGE_PRESERVE for principal-based access control list is equivalent to IGNORE.");
            return Collections.emptyList();
        }

        JackrabbitAccessControlManager acMgr = getAccessControlManager(session);
        Principal principal = getPrincipal(session, principalName, accessControlledPath);
        PrincipalAccessControlList acl = getPolicy(acMgr, PrincipalAccessControlList.class, principal);
        if (acl != null && aclHandling == AccessControlHandling.OVERWRITE) {
            // remove existing policy for 'OVERWRITE'
            acMgr.removePolicy(acl.getPath(), acl);
            acl = null;
        }

        if (acl == null) {
            acl = getApplicablePolicy(acMgr, PrincipalAccessControlList.class, principal);
        }

        // apply ACEs of package for MERGE and OVERWRITE
        for (PrincipalBasedAccessControlEntry entry : entries) {
            Entry<Map<String, Value>, Map<String, Value[]>> restrictions = entry.separateRestrictions(acl);
            acl.addEntry(
                    entry.effectivePath, entry.getPrivileges(acMgr), restrictions.getKey(), restrictions.getValue());
        }
        acMgr.setPolicy(acl.getPath(), acl);

        final String path;
        if (acl.getPath() == null) {
            path = "/" + JackrabbitACLManagement.REP_REPO_POLICY;
        } else if ("/".equals(acl.getPath())) {
            path = "/" + JackrabbitACLManagement.REP_PRINCIPAL_POLICY;
        } else {
            path = acl.getPath() + "/" + JackrabbitACLManagement.REP_PRINCIPAL_POLICY;
        }
        return Collections.singletonList(path);
    }
}

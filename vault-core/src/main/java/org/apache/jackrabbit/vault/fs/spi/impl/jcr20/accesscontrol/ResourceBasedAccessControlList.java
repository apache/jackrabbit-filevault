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
import javax.jcr.security.AccessControlEntry;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.jackrabbit.api.security.JackrabbitAccessControlList;
import org.apache.jackrabbit.api.security.JackrabbitAccessControlManager;
import org.apache.jackrabbit.vault.fs.io.AccessControlHandling;
import org.apache.jackrabbit.vault.fs.spi.impl.jcr20.JackrabbitACLManagement;

public class ResourceBasedAccessControlList extends JackrabbitAccessControlPolicy {

    public static final class Builder implements JackrabbitAccessControlPolicyBuilder<ResourceBasedAccessControlList> {
        private final List<ResourceBasedAccessControlEntry> entries;

        public Builder() {
            entries = new ArrayList<>();
        }

        @Override
        public void addEntry(AbstractAccessControlEntry entry) {
            if (!(entry instanceof ResourceBasedAccessControlEntry)) {
                throw new IllegalStateException("Only entries of type ResourceBasedAccessControlEntry are supported");
            }
            entries.add((ResourceBasedAccessControlEntry) entry);
        }

        @Override
        public ResourceBasedAccessControlList build() {
            return new ResourceBasedAccessControlList(entries);
        }
    }

    private final List<ResourceBasedAccessControlEntry> entries = new ArrayList<>();

    ResourceBasedAccessControlList(List<ResourceBasedAccessControlEntry> aceList) {
        super();
        this.entries.addAll(aceList);
    }

    @Override
    public List<String> apply(Session session, final AccessControlHandling aclHandling, String accessControlledPath)
            throws RepositoryException {
        if (aclHandling == AccessControlHandling.IGNORE) {
            return Collections.emptyList();
        }
        JackrabbitAccessControlManager acMgr = getAccessControlManager(session);
        // find principals of existing ACL
        JackrabbitAccessControlList acl = getPolicy(acMgr, JackrabbitAccessControlList.class, accessControlledPath);
        Set<String> existingPrincipals = new HashSet<>();
        if (acl != null) {
            for (AccessControlEntry ace : acl.getAccessControlEntries()) {
                existingPrincipals.add(ace.getPrincipal().getName());
            }

            // remove existing policy for 'overwrite'
            if (aclHandling == AccessControlHandling.OVERWRITE) {
                acMgr.removePolicy(accessControlledPath, acl);
                acl = null;
            }
        }

        if (acl == null) {
            acl = getApplicablePolicy(acMgr, JackrabbitAccessControlList.class, accessControlledPath);
        }

        // clear all ACEs of the package principals for merge (VLT-94), otherwise the `acl.addEntry()` below
        // might just combine the privileges.
        if (aclHandling == AccessControlHandling.MERGE) {
            for (ResourceBasedAccessControlEntry entry : entries) {
                for (AccessControlEntry ace : acl.getAccessControlEntries()) {
                    if (ace.getPrincipal().getName().equals(entry.principalName)) {
                        acl.removeAccessControlEntry(ace);
                    }
                }
            }
        }

        // apply ACEs of package
        for (ResourceBasedAccessControlEntry ace : entries) {
            final String principalName = ace.principalName;
            if (aclHandling == AccessControlHandling.MERGE_PRESERVE && existingPrincipals.contains(principalName)) {
                // skip principal if it already has an ACL
                continue;
            }
            Principal principal = getPrincipal(principalName);
            Entry<Map<String, Value>, Map<String, Value[]>> restrictions = ace.separateRestrictions(acl);
            acl.addEntry(
                    principal, ace.getPrivileges(acMgr), ace.allow, restrictions.getKey(), restrictions.getValue());
        }
        acMgr.setPolicy(accessControlledPath, acl);
        final String path;
        if (accessControlledPath == null) {
            path = "/" + JackrabbitACLManagement.REP_REPO_POLICY;
        } else if ("/".equals(accessControlledPath)) {
            path = "/" + JackrabbitACLManagement.REP_POLICY;
        } else {
            path = accessControlledPath + "/" + JackrabbitACLManagement.REP_POLICY;
        }
        return Collections.singletonList(path);
    }
}

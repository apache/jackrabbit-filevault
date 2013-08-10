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

package org.apache.jackrabbit.vault.packaging.integration;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.jcr.RepositoryException;
import javax.jcr.security.AccessControlEntry;
import javax.jcr.security.AccessControlPolicy;
import javax.jcr.security.Privilege;

import org.apache.jackrabbit.api.security.JackrabbitAccessControlEntry;
import org.apache.jackrabbit.api.security.JackrabbitAccessControlList;
import org.apache.jackrabbit.vault.packaging.JcrPackage;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

/**
 * AC Handling and import mode tests
 */
public class TestACLAndMerge extends IntegrationTestBase {

    @Override
    public void tearDown() throws Exception {
        // remove test node
        if (admin.nodeExists("/testroot")) {
            admin.getNode("/testroot").remove();
            admin.save();
        }
        super.tearDown();
    }

    /**
     * Installs 2 packages both with AC Handling OVERWRITE and Import Mode MERGE and tests if AC Handling wins over
     * import mode.
     */
    @Test
    public void testACAndMerge() throws RepositoryException, IOException, PackageException {
        assertNodeMissing("/testroot");

        JcrPackage pack = packMgr.upload(getStream("testpackages/mode_ac_test_a.zip"), false);
        assertNotNull(pack);
        pack.install(getDefaultOptions());

        // test if nodes and ACLs of first package exist
        assertNodeExists("/testroot/node_a");
        assertPermission("/testroot/secured", false, new String[]{"jcr:all"}, "everyone", null);

        pack = packMgr.upload(getStream("testpackages/mode_ac_test_b.zip"), false);
        assertNotNull(pack);
        pack.install(getDefaultOptions());

        // test if nodes and ACLs of 2nd package exist
        assertNodeExists("/testroot/node_a");
        assertNodeExists("/testroot/node_b");
        assertPermission("/testroot/secured", false, new String[]{"jcr:all"}, "everyone", null);
        assertPermission("/testroot/secured", true, new String[]{"jcr:read"}, "everyone", "*/foo/*");

    }

    /**
     * Installs 2 packages with the same ACL. the later packages has AC Handling MERGE and should overwrite the
     * existing ACL.
     */
    @Test
    public void testACMerge() throws RepositoryException, IOException, PackageException {
        assertNodeMissing("/testroot");

        JcrPackage pack = packMgr.upload(getStream("testpackages/mode_ac_test_a.zip"), false);
        assertNotNull(pack);
        pack.install(getDefaultOptions());

        // test if nodes and ACLs of first package exist
        assertNodeExists("/testroot/node_a");
        assertPermission("/testroot/secured", false, new String[]{"jcr:all"}, "everyone", null);

        pack = packMgr.upload(getStream("testpackages/mode_ac_test_b_merge.zip"), false);
        assertNotNull(pack);
        pack.install(getDefaultOptions());

        // test if nodes and ACLs of 2nd package exist
        assertNodeExists("/testroot/node_a");
        assertNodeExists("/testroot/node_b");
        assertPermission("/testroot/secured", false, new String[]{"jcr:all"}, "everyone", null);
        assertPermission("/testroot/secured", true, new String[]{"jcr:read"}, "everyone", "*/foo/*");

    }

    /**
     * Installs 2 packages with the same ACL. the later packages has AC Handling MERGE_PRESERVER and should
     * retain the existing ACL.
     */
    @Test
    public void testACMergePreserve() throws RepositoryException, IOException, PackageException {
        assertNodeMissing("/testroot");

        JcrPackage pack = packMgr.upload(getStream("testpackages/mode_ac_test_a.zip"), false);
        assertNotNull(pack);
        pack.install(getDefaultOptions());

        // test if nodes and ACLs of first package exist
        assertNodeExists("/testroot/node_a");
        assertPermission("/testroot/secured", false, new String[]{"jcr:all"}, "everyone", null);

        pack = packMgr.upload(getStream("testpackages/mode_ac_test_b_preserve.zip"), false);
        assertNotNull(pack);
        pack.install(getDefaultOptions());

        // test if nodes and ACLs of 2nd package exist
        assertNodeExists("/testroot/node_a");
        assertNodeExists("/testroot/node_b");
        assertPermission("/testroot/secured", false, new String[]{"jcr:all"}, "everyone", null);
        assertPermissionMissing("/testroot/secured", true, new String[]{"jcr:read"}, "everyone", "*/foo/*");

    }

    protected void assertPermissionMissing(String path, boolean allow, String[] privs, String name, String globRest)
            throws RepositoryException {
        if (hasPermission(path, allow, privs, name, globRest)) {
            fail("Expected permission should not exist on path " + path);
        }
    }

    protected void assertPermission(String path, boolean allow, String[] privs, String name, String globRest)
            throws RepositoryException {
        if (!hasPermission(path, allow, privs, name, globRest)) {
            fail("Expected permission missing on path " + path);
        }
    }

    protected boolean hasPermission(String path, boolean allow, String[] privs, String name, String globRest)
            throws RepositoryException {
        AccessControlPolicy[] ap = admin.getAccessControlManager().getPolicies(path);
        boolean found = false;
        for (AccessControlPolicy p: ap) {
            if (p instanceof JackrabbitAccessControlList) {
                JackrabbitAccessControlList acl = (JackrabbitAccessControlList) p;
                for (AccessControlEntry ac: acl.getAccessControlEntries()) {
                    if (ac instanceof JackrabbitAccessControlEntry) {
                        JackrabbitAccessControlEntry ace = (JackrabbitAccessControlEntry) ac;
                        if (ace.isAllow() != allow) {
                            continue;
                        }
                        if (!ace.getPrincipal().getName().equals(name)) {
                            continue;
                        }
                        Set<String> expectedPrivs = new HashSet<String>(Arrays.asList(privs));
                        for (Privilege priv: ace.getPrivileges()) {
                            if (!expectedPrivs.remove(priv.getName())) {
                                expectedPrivs.add("dummy");
                                break;
                            }
                        }
                        if (!expectedPrivs.isEmpty()) {
                            continue;
                        }
                        if (globRest != null && !globRest.equals(ace.getRestriction("rep:glob").getString())) {
                            continue;
                        }
                        found = true;
                        break;
                    }
                }
            }
        }
        return found;
    }
}
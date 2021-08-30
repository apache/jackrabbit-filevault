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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.jcr.RepositoryException;

import org.apache.jackrabbit.JcrConstants;
import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.jackrabbit.commons.JcrUtils;
import org.apache.jackrabbit.vault.fs.io.AccessControlHandling;
import org.apache.jackrabbit.vault.fs.io.ImportOptions;
import org.apache.jackrabbit.vault.packaging.JcrPackage;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * AC Handling and import mode tests
 */
public class ACLAndMergeIT extends IntegrationTestBase {

    private final static String NAME_TEST_USER = "testuser";

    private UserManager uMgr;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        uMgr = ((JackrabbitSession) admin).getUserManager();
        uMgr.createUser(NAME_TEST_USER, "test");
        admin.save();
    }

    @Override
    public void tearDown() throws Exception {
        // remove test node
        if (admin.nodeExists("/testroot")) {
            admin.getNode("/testroot").remove();
            admin.save();
        }
        try {
            Authorizable testUser = uMgr.getAuthorizable(NAME_TEST_USER);
            testUser.remove();
            admin.save();
        } catch (RepositoryException e) {
            // ignore
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

        extractVaultPackage("/test-packages/mode_ac_test_a.zip");

        // test if nodes and ACLs of first package exist
        assertNodeExists("/testroot/node_a");
        assertPermission("/testroot/secured", false, new String[]{"jcr:all"}, "everyone", null);

        extractVaultPackage("/test-packages/mode_ac_test_b.zip");

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

        extractVaultPackage("/test-packages/mode_ac_test_a.zip");

        // test if nodes and ACLs of first package exist
        assertNodeExists("/testroot/node_a");
        assertPermission("/testroot/secured", false, new String[]{"jcr:all"}, "everyone", null);

        extractVaultPackage("/test-packages/mode_ac_test_b_merge.zip");

        // test if nodes and ACLs of 2nd package exist
        assertNodeExists("/testroot/node_a");
        assertNodeExists("/testroot/node_b");
        assertPermission("/testroot/secured", false, new String[]{"jcr:all"}, "everyone", null);
        assertPermission("/testroot/secured", true, new String[]{"jcr:read"}, "everyone", "*/foo/*");
    }

    /**
     * Installs 2 packages with the same ACL. the later packages has a sub package with a different AC handling.
     * See JCRVLT-107.
     */
    @Test
    public void testACMergeSubpackage() throws RepositoryException, IOException, PackageException {
        assertNodeMissing("/testroot");

        extractVaultPackage("/test-packages/mode_ac_test_a.zip");

        // test if nodes and ACLs of first package exist
        assertNodeExists("/testroot/node_a");
        assertPermission("/testroot/secured", false, new String[]{"jcr:all"}, "everyone", null);

        installPackage("/test-packages/mode_ac_subpackage.zip");

        // test if nodes and ACLs of 2nd package exist
        assertNodeExists("/testroot/node_a");
        assertNodeExists("/testroot/node_b");
        assertPermission("/testroot/secured", false, new String[]{"jcr:all"}, "everyone", null);
        assertPermission("/testroot/secured", true, new String[]{"jcr:read"}, "everyone", "*/foo/*");
    }

    /**
     * Installs 2 packages with the same ACE. the later packages has AC Handling MERGE and should overwrite the
     * existing ACL.
     */
    @Test
    public void testACMerge4() throws RepositoryException, IOException, PackageException {
        assertNodeMissing("/testroot");

        extractVaultPackage("/test-packages/mode_ac_test_b2_merge.zip");

        // test if nodes and ACLs of first package exist
        assertNodeExists("/testroot/node_a");
        assertPermission("/testroot/secured", true, new String[]{"jcr:read", "jcr:write"}, "everyone", null);

        extractVaultPackage("/test-packages/mode_ac_test_b3_merge.zip");

        // test if nodes and ACLs of 2nd package exist
        assertNodeExists("/testroot/node_a");
        assertNodeExists("/testroot/node_b");
        assertPermission("/testroot/secured", true, new String[]{"jcr:read", "jcr:versionManagement"}, "everyone", null);
    }

    /**
     * Installs 2 packages with ACL for different principals. the first package has an ace for 'everyone' the 2nd for
     * 'testuser'. the later package should not corrupt the existing acl (unlike overwrite).
     */
    @Test
    public void testACMerge2() throws RepositoryException, IOException, PackageException {
        assertNodeMissing("/testroot");

        extractVaultPackage("/test-packages/mode_ac_test_a.zip");

        // test if nodes and ACLs of first package exist
        assertNodeExists("/testroot/node_a");
        assertPermission("/testroot/secured", false, new String[]{"jcr:all"}, "everyone", null);

        extractVaultPackage("/test-packages/mode_ac_test_c_merge.zip");

        // test if nodes and ACLs of 2nd package exist
        assertNodeExists("/testroot/node_a");
        assertNodeExists("/testroot/node_c");
        assertPermission("/testroot/secured", false, new String[]{"jcr:all"}, "everyone", null);
        assertPermission("/testroot/secured", true, new String[]{"jcr:all"}, "testuser", null);
    }

    /**
     * Installs 2 packages with ACL for different principals. the first package has an ace for 'everyone' the 2nd for
     * 'everyone' and 'testuser'. merge mode should overwrite the 'everyone' ACE.
     */
    @Test
    public void testACMerge3() throws RepositoryException, IOException, PackageException {
        assertNodeMissing("/testroot");

        extractVaultPackage("/test-packages/mode_ac_test_a.zip");

        // test if nodes and ACLs of first package exist
        assertNodeExists("/testroot/node_a");
        assertPermission("/testroot/secured", false, new String[]{"jcr:all"}, "everyone", null);

        extractVaultPackage("/test-packages/mode_ac_test_d.zip");

        // test if nodes and ACLs of 2nd package exist
        assertNodeExists("/testroot/node_a");
        assertNodeExists("/testroot/node_d");
        assertPermission("/testroot/secured", true, new String[]{"jcr:all"}, "everyone", null);
        assertPermission("/testroot/secured", true, new String[]{"jcr:all"}, "testuser", null);
    }

    /**
     * Installs 2 packages with ACL for different principals. the first package has an ace for 'everyone' the 2nd for
     * 'everyone' and 'testuser'. merge_preserve mode should NOT overwrite the 'everyone' ACE.
     */
    @Test
    public void testACMergePreserve2() throws RepositoryException, IOException, PackageException {
        assertNodeMissing("/testroot");

        extractVaultPackage("/test-packages/mode_ac_test_a.zip");

        // test if nodes and ACLs of first package exist
        assertNodeExists("/testroot/node_a");
        assertPermission("/testroot/secured", false, new String[]{"jcr:all"}, "everyone", null);

        ImportOptions opts = getDefaultOptions();
        opts.setAccessControlHandling(AccessControlHandling.MERGE_PRESERVE);
        extractVaultPackage("/test-packages/mode_ac_test_d.zip", opts);

        // test if nodes and ACLs of 2nd package exist
        assertNodeExists("/testroot/node_a");
        assertNodeExists("/testroot/node_d");
        assertPermission("/testroot/secured", false, new String[]{"jcr:all"}, "everyone", null);
        assertPermission("/testroot/secured", true, new String[]{"jcr:all"}, "testuser", null);
    }



    /**
     * Installs 2 packages with the same ACL. the later packages has AC Handling MERGE_PRESERVE and should
     * retain the existing ACL.
     */
    @Test
    public void testACMergePreserve() throws RepositoryException, IOException, PackageException {
        assertNodeMissing("/testroot");

        extractVaultPackage("/test-packages/mode_ac_test_a.zip");

        // test if nodes and ACLs of first package exist
        assertNodeExists("/testroot/node_a");
        assertPermission("/testroot/secured", false, new String[]{"jcr:all"}, "everyone", null);

        extractVaultPackage("/test-packages/mode_ac_test_b_preserve.zip");

        // test if nodes and ACLs of 2nd package exist
        assertNodeExists("/testroot/node_a");
        assertNodeExists("/testroot/node_b");
        assertPermission("/testroot/secured", false, new String[]{"jcr:all"}, "everyone", null);
        assertPermissionMissing("/testroot/secured", true, new String[]{"jcr:read"}, "everyone", "*/foo/*");

    }

    /**
     * Installs a second package with removed mix:accesscontrolable and different nodetype but with ac merge_preserve.
     */
    @Test
    public void testACMergePreserveNodeType() throws RepositoryException, IOException, PackageException {
        assertNodeMissing("/testroot");

        extractVaultPackage("/test-packages/mode_ac_test_a.zip");

        // test if nodes and ACLs of first package exist
        assertNodeExists("/testroot/node_a");
        assertPermission("/testroot/secured", false, new String[]{"jcr:all"}, "everyone", null);
        assertProperty("/testroot/secured/jcr:primaryType", "nt:folder");

        extractVaultPackage("/test-packages/mode_ac_test_unsecured_nodetype.zip");

        // test if permissions remain and pt changed
        assertPermission("/testroot/secured", false, new String[]{"jcr:all"}, "everyone", null);
        assertProperty("/testroot/secured/jcr:primaryType", "sling:Folder");
    }

    /**
     * Installs a second package with removed mix:accesscontrolable and different uuid but with ac merge_preserve.
     */
    @Test
    @Ignore("JCRVLT-127")
    public void testACMergePreserveUUID() throws RepositoryException, IOException, PackageException {
        assertNodeMissing("/testroot");

        JcrPackage pack = packMgr.upload(getStream("testpackages/mode_ac_test_a_uuid.zip"), false);
        assertNotNull(pack);
        pack.install(getDefaultOptions());

        // test if nodes and ACLs of first package exist
        assertNodeExists("/testroot/node_a");
        assertPermission("/testroot/secured", false, new String[]{"jcr:all"}, "everyone", null);
        assertProperty("/testroot/secured/jcr:uuid", "88292ab3-40da-44aa-aba2-0be0019cddc1");

        pack = packMgr.upload(getStream("testpackages/mode_ac_test_unsecured_uuid.zip"), false);
        assertNotNull(pack);
        pack.install(getDefaultOptions());

        // test if permissions remain and uuid changed
        assertPermission("/testroot/secured", false, new String[]{"jcr:all"}, "everyone", null);
        assertProperty("/testroot/secured/jcr:uuid", "98292ab3-40da-44aa-aba2-0be0019cddc2");
    }

    /**
     * Installs a package with 3 ACLs and checks if the order of the entries is still correct.
     */
    @Test
    public void testACOrderOverwrite() throws Exception {
        assertNodeMissing("/testroot");
        doOrderTest(AccessControlHandling.OVERWRITE);
    }

    /**
     * Installs a package with 3 ACLs and checks if the order of the entries is still correct.
     */
    @Test
    public void testACOrderMerge() throws Exception {
        assertNodeMissing("/testroot");
        doOrderTest(AccessControlHandling.OVERWRITE);
        doOrderTest(AccessControlHandling.MERGE);
    }

    /**
     * Installs a package with 3 ACLs and checks if the order of the entries is still correct.
     */
    @Test
    public void testACOrderMergePreserve() throws Exception {
        assertNodeMissing("/testroot");
        doOrderTest(AccessControlHandling.OVERWRITE);
        doOrderTest(AccessControlHandling.MERGE_PRESERVE);
    }

    private void doOrderTest(AccessControlHandling ac) throws Exception {
        ImportOptions opts = getDefaultOptions();
        opts.setAccessControlHandling(ac);
        extractVaultPackage("/test-packages/mode_ac_test_e.zip", opts);

        // test if nodes and ACLs of first package exist
        assertNodeExists("/testroot/node_e");
        int idx0 = hasPermission("/testroot/secured", false, new String[]{"jcr:all"}, "everyone", Collections.<String, String[]>emptyMap());
        int idx1 = hasPermission("/testroot/secured", true, new String[]{"jcr:all"}, "testuser", Collections.<String, String[]>emptyMap());
        int idx2 = hasPermission("/testroot/secured", true, new String[]{"jcr:all"}, "testuser1", Collections.<String, String[]>emptyMap());

        assertTrue("All ACEs must exist", idx0 >= 0 && idx1 >= 0 && idx2 >= 0);
        String result = String.format("%d < %d < %d", idx0, idx1, idx2);
        assertEquals("ACE order ", "0 < 1 < 2", result);
    }

    /**
     * Installs a package with oak ACL content.
     */
    @Test
    public void testOakContent() throws RepositoryException, IOException, PackageException {
        Assume.assumeTrue(isOak());
        assertNodeMissing("/testroot");

        extractVaultPackage("/test-packages/oak_ac_content_test.zip");

        // test if nodes and ACLs of first package exist
        assertNodeExists("/testroot/node_a");
        Map<String, String[]> restrictions = new HashMap<String, String[]>();
        restrictions.put("rep:glob", new String[]{"*/foo"});
        restrictions.put("rep:ntNames", new String[]{"nt:unstructured"});
        restrictions.put("rep:prefixes", new String[]{"rep", "granite"});
        assertTrue(
                "expected permission missing",
                hasPermission("/testroot/secured", true, new String[]{"jcr:all"}, "everyone", restrictions) >= 0
        );
    }

    /**
     * Installs a package with missing ACL user.
     */
    @Test
    public void testMissingUser() throws RepositoryException, IOException, PackageException {
        assertNodeMissing("/testroot");

        extractVaultPackage("/test-packages/mode_ac_test_lateuser.zip");

        // test if nodes and ACLs of first package exist
        assertNodeExists("/testroot/node_d");
        assertPermission("/testroot/secured", true, new String[]{"jcr:all"}, "missinguser", null);
    }


    /**
     * Installs a package with repository level acl and then installs another that removes them again.
     */
    @Test
    public void testRepoACL() throws RepositoryException, IOException, PackageException {
        removeRepoACL();

        ImportOptions opts = getDefaultOptions();
        opts.setAccessControlHandling(AccessControlHandling.OVERWRITE);
        extractVaultPackage("/test-packages/repo_policy.zip", opts);

        // test if nodes and ACLs of first package exist
        assertPermission(null, false, new String[]{"jcr:all"}, "everyone", null);
        assertPermission(null, false, new String[]{"jcr:all"}, "testuser", null);

        opts = getDefaultOptions();
        opts.setAccessControlHandling(AccessControlHandling.OVERWRITE);
        extractVaultPackage("/test-packages/repo_no_policy.zip", opts);

        assertPermissionMissing(null, false, new String[]{"jcr:all"}, "everyone", null);
        assertPermissionMissing(null, false, new String[]{"jcr:all"}, "testuser", null);

    }

    /**
     * Installs a package with repository level acl with AccessControlHandling.MERGE.
     */
    @Test
    public void testRepoACLMerge() throws RepositoryException, IOException, PackageException {
        removeRepoACL();
        addACL(null, true, new String[]{"jcr:all"}, "testuser");
        assertPermission(null, true, new String[]{"jcr:all"}, "testuser", null);
        addACL(null, true, new String[]{"jcr:all"}, "testuser1");
        assertPermission(null, true, new String[]{"jcr:all"}, "testuser1", null);

        ImportOptions opts = getDefaultOptions();
        opts.setAccessControlHandling(AccessControlHandling.MERGE);
        extractVaultPackage("/test-packages/repo_policy.zip", opts);

        // test if nodes and ACLs of first package exist
        assertPermission(null, false, new String[]{"jcr:all"}, "everyone", null);
        assertPermission(null, false, new String[]{"jcr:all"}, "testuser", null);
        assertPermission(null, true, new String[]{"jcr:all"}, "testuser1", null);
    }

    /**
     * Installs a package with repository level acl with AccessControlHandling.MERGE_PRESERVE.
     */
    @Test
    public void testRepoACLMergePreserve() throws RepositoryException, IOException, PackageException {
        removeRepoACL();
        addACL(null, true, new String[]{"jcr:all"}, "testuser");
        assertPermission(null, true, new String[]{"jcr:all"}, "testuser", null);
        addACL(null, true, new String[]{"jcr:all"}, "testuser1");
        assertPermission(null, true, new String[]{"jcr:all"}, "testuser1", null);

        ImportOptions opts = getDefaultOptions();
        opts.setAccessControlHandling(AccessControlHandling.MERGE_PRESERVE);
        extractVaultPackage("/test-packages/repo_policy.zip", opts);

        // test if nodes and ACLs of first package exist
        assertPermission(null, false, new String[]{"jcr:all"}, "everyone", null);
        assertPermission(null, true, new String[]{"jcr:all"}, "testuser", null);
        assertPermission(null, true, new String[]{"jcr:all"}, "testuser1", null);
    }

    /**
     * Installs a package at the root level (JCRVLT-75)
     */
    @Test
    public void testRootACL() throws RepositoryException, IOException, PackageException {
        ImportOptions opts = getDefaultOptions();
        opts.setAccessControlHandling(AccessControlHandling.OVERWRITE);
        extractVaultPackage("/test-packages/root_policy.zip", opts);

        // test if nodes and ACLs of first package exist
        assertPermission("/", true, new String[]{"jcr:all"}, "everyone", null);
    }
    
    /** Check effect of filter definitions */
    @Test
    public void testACLsOutsideFilter() throws IOException, PackageException, RepositoryException {
        extractVaultPackageStrict("/test-packages/ac_outside_filter.zip");
        assertNodeExists("/testroot/node_a");

        // this ACL is not contained in the filter and not installed because ancestor is no filter root
        assertPermissionMissing("/testroot", false, new String[]{"jcr:all"}, "everyone", null);

        // this is not contained in the filter root but installed nevertheless (due to ancestor being a filter root)
        assertPermission("/testroot/secured", false, new String[]{"jcr:all"}, "everyone", null);
    }
}
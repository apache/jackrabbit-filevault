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

import java.io.File;
import java.io.IOException;
import java.security.Principal;

import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;
import javax.jcr.nodetype.NodeType;

import org.apache.commons.io.FileUtils;
import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.jackrabbit.commons.jackrabbit.authorization.AccessControlUtils;
import org.apache.jackrabbit.vault.fs.config.ConfigurationException;
import org.apache.jackrabbit.vault.fs.io.ImportOptions;
import org.apache.jackrabbit.vault.fs.io.Importer;
import org.apache.jackrabbit.vault.fs.io.ZipArchive;
import org.apache.jackrabbit.vault.packaging.InstallContext;
import org.apache.jackrabbit.vault.packaging.JcrPackage;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.impl.JcrPackageManagerImpl;
import org.apache.tika.io.IOUtils;
import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

/**
 * {@code TestPackageInstall}...
 */
public class TestPackageInstall extends IntegrationTestBase {

    /**
     * Installs a package that contains and checks if everything is correct.
     */
    @Test
    public void testUpload() throws RepositoryException, IOException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("testpackages/tmp.zip"), false);
        assertNotNull(pack);
        assertPackageNodeExists(TMP_PACKAGE_ID);

        // upload already unrwapps it, so check if definition is ok
        assertNodeExists(getInstallationPath(TMP_PACKAGE_ID) + "/jcr:content/vlt:definition");

        // todo: check definition props

    }

    /**
     * Test if rewrap of a small package works
     */
    @Test
    public void testRewrap() throws RepositoryException, IOException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("testpackages/tmp.zip"), false);
        assertNotNull(pack);

        ImportOptions opts = getDefaultOptions();
        pack.install(opts);

        packMgr.rewrap(pack, opts.getListener());
    }

    /**
     * Tests if unwrapping an already installed package preserves the status
     */
    @Test
    public void testUnwrapPreserveInstall() throws RepositoryException, IOException, PackageException {

        JcrPackage pack = packMgr.upload(getStream("testpackages/tmp.zip"), true, true);
        assertNotNull(pack);
        assertTrue(pack.isValid());
        assertPackageNodeExists(TMP_PACKAGE_ID);
        pack.install(getDefaultOptions());
        assertNodeExists("/tmp/foo");

        long lastUnpacked = pack.getDefinition().getLastUnpacked().getTimeInMillis();
        assertTrue(lastUnpacked > 0);

        // now upload again, but don't install
        pack = packMgr.upload(getStream("testpackages/tmp.zip"), true, true);
        assertNotNull(pack);
        PackageId pkgId = pack.getDefinition().getId();
        assertTrue(pack.isValid());
        assertTrue(pack.isInstalled());
        assertEquals(lastUnpacked, pack.getDefinition().getLastUnpacked().getTimeInMillis());

        // now re-acquire package and test again
        pack = packMgr.open(pkgId);
        assertTrue(pack.isValid());
        assertTrue(pack.isInstalled());
        assertEquals(lastUnpacked, pack.getDefinition().getLastUnpacked().getTimeInMillis());

        // a package with a different created date should not preserve the status!
        pack = packMgr.upload(getStream("testpackages/tmp_with_modified_created_date.zip"), true, true);
        assertNotNull(pack);
        assertTrue(pack.isValid());
        assertFalse(pack.isInstalled());
    }

    /**
     * Installs a package that contains and checks if everything is correct.
     */
    @Test
    public void testUploadWithThumbnail() throws RepositoryException, IOException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("testpackages/tmp_with_thumbnail.zip"), false);
        assertNotNull(pack);
        assertPackageNodeExists(TMP_PACKAGE_ID);

        // upload already unrwapps it, so check if definition is ok
        assertNodeExists(getInstallationPath(TMP_PACKAGE_ID) + "/jcr:content/vlt:definition/thumbnail.png");
    }

    /**
     * Installs a package that contains /tmp/fullcoverage/a/aa using a vlt:FullCoverage mixin.
     * check if the package manager installs that node type although not present in the package.
     */
    @Test
    public void testFullCoverageNT() throws RepositoryException, IOException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("testpackages/fullcoverage.zip"), false);
        assertNotNull(pack);

        // just extract - no snapshots
        pack.extract(getDefaultOptions());
        assertNodeExists("/tmp/fullcoverage/a/aa");

        admin.getWorkspace().getNodeTypeManager().getNodeType("vlt:FullCoverage");
    }

    /**
     * Installs a package that contains a folder below a jcr:resource which is augmented by a
     * mixin that should allow a sub folder.
     */
    @Test
    public void testDeepMixin() throws RepositoryException, IOException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("testpackages/deepmixintest.zip"), false);
        assertNotNull(pack);

        // just extract - no snapshots
        pack.extract(getDefaultOptions());
        assertNodeExists("/etc/designs/apache/images/backgroundImage.png/jcr:content/dam:thumbnails/dam:thumbnail_48.png");
    }

    /**
     * Installs a package that contains a folder a filter to a jcr:content[nt:unstructured] node.
     * See bug #42562
     */
    @Test
    public void testJcrContent() throws RepositoryException, IOException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("testpackages/tmp_testpage_jcr_content.zip"), false);
        assertNotNull(pack);

        // just extract - no snapshots
        pack.extract(getDefaultOptions());
        assertNodeExists("/tmp/testpage/jcr:content/foo");
    }

    /**
     * Installs a package that just adds a property to the root node.
     */
    @Test
    public void testRootImport() throws RepositoryException, IOException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("testpackages/testrootimport.zip"), false);
        assertNotNull(pack);

        // just extract - no snapshots
        pack.extract(getDefaultOptions());
        assertProperty("/testproperty", "hello");
    }

    /**
     * Installs a package with an install hook
     */
    @Test
    public void testHook() throws RepositoryException, IOException, PackageException {
        if (admin.nodeExists("/testroot")) {
            admin.getNode("/testroot").remove();
        }
        admin.getRootNode().addNode("testroot", "nt:unstructured").addNode("testnode", "nt:unstructured");
        admin.save();
        JcrPackage pack = packMgr.upload(getStream("testpackages/test_hook.zip"), false);
        assertNotNull(pack);
        pack.install(getDefaultOptions());
        assertTrue(admin.propertyExists("/testroot/hook-example"));
    }

    /**
     * Installs a package with an install hook
     */
    @Test
    public void testHookFail() throws RepositoryException, IOException, PackageException {
        if (admin.nodeExists("/testroot")) {
            admin.getNode("/testroot").remove();
        }
        admin.save();
        JcrPackage pack = packMgr.upload(getStream("testpackages/test_hook.zip"), false);
        assertNotNull(pack);
        try {
            pack.install(getDefaultOptions());
            fail("installing failing hook should fail");
        } catch (PackageException e) {
            // ok
        }
    }

    /**
     * Installs a package with an invalid hook
     */
    @Test
    public void testInvalidHook() throws RepositoryException, IOException {
        JcrPackage pack = packMgr.upload(getStream("testpackages/invalid_hook.zip"), false);
        assertNotNull(pack);

        try {
            pack.install(getDefaultOptions());
            fail("Package install should fail.");
        } catch (PackageException e) {
            // ok
        }
    }

    /**
     * Installs a package with an external hook
     */
    @Test
    public void testExternalHook() throws RepositoryException, IOException, PackageException {
        if (!admin.nodeExists("/testroot")) {
            admin.getRootNode().addNode("testroot", "nt:unstructured");
            admin.save();
        }

        JcrPackage pack = packMgr.upload(getStream("testpackages/external_hook.zip"), false);
        assertNotNull(pack);

        pack.install(getDefaultOptions());

        assertProperty("/testroot/TestHook1", InstallContext.Phase.END.toString());
        assertProperty("/testroot/TestHook2", InstallContext.Phase.END.toString());
    }

    /**
     * Installs a package with no properties
     */
    @Test
    public void testNoProperties() throws RepositoryException, IOException, PackageException {
        File tmpFile = File.createTempFile("vlttest", "zip");
        IOUtils.copy(getStream("testpackages/tmp_no_properties.zip"), FileUtils.openOutputStream(tmpFile));
        JcrPackage pack = packMgr.upload(tmpFile, true, true, "testpackage", false);
        assertNotNull(pack);

        pack.install(getDefaultOptions());
    }

    /**
     * Installs a package with non-child filter doesn't remove the root.
     *
     * <pre>
     *   <workspaceFilter version="1.0">
     *   <filter root="/etc">
     *     <include pattern="/etc"/>
     *     <include pattern="/etc/clientlibs"/>
     *     <include pattern="/etc/clientlibs/granite"/>
     *     <include pattern="/etc/clientlibs/granite/test(/.*)?"/>
     *   </filter>
     *  </workspaceFilter>
     */
    @Test
    public void testNoChildFilter() throws RepositoryException, IOException, PackageException {
        File tmpFile = File.createTempFile("vlttest", "zip");
        IOUtils.copy(getStream("testpackages/test-package-with-etc.zip"), FileUtils.openOutputStream(tmpFile));
        JcrPackage pack = packMgr.upload(tmpFile, true, true, "test-package-with-etc", false);
        assertNodeExists("/etc");
        admin.getNode("/etc").addNode("foo", NodeType.NT_FOLDER);
        admin.save();
        pack.install(getDefaultOptions());
        assertNodeExists("/etc/foo");
    }

    @Test
    public void testDeepContentImport() throws IOException, RepositoryException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("testpackages/tmp_test_deep.zip"), false);
        assertNotNull(pack);
        pack.install(getDefaultOptions());

        assertNodeExists("/tmp/test/content/foo/jcr:content/a/b/foo.jsp/jcr:content");
        assertNodeExists("/tmp/test/content/foo/jcr:content/a/c/resource");
        assertNodeExists("/tmp/test/content/foo/jcr:content/a/d");
        assertNodeExists("/tmp/test/content/foo/jcr:content/a/folder/file.txt/jcr:content");
    }

    /**
     * installs a package that contains a node with childnode ordering and full-coverage sub nodes.
     * see JCRVLT-24
     */
    @Test
    public void testChildNodeOrder() throws IOException, RepositoryException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("testpackages/test_childnodeorder.zip"), false);
        assertNotNull(pack);
        pack.install(getDefaultOptions());

        assertNodeExists("/tmp/ordertest/test/rail/items/modes/items");
        NodeIterator iter = admin.getNode("/tmp/ordertest/test/rail/items/modes/items").getNodes();
        StringBuilder names = new StringBuilder();
        while (iter.hasNext()) {
            names.append(iter.nextNode().getName()).append(",");
        }
        assertEquals("child order", "a,d,b,c,", names.toString());
    }

    /**
     * installs a package that contains a node with childnode ordering and full-coverage sub nodes.
     * see JCRVLT-44
     */
    @Test
    public void testChildNodeOrder2() throws IOException, RepositoryException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("testpackages/test_childnodeorder2.zip"), false);
        assertNotNull(pack);
        pack.install(getDefaultOptions());

        assertNodeExists("/tmp/test/en");
        NodeIterator iter = admin.getNode("/tmp/test/en").getNodes();
        StringBuilder names = new StringBuilder();
        while (iter.hasNext()) {
            names.append(iter.nextNode().getName()).append(",");
        }
        assertEquals("child order", "jcr:content,toolbar,products,services,company,events,support,community,blog,", names.toString());
    }

    /**
     * Installs a package that and checks if snapshot is created
     */
    @Test
    public void testSnapshotExists() throws RepositoryException, IOException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("testpackages/tmp.zip"), false);
        assertNotNull(pack);
        pack.install(getDefaultOptions());

        assertPackageNodeExists(TMP_SNAPSHOT_PACKAGE_ID);
        assertNodeExists("/tmp/foo/bar/tobi");
    }

    /**
     * Installs and uninstalls a package that and checks if the content is reverted.
     */
    @Test
    public void testUninstall() throws RepositoryException, IOException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("testpackages/tmp.zip"), false);
        assertNotNull(pack);
        pack.install(getDefaultOptions());
        assertNodeExists("/tmp/foo/bar/tobi");

        pack.uninstall(getDefaultOptions());
        assertNodeMissing("/tmp/foo/bar/tobi");
    }

    /**
     * Uninstalls a package that has no snapshot (JCRVLT-89)
     */
    @Test
    public void testUninstallNoSnapshot() throws RepositoryException, IOException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("testpackages/tmp.zip"), false);
        assertNotNull(pack);

        // extract should not generate snapshots
        pack.extract(getDefaultOptions());
        assertNodeExists("/tmp/foo/bar/tobi");
        assertPackageNodeMissing(TMP_SNAPSHOT_PACKAGE_ID);

        pack.uninstall(getDefaultOptions());
        assertNodeExists("/tmp/foo/bar/tobi");
    }

    /**
     * Checks if uninstalling a package in strict mode with no snapshot fails (JCRVLT-89).
     */
    @Test
    public void testUninstallNoSnapshotStrict() throws RepositoryException, IOException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("testpackages/tmp.zip"), false);
        assertNotNull(pack);

        // extract should not generate snapshots
        pack.extract(getDefaultOptions());
        assertNodeExists("/tmp/foo/bar/tobi");
        assertPackageNodeMissing(TMP_SNAPSHOT_PACKAGE_ID);

        ImportOptions opts = getDefaultOptions();
        opts.setStrict(true);
        try {
            pack.uninstall(opts);
            fail("uninstalling a package with no snapshot should fail in strict mode.");
        } catch (PackageException e) {
            // ok
        }
    }

    /**
     * Installs a binary properties.
     */
    @Test
    public void testBinaryProperties() throws RepositoryException, IOException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("testpackages/tmp_binary.zip"), false);
        assertNotNull(pack);
        pack.install(getDefaultOptions());

        Property p = admin.getProperty("/tmp/binary/test/jcr:data");
        assertEquals(PropertyType.BINARY, p.getType());

        StringBuilder buffer = new StringBuilder(8192);
        while (buffer.length() < 8192) {
            buffer.append("0123456789abcdef");
        }
        String result = IOUtils.toString(p.getBinary().getStream());

        assertEquals(buffer.toString(), result);
    }

    /**
     * Installs a binary properties twice to check if it doesn't report an update.
     * TODO: this is not implemented yet. see JCRVLT-110
     */
    @Test
    @Ignore
    public void testBinaryPropertyTwice() throws RepositoryException, IOException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("testpackages/tmp_binary.zip"), false);
        assertNotNull(pack);
        pack.install(getDefaultOptions());

        Property p = admin.getProperty("/tmp/binary/test/jcr:data");
        assertEquals(PropertyType.BINARY, p.getType());

        StringBuilder buffer = new StringBuilder(8192);
        while (buffer.length() < 8192) {
            buffer.append("0123456789abcdef");
        }
        String result = IOUtils.toString(p.getBinary().getStream());

        assertEquals(buffer.toString(), result);

        // install again to check if binary data is not updated
        ImportOptions opts = getDefaultOptions();
        TrackingListener listener = new TrackingListener(opts.getListener());
        opts.setListener(listener);

        pack.install(opts);

        //TODO: assertEquals("-", listener.getActions().get("/tmp/binary/test"));
        assertEquals("U", listener.getActions().get("/tmp/binary/test"));
    }

    /**
     * Test is binaries outside the filter are not imported (JCRVLT-126)
     */
    @Test
    public void testBinaryPropertiesOutsideFilter() throws RepositoryException, IOException, PackageException {
        // first install the package once to create the intermediate nodes
        JcrPackage pack = packMgr.upload(getStream("testpackages/test_filter_binary.zip"), false);
        assertNotNull(pack);
        pack.install(getDefaultOptions());
        assertProperty("/tmp/test", "123");

        // delete the binary properties
        if (admin.itemExists("/root-binary-property")) {
            admin.removeItem("/root-binary-property");
        }

        admin.removeItem("/tmp/tmp-binary-property");
        admin.removeItem("/tmp/test");
        admin.removeItem("/tmp/test-project");
        admin.save();

        assertPropertyMissing("/root-binary-property");
        assertPropertyMissing("/tmp/tmp-binary-property");

        // now install again and check if the properties are still missing
        pack.install(getDefaultOptions());
        assertPropertyMissing("/tmp/test");
        assertPropertyMissing("/root-binary-property");
        assertPropertyMissing("/tmp/tmp-binary-property");
    }

    /**
     * Installs a package with a different node type
     */
    @Test
    public void testNodeTypeChange() throws RepositoryException, IOException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("testpackages/tmp.zip"), false);
        assertNotNull(pack);
        assertPackageNodeExists(TMP_PACKAGE_ID);

        ImportOptions opts = getDefaultOptions();
        pack.install(opts);

        assertNodeExists("/tmp/foo");
        assertEquals(admin.getNode("/tmp").getPrimaryNodeType().getName(), "sling:OrderedFolder");

        pack = packMgr.upload(getStream("testpackages/tmp_nt_folder.zip"), false);
        assertNotNull(pack);
        assertPackageNodeExists(TMP_PACKAGE_ID);

        pack.install(opts);

        assertNodeExists("/tmp/foo");
        assertEquals(admin.getNode("/tmp").getPrimaryNodeType().getName(), "nt:folder");
    }

    /**
     * Installs a package with versioned nodes
     */
    @Test
    public void testVersionInstall() throws RepositoryException, IOException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("testpackages/test_version.zip"), false);
        assertNotNull(pack);

        ImportOptions opts = getDefaultOptions();
        pack.install(opts);

        assertProperty("/testroot/a/test", "123");
        assertProperty("/testroot/a/jcr:isCheckedOut", "false");

        // modify
        admin.getWorkspace().getVersionManager().checkout("/testroot/a");
        admin.getProperty("/testroot/a/test").setValue("test");
        admin.save();
        admin.getWorkspace().getVersionManager().checkin("/testroot/a");

        // install a 2nd time
        opts = getDefaultOptions();
        pack.install(opts);

        assertProperty("/testroot/a/test", "123");
        assertProperty("/testroot/a/jcr:isCheckedOut", "false");

    }


    /**
     * Installs a package with versions retains checked out state
     */
    @Test
    public void testVersionInstallCheckedOut() throws RepositoryException, IOException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("testpackages/test_version.zip"), false);
        assertNotNull(pack);

        ImportOptions opts = getDefaultOptions();
        pack.install(opts);

        assertProperty("/testroot/a/test", "123");
        assertProperty("/testroot/a/jcr:isCheckedOut", "false");

        // modify
        admin.getWorkspace().getVersionManager().checkout("/testroot/a");
        admin.getProperty("/testroot/a/test").setValue("test");
        admin.save();

        // install a 2nd time
        opts = getDefaultOptions();
        pack.install(opts);

        assertProperty("/testroot/a/test", "123");
        assertProperty("/testroot/a/jcr:isCheckedOut", "false");
    }

    /**
     * Tests if package installation works w/o RW access to / and /tmp.
     * this currently fails, due to the creation of the snapshot.
     * also see {@link TestNoRootAccessExport#exportNoRootAccess()}
     */
    @Test
    @Ignore("JCRVLT-100")
    public void testInstallWithoutRootAndTmpAccess() throws IOException, RepositoryException, ConfigurationException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("testpackages/tmp_foo.zip"), true, true);
        assertNotNull(pack);
        assertTrue(pack.isValid());
        PackageId id = pack.getPackage().getId();
        pack.close();

        // Create test user
        UserManager userManager = ((JackrabbitSession)admin).getUserManager();
        String userId = "user1";
        String userPwd = "pwd1";
        User user1 = userManager.createUser(userId, userPwd);
        Principal principal1 = user1.getPrincipal();

        // Create /tmp folder
        admin.getRootNode().addNode("tmp").addNode("foo");
        admin.save();

        // Setup test user ACLs such that the
        // root node is not accessible
        AccessControlUtils.addAccessControlEntry(admin, null, principal1, new String[]{"jcr:namespaceManagement","jcr:nodeTypeDefinitionManagement"}, true);
        AccessControlUtils.addAccessControlEntry(admin, "/", principal1, new String[]{"jcr:all"}, false);
        AccessControlUtils.addAccessControlEntry(admin, packMgr.getRegistry().getPackRootPaths()[0], principal1, new String[]{"jcr:all"}, true);
        AccessControlUtils.addAccessControlEntry(admin, "/tmp/foo", principal1, new String[]{"jcr:all"}, true);
        admin.save();

        Session session = repository.login(new SimpleCredentials(userId, userPwd.toCharArray()));
        JcrPackageManagerImpl userPackMgr = new JcrPackageManagerImpl(session, new String[0]);
        pack = userPackMgr.open(id);
        ImportOptions opts = getDefaultOptions();
        pack.install(opts);
        pack.close();
        session.logout();

        assertNodeExists("/tmp/foo/bar/tobi");
    }

    /**
     * Test if package extraction works w/o RW access to / and /tmp.
     */
    @Test
    public void testExtractWithoutRootAndTmpAccess() throws IOException, RepositoryException, ConfigurationException, PackageException {
        Assume.assumeTrue(!isOak());

        JcrPackage pack = packMgr.upload(getStream("testpackages/tmp_foo.zip"), true, true);
        assertNotNull(pack);
        assertTrue(pack.isValid());
        PackageId id = pack.getPackage().getId();
        pack.close();

        // Create test user
        UserManager userManager = ((JackrabbitSession)admin).getUserManager();
        String userId = "user1";
        String userPwd = "pwd1";
        User user1 = userManager.createUser(userId, userPwd);
        Principal principal1 = user1.getPrincipal();

        // Create /tmp folder
        admin.getRootNode().addNode("tmp").addNode("foo");
        admin.save();

        // Setup test user ACLs such that the
        // root node is not accessible
        AccessControlUtils.addAccessControlEntry(admin, null, principal1, new String[]{"jcr:namespaceManagement","jcr:nodeTypeDefinitionManagement"}, true);
        AccessControlUtils.addAccessControlEntry(admin, "/", principal1, new String[]{"jcr:all"}, false);
        AccessControlUtils.addAccessControlEntry(admin, packMgr.getRegistry().getPackRootPaths()[0], principal1, new String[]{"jcr:all"}, true);
        AccessControlUtils.addAccessControlEntry(admin, "/tmp/foo", principal1, new String[]{"jcr:all"}, true);
        admin.save();

        Session session = repository.login(new SimpleCredentials(userId, userPwd.toCharArray()));
        JcrPackageManagerImpl userPackMgr = new JcrPackageManagerImpl(session, new String[0]);
        pack = userPackMgr.open(id);
        ImportOptions opts = getDefaultOptions();
        pack.extract(opts);
        pack.close();
        session.logout();

        assertNodeExists("/tmp/foo/bar/tobi");
    }


    // todo: upload with version
    // todo: rename

}
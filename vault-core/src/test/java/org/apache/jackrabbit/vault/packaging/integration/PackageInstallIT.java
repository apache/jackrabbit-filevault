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

import static org.apache.jackrabbit.vault.packaging.JcrPackageDefinition.PN_DEPENDENCIES;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.security.Principal;
import java.util.Collections;

import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;
import javax.jcr.Value;
import javax.jcr.nodetype.NodeType;

import org.apache.commons.io.FileUtils;
import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.jackrabbit.commons.jackrabbit.authorization.AccessControlUtils;
import org.apache.jackrabbit.vault.fs.config.ConfigurationException;
import org.apache.jackrabbit.vault.fs.io.ImportOptions;
import org.apache.jackrabbit.vault.packaging.Dependency;
import org.apache.jackrabbit.vault.packaging.InstallContext;
import org.apache.jackrabbit.vault.packaging.JcrPackage;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.events.impl.PackageEventDispatcherImpl;
import org.apache.jackrabbit.vault.packaging.impl.ActivityLog;
import org.apache.jackrabbit.vault.packaging.impl.JcrPackageManagerImpl;
import org.apache.jackrabbit.vault.packaging.registry.impl.JcrPackageRegistry;
import org.apache.jackrabbit.vault.packaging.registry.impl.JcrRegisteredPackage;
import org.apache.tika.io.IOUtils;
import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

/**
 * {@code PackageInstallIT}...
 */
public class PackageInstallIT extends IntegrationTestBase {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    /**
     * Installs a package that contains and checks if everything is correct.
     */
    @Test
    public void testUpload() throws RepositoryException, IOException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("/test-packages/tmp.zip"), false);
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
        JcrPackage pack = packMgr.upload(getStream("/test-packages/tmp.zip"), false);
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

        JcrPackage pack = packMgr.upload(getStream("/test-packages/tmp.zip"), true, true);
        assertNotNull(pack);
        assertTrue(pack.isValid());
        assertPackageNodeExists(TMP_PACKAGE_ID);
        pack.install(getDefaultOptions());
        assertNodeExists("/tmp/foo");

        long lastUnpacked = pack.getDefinition().getLastUnpacked().getTimeInMillis();
        assertTrue(lastUnpacked > 0);

        // now upload again, but don't install
        pack = packMgr.upload(getStream("/test-packages/tmp.zip"), true, true);
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
        pack = packMgr.upload(getStream("/test-packages/tmp_with_modified_created_date.zip"), true, true);
        assertNotNull(pack);
        assertTrue(pack.isValid());
        assertFalse(pack.isInstalled());
    }

    /**
     * Installs a package that contains and checks if everything is correct.
     */
    @Test
    public void testUploadWithThumbnail() throws RepositoryException, IOException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("/test-packages/tmp_with_thumbnail.zip"), false);
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
        JcrPackage pack = packMgr.upload(getStream("/test-packages/fullcoverage.zip"), false);
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
        JcrPackage pack = packMgr.upload(getStream("/test-packages/deepmixintest.zip"), false);
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
        JcrPackage pack = packMgr.upload(getStream("/test-packages/tmp_testpage_jcr_content.zip"), false);
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
        JcrPackage pack = packMgr.upload(getStream("/test-packages/testrootimport.zip"), false);
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
        JcrPackage pack = packMgr.upload(getStream("/test-packages/test_hook.zip"), false);
        assertNotNull(pack);
        pack.install(getDefaultOptions());
        assertTrue(admin.propertyExists("/testroot/hook-example"));
    }

    /**
     * Installs a package with an install hook and a not allowed user
     */
    @Test
    public void testHookWithNotAllowedNonAdminUser() throws RepositoryException, IOException, PackageException {
        if (admin.nodeExists("/testroot")) {
            admin.getNode("/testroot").remove();
        }
        admin.getRootNode().addNode("testroot", "nt:unstructured").addNode("testnode", "nt:unstructured");
        admin.save();
        
        // Create test user
        UserManager userManager = ((JackrabbitSession)admin).getUserManager();
        String userId = "user1";
        String userPwd = "pwd1";
        User user1 = userManager.createUser(userId, userPwd);
        Principal principal1 = user1.getPrincipal();
        
        // Setup test user ACLs that there are no restrictions
        AccessControlUtils.addAccessControlEntry(admin, null, principal1, new String[]{"jcr:namespaceManagement","jcr:nodeTypeDefinitionManagement"}, true);
        AccessControlUtils.addAccessControlEntry(admin, "/", principal1, new String[]{"jcr:all"}, true);
        admin.save();
        
        Session userSession = repository.login(new SimpleCredentials(userId, userPwd.toCharArray()));
        try {
            packMgr = new JcrPackageManagerImpl(userSession, new String[0]);
    
            PackageEventDispatcherImpl dispatcher = new PackageEventDispatcherImpl();
            dispatcher.bindPackageEventListener(new ActivityLog(), Collections.singletonMap("component.id", (Object) "1234"));
            packMgr.setDispatcher(dispatcher);
            
            JcrPackage pack = packMgr.upload(getStream("/test-packages/test_hook.zip"), false);
            assertNotNull(pack);
            thrown.expect(PackageException.class);
            thrown.expectMessage("Package extraction requires admin session as it has a hook");
            packMgr.getInternalRegistry().installPackage(userSession, new JcrRegisteredPackage(pack), getDefaultOptions(), true);
            
        
        } finally {
            userSession.logout();
        }
    }

    /**
     * Installs a package with an install hook and an explicitly allowed user
     */
    @Test
    public void testHookWithAllowedNonAdminUser() throws RepositoryException, IOException, PackageException {
        if (admin.nodeExists("/testroot")) {
            admin.getNode("/testroot").remove();
        }
        admin.getRootNode().addNode("testroot", "nt:unstructured").addNode("testnode", "nt:unstructured");
        admin.save();
        
        // Create test user
        UserManager userManager = ((JackrabbitSession)admin).getUserManager();
        String userId = "user1";
        String userPwd = "pwd1";
        User user1 = userManager.createUser(userId, userPwd);
        Principal principal1 = user1.getPrincipal();
        
        // Setup test user ACLs that there are no restrictions
        AccessControlUtils.addAccessControlEntry(admin, null, principal1, new String[]{"jcr:namespaceManagement","jcr:nodeTypeDefinitionManagement"}, true);
        AccessControlUtils.addAccessControlEntry(admin, "/", principal1, new String[]{"jcr:all"}, true);
        admin.save();
        
        Session userSession = repository.login(new SimpleCredentials(userId, userPwd.toCharArray()));
        try {
            packMgr = new JcrPackageManagerImpl(userSession, new String[0], new String[] {"user1"}, null, false);
    
            PackageEventDispatcherImpl dispatcher = new PackageEventDispatcherImpl();
            dispatcher.bindPackageEventListener(new ActivityLog(), Collections.singletonMap("component.id", (Object) "1234"));
            packMgr.setDispatcher(dispatcher);
            
            JcrPackage pack = packMgr.upload(getStream("/test-packages/test_hook.zip"), false);
            assertNotNull(pack);
            packMgr.getInternalRegistry().installPackage(userSession, new JcrRegisteredPackage(pack), getDefaultOptions(), true);
            assertTrue(admin.propertyExists("/testroot/hook-example"));
            
        } finally {
            userSession.logout();
        }
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
        JcrPackage pack = packMgr.upload(getStream("/test-packages/test_hook.zip"), false);
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
        JcrPackage pack = packMgr.upload(getStream("/test-packages/invalid_hook.zip"), false);
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

        JcrPackage pack = packMgr.upload(getStream("/test-packages/external_hook.zip"), false);
        assertNotNull(pack);

        pack.install(getDefaultOptions());

        assertProperty("/testroot/TestHook1", InstallContext.Phase.END.toString());
        assertProperty("/testroot/TestHook2", InstallContext.Phase.END.toString());
    }

    /**
     * Installs a package with an external hook which throws an exception in the INSTALLED phase
     */
    @Test
    public void testExternalHookFailsInInstalledPhase() throws RepositoryException, IOException, PackageException {
        try {
            extractVaultPackage("/test-packages/external_hook_failing_in_installed_phase.zip");
            fail("Package install should fail due to installhook exception.");
        } catch (PackageException e) {
            // ok
        }
        // although there was some exception, this has only been triggered after the package has been extracted!
        assertNodeExists("/testroot");
    }

    /**
     * Installs a package with no properties
     */
    @Test
    public void testNoProperties() throws RepositoryException, IOException, PackageException {
        File tmpFile = File.createTempFile("vlttest", "zip");
        IOUtils.copy(getStream("/test-packages/tmp_no_properties.zip"), FileUtils.openOutputStream(tmpFile));
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
        IOUtils.copy(getStream("/test-packages/test-package-with-etc.zip"), FileUtils.openOutputStream(tmpFile));
        JcrPackage pack = packMgr.upload(tmpFile, true, true, "test-package-with-etc", false);
        assertNodeExists("/etc");
        admin.getNode("/etc").addNode("foo", NodeType.NT_FOLDER);
        admin.save();
        pack.install(getDefaultOptions());
        assertNodeExists("/etc/foo");
    }

    @Test
    public void testDeepContentImport() throws IOException, RepositoryException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("/test-packages/tmp_test_deep.zip"), false);
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
        JcrPackage pack = packMgr.upload(getStream("/test-packages/test_childnodeorder.zip"), false);
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
        JcrPackage pack = packMgr.upload(getStream("/test-packages/test_childnodeorder2.zip"), false);
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
        JcrPackage pack = packMgr.upload(getStream("/test-packages/tmp.zip"), false);
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
        JcrPackage pack = packMgr.upload(getStream("/test-packages/tmp.zip"), false);
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
        JcrPackage pack = packMgr.upload(getStream("/test-packages/tmp.zip"), false);
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
        JcrPackage pack = packMgr.upload(getStream("/test-packages/tmp.zip"), false);
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
        JcrPackage pack = packMgr.upload(getStream("/test-packages/tmp_binary.zip"), false);
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
        JcrPackage pack = packMgr.upload(getStream("/test-packages/tmp_binary.zip"), false);
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
        JcrPackage pack = packMgr.upload(getStream("/test-packages/test_filter_binary.zip"), false);
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
        JcrPackage pack = packMgr.upload(getStream("/test-packages/tmp.zip"), false);
        assertNotNull(pack);
        assertPackageNodeExists(TMP_PACKAGE_ID);

        ImportOptions opts = getDefaultOptions();
        pack.install(opts);

        assertNodeExists("/tmp/foo");
        assertEquals(admin.getNode("/tmp").getPrimaryNodeType().getName(), "sling:OrderedFolder");

        pack = packMgr.upload(getStream("/test-packages/tmp_nt_folder.zip"), false);
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
        JcrPackage pack = packMgr.upload(getStream("/test-packages/test_version.zip"), false);
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
        JcrPackage pack = packMgr.upload(getStream("/test-packages/test_version.zip"), false);
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
     * Installs a package with invalid dependency strings. see JCRVLT-265
     */
    @Test
    public void testInvalidDependenciesInProperties() throws RepositoryException, IOException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("/test-packages/null-dependency-test.zip"), false);
        assertNotNull(pack);
        for (Dependency dep: pack.getDefinition().getDependencies()) {
            assertNotNull("dependency element", dep);
        }
    }

    /**
     * Creates a package definition with invalid dependencies. see JCRVLT-265
     */
    @Test
    public void testInvalidDependenciesInDefinition() throws RepositoryException, IOException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("/test-packages/null-dependency-test.zip"), false);
        assertNotNull(pack);
        Dependency[] deps = {new Dependency(TMP_PACKAGE_ID), null};
        pack.getDefinition().setDependencies(deps, true);
        for (Dependency dep: pack.getDefinition().getDependencies()) {
            assertNotNull("dependency element", dep);
        }

        Value[] values = {admin.getValueFactory().createValue("")};
        pack.getDefNode().setProperty(PN_DEPENDENCIES, values);
        admin.save();

        pack = packMgr.open(pack.getDefinition().getId());
        for (Dependency dep: pack.getDefinition().getDependencies()) {
            assertNotNull("dependency element", dep);
        }
    }

    /**
     * Tests if package installation works w/o RW access to / and /tmp.
     * this currently fails, due to the creation of the snapshot.
     * also see {@link NoRootAccessExportIT#exportNoRootAccess()}
     */
    @Test
    @Ignore("JCRVLT-100")
    public void testInstallWithoutRootAndTmpAccess() throws IOException, RepositoryException, ConfigurationException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("/test-packages/tmp_foo.zip"), true, true);
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
        AccessControlUtils.addAccessControlEntry(admin, ((JcrPackageRegistry)packMgr.getRegistry()).getPackRootPaths()[0], principal1, new String[]{"jcr:all"}, true);
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

        JcrPackage pack = packMgr.upload(getStream("/test-packages/tmp_foo.zip"), true, true);
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
        AccessControlUtils.addAccessControlEntry(admin, ((JcrPackageRegistry)packMgr.getRegistry()).getPackRootPaths()[0], principal1, new String[]{"jcr:all"}, true);
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

    /**
     * Tests if installing a package with a 0-mtime entry works with java9.
     * see http://bugs.java.com/view_bug.do?bug_id=JDK-8184940
     */
    @Test
    public void testPackageInstallWith0MtimeZipEntry() throws IOException, RepositoryException, NoSuchFieldException, IllegalAccessException {
        JcrPackage pack = packMgr.upload(getStream("/test-packages/properties-with-0mtime.zip"), true, true);
        assertEquals("packageid", TMP_PACKAGE_ID, pack.getDefinition().getId());
    }

    // todo: upload with version
    // todo: rename

}
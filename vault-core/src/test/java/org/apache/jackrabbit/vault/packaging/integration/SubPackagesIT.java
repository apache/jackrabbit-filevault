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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;

import org.apache.jackrabbit.vault.fs.io.ImportOptions;
import org.apache.jackrabbit.vault.packaging.Dependency;
import org.apache.jackrabbit.vault.packaging.DependencyHandling;
import org.apache.jackrabbit.vault.packaging.JcrPackage;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.impl.JcrPackageManagerImpl;
import org.apache.jackrabbit.vault.packaging.registry.impl.JcrPackageRegistry;
import org.apache.tika.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Tests that covers sub packages. the package "my_packages:subtest" contains 2 sub packages
 * "my_packages:sub_a" and "my_packages:sub_b" which each contain 1 node /tmp/a and /tmp/b respectively.
 */
@RunWith(Parameterized.class)
public class SubPackagesIT extends IntegrationTestBase {

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {new String[]{"/etc/packages"}, false},
                {new String[]{"/var/packages"}, false},
                {new String[]{"/var/packages", "/etc/packages"}, false},
                {new String[]{"/var/packages/deep/path"}, false}
        });
    }

    private static final PackageId PACKAGE_ID_SUB_TEST = PackageId.fromString("my_packages:subtest");
    private static final PackageId PACKAGE_ID_SUB_A_SNAPSHOT = PackageId.fromString("my_packages/.snapshot:sub_a");
    private static final PackageId PACKAGE_ID_SUB_B_SNAPSHOT = PackageId.fromString("my_packages/.snapshot:sub_b");
    private static final PackageId PACKAGE_ID_SUB_TEST_10 = PackageId.fromString("my_packages:subtest_test_version:1.0");
    private static final PackageId PACKAGE_ID_SUB_TEST_101 = PackageId.fromString("my_packages:subtest_test_version:1.0.1");

    private final String[] packageRoots;

    public SubPackagesIT(String[] packageRoots, boolean dummy) {
        this.packageRoots = packageRoots;
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();

        // overwrite package manager with special root path
        packMgr = new JcrPackageManagerImpl(admin, packageRoots);
    }

    /**
     * Returns the installation path of the package including the ".zip" extension.
     * @param id the package id
     * @return the path
     */
    public String getInstallationPath(PackageId id) {
        // make sure we use the one from the test parameter
        return packageRoots[0] + "/" + ((JcrPackageRegistry)packMgr.getRegistry()).getRelativeInstallationPath(id) + ".zip";
    }

    /**
     * Installs a package that contains sub packages non recursive
     */
    @Test
    public void testNonRecursive() throws RepositoryException, IOException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("/test-packages/subtest.zip"), false);
        assertNotNull(pack);

        // install
        ImportOptions opts = getDefaultOptions();
        opts.setNonRecursive(true);
        pack.install(opts);

        // check for sub packages. should be installed below the primary package root
        assertPackageNodeExists(PACKAGE_ID_SUB_A);
        assertPackageNodeExists(PACKAGE_ID_SUB_B);

        // check for snapshots
        assertPackageNodeMissing(PACKAGE_ID_SUB_A_SNAPSHOT);
        assertPackageNodeMissing(PACKAGE_ID_SUB_B_SNAPSHOT);

        assertNodeMissing("/tmp/a");
        assertNodeMissing("/tmp/b");

        // check for sub packages dependency
        String expected = new Dependency(pack.getDefinition().getId()).toString();

        JcrPackage p1 = packMgr.open(PACKAGE_ID_SUB_A);
        assertEquals("has 1 dependency", 1, p1.getDefinition().getDependencies().length);
        assertEquals("has dependency to parent package", expected, p1.getDefinition().getDependencies()[0].toString());
        JcrPackage p2 = packMgr.open(PACKAGE_ID_SUB_B);
        assertEquals("has 1 dependency", 1, p2.getDefinition().getDependencies().length);
        assertEquals("has dependency to parent package", expected, p2.getDefinition().getDependencies()[0].toString());

    }

    /**
     * Installs a package that contains sub packages recursive but has a sub package handling that ignores A
     */
    @Test
    public void testRecursiveIgnoreA() throws RepositoryException, IOException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("/test-packages/subtest_ignore_a.zip"), false);
        assertNotNull(pack);

        // install
        ImportOptions opts = getDefaultOptions();
        opts.setNonRecursive(false);
        pack.install(opts);

        // check for sub packages
        assertPackageNodeExists(PACKAGE_ID_SUB_A); // todo: ignore should ignore A completely
        assertPackageNodeExists(PACKAGE_ID_SUB_B);

        assertNodeMissing("/tmp/a");
        assertNodeExists("/tmp/b");
    }

    /**
     * Installs a package that contains sub packages recursive but has a sub package handling that only adds A
     */
    @Test
    public void testRecursiveAddA() throws RepositoryException, IOException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("/test-packages/subtest_add_a.zip"), false);
        assertNotNull(pack);

        // install
        ImportOptions opts = getDefaultOptions();
        opts.setNonRecursive(false);
        pack.install(opts);

        // check for sub packages
        assertPackageNodeExists(PACKAGE_ID_SUB_A);
        assertPackageNodeExists(PACKAGE_ID_SUB_B);

        assertNodeMissing("/tmp/a");
        assertNodeExists("/tmp/b");
    }

    /**
     * Installs a package that contains sub packages recursive but has a sub package handling that only extracts A
     */
    @Test
    public void testRecursiveExtractA() throws RepositoryException, IOException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("/test-packages/subtest_extract_a.zip"), false);
        assertNotNull(pack);

        // install
        ImportOptions opts = getDefaultOptions();
        opts.setNonRecursive(false);
        pack.install(opts);

        // check for sub packages
        assertPackageNodeExists(PACKAGE_ID_SUB_A);
        assertPackageNodeExists(PACKAGE_ID_SUB_B);

        // check for snapshots
        assertPackageNodeMissing(PACKAGE_ID_SUB_A_SNAPSHOT);
        assertPackageNodeExists(PACKAGE_ID_SUB_B_SNAPSHOT);

        assertNodeExists("/tmp/a");
        assertNodeExists("/tmp/b");
    }


    /**
     * Installs a package that contains sub packages recursive
     */
    @Test
    public void testRecursive() throws RepositoryException, IOException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("/test-packages/subtest.zip"), false);
        assertNotNull(pack);

        // install
        ImportOptions opts = getDefaultOptions();
        opts.setNonRecursive(false);
        pack.install(opts);

        // check for sub packages
        assertPackageNodeExists(PACKAGE_ID_SUB_A);
        assertPackageNodeExists(PACKAGE_ID_SUB_B);

        // check for snapshots
        assertPackageNodeExists(PACKAGE_ID_SUB_A_SNAPSHOT);
        assertPackageNodeExists(PACKAGE_ID_SUB_B_SNAPSHOT);

        assertNodeExists("/tmp/a");
        assertNodeExists("/tmp/b");
    }


    /**
     * Uninstalls a package that contains sub packages non recursive
     */
    @Test
    public void testUninstallNonRecursive() throws RepositoryException, IOException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("/test-packages/subtest.zip"), false);
        assertNotNull(pack);

        // install
        ImportOptions opts = getDefaultOptions();
        opts.setNonRecursive(false);
        pack.install(opts);

        assertNodeExists("/tmp/a");
        assertNodeExists("/tmp/b");

        // uninstall
        opts.setNonRecursive(true);
        pack.uninstall(opts);

        assertPackageNodeMissing(PACKAGE_ID_SUB_A);
        assertPackageNodeMissing(PACKAGE_ID_SUB_B);
        assertNodeExists("/tmp/a");
        assertNodeExists("/tmp/b");

    }

    /**
     * Uninstalls a package that contains sub packages recursive
     */
    @Test
    public void testUninstallRecursive() throws RepositoryException, IOException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("/test-packages/subtest.zip"), false);
        assertNotNull(pack);

        // install
        ImportOptions opts = getDefaultOptions();
        opts.setNonRecursive(false);
        pack.install(opts);

        assertNodeExists("/tmp/a");
        assertNodeExists("/tmp/b");

        // uninstall
        opts.setNonRecursive(false);
        pack.uninstall(opts);

        assertPackageNodeMissing(PACKAGE_ID_SUB_A);
        assertPackageNodeMissing(PACKAGE_ID_SUB_B);
        assertNodeMissing("/tmp/a");
        assertNodeMissing("/tmp/b");

    }

    /**
     * Tests if non-recursive extraction clears the installed state (JCRVLT-114)
     */
    @Test
    public void testNonRecursiveClearsInstalledState() throws RepositoryException, IOException, PackageException {
        JcrPackage packNewer = packMgr.upload(getStream("/test-packages/subtest_extract_contains_newer_version.zip"), false);
        assertNotNull(packNewer);

        // extract the sub packages, but don't install them.
        ImportOptions opts = getDefaultOptions();
        opts.setNonRecursive(true);
        packNewer.install(opts);

        // check for sub packages version 1.0.1 exists but not installed
        assertNodeMissing("/tmp/a");
        assertPackageNodeExists(PACKAGE_ID_SUB_TEST_101);
        assertFalse(packMgr.open(admin.getNode(getInstallationPath(PACKAGE_ID_SUB_TEST_101))).isInstalled());
    }

    /**
     * Uninstalls a package that contains sub packages where a snapshot of a sub package was deleted
     */
    @Test
    public void testUninstallMissingSnapshot() throws RepositoryException, IOException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("/test-packages/subtest.zip"), false);
        assertNotNull(pack);

        // install
        ImportOptions opts = getDefaultOptions();
        opts.setNonRecursive(false);
        pack.install(opts);

        assertNodeExists("/tmp/a");
        assertNodeExists("/tmp/b");

        admin.getNode(getInstallationPath(PACKAGE_ID_SUB_A_SNAPSHOT)).remove();
        admin.save();

        // uninstall
        opts.setNonRecursive(false);
        pack.uninstall(opts);

        assertPackageNodeMissing(PACKAGE_ID_SUB_A);
        assertPackageNodeMissing(PACKAGE_ID_SUB_B);
        assertNodeExists("/tmp/a");
        assertNodeMissing("/tmp/b");

    }

    /**
     * Installs 2 packages that contains same sub packages with different version
     */
    @Test
    public void testSkipOlderVersionInstallation() throws RepositoryException, IOException, PackageException {
        JcrPackage packNewer = packMgr.upload(getStream("/test-packages/subtest_extract_contains_newer_version.zip"), false);
        assertNotNull(packNewer);

        // install package that contains newer version of the sub package first
        ImportOptions opts = getDefaultOptions();
        opts.setNonRecursive(false);
        packNewer.install(opts);

        // check for sub packages version 1.0.1 exists
        assertPackageNodeExists(PACKAGE_ID_SUB_TEST_101);
        assertTrue(packMgr.open(admin.getNode(getInstallationPath(PACKAGE_ID_SUB_TEST_101))).isInstalled());
        assertNodeExists("/tmp/b");

        opts = getDefaultOptions();
        opts.setNonRecursive(false);
        JcrPackage packOlder = packMgr.upload(getStream("/test-packages/subtest_extract_contains_older_version.zip"), false);
        packOlder.install(opts);
        assertPackageNodeExists(PACKAGE_ID_SUB_TEST_10);
        assertFalse(packMgr.open(admin.getNode(getInstallationPath(PACKAGE_ID_SUB_TEST_10))).isInstalled());
        assertNodeMissing("/tmp/a");

    }

    /**
     * Tests if skipping sub packages only works for installed packages
     */
    @Test
    public void testNotSkipOlderVersionInstallation() throws RepositoryException, IOException, PackageException {
        JcrPackage packNewer = packMgr.upload(getStream("/test-packages/subtest_extract_contains_newer_version.zip"), false);
        assertNotNull(packNewer);

        // extract the sub packages, but don't install them.
        ImportOptions opts = getDefaultOptions();
        opts.setNonRecursive(true);
        packNewer.install(opts);

        // check for sub packages version 1.0.1 exists but not installed
        assertNodeMissing("/tmp/a");
        assertPackageNodeExists(PACKAGE_ID_SUB_TEST_101);
        assertFalse(packMgr.open(admin.getNode(getInstallationPath(PACKAGE_ID_SUB_TEST_101))).isInstalled());

        opts = getDefaultOptions();
        opts.setNonRecursive(false);
        JcrPackage packOlder = packMgr.upload(getStream("/test-packages/subtest_extract_contains_older_version.zip"), false);
        packOlder.install(opts);
        assertPackageNodeExists(PACKAGE_ID_SUB_TEST_10);
        assertTrue(packMgr.open(admin.getNode(getInstallationPath(PACKAGE_ID_SUB_TEST_10))).isInstalled());
        assertNodeExists("/tmp/a");
    }

    @Test
    public void testDowngradeInstallationOfSubpackages() throws PathNotFoundException, RepositoryException, IOException, PackageException {
        try (JcrPackage packNewer = packMgr.upload(getStream("/test-packages/subtest_extract_contains_newer_version.zip"), false)) {
            assertNotNull(packNewer);
    
            // install package that contains newer version of the sub package first
            ImportOptions opts = getDefaultOptions();
            packNewer.install(opts);
        }
        // check for sub packages version 1.0.1 exists
        assertPackageNodeExists(PACKAGE_ID_SUB_TEST_101);
        try (JcrPackage subPackage = packMgr.open(admin.getNode(getInstallationPath(PACKAGE_ID_SUB_TEST_101)))) {
            assertTrue(subPackage.isInstalled());
        }
        assertNodeExists("/tmp/b");
        
        // now install package which is supposed to downgrade
        try (JcrPackage packOlder = packMgr.upload(getStream("/test-packages/subtest_extract_contains_older_version_force_downgrade.zip"), false)) {
            assertNotNull(packOlder);
            packOlder.install(getDefaultOptions());
        }
        assertPackageNodeExists(PACKAGE_ID_SUB_TEST_10);
        try (JcrPackage subPackage = packMgr.open(admin.getNode(getInstallationPath(PACKAGE_ID_SUB_TEST_10)))) {
            assertTrue("Older Subpackage is not installed, although it is explicitly requested to downgrade", subPackage.isInstalled());
        }
        assertNodeExists("/tmp/a");
    }

    /**
     * Test if subpackage extraction works
     */
    @Test
    public void testPackageExtract() throws IOException, RepositoryException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("/test-packages/subtest.zip"), false);
        assertNotNull(pack);

        // install
        ImportOptions opts = getDefaultOptions();
        PackageId[] ids = pack.extractSubpackages(opts);

        // check for sub packages
        assertPackageNodeExists(PACKAGE_ID_SUB_A);
        assertPackageNodeExists(PACKAGE_ID_SUB_B);

        assertEquals("Package Id", ids[0], PACKAGE_ID_SUB_A);
        assertEquals("Package Id", ids[1], PACKAGE_ID_SUB_B);
    }

    /**
     * Test if subpackage extraction works twice
     */
    @Test
    public void testPackageExtractTwice() throws IOException, RepositoryException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("/test-packages/subtest.zip"), false);
        assertNotNull(pack);

        // install
        ImportOptions opts = getDefaultOptions();
        PackageId[] ids = pack.extractSubpackages(opts);
        PackageId pid = ids[0];
        JcrPackage subPackage = packMgr.open(pid);
        subPackage.install(opts);
        assertTrue("Package is installed", subPackage.isInstalled());

        ids = pack.extractSubpackages(opts);
        assertEquals("Package Id", ids[0], PACKAGE_ID_SUB_A);
        assertEquals("Package Id", ids[1], PACKAGE_ID_SUB_B);

        subPackage = packMgr.open(pid);
        subPackage.install(opts);
        assertTrue("Package is still installed", subPackage.isInstalled());
    }

    /**
     * Test if extracted sub-packages have their parent package as dependency, even if not specified in their properties.
     * but not, if the parent package has no content or no nodetypes.
     */
    @Test
    public void testSubPackageDependency() throws IOException, RepositoryException, PackageException {
        // install other package that provides sling node type
        JcrPackage pack = packMgr.upload(getStream("/test-packages/test_a-1.0.zip"), false);
        ImportOptions opts = getDefaultOptions();
        pack.install(opts);
        assertTrue(admin.getWorkspace().getNodeTypeManager().hasNodeType("sling:Folder"));

        pack = packMgr.upload(getStream("/test-packages/subtest.zip"), false);
        assertNotNull(pack);

        // install
        opts = getDefaultOptions();
        pack.extractSubpackages(opts);

        JcrPackage p1 = packMgr.open(admin.getNode(getInstallationPath(PACKAGE_ID_SUB_A)));
        assertEquals("has 0 dependency", 0, p1.getDefinition().getDependencies().length);

        JcrPackage p2 = packMgr.open(admin.getNode(getInstallationPath(PACKAGE_ID_SUB_B)));
        assertEquals("has 0 dependency", 0, p2.getDefinition().getDependencies().length);

        // parent package should node be be installed.
        assertEquals("Parent package with not content should be marked as installed.", true, pack.isInstalled());
    }

    /**
     * Test if extracted sub-packages inherit the dependencies of their parent packages.
     */
    @Test
    public void testSubPackageInheritDependency() throws IOException, RepositoryException, PackageException {
        // install other package that provides sling node type
        JcrPackage pack = packMgr.upload(getStream("/test-packages/test_a-1.0.zip"), false);
        ImportOptions opts = getDefaultOptions();
        pack.install(opts);
        assertTrue(admin.getWorkspace().getNodeTypeManager().hasNodeType("sling:Folder"));

        pack = packMgr.upload(getStream("/test-packages/subtest_inherit_dep.zip"), false);
        assertNotNull(pack);

        // install
        opts = getDefaultOptions();
        pack.extractSubpackages(opts);

        // check for sub packages dependency
        String expected = "testGroup:testName:[1.0,2.0]";

        JcrPackage p1 = packMgr.open(PACKAGE_ID_SUB_A);
        assertEquals("has 1 dependency", 1, p1.getDefinition().getDependencies().length);
        assertEquals("has dep inherited from parent", expected, p1.getDefinition().getDependencies()[0].toString());

        JcrPackage p2 = packMgr.open(PACKAGE_ID_SUB_B);
        assertEquals("has 1 dependency", 1, p2.getDefinition().getDependencies().length);
        assertEquals("has dep inherited from parent", expected, p2.getDefinition().getDependencies()[0].toString());

        // parent package should node be be installed.
        assertEquals("Parent package with not content should be marked as installed.", true, pack.isInstalled());
    }

    /**
     * Test if extracted sub-packages have their parent package as dependency, even if not specified in their properties.
     * but only if the parent package has content
     */
    @Test
    public void testSubPackageWithContentDependency() throws IOException, RepositoryException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("/test-packages/subtest_with_content.zip"), false);
        assertNotNull(pack);
        PackageId pId = pack.getDefinition().getId();

        // install
        ImportOptions opts = getDefaultOptions();
        pack.extractSubpackages(opts);

        // check for sub packages dependency
        String expected = new Dependency(pId).toString();

        JcrPackage p1 = packMgr.open(admin.getNode(getInstallationPath(PACKAGE_ID_SUB_A)));
        assertEquals("has 1 dependency", 1, p1.getDefinition().getDependencies().length);
        assertEquals("has dependency to parent package", expected, p1.getDefinition().getDependencies()[0].toString());
        JcrPackage p2 = packMgr.open(admin.getNode(getInstallationPath(PACKAGE_ID_SUB_B)));
        assertEquals("has 1 dependency", 1, p2.getDefinition().getDependencies().length);
        assertEquals("has dependency to parent package", expected, p2.getDefinition().getDependencies()[0].toString());

        // parent package should not be installed.
        assertEquals("Parent package with content should not be marked as installed.", false, pack.isInstalled());
    }

    /**
     * Test if extracted sub-packages have their parent package as dependency, even if not specified in their properties.
     * but only if the parent package has nodetypes
     */
    @Test
    public void testSubPackageWithNodeTypesDependency() throws IOException, RepositoryException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("/test-packages/subtest_with_nodetypes.zip"), false);
        assertNotNull(pack);
        PackageId pId = pack.getDefinition().getId();

        // install
        ImportOptions opts = getDefaultOptions();
        pack.extractSubpackages(opts);

        // check for sub packages dependency
        String expected = new Dependency(pId).toString();

        JcrPackage p1 = packMgr.open(admin.getNode(getInstallationPath(PACKAGE_ID_SUB_A)));
        assertEquals("has 1 dependency", 1, p1.getDefinition().getDependencies().length);
        assertEquals("has dependency to parent package", expected, p1.getDefinition().getDependencies()[0].toString());
        JcrPackage p2 = packMgr.open(admin.getNode(getInstallationPath(PACKAGE_ID_SUB_B)));
        assertEquals("has 1 dependency", 1, p2.getDefinition().getDependencies().length);
        assertEquals("has dependency to parent package", expected, p2.getDefinition().getDependencies()[0].toString());

        // parent package should not be installed.
        assertEquals("Parent package with content should not be marked as installed.", false, pack.isInstalled());
    }

    /**
     * Test if subpackage extraction works recursively
     */
    @Test
    public void testRecursivePackageExtract() throws IOException, RepositoryException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("/test-packages/subsubtest.zip"), false);
        assertNotNull(pack);

        // install
        ImportOptions opts = getDefaultOptions();
        opts.setNonRecursive(false);
        PackageId[] ids = pack.extractSubpackages(opts);

        // check for sub packages
        assertPackageNodeExists(PACKAGE_ID_SUB_TEST);
        assertPackageNodeExists(PACKAGE_ID_SUB_A);
        assertPackageNodeExists(PACKAGE_ID_SUB_B);

        assertEquals("Package Id", ids[0], PACKAGE_ID_SUB_A);
        assertEquals("Package Id", ids[1], PACKAGE_ID_SUB_B);
        assertEquals("Package Id", ids[2], PACKAGE_ID_SUB_TEST);
    }

    /**
     * Test if subpackage extraction works non-recursively
     */
    @Test
    public void testNonRecursivePackageExtract() throws IOException, RepositoryException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("/test-packages/subsubtest.zip"), false);
        assertNotNull(pack);

        // install
        ImportOptions opts = getDefaultOptions();
        opts.setNonRecursive(true);
        PackageId[] ids = pack.extractSubpackages(opts);

        // check for sub packages
        assertPackageNodeExists(PACKAGE_ID_SUB_TEST);
        assertPackageNodeMissing(PACKAGE_ID_SUB_A);
        assertPackageNodeMissing(PACKAGE_ID_SUB_B);

        assertEquals("Package Id", ids[0], PACKAGE_ID_SUB_TEST);
    }

    @Test
    public void testSubPackageDependency2() throws IOException, RepositoryException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("/test-packages/subsubtest.zip"), false);
        assertNotNull(pack);

        // install
        ImportOptions opts = getDefaultOptions();
        pack.extractSubpackages(opts);

        JcrPackage p0 = packMgr.open(admin.getNode(getInstallationPath(PACKAGE_ID_SUB_TEST)));
        assertEquals("has 0 dependency", 0, p0.getDefinition().getDependencies().length);

        // check for sub packages dependency
        String expected = new Dependency(p0.getDefinition().getId()).toString();
        JcrPackage p1 = packMgr.open(admin.getNode(getInstallationPath(PACKAGE_ID_SUB_A)));
        assertEquals("has 1 dependency", 1, p1.getDefinition().getDependencies().length);
        assertEquals("has dependency to parent package", expected, p1.getDefinition().getDependencies()[0].toString());
        JcrPackage p2 = packMgr.open(admin.getNode(getInstallationPath(PACKAGE_ID_SUB_B)));
        assertEquals("has 1 dependency", 1, p2.getDefinition().getDependencies().length);
        assertEquals("has dependency to parent package", expected, p2.getDefinition().getDependencies()[0].toString());
    }

    @Test
    public void testInstallingSubPackagesTwice() throws IOException, RepositoryException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("/test-packages/subtest.zip"), true);
        assertNotNull(pack);

        // install
        ImportOptions opts = getDefaultOptions();
        opts.setNonRecursive(true);
        pack.install(opts);

        // install
        opts = getDefaultOptions();
        opts.setNonRecursive(true);
        pack.install(opts);
    }

    /**
     * Test if installing and re-creating a package with sub-packages on an alternative path results in the same package again.
     */
    @Test
    public void testRoundTrip() throws IOException, RepositoryException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("/test-packages/subtest.zip"), false);
        assertNotNull(pack);

        // install
        ImportOptions opts = getDefaultOptions();
        opts.setNonRecursive(true);
        pack.install(opts);

        // create new package
        JcrPackage pkg = packMgr.open(PACKAGE_ID_SUB_TEST);
        //
        packMgr.assemble(pkg, getLoggingProgressTrackerListener());

        try (ZipInputStream in = new ZipInputStream(pkg.getData().getBinary().getStream())) {
            ZipEntry e;
            List<String> entries = new ArrayList<>();
            String filter = "";
            while ((e = in.getNextEntry()) != null) {
                entries.add(e.getName());
                if ("META-INF/vault/filter.xml".equals(e.getName())) {
                    filter = IOUtils.toString(in, "utf-8");
                }
            }
            Collections.sort(entries);
            StringBuffer result = new StringBuffer();
            for (String name: entries) {
                // exclude some of the entries that depend on the repository setup
                if ("jcr_root/etc/.content.xml".equals(name)
                        || "jcr_root/etc/packages/my_packages/.content.xml".equals(name)
                        || "jcr_root/etc/packages/.content.xml".equals(name)) {
                    continue;
                }
                result.append(name).append("\n");
            }
        
            assertEquals("Filter must be correct",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<workspaceFilter version=\"1.0\">\n" +
                    "    <filter root=\"/etc/packages/my_packages/sub_a.zip\"/>\n" +
                    "    <filter root=\"/etc/packages/my_packages/sub_b.zip\"/>\n" +
                    "</workspaceFilter>\n", filter);
    
            assertEquals("Package must contain proper entries.",
                    "META-INF/\n" +
                    "META-INF/MANIFEST.MF\n" +
                    "META-INF/vault/\n" +
                    "META-INF/vault/config.xml\n" +
                    "META-INF/vault/definition/\n" +
                    "META-INF/vault/definition/.content.xml\n" +
                    "META-INF/vault/filter.xml\n" +
                    "META-INF/vault/nodetypes.cnd\n" +
                    "META-INF/vault/properties.xml\n" +
                    "jcr_root/.content.xml\n" +
                    "jcr_root/etc/\n" +
                    "jcr_root/etc/packages/\n" +
                    "jcr_root/etc/packages/my_packages/\n" +
                    "jcr_root/etc/packages/my_packages/sub_a.zip\n" +
                    "jcr_root/etc/packages/my_packages/sub_a.zip.dir/\n" +
                    "jcr_root/etc/packages/my_packages/sub_a.zip.dir/.content.xml\n" +
                    "jcr_root/etc/packages/my_packages/sub_a.zip.dir/_jcr_content/\n" +
                    "jcr_root/etc/packages/my_packages/sub_a.zip.dir/_jcr_content/_vlt_definition/\n" +
                    "jcr_root/etc/packages/my_packages/sub_a.zip.dir/_jcr_content/_vlt_definition/.content.xml\n" +
                    "jcr_root/etc/packages/my_packages/sub_b.zip\n" +
                    "jcr_root/etc/packages/my_packages/sub_b.zip.dir/\n" +
                    "jcr_root/etc/packages/my_packages/sub_b.zip.dir/.content.xml\n" +
                    "jcr_root/etc/packages/my_packages/sub_b.zip.dir/_jcr_content/\n" +
                    "jcr_root/etc/packages/my_packages/sub_b.zip.dir/_jcr_content/_vlt_definition/\n" +
                    "jcr_root/etc/packages/my_packages/sub_b.zip.dir/_jcr_content/_vlt_definition/.content.xml\n", result.toString());
        }
    }

    /**
     * Test extracts a multipackage-a-1.0 which contains content and a subpackage. The subpackage will add a dependency
     * on the parent package. later installs newer version multi-package-a-2.0 which will try to reinstall the subpackage,
     * but the dependency needs to update. see JCRVLT-264
     */
    @Test
    public void testMixedPackageUpdatesCorrectly() throws Exception {
       // install 1.0
        JcrPackage pack = packMgr.upload(getStream("/test-packages/multipackage-a-1.0.zip"), false);
        assertNotNull(pack);

        ImportOptions opts = getDefaultOptions();
        pack.install(opts);

        assertNodeExists("/tmp/testroot/sub");
        assertProperty("/apps/test/version","1.0");

        // install 2.0
        pack = packMgr.upload(getStream("/test-packages/multipackage-a-2.0.zip"), false);
        assertNotNull(pack);
        opts = getDefaultOptions();
        opts.setDependencyHandling(DependencyHandling.REQUIRED);
        pack.install(opts);

        assertNodeExists("/tmp/testroot/sub");
        assertProperty("/apps/test/version","2.0");

    }
}
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

import javax.jcr.RepositoryException;

import org.apache.jackrabbit.vault.fs.io.ImportOptions;
import org.apache.jackrabbit.vault.packaging.Dependency;
import org.apache.jackrabbit.vault.packaging.JcrPackage;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests that covers sub packages. the package "my_packages:subtest" contains 2 sub packages
 * "my_packages:sub_a" and "my_packages:sub_b" which each contain 1 node /tmp/a and /tmp/b respectively.
 */
public class TestSubPackages extends IntegrationTestBase {

    /**
     * Installs a package that contains sub packages non recursive
     */
    @Test
    public void testNonRecursive() throws RepositoryException, IOException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("testpackages/subtest.zip"), false);
        assertNotNull(pack);

        // install
        ImportOptions opts = getDefaultOptions();
        opts.setNonRecursive(true);
        pack.install(opts);

        // check for sub packages
        assertNodeExists("/etc/packages/my_packages/sub_a.zip");
        assertNodeExists("/etc/packages/my_packages/sub_b.zip");

        // check for snapshots
        assertNodeMissing("/etc/packages/my_packages/.snapshot/sub_a.zip");
        assertNodeMissing("/etc/packages/my_packages/.snapshot/sub_b.zip");

        assertNodeMissing("/tmp/a");
        assertNodeMissing("/tmp/b");

        // check for sub packages dependency
        String expected = new Dependency(pack.getDefinition().getId()).toString();

        JcrPackage p1 = packMgr.open(admin.getNode("/etc/packages/my_packages/sub_a.zip"));
        assertEquals("has 1 dependency", 1, p1.getDefinition().getDependencies().length);
        assertEquals("has dependency to parent package", expected, p1.getDefinition().getDependencies()[0].toString());
        JcrPackage p2 = packMgr.open(admin.getNode("/etc/packages/my_packages/sub_b.zip"));
        assertEquals("has 1 dependency", 1, p2.getDefinition().getDependencies().length);
        assertEquals("has dependency to parent package", expected, p2.getDefinition().getDependencies()[0].toString());

    }

    /**
     * Installs a package that contains sub packages recursive but has a sub package handling that ignores A
     */
    @Test
    public void testRecursiveIgnoreA() throws RepositoryException, IOException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("testpackages/subtest_ignore_a.zip"), false);
        assertNotNull(pack);

        // install
        ImportOptions opts = getDefaultOptions();
        opts.setNonRecursive(false);
        pack.install(opts);

        // check for sub packages
        assertNodeExists("/etc/packages/my_packages/sub_a.zip"); // todo: ignore should ignore A completely
        assertNodeExists("/etc/packages/my_packages/sub_b.zip");

        assertNodeMissing("/tmp/a");
        assertNodeExists("/tmp/b");
    }

    /**
     * Installs a package that contains sub packages recursive but has a sub package handling that ignores A
     */
    @Test
    public void testRecursiveAddA() throws RepositoryException, IOException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("testpackages/subtest_add_a.zip"), false);
        assertNotNull(pack);

        // install
        ImportOptions opts = getDefaultOptions();
        opts.setNonRecursive(false);
        pack.install(opts);

        // check for sub packages
        assertNodeExists("/etc/packages/my_packages/sub_a.zip");
        assertNodeExists("/etc/packages/my_packages/sub_b.zip");

        assertNodeMissing("/tmp/a");
        assertNodeExists("/tmp/b");
    }

    /**
     * Installs a package that contains sub packages recursive but has a sub package handling that only extracts A
     */
    @Test
    public void testRecursiveExtractA() throws RepositoryException, IOException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("testpackages/subtest_extract_a.zip"), false);
        assertNotNull(pack);

        // install
        ImportOptions opts = getDefaultOptions();
        opts.setNonRecursive(false);
        pack.install(opts);

        // check for sub packages
        assertNodeExists("/etc/packages/my_packages/sub_a.zip");
        assertNodeExists("/etc/packages/my_packages/sub_b.zip");

        // check for snapshots
        assertNodeMissing("/etc/packages/my_packages/.snapshot/sub_a.zip");
        assertNodeExists("/etc/packages/my_packages/.snapshot/sub_b.zip");

        assertNodeExists("/tmp/a");
        assertNodeExists("/tmp/b");
    }


    /**
     * Installs a package that contains sub packages recursive
     */
    @Test
    public void testRecursive() throws RepositoryException, IOException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("testpackages/subtest.zip"), false);
        assertNotNull(pack);

        // install
        ImportOptions opts = getDefaultOptions();
        opts.setNonRecursive(false);
        pack.install(opts);

        // check for sub packages
        assertNodeExists("/etc/packages/my_packages/sub_a.zip");
        assertNodeExists("/etc/packages/my_packages/sub_b.zip");

        // check for snapshots
        assertNodeExists("/etc/packages/my_packages/.snapshot/sub_a.zip");
        assertNodeExists("/etc/packages/my_packages/.snapshot/sub_b.zip");

        assertNodeExists("/tmp/a");
        assertNodeExists("/tmp/b");
    }


    /**
     * Uninstalls a package that contains sub packages non recursive
     */
    @Test
    public void testUninstallNonRecursive() throws RepositoryException, IOException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("testpackages/subtest.zip"), false);
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

        assertNodeMissing("/etc/packages/my_packages/sub_a.zip");
        assertNodeMissing("/etc/packages/my_packages/sub_b.zip");
        assertNodeExists("/tmp/a");
        assertNodeExists("/tmp/b");

    }

    /**
     * Uninstalls a package that contains sub packages recursive
     */
    @Test
    public void testUninstallRecursive() throws RepositoryException, IOException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("testpackages/subtest.zip"), false);
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

        assertNodeMissing("/etc/packages/my_packages/sub_a.zip");
        assertNodeMissing("/etc/packages/my_packages/sub_b.zip");
        assertNodeMissing("/tmp/a");
        assertNodeMissing("/tmp/b");

    }

    /**
     * Tests if non-recursive extraction clears the installed state (JCRVLT-114)
     */
    @Test
    public void testNonRecursiveClearsInstalledState() throws RepositoryException, IOException, PackageException {
        JcrPackage packNewer = packMgr.upload(getStream("testpackages/subtest_extract_contains_newer_version.zip"), false);
        assertNotNull(packNewer);

        // extract the sub packages, but don't install them.
        ImportOptions opts = getDefaultOptions();
        opts.setNonRecursive(true);
        packNewer.install(opts);

        // check for sub packages version 1.0.1 exists but not installed
        assertNodeMissing("/tmp/a");
        assertNodeExists("/etc/packages/my_packages/subtest_test_version-1.0.1.zip");
        assertFalse(packMgr.open(admin.getNode("/etc/packages/my_packages/subtest_test_version-1.0.1.zip")).isInstalled());
    }

    /**
     * Uninstalls a package that contains sub packages where a snapshot of a sub package was deleted
     */
    @Test
    public void testUninstallMissingSnapshot() throws RepositoryException, IOException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("testpackages/subtest.zip"), false);
        assertNotNull(pack);

        // install
        ImportOptions opts = getDefaultOptions();
        opts.setNonRecursive(false);
        pack.install(opts);

        assertNodeExists("/tmp/a");
        assertNodeExists("/tmp/b");

        admin.getNode("/etc/packages/my_packages/.snapshot/sub_a.zip").remove();
        admin.save();

        // uninstall
        opts.setNonRecursive(false);
        pack.uninstall(opts);

        assertNodeMissing("/etc/packages/my_packages/sub_a.zip");
        assertNodeMissing("/etc/packages/my_packages/sub_b.zip");
        assertNodeExists("/tmp/a");
        assertNodeMissing("/tmp/b");

    }

    /**
     * Installs 2 packages that contains same sub packages with different version
     */
    @Test
    public void testSkipOlderVersionInstallation() throws RepositoryException, IOException, PackageException {
        JcrPackage packNewer = packMgr.upload(getStream("testpackages/subtest_extract_contains_newer_version.zip"), false);
        assertNotNull(packNewer);

        // install package that contains newer version of the sub package first
        ImportOptions opts = getDefaultOptions();
        opts.setNonRecursive(false);
        packNewer.install(opts);

        // check for sub packages version 1.0.1 exists
        assertNodeExists("/etc/packages/my_packages/subtest_test_version-1.0.1.zip");
        assertTrue(packMgr.open(admin.getNode("/etc/packages/my_packages/subtest_test_version-1.0.1.zip")).isInstalled());
        assertNodeExists("/tmp/b");

        opts = getDefaultOptions();
        opts.setNonRecursive(false);
        JcrPackage packOlder = packMgr.upload(getStream("testpackages/subtest_extract_contains_older_version.zip"), false);
        packOlder.install(opts);
        assertNodeExists("/etc/packages/my_packages/subtest_test_version-1.0.zip");
        assertFalse(packMgr.open(admin.getNode("/etc/packages/my_packages/subtest_test_version-1.0.zip")).isInstalled());
        assertNodeMissing("/tmp/a");

    }

    /**
     * Tests if skipping sub packages only works for installed packages
     */
    @Test
    public void testNotSkipOlderVersionInstallation() throws RepositoryException, IOException, PackageException {
        JcrPackage packNewer = packMgr.upload(getStream("testpackages/subtest_extract_contains_newer_version.zip"), false);
        assertNotNull(packNewer);

        // extract the sub packages, but don't install them.
        ImportOptions opts = getDefaultOptions();
        opts.setNonRecursive(true);
        packNewer.install(opts);

        // check for sub packages version 1.0.1 exists but not installed
        assertNodeMissing("/tmp/a");
        assertNodeExists("/etc/packages/my_packages/subtest_test_version-1.0.1.zip");
        assertFalse(packMgr.open(admin.getNode("/etc/packages/my_packages/subtest_test_version-1.0.1.zip")).isInstalled());

        opts = getDefaultOptions();
        opts.setNonRecursive(false);
        JcrPackage packOlder = packMgr.upload(getStream("testpackages/subtest_extract_contains_older_version.zip"), false);
        packOlder.install(opts);
        assertNodeExists("/etc/packages/my_packages/subtest_test_version-1.0.zip");
        assertTrue(packMgr.open(admin.getNode("/etc/packages/my_packages/subtest_test_version-1.0.zip")).isInstalled());
        assertNodeExists("/tmp/a");

    }

    /**
     * Test if subpackage extraction works
     */
    @Test
    public void testPackageExtract() throws IOException, RepositoryException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("testpackages/subtest.zip"), false);
        assertNotNull(pack);

        // install
        ImportOptions opts = getDefaultOptions();
        PackageId[] ids = pack.extractSubpackages(opts);

        // check for sub packages
        assertNodeExists("/etc/packages/my_packages/sub_a.zip");
        assertNodeExists("/etc/packages/my_packages/sub_b.zip");

        assertEquals("Package Id", ids[0].toString(), "my_packages:sub_a");
        assertEquals("Package Id", ids[1].toString(), "my_packages:sub_b");
    }

    /**
     * Test if subpackage extraction works twice
     */
    @Test
    public void testPackageExtractTwice() throws IOException, RepositoryException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("testpackages/subtest.zip"), false);
        assertNotNull(pack);

        // install
        ImportOptions opts = getDefaultOptions();
        PackageId[] ids = pack.extractSubpackages(opts);
        PackageId pid = ids[0];
        JcrPackage subPackage = packMgr.open(pid);
        subPackage.install(opts);
        assertTrue("Package is installed", subPackage.isInstalled());

        ids = pack.extractSubpackages(opts);
        assertEquals("Package Id", ids[0].toString(), "my_packages:sub_a");
        assertEquals("Package Id", ids[1].toString(), "my_packages:sub_b");

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
        JcrPackage pack = packMgr.upload(getStream("testpackages/test_a-1.0.zip"), false);
        ImportOptions opts = getDefaultOptions();
        pack.install(opts);
        assertTrue(admin.getWorkspace().getNodeTypeManager().hasNodeType("sling:Folder"));

        pack = packMgr.upload(getStream("testpackages/subtest.zip"), false);
        assertNotNull(pack);

        // install
        opts = getDefaultOptions();
        pack.extractSubpackages(opts);

        JcrPackage p1 = packMgr.open(admin.getNode("/etc/packages/my_packages/sub_a.zip"));
        assertEquals("has 0 dependency", 0, p1.getDefinition().getDependencies().length);

        JcrPackage p2 = packMgr.open(admin.getNode("/etc/packages/my_packages/sub_b.zip"));
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
        JcrPackage pack = packMgr.upload(getStream("testpackages/test_a-1.0.zip"), false);
        ImportOptions opts = getDefaultOptions();
        pack.install(opts);
        assertTrue(admin.getWorkspace().getNodeTypeManager().hasNodeType("sling:Folder"));

        pack = packMgr.upload(getStream("testpackages/subtest_inherit_dep.zip"), false);
        assertNotNull(pack);

        // install
        opts = getDefaultOptions();
        pack.extractSubpackages(opts);

        // check for sub packages dependency
        String expected = "testGroup:testName:[1.0,2.0]";

        JcrPackage p1 = packMgr.open(admin.getNode("/etc/packages/my_packages/sub_a.zip"));
        assertEquals("has 1 dependency", 1, p1.getDefinition().getDependencies().length);
        assertEquals("has dep inherited from parent", expected, p1.getDefinition().getDependencies()[0].toString());

        JcrPackage p2 = packMgr.open(admin.getNode("/etc/packages/my_packages/sub_b.zip"));
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
        JcrPackage pack = packMgr.upload(getStream("testpackages/subtest_with_content.zip"), false);
        assertNotNull(pack);
        PackageId pId = pack.getDefinition().getId();

        // install
        ImportOptions opts = getDefaultOptions();
        pack.extractSubpackages(opts);

        // check for sub packages dependency
        String expected = new Dependency(pId).toString();

        JcrPackage p1 = packMgr.open(admin.getNode("/etc/packages/my_packages/sub_a.zip"));
        assertEquals("has 1 dependency", 1, p1.getDefinition().getDependencies().length);
        assertEquals("has dependency to parent package", expected, p1.getDefinition().getDependencies()[0].toString());
        JcrPackage p2 = packMgr.open(admin.getNode("/etc/packages/my_packages/sub_b.zip"));
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
        JcrPackage pack = packMgr.upload(getStream("testpackages/subtest_with_nodetypes.zip"), false);
        assertNotNull(pack);
        PackageId pId = pack.getDefinition().getId();

        // install
        ImportOptions opts = getDefaultOptions();
        pack.extractSubpackages(opts);

        // check for sub packages dependency
        String expected = new Dependency(pId).toString();

        JcrPackage p1 = packMgr.open(admin.getNode("/etc/packages/my_packages/sub_a.zip"));
        assertEquals("has 1 dependency", 1, p1.getDefinition().getDependencies().length);
        assertEquals("has dependency to parent package", expected, p1.getDefinition().getDependencies()[0].toString());
        JcrPackage p2 = packMgr.open(admin.getNode("/etc/packages/my_packages/sub_b.zip"));
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
        JcrPackage pack = packMgr.upload(getStream("testpackages/subsubtest.zip"), false);
        assertNotNull(pack);

        // install
        ImportOptions opts = getDefaultOptions();
        opts.setNonRecursive(false);
        PackageId[] ids = pack.extractSubpackages(opts);

        // check for sub packages
        assertNodeExists("/etc/packages/my_packages/subtest.zip");
        assertNodeExists("/etc/packages/my_packages/sub_a.zip");
        assertNodeExists("/etc/packages/my_packages/sub_b.zip");

        assertEquals("Package Id", ids[0].toString(), "my_packages:sub_a");
        assertEquals("Package Id", ids[1].toString(), "my_packages:sub_b");
        assertEquals("Package Id", ids[2].toString(), "my_packages:subtest");
    }

    /**
     * Test if subpackage extraction works non-recursively
     */
    @Test
    public void testNonRecursivePackageExtract() throws IOException, RepositoryException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("testpackages/subsubtest.zip"), false);
        assertNotNull(pack);

        // install
        ImportOptions opts = getDefaultOptions();
        opts.setNonRecursive(true);
        PackageId[] ids = pack.extractSubpackages(opts);

        // check for sub packages
        assertNodeExists("/etc/packages/my_packages/subtest.zip");
        assertNodeMissing("/etc/packages/my_packages/sub_a.zip");
        assertNodeMissing("/etc/packages/my_packages/sub_b.zip");

        assertEquals("Package Id", ids[0].toString(), "my_packages:subtest");
    }

    @Test
    public void testSubPackageDependency2() throws IOException, RepositoryException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("testpackages/subsubtest.zip"), false);
        assertNotNull(pack);

        // install
        ImportOptions opts = getDefaultOptions();
        pack.extractSubpackages(opts);

        JcrPackage p0 = packMgr.open(admin.getNode("/etc/packages/my_packages/subtest.zip"));
        assertEquals("has 0 dependency", 0, p0.getDefinition().getDependencies().length);

        // check for sub packages dependency
        String expected = new Dependency(p0.getDefinition().getId()).toString();
        JcrPackage p1 = packMgr.open(admin.getNode("/etc/packages/my_packages/sub_a.zip"));
        assertEquals("has 1 dependency", 1, p1.getDefinition().getDependencies().length);
        assertEquals("has dependency to parent package", expected, p1.getDefinition().getDependencies()[0].toString());
        JcrPackage p2 = packMgr.open(admin.getNode("/etc/packages/my_packages/sub_b.zip"));
        assertEquals("has 1 dependency", 1, p2.getDefinition().getDependencies().length);
        assertEquals("has dependency to parent package", expected, p2.getDefinition().getDependencies()[0].toString());
    }

}
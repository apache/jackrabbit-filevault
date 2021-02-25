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
import java.util.List;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.apache.jackrabbit.vault.packaging.Dependency;
import org.apache.jackrabbit.vault.packaging.NoSuchPackageException;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.apache.jackrabbit.vault.packaging.PackageExistsException;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.registry.DependencyReport;
import org.apache.jackrabbit.vault.packaging.registry.RegisteredPackage;
import org.apache.jackrabbit.vault.packaging.registry.impl.JcrPackageRegistry;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Test the Package registry interface
 */
public class PackageRegistryIT extends IntegrationTestBase {

    private JcrPackageRegistry registry;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();

        registry = new JcrPackageRegistry(admin);
    }

    /**
     * checks is a non existent package is really not there
     */
    @Test
    public void testOpenNonExistentPackage() throws IOException, PackageException {
        assertFalse("package must not exist", registry.contains(TMP_PACKAGE_ID));
        assertNull("package must not exist", registry.open(TMP_PACKAGE_ID));
    }

    /**
     * registers a package via stream
     */
    @Test
    public void testRegisterStream() throws IOException, PackageException {
        PackageId id = registry.register(getStream("/test-packages/tmp.zip"), false);
        assertEquals("package id", TMP_PACKAGE_ID, id);

        try (RegisteredPackage pkg = registry.open(id)) {
            assertEquals("package id of registered is correct", TMP_PACKAGE_ID, pkg.getId());
            assertFalse("Package is not installed", pkg.isInstalled());
        }
    }

    /**
     * registers a package twice via stream (replace = false)
     */
    @Test
    public void testRegisterStreamTwiceFails() throws IOException, PackageException {
        PackageId id = registry.register(getStream("/test-packages/tmp.zip"), false);
        assertEquals("package id", TMP_PACKAGE_ID, id);

        try {
            registry.register(getStream("/test-packages/tmp.zip"), false);
            fail("registering the package twice should fail");
        } catch (PackageExistsException e) {
            // expected
            assertEquals("colliding pid must be correct", id, e.getId());
        }
    }

    /**
     * registers a package twice via stream (replace = true)
     */
    @Test
    public void testRegisterStreamTwiceSucceeds() throws IOException, PackageException {
        PackageId id = registry.register(getStream("/test-packages/tmp.zip"), false);
        assertEquals("package id", TMP_PACKAGE_ID, id);
        registry.register(getStream("/test-packages/tmp.zip"), true);
    }

    /**
     * registers a package twice via a file
     */
    @Test
    public void testRegisterFileTwiceFails() throws IOException, PackageException {
        File file = getTempFile("/test-packages/tmp.zip");
        PackageId id = registry.register(file, false);
        assertEquals("package id", TMP_PACKAGE_ID, id);
        assertTrue("file should still exist", file.exists());

        try (RegisteredPackage pkg = registry.open(id)) {
            assertEquals("package id of registered is correct", TMP_PACKAGE_ID, pkg.getId());
            assertFalse("Package is not installed", pkg.isInstalled());
        }

        try {
            registry.register(file, false);
            fail("registering the package twice should fail");
        } catch (PackageExistsException e) {
            // expected
            assertEquals("colliding pid must be correct", id, e.getId());
        } finally {
            file.delete();
        }
    }

    /**
     * registers a package twice via a temp file file
     */
    @Test
    public void testRegisterTempFileTwiceFails() throws IOException, PackageException {
        File file = getTempFile("/test-packages/tmp.zip");
        PackageId id = registry.register(file, false);
        assertEquals("package id", TMP_PACKAGE_ID, id);

        try (RegisteredPackage pkg = registry.open(id)) {
            assertEquals("package id of registered is correct", TMP_PACKAGE_ID, pkg.getId());
            assertFalse("Package is not installed", pkg.isInstalled());
        }

        file = getTempFile("/test-packages/tmp.zip");
        try {
            registry.register(file, false);
            fail("registering the package twice should fail");
        } catch (PackageExistsException e) {
            // expected
            assertEquals("colliding pid must be correct", id, e.getId());
        }
    }

    /**
     * registers a package twice via file (replace = true)
     */
    @Test
    public void testRegisterFileTwiceSucceeds() throws IOException, PackageException {
        File file = getTempFile("/test-packages/tmp.zip");
        PackageId id = registry.register(file, false);
        assertEquals("package id", TMP_PACKAGE_ID, id);
        assertTrue("file should still exist", file.exists());
        registry.register(file, true);
        file.delete();
    }

    /**
     * test if package removal works
     */
    @Test
    public void testRemovePackage() throws IOException, PackageException {
        PackageId id = registry.register(getStream("/test-packages/tmp.zip"), false);
        assertEquals("package id", TMP_PACKAGE_ID, id);

        registry.remove(id);
        assertFalse("package must not exist", registry.contains(TMP_PACKAGE_ID));
        assertNull("package must not exist", registry.open(TMP_PACKAGE_ID));
    }

    /**
     * test packages set
     */
    @Test
    public void testPackages() throws IOException, PackageException {
        assertTrue("initially the packages set is empty", registry.packages().isEmpty());
        registry.register(getStream("/test-packages/tmp.zip"), false);
        assertEquals("packages contains 1 element", 1, registry.packages().size());
        assertTrue("contains new package", registry.packages().contains(TMP_PACKAGE_ID));
    }

    /**
     * test packages set with multiple roots
     */
    @Test
    public void testPackagesMultiRoot() throws IOException, PackageException, RepositoryException {
        assertTrue("initially the packages set is empty", registry.packages().isEmpty());
        registry.register(getStream(TEST_PACKAGE_A_10), false);
        registry.register(getStream(TEST_PACKAGE_B_10), false);
        assertEquals("packages contains 2 elements", 2, registry.packages().size());
        JcrPackageRegistry multiReg = new JcrPackageRegistry(admin, "/var/packages" , "/etc/packages");
        assertEquals("packages contains 2 elements", 2, multiReg.packages().size());

        // install 3rd package in /var
        registry.register(getStream(TEST_PACKAGE_C_10), false);
        assertEquals("packages contains 3 elements", 3, multiReg.packages().size());

        assertTrue("contains new packages", multiReg.packages().contains(TEST_PACKAGE_A_10_ID));
        assertTrue("contains new packages", multiReg.packages().contains(TEST_PACKAGE_B_10_ID));
        assertTrue("contains new packages", multiReg.packages().contains(TEST_PACKAGE_C_10_ID));
    }

    /**
     * test if remove non existing should fail
     */
    @Test
    public void testRemoveNonExistingPackage() throws IOException, PackageException {
        try {
            registry.remove(TMP_PACKAGE_ID);
            fail("remove non existing should fail");
        } catch (NoSuchPackageException e) {
            assertEquals("exception should contain correct package id", TMP_PACKAGE_ID, e.getId());
        }
    }

    /**
     * test if analyze dependencies fails for non existing package
     */
    @Test
    public void testAnalyzeDependenciesFailsForNonExisting() throws IOException {
        try {
            registry.analyzeDependencies(TMP_PACKAGE_ID, false);
            fail("usage report should fail for non existing package.");
        } catch (NoSuchPackageException e) {
            assertEquals("exception should contain correct package id", TMP_PACKAGE_ID, e.getId());
        }
    }

    /**
     * test if analyze dependencies is correct for non installed packages
     */
    @Test
    public void testAnalyzeDependencies() throws IOException, PackageException {
        // a depends on b and c
        PackageId idA = registry.register(getStream(TEST_PACKAGE_A_10), false);

        DependencyReport report = registry.analyzeDependencies(idA, false);
        assertEquals("resolved dependencies", "", PackageId.toString(report.getResolvedDependencies()));
        assertEquals("unresolved dependencies", "my_packages:test_b,my_packages:test_c:[1.0,2.0)", Dependency.toString(report.getUnresolvedDependencies()));

        // b depends on c
        registry.register(getStream(TEST_PACKAGE_B_10), false);
        report = registry.analyzeDependencies(idA, false);
        assertEquals("resolved dependencies", "my_packages:test_b:1.0", PackageId.toString(report.getResolvedDependencies()));
        assertEquals("unresolved dependencies", "my_packages:test_c:[1.0,2.0)", Dependency.toString(report.getUnresolvedDependencies()));

        registry.register(getStream(TEST_PACKAGE_C_10), false);
        report = registry.analyzeDependencies(idA, false);
        assertEquals("resolved dependencies", "my_packages:test_b:1.0,my_packages:test_c:1.0", PackageId.toString(report.getResolvedDependencies()));
        assertEquals("unresolved dependencies", "", Dependency.toString(report.getUnresolvedDependencies()));

    }

    /**
     * test if analyze dependencies is correct for non installed packages
     */
    @Test
    public void testAnalyzeInstalledDependencies() throws IOException, PackageException, RepositoryException {
        // a depends on b and c
        PackageId idA = registry.register(getStream(TEST_PACKAGE_A_10), false);

        DependencyReport report = registry.analyzeDependencies(idA, true);
        assertEquals("resolved dependencies", "", PackageId.toString(report.getResolvedDependencies()));
        assertEquals("unresolved dependencies", "my_packages:test_b,my_packages:test_c:[1.0,2.0)", Dependency.toString(report.getUnresolvedDependencies()));

        // b depends on c
        PackageId idB = registry.register(getStream(TEST_PACKAGE_B_10), false);
        report = registry.analyzeDependencies(idA, true);
        assertEquals("resolved dependencies", "", PackageId.toString(report.getResolvedDependencies()));
        assertEquals("unresolved dependencies", "my_packages:test_b,my_packages:test_c:[1.0,2.0)", Dependency.toString(report.getUnresolvedDependencies()));

        // install B
        packMgr.open(idB).install(getDefaultOptions());

        report = registry.analyzeDependencies(idA, true);
        assertEquals("resolved dependencies", "my_packages:test_b:1.0", PackageId.toString(report.getResolvedDependencies()));
        assertEquals("unresolved dependencies", "my_packages:test_c:[1.0,2.0)", Dependency.toString(report.getUnresolvedDependencies()));
    }

    @Test
    public void testUsages() throws Exception {
        PackageId idB = registry.register(getStream(TEST_PACKAGE_B_10), false);
        PackageId idC = registry.register(getStream(TEST_PACKAGE_C_10), false);

        assertEquals("usage", "", PackageId.toString(registry.usage(idC)));

        packMgr.open(idB).install(getDefaultOptions());
        packMgr.open(idC).install(getDefaultOptions());

        assertEquals("usage", "my_packages:test_b:1.0", PackageId.toString(registry.usage(idC)));
    }

    @Test
    public void testAlternativeRoot() throws IOException, PackageException, RepositoryException {
        JcrPackageRegistry reg = new JcrPackageRegistry(admin, "/var/packages" , "/etc/packages");
        File file = getTempFile("/test-packages/tmp.zip");
        PackageId id = reg.register(file, false);
        assertEquals("package id", TMP_PACKAGE_ID, id);
        file.delete();

        assertNodeExists("/var/packages/my_packages/tmp.zip");
    }

    @Test
    public void testAlternativeRootBackwardCompat() throws IOException, PackageException, RepositoryException {
        // install with default registry
        PackageId idB = registry.register(getStream(TEST_PACKAGE_B_10), false);
        assertNodeExists("/etc/packages/my_packages/test_b-1.0.zip");

        JcrPackageRegistry reg = new JcrPackageRegistry(admin, "/var/packages", "/etc/packages");
        PackageId id = reg.register(getStream("/test-packages/tmp.zip"), false);

        assertNodeExists("/var/packages/my_packages/tmp.zip");

        assertTrue("alternative registry must find packages on old location", reg.contains(idB));
        assertFalse("old registry must not find packages at new location", registry.contains(id));
    }

    @Test
    public void testPackageRootNoCreate() throws RepositoryException {
        assertTrue("no nodes, no roots", registry.getPackageRoots().isEmpty());
    }

    @Test
    public void testPrimaryPackageRootNoCreate() throws RepositoryException {
        assertNull("no node, no root", registry.getPrimaryPackageRoot(false));
    }

    @Test
    public void testPrimaryPackageRootCreatesLegacy() throws RepositoryException {
        Node root = registry.getPrimaryPackageRoot(true);
        assertEquals("root has legacy path", "/etc/packages", root.getPath());
    }

    @Test
    public void testAlternativePackageRootCreatesOnlyOneNode() throws RepositoryException {
        JcrPackageRegistry reg = new JcrPackageRegistry(admin, "/var/packages", "/etc/packages");
        Node root = reg.getPrimaryPackageRoot(true);
        assertEquals("root has correct path", "/var/packages", root.getPath());
        List<Node> roots = reg.getPackageRoots();
        assertEquals("Has 1 package root", 1, roots.size());
    }

    @Test
    public void testAlternativePackageRootReportsBothNodes() throws RepositoryException {
        registry.getPrimaryPackageRoot(true); // create legacy path
        JcrPackageRegistry reg = new JcrPackageRegistry(admin, "/var/packages", "/etc/packages");
        reg.getPrimaryPackageRoot(true); // create new path
        List<Node> roots = reg.getPackageRoots();
        assertEquals("Has 2 package root", 2, roots.size());
        assertEquals("primary root has correct path", "/var/packages", roots.get(0).getPath());
        assertEquals("secondary root has legacy path", "/etc/packages", roots.get(1).getPath());
    }

}
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
package org.apache.jackrabbit.vault.packaging.integration;

import javax.jcr.Binary;
import javax.jcr.Node;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.jackrabbit.vault.packaging.Dependency;
import org.apache.jackrabbit.vault.packaging.JcrPackage;
import org.apache.jackrabbit.vault.packaging.JcrPackageDefinition;
import org.apache.jackrabbit.vault.packaging.NoSuchPackageException;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.apache.jackrabbit.vault.packaging.PackageExistsException;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.PackageProperties;
import org.apache.jackrabbit.vault.packaging.VaultPackageBinaryLocation;
import org.apache.jackrabbit.vault.packaging.impl.ZipVaultPackage;
import org.apache.jackrabbit.vault.packaging.registry.DependencyReport;
import org.apache.jackrabbit.vault.packaging.registry.RegisteredPackage;
import org.apache.jackrabbit.vault.packaging.registry.impl.JcrPackageRegistry;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
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

    @Test
    public void testUploadRegistersSubpackagesRecursively() throws Exception {
        PackageId outerId = new PackageId("my_packages", "outer", "1.0");
        PackageId middleId = new PackageId("my_packages", "middle", "1.0");
        byte[] leaf;
        try (InputStream input = getStream("/test-packages/tmp.zip")) {
            leaf = input.readAllBytes();
        }
        byte[] middle = createContainerPackage(middleId, leaf);
        byte[] outer = createContainerPackage(outerId, middle);

        for (boolean replace : new boolean[] {false, true}) {
            try (JcrPackage uploaded = registry.upload(new ByteArrayInputStream(outer), replace)) {
                assertEquals(outerId, uploaded.getDefinition().getId());
            }
            assertRegisteredPackage(outerId, outer);
            assertReferencedPackage(middleId, outerId, middle.length);
            assertReferencedPackage(TMP_PACKAGE_ID, middleId, leaf.length);
        }
        assertNodeMissing("/tmp");
    }

    @Test
    public void testUploadFilePreservesAndTraversesStoredSubpackages() throws Exception {
        PackageId outerId = new PackageId("my_packages", "outer", "1.0");
        PackageId middleId = new PackageId("my_packages", "middle", "1.0");
        byte[] leaf;
        try (InputStream input = getStream("/test-packages/tmp.zip")) {
            leaf = input.readAllBytes();
        }
        byte[] existingMiddle = createContainerPackage(middleId, leaf);
        String middleNodeId;
        try (JcrPackage existing = registry.upload(new ByteArrayInputStream(existingMiddle), false)) {
            middleNodeId = existing.getNode().getIdentifier();
            existing.getDefinition().set(JcrPackageDefinition.PN_DESCRIPTION, "retained", true);
        }
        registry.remove(TMP_PACKAGE_ID);
        byte[] outer = createContainerPackage(outerId, createContainerPackage(middleId, null));
        File file = tempFolder.newFile();
        Files.write(file.toPath(), outer);

        for (boolean replace : new boolean[] {false, true}) {
            try (ZipVaultPackage source = new ZipVaultPackage(file, false, true);
                    JcrPackage uploaded = registry.upload(source, replace)) {
                assertEquals(outerId, uploaded.getDefinition().getId());
            }
            assertRegisteredPackage(middleId, existingMiddle);
            assertReferencedPackage(TMP_PACKAGE_ID, middleId, leaf.length);
            try (JcrPackage existing = registry.openJcrPackage(middleId)) {
                assertEquals(middleNodeId, existing.getNode().getIdentifier());
                assertEquals("retained", existing.getDefinition().getDescription());
            }
            registry.remove(TMP_PACKAGE_ID);
        }
    }

    @Test
    public void testReferencedPackageCanBeInstalledDirectly() throws Exception {
        PackageId outerId = new PackageId("my_packages", "outer", "1.0");
        byte[] leaf;
        try (InputStream input = getStream("/test-packages/tmp.zip")) {
            leaf = input.readAllBytes();
        }
        registry.register(new ByteArrayInputStream(createContainerPackage(outerId, leaf)), false);
        try (JcrPackage subPackage = registry.openJcrPackage(TMP_PACKAGE_ID)) {
            subPackage.install(getDefaultOptions());
            assertNull(subPackage.getData());
            assertTrue(subPackage.isInstalled());
            assertNodeExists("/tmp");
            subPackage.uninstall(getDefaultOptions());
            assertNodeMissing("/tmp");
        }
    }

    @Test
    public void testReferencedPackageCanBeRewrapped() throws Exception {
        PackageId outerId = new PackageId("my_packages", "outer", "1.0");
        byte[] leaf;
        try (InputStream input = getStream("/test-packages/tmp.zip")) {
            leaf = input.readAllBytes();
        }
        registry.register(new ByteArrayInputStream(createContainerPackage(outerId, leaf)), false);
        try (JcrPackage subPackage = registry.openJcrPackage(TMP_PACKAGE_ID)) {
            assertNull(subPackage.getData());
            subPackage.getDefinition().set(JcrPackageDefinition.PN_DESCRIPTION, "rewrapped", true);
            packMgr.rewrap(subPackage, null);
        }
        try (JcrPackage subPackage = registry.openJcrPackage(TMP_PACKAGE_ID)) {
            assertNotNull(subPackage.getData());
            assertEquals(VaultPackageBinaryLocation.LOCAL_BINARY, subPackage.getArchiveLocationType());
            assertFalse(subPackage.getDefNode().hasProperty(JcrPackageDefinition.PN_CONTAINER));
            assertEquals("rewrapped", subPackage.getPackage().getProperties().getDescription());
        }
    }

    @Test
    public void testDanglingContainerReferenceFailsButCanBeRemoved() throws Exception {
        PackageId outerId = new PackageId("my_packages", "outer", "1.0");
        byte[] leaf;
        try (InputStream input = getStream("/test-packages/tmp.zip")) {
            leaf = input.readAllBytes();
        }
        registry.register(new ByteArrayInputStream(createContainerPackage(outerId, leaf)), false);
        registry.remove(outerId);
        try (JcrPackage subPackage = registry.openJcrPackage(TMP_PACKAGE_ID)) {
            assertThrows(RepositoryException.class, subPackage::getPackage);
        }
        registry.remove(TMP_PACKAGE_ID);
        assertFalse(registry.contains(TMP_PACKAGE_ID));
    }

    @Test
    public void testReplacingContainerCannotLoseReferencedEntries() throws Exception {
        PackageId outerId = new PackageId("my_packages", "outer", "1.0");
        byte[] leaf;
        try (InputStream input = getStream("/test-packages/tmp.zip")) {
            leaf = input.readAllBytes();
        }
        registry.register(new ByteArrayInputStream(createContainerPackage(outerId, leaf)), false);
        byte[] replacement = createContainerPackage(outerId, null);
        assertThrows(IOException.class, () -> registry.register(new ByteArrayInputStream(replacement), true));
        assertReferencedPackage(TMP_PACKAGE_ID, outerId, leaf.length);
    }

    @Test
    public void testCyclicContainerReferenceFails() throws Exception {
        PackageId outerId = new PackageId("my_packages", "outer", "1.0");
        byte[] leaf;
        try (InputStream input = getStream("/test-packages/tmp.zip")) {
            leaf = input.readAllBytes();
        }
        registry.register(new ByteArrayInputStream(createContainerPackage(outerId, leaf)), false);
        try (JcrPackage subPackage = registry.openJcrPackage(TMP_PACKAGE_ID)) {
            Node node = subPackage.getNode();
            node.addMixin("mix:referenceable");
            subPackage
                    .getDefNode()
                    .setProperty(
                            JcrPackageDefinition.PN_CONTAINER,
                            admin.getValueFactory().createValue(node, true));
            admin.save();
            IOException exception = assertThrows(IOException.class, subPackage::getPackage);
            assertTrue(exception.getMessage().contains("Cyclic package container reference"));
        }
    }

    private byte[] createContainerPackage(PackageId id, byte[] subPackage) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("META-INF/vault/properties.xml"));
            Properties properties = new Properties();
            properties.setProperty(PackageProperties.NAME_GROUP, id.getGroup());
            properties.setProperty(PackageProperties.NAME_NAME, id.getName());
            properties.setProperty(PackageProperties.NAME_VERSION, id.getVersionString());
            properties.storeToXML(zip, null);
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("META-INF/vault/filter.xml"));
            zip.write(("<workspaceFilter version=\"1.0\"><filter root=\"/etc/packages\"/></workspaceFilter>")
                    .getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("jcr_root/"));
            zip.closeEntry();
            if (subPackage != null) {
                zip.putNextEntry(new ZipEntry("jcr_root/etc/packages/group/nested.jar"));
                zip.write(subPackage);
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }

    private void assertRegisteredPackage(PackageId id, byte[] expectedData) throws Exception {
        try (JcrPackage registered = registry.openJcrPackage(id)) {
            assertNotNull(registered);
            assertEquals(id, registered.getDefinition().getId());
            assertEquals(id, registered.getPackage().getId());
            assertFalse(registered.isInstalled());
            Binary binary = registered.getData().getBinary();
            try (InputStream input = binary.getStream()) {
                assertArrayEquals(expectedData, input.readAllBytes());
            } finally {
                binary.dispose();
            }
        }
    }

    private void assertReferencedPackage(PackageId id, PackageId containerId, long size) throws Exception {
        try (JcrPackage registered = registry.openJcrPackage(id);
                JcrPackage container = registry.openJcrPackage(containerId)) {
            assertNotNull(registered);
            assertNull(registered.getData());
            assertEquals(VaultPackageBinaryLocation.CONTAINER_ENTRY, registered.getArchiveLocationType());
            assertEquals(size, registered.getSize());
            assertFalse(registered.isEmpty());
            assertFalse(registered.isInstalled());
            Node definition = registered.getDefNode();
            assertEquals(
                    PropertyType.WEAKREFERENCE,
                    definition.getProperty(JcrPackageDefinition.PN_CONTAINER).getType());
            assertTrue(definition
                    .getProperty(JcrPackageDefinition.PN_CONTAINER)
                    .getNode()
                    .isSame(container.getNode()));
            assertEquals(
                    "/jcr_root/etc/packages/group/nested.jar",
                    definition
                            .getProperty(JcrPackageDefinition.PN_CONTAINER_ENTRY)
                            .getString());
            assertEquals(id, registered.getPackage().getId());
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
        File file = getFile("/test-packages/tmp.zip");
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
        }
    }

    /**
     * registers a package twice via a temp file file
     */
    @Test
    public void testRegisterTempFileTwiceFails() throws IOException, PackageException {
        File file = getFile("/test-packages/tmp.zip");
        PackageId id = registry.register(file, false);
        assertEquals("package id", TMP_PACKAGE_ID, id);

        try (RegisteredPackage pkg = registry.open(id)) {
            assertEquals("package id of registered is correct", TMP_PACKAGE_ID, pkg.getId());
            assertFalse("Package is not installed", pkg.isInstalled());
        }

        file = getFile("/test-packages/tmp.zip");
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
        File file = getFile("/test-packages/tmp.zip");
        PackageId id = registry.register(file, false);
        assertEquals("package id", TMP_PACKAGE_ID, id);
        assertTrue("file should still exist", file.exists());
        registry.register(file, true);
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

    @Test
    public void testRemovePackageCallsClose() throws IOException, PackageException, RepositoryException {
        PackageId id = registry.register(getStream("/test-packages/tmp.zip"), false);
        assertEquals("package id", TMP_PACKAGE_ID, id);

        JcrPackageRegistry testReg = Mockito.spy(registry);
        JcrPackage pack = Mockito.spy(registry.openJcrPackage(TMP_PACKAGE_ID));
        Mockito.doReturn(pack).when(testReg).openJcrPackage(TMP_PACKAGE_ID);
        testReg.remove(id);

        Mockito.verify(pack, Mockito.times(1)).close();
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
        JcrPackageRegistry multiReg = new JcrPackageRegistry(admin, "/var/packages", "/etc/packages");
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
        assertEquals(
                "unresolved dependencies",
                "my_packages:test_b,my_packages:test_c:[1.0,2.0)",
                Dependency.toString(report.getUnresolvedDependencies()));

        // b depends on c
        registry.register(getStream(TEST_PACKAGE_B_10), false);
        report = registry.analyzeDependencies(idA, false);
        assertEquals(
                "resolved dependencies",
                "my_packages:test_b:1.0",
                PackageId.toString(report.getResolvedDependencies()));
        assertEquals(
                "unresolved dependencies",
                "my_packages:test_c:[1.0,2.0)",
                Dependency.toString(report.getUnresolvedDependencies()));

        registry.register(getStream(TEST_PACKAGE_C_10), false);
        report = registry.analyzeDependencies(idA, false);
        assertEquals(
                "resolved dependencies",
                "my_packages:test_b:1.0,my_packages:test_c:1.0",
                PackageId.toString(report.getResolvedDependencies()));
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
        assertEquals(
                "unresolved dependencies",
                "my_packages:test_b,my_packages:test_c:[1.0,2.0)",
                Dependency.toString(report.getUnresolvedDependencies()));

        // b depends on c
        PackageId idB = registry.register(getStream(TEST_PACKAGE_B_10), false);
        report = registry.analyzeDependencies(idA, true);
        assertEquals("resolved dependencies", "", PackageId.toString(report.getResolvedDependencies()));
        assertEquals(
                "unresolved dependencies",
                "my_packages:test_b,my_packages:test_c:[1.0,2.0)",
                Dependency.toString(report.getUnresolvedDependencies()));

        // install B
        packMgr.open(idB).install(getDefaultOptions());

        report = registry.analyzeDependencies(idA, true);
        assertEquals(
                "resolved dependencies",
                "my_packages:test_b:1.0",
                PackageId.toString(report.getResolvedDependencies()));
        assertEquals(
                "unresolved dependencies",
                "my_packages:test_c:[1.0,2.0)",
                Dependency.toString(report.getUnresolvedDependencies()));
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
        JcrPackageRegistry reg = new JcrPackageRegistry(admin, "/var/packages", "/etc/packages");
        File file = getFile("/test-packages/tmp.zip");
        PackageId id = reg.register(file, false);
        assertEquals("package id", TMP_PACKAGE_ID, id);

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
        assertEquals(
                "primary root has correct path", "/var/packages", roots.get(0).getPath());
        assertEquals(
                "secondary root has legacy path", "/etc/packages", roots.get(1).getPath());
    }
}

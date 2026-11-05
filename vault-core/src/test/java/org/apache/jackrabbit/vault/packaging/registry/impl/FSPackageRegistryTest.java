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
package org.apache.jackrabbit.vault.packaging.registry.impl;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.jackrabbit.vault.packaging.NoSuchPackageException;
import org.apache.jackrabbit.vault.packaging.PackageExistsException;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.registry.impl.FSPackageRegistry.Config;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestName;
import org.mockito.Mockito;
import org.osgi.framework.BundleContext;
import org.osgi.util.converter.Converter;
import org.osgi.util.converter.Converters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class FSPackageRegistryTest {

    @Rule
    public TemporaryFolder tmpFolder = new TemporaryFolder();

    @Rule
    public TestName testName = new TestName();

    private static final PackageId TEST_PACKAGE_ID = new PackageId("test", "test-package-with-etc", "1.0");

    // empty workspace filter
    private static final String FILTER_XML = "<workspaceFilter version=\"1.0\"/>\n";

    private Path resolvePathSafely(Path base, String other) {
        try {
            return base.resolve(other);
        } catch (InvalidPathException e) {
            // Skip test if Windows file system rejects characters (e.g., ':') in paths
            Assume.assumeTrue("Skipping test due to platform path limitations: " + e.getMessage(), false);
            return Paths.get("WE NEVER GET HERE");
        }
    }

    private void copyResourceStreamToFile(Path targetFile, String name) throws IOException {
        try (InputStream in = getClass().getResourceAsStream(name)) {
            Files.copy(in, targetFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Path getTempRegistryHomeWithPackage(String packageName, String packageMetadataName) throws IOException {
        Path tmpDir = tmpFolder.newFolder().toPath();
        copyResourceStreamToFile(tmpDir.resolve("package1.zip"), packageName);
        copyResourceStreamToFile(tmpDir.resolve("package1.zip.xml"), packageMetadataName);
        return tmpDir;
    }

    @Test
    public void testFailHomeDirIsFile() throws IOException {
        Path tmpDir = tmpFolder.newFolder().toPath();
        Path file = Paths.get(tmpDir.toFile().getPath(), "file");
        Files.write(file, "EMPTY".getBytes());
        try {
            createRegistryWithDefaultConstructor(file);
            fail("registry creation should fail when homeDir is a file, not a directory");
        } catch (IOException expected) {
            // ok
        }
    }

    @Test
    public void testHomeDirIsSymlink() throws IOException {
        Path tmpDir = tmpFolder.newFolder().toPath();
        Path path = Paths.get(tmpDir.toFile().getPath(), "path");
        Files.createDirectories(path);
        Path link = Paths.get(tmpDir.toFile().getPath(), "link");
        try {
            Files.createSymbolicLink(link, path);
            createRegistryWithDefaultConstructor(link);
        } catch (FileSystemException tolerated) {
            // symlink creation apparently unsupported
        }
    }

    @Test
    public void testRegisterAndRemove() throws IOException, PackageExistsException, NoSuchPackageException {
        FSPackageRegistry registry =
                createRegistryWithDefaultConstructor(tmpFolder.newFolder().toPath());
        try (InputStream in = getClass().getResourceAsStream("test-package.zip")) {
            registry.register(in, false);
        }
        assertTrue(registry.contains(TEST_PACKAGE_ID));
        registry.remove(TEST_PACKAGE_ID);
        assertFalse(registry.contains(TEST_PACKAGE_ID));
    }

    @Test
    public void testCacheInitializedAfterOSGiActivate() throws IOException {
        new FSPackageRegistry();
        Path registryHomeDir = getTempRegistryHomeWithPackage("test-package.zip", "test-package.xml");
        FSPackageRegistry registry = createRegistryWithDefaultConstructor(registryHomeDir);
        assertTrue(registry.contains(TEST_PACKAGE_ID));
        assertEquals(Collections.singleton(TEST_PACKAGE_ID), registry.packages());
    }

    @Test
    public void testCacheInitializedAfterOSGiActivateLocaleTr() throws IOException {
        Locale defaultLocale = Locale.getDefault();
        try {
            // see JCRVLT-788; this verifies that switching to a locale where
            // uppercase("i") != 'I' does not cause failures
            Locale.setDefault(new Locale("tr", "TR"));
            testCacheInitializedAfterOSGiActivate();
        } finally {
            Locale.setDefault(defaultLocale);
        }
    }

    private FSPackageRegistry createRegistryWithDefaultConstructor(Path homePath) throws IOException {
        FSPackageRegistry registry = new FSPackageRegistry();
        BundleContext context = Mockito.mock(BundleContext.class);
        Mockito.when(context.getProperty(FSPackageRegistry.REPOSITORY_HOME))
                .thenReturn(tmpFolder.getRoot().toString());
        Converter converter = Converters.standardConverter();
        Map<String, Object> map = new HashMap<>();
        map.put("homePath", homePath.toString());
        map.put("authIdsForHookExecution", new String[0]);
        map.put("authIdsForRootInstallation", new String[0]);
        Config config = converter.convert(map).to(Config.class);
        registry.activate(context, config);
        return registry;
    }

    private File createPackageZip(String group) throws IOException {
        File file = tmpFolder.newFile("traversal.zip");
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(file))) {
            out.putNextEntry(new ZipEntry("jcr_root/"));
            out.closeEntry();
            out.putNextEntry(new ZipEntry("META-INF/vault/filter.xml"));
            out.write(FILTER_XML.getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
            Properties props = new Properties();
            props.setProperty("group", group);
            props.setProperty("name", "evil");
            props.setProperty("version", "1.0");
            ByteArrayOutputStream propsXml = new ByteArrayOutputStream();
            props.storeToXML(propsXml, null, "UTF-8");
            out.putNextEntry(new ZipEntry("META-INF/vault/properties.xml"));
            out.write(propsXml.toByteArray());
            out.closeEntry();
        }
        return file;
    }

    @Test
    public void testCacheRefusesTraversingIdForGroup() throws IOException {
        FSInstallStateCache cache =
                new FSInstallStateCache(tmpFolder.newFolder().toPath());

        List<String> evilGroups = List.of("foo\\bar/qux", "\\", ".", "..", "foo/./bar", "foo/../bar");

        for (String evilGroup : evilGroups) {
            try {
                cache.getPackageFile(new PackageId(evilGroup, "evil", "1.0"));
                fail("Resolving a package id with traversing group segments should be refused for group: " + evilGroup);
            } catch (IllegalArgumentException expected) {
                // ok
            }
        }
    }

    @Test
    public void testCacheRefusesTraversingIdForName() throws IOException {
        FSInstallStateCache cache =
                new FSInstallStateCache(tmpFolder.newFolder().toPath());

        List<String> evilNames = List.of("foo\\bar", "foo/bar", ".", "..");

        for (String evilName : evilNames) {
            try {
                cache.getPackageFile(new PackageId("group", evilName, "1.0"));
                fail("Resolving a package id with traversing name segments should be refused for name: " + evilName);
            } catch (IllegalArgumentException expected) {
                // ok
            }
        }
    }

    @Test
    public void testCacheRefusesTraversingIdForVersion() throws IOException {
        FSInstallStateCache cache =
                new FSInstallStateCache(tmpFolder.newFolder().toPath());

        List<String> evilVersions = List.of("foo\\bar", "foo/bar"); // , ".", ".."); ???

        for (String evilVersion : evilVersions) {
            try {
                cache.getPackageFile(new PackageId("group", "name", evilVersion));
                fail("Resolving a package id with traversing version should be refused for version: " + evilVersion);
            } catch (IllegalArgumentException expected) {
                // ok
            }
        }
    }

    @Test
    public void testCacheResolvesValidIdInsideHome() throws IOException {
        Path home = tmpFolder.newFolder().toPath();
        FSInstallStateCache cache = new FSInstallStateCache(home);
        Path packageFile = cache.getPackageFile(new PackageId("group", "name", "1.0"));
        assertTrue(packageFile.startsWith(home.normalize()));
    }

    @Test
    public void testRegisterFileWithTraversingIdIsRejected() throws IOException, PackageExistsException {
        File home = tmpFolder.newFolder();
        FSPackageRegistry registry = new FSPackageRegistry(home);
        File pkg = createPackageZip("../../../../outside");
        try {
            registry.register(pkg, false);
            fail("registering a package whose properties.xml declares a traversing group should be refused");
        } catch (IllegalArgumentException expected) {
            // ok, same behavior as upload(InputStream, boolean)
        }
    }

    private File makePackage(Path path, String group, String name) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("group", group);
        properties.setProperty("name", name);
        properties.setProperty("version", "1.0");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(path))) {
            out.putNextEntry(new ZipEntry("jcr_root/"));
            out.closeEntry();
            out.putNextEntry(new ZipEntry("META-INF/vault/filter.xml"));
            out.write(FILTER_XML.getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
            out.putNextEntry(new ZipEntry("META-INF/vault/properties.xml"));
            properties.storeToXML(out, null, "UTF-8");
            out.closeEntry();
        }
        return path.toFile();
    }

    /**
     * Helper method to test package registration behavior and path traversal protection.
     * It creates a temporary registry home, generates a package with the specified group and name,
     * attempts to register it, and verifies whether the resulting installation path is valid,
     * stays within the registry home directory, and gets correctly created on the file system.
     *
     * @param label          a unique label used to name the test directory and zip file
     * @param group          the package group name to include in the package properties
     * @param name           the package name to include in the package properties
     * @param expectedValid  {@code true} if the generated {@link PackageId} is expected to be valid;
     *                       {@code false} otherwise
     * @param expectedInHome {@code true} if the planned installation path is expected to safely reside
     *                       within the registry home directory; {@code false} if it attempts an escape/traversal
     * @throws Exception     if any I/O, registration, or assertion error occurs during the test execution
     */
    private void runCase(String label, String group, String name, boolean expectedValid, boolean expectedInHome)
            throws Exception {
        Path root = tmpFolder.newFolder(testName.getMethodName()).toPath();
        Path home = resolvePathSafely(root.resolve(label), "registry");
        PackageId id = new PackageId(group, name, "1.0");
        assertEquals(expectedValid, id.isValid());
        File zip = makePackage(root.resolve(label + ".zip"), group, name);
        try {
            FSPackageRegistry registry = new FSPackageRegistry(home.toFile());
            registry.register(zip, false);
        } catch (Exception exception) {
            // expected for traversal/attacks
        }
        if (id.isValid()) {
            Path planned = resolvePathSafely(home, AbstractPackageRegistry.getRelativeInstallationPath(id) + ".zip");
            Path meta = resolvePathSafely(home, AbstractPackageRegistry.getRelativeInstallationPath(id) + ".xml");
            assertEquals(
                    expectedInHome,
                    planned.toAbsolutePath()
                            .normalize()
                            .startsWith(home.toAbsolutePath().normalize()));
            assertEquals(expectedInHome, Files.isRegularFile(planned));
            assertEquals(expectedInHome, Files.isRegularFile(meta));
        } else {
            try {
                String path = AbstractPackageRegistry.getRelativeInstallationPath(id);
                fail("should not get here, got path '" + path + "' for package id '" + id + "')");
            } catch (IllegalArgumentException exception) {
                // expected, all good
            }
        }
    }

    /**
     * Helper method to test staged package registration and path traversal protection under
     * multistep scenarios (registering a valid seed package followed by evaluating an attack package).
     * It verifies that the seed package ID is valid and registers successfully, checks that the
     * attack package ID is invalid, and ensures that attempting to resolve its installation path
     * throws an {@link IllegalArgumentException}.
     *
     * @throws Exception if any I/O, registration, or assertion error occurs during the test execution
     */
    private void runStagedCase() throws Exception {
        Path root = tmpFolder.newFolder(testName.getMethodName()).toPath();
        String label = "expanded-uri-staged";
        Path home = resolvePathSafely(root.resolve(label), "registry");
        FSPackageRegistry registry = new FSPackageRegistry(home.toFile());
        PackageId seedId = new PackageId("g/{", "seed", "1.0");
        assertTrue(seedId.isValid());
        try {
            registry.register(makePackage(root.resolve("seed.zip"), "g/{", "seed"), false);
        } catch (Exception exception) {
            throw exception;
        }
        PackageId attackId = new PackageId("g", "{/../../../escaped:}N", "1.0");
        assertFalse(attackId.isValid());
        try {
            String path = AbstractPackageRegistry.getRelativeInstallationPath(attackId);
            fail("should not get here, got path '" + path + "' for package id '" + attackId + "')");
        } catch (IllegalArgumentException exception) {
            // expected, all good
        }
    }

    /**
     * Helper method to test staged package registration security using input streams for the seed
     * package and path traversal protection for the attack package. It initializes a registry with a
     * seed package stream, constructs an attack package ID using the specified attack name, verifies
     * that the package ID is invalid, and ensures that attempting to resolve its installation path
     * throws an {@link IllegalArgumentException}.
     *
     * @param label      a unique label used to name the test directories and files
     * @param attackName the package name for the attack package to evaluate
     * @throws Exception if any I/O, registration, or setup error occurs during the test execution
     */
    private void runStagedStreamCase(String label, String attackName) throws Exception {
        Path root = tmpFolder.newFolder(testName.getMethodName()).toPath();
        Path home = resolvePathSafely(root.resolve(label), "registry");
        FSPackageRegistry registry = new FSPackageRegistry(home.toFile());
        Path seedZip =
                makePackage(root.resolve(label + "-seed.zip"), "g/{", "seed").toPath();
        try (InputStream in = Files.newInputStream(seedZip)) {
            registry.register(in, false);
        }
        PackageId attackId = new PackageId("g", attackName, "1.0");
        assertFalse(attackId.isValid());
        try {
            String path = AbstractPackageRegistry.getRelativeInstallationPath(attackId);
            fail("should not get here, got path '" + path + "' for package id '" + attackId + "')");
        } catch (IllegalArgumentException exception) {
            // expected, all good
        }
    }

    /**
     * Helper method to test package registration behavior against plain file traversal attempts
     * in a staged scenario. It initializes a registry, registers a seed package, constructs an
     * attack package ID containing plain path traversal components, verifies that the package ID
     * is invalid, and ensures that attempting to resolve its installation path throws an
     * {@link IllegalArgumentException}.
     *
     * @throws Exception if any I/O, registration, or setup error occurs during the test execution
     */
    private void runStagedPlainFileCase() throws Exception {
        Path root = tmpFolder.newFolder(testName.getMethodName()).toPath();
        String label = "plain-file-staged";
        Path home = resolvePathSafely(root.resolve(label), "registry");
        FSPackageRegistry registry = new FSPackageRegistry(home.toFile());
        registry.register(makePackage(root.resolve(label + "-seed.zip"), "g", "seed"), false);
        PackageId attackId = new PackageId("g", "../../outside", "1.0");
        assertFalse(attackId.isValid());
        try {
            String path = AbstractPackageRegistry.getRelativeInstallationPath(attackId);
            fail("should not get here, got path '" + path + "' for package id '" + attackId + "')");
        } catch (IllegalArgumentException exception) {
            // expected, all good
        }
    }

    /**
     * Helper method to test package registration security behavior when an attack package
     * with path traversal or invalid characters is evaluated. It initializes a registry
     * with a seed package, constructs an attack package ID with malicious path components,
     * verifies that the package ID is invalid, and ensures that attempting to resolve
     * its installation path throws an {@link IllegalArgumentException}.
     *
     * @throws Exception if any I/O, registration, or setup error occurs during the test execution
     */
    private void runExistingTargetCase() throws Exception {
        Path root = tmpFolder.newFolder(testName.getMethodName()).toPath();
        String label = "expanded-uri-existing-zip";
        Path home = resolvePathSafely(root.resolve(label), "registry");
        FSPackageRegistry registry = new FSPackageRegistry(home.toFile());
        try (InputStream seed = Files.newInputStream(
                makePackage(root.resolve(label + "-seed.zip"), "g/{", "seed").toPath())) {
            registry.register(seed, false);
        }
        PackageId attackId = new PackageId("g", "{/../../../escaped:}N", "1.0");
        assertFalse(attackId.isValid());
        try {
            String path = AbstractPackageRegistry.getRelativeInstallationPath(attackId);
            fail("should not get here, got path '" + path + "' for package id '" + attackId + "')");
        } catch (IllegalArgumentException exception) {
            // expected, all good
        }
    }

    @Test
    public void testEscapeFolderOrdinary() throws Exception {
        runCase("ordinary", "g", "normal", true, true);
    }

    @Test
    public void testEscapeFolderPlainTraversal() throws Exception {
        runCase("plain-traversal", "g", "../../outside", false, false);
    }

    @Test
    public void testEscapeFolderExpandedUri() throws Exception {
        runCase("expanded-uri", "g", "{urn:/../../../escaped/marker}N", false, false);
    }

    @Test
    public void testEscapeFolderExpandedUriUnseeded() throws Exception {
        runCase("expanded-uri-unseeded", "g", "{/../../../escaped:}N", false, false);
    }

    @Test
    public void testEscapeFolderStagedCase() throws Exception {
        runStagedCase();
    }

    @Test
    public void testEscapeFolderStagedStreamCase() throws Exception {
        runStagedStreamCase("expanded-uri-stream-staged", "{/../../../escaped:}N");
    }

    @Test
    public void testEscapeFolderStagedPlainStreamStaged() throws Exception {
        runStagedStreamCase("plain-stream-staged", "../../outside");
    }

    @Test
    public void testEscapeFolderStagedPlainFileCase() throws Exception {
        runStagedPlainFileCase();
    }

    @Test
    public void testEscapeFolderExistingTargetCase() throws Exception {
        runExistingTargetCase();
    }

    /**
     * A valid package must resolve when the registry home is the relative current directory.
     */
    @Test
    public void testGetPackageFileWithRelativeCurrentDirectoryHome() throws IOException {
        PackageId id = new PackageId("test", "normal", "1.0");
        assertTrue(id.isValid());
        FSInstallStateCache cache = new FSInstallStateCache(Paths.get("."));

        Path packageFile = cache.getPackageFile(id);

        assertEquals(
                Paths.get("test", "normal-1.0.zip").toAbsolutePath().normalize(),
                packageFile.toAbsolutePath().normalize());
    }

    /**
     * The absolute spelling of the same registry home resolves the same valid package.
     */
    @Test
    public void testGetPackageFileWithAbsoluteCurrentDirectoryHome() throws IOException {
        PackageId id = new PackageId("test", "normal", "1.0");
        assertTrue(id.isValid());
        FSInstallStateCache cache =
                new FSInstallStateCache(Paths.get(".").toAbsolutePath().normalize());

        Path packageFile = cache.getPackageFile(id);

        assertEquals(
                Paths.get("test", "normal-1.0.zip").toAbsolutePath().normalize(),
                packageFile.toAbsolutePath().normalize());
    }
}

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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.apache.jackrabbit.vault.packaging.NoSuchPackageException;
import org.apache.jackrabbit.vault.packaging.PackageExistsException;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.registry.impl.FSPackageRegistry.Config;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
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

    private static final PackageId TEST_PACKAGE_ID = new PackageId("test", "test-package-with-etc", "1.0");

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
}

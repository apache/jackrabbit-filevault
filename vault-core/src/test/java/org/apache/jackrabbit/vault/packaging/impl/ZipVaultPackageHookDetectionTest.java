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
package org.apache.jackrabbit.vault.packaging.impl;

import javax.jcr.Session;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.jackrabbit.vault.fs.io.ImportOptions;
import org.apache.jackrabbit.vault.fs.io.ZipArchive;
import org.apache.jackrabbit.vault.packaging.InstallHookProcessor;
import org.apache.jackrabbit.vault.packaging.InstallHookProcessorFactory;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.apache.jackrabbit.vault.packaging.registry.impl.AbstractPackageRegistry.SecurityConfig;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests that install hook presence is detected by pure archive listing, without
 * loading or instantiating any hook class. The detection feeds the
 * admin allowance check that must run <em>before</em> hook registration.
 */
public class ZipVaultPackageHookDetectionTest {

    private static final String FILTER_XML =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<workspaceFilter version=\"1.0\"/>\n";

    @Rule
    public TemporaryFolder tmpFolder = new TemporaryFolder();

    private File createPackage(boolean withHookJar, boolean withHookProperty) throws IOException {
        File file = tmpFolder.newFile("package.zip");
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(file))) {
            out.putNextEntry(new ZipEntry("jcr_root/"));
            out.closeEntry();
            out.putNextEntry(new ZipEntry("META-INF/vault/filter.xml"));
            out.write(FILTER_XML.getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
            Properties props = new Properties();
            props.setProperty("group", "test");
            props.setProperty("name", "test-package");
            props.setProperty("version", "1.0");
            if (withHookProperty) {
                props.setProperty("installhook.test.class", "com.example.NonExistingHook");
            }
            ByteArrayOutputStream propsXml = new ByteArrayOutputStream();
            props.storeToXML(propsXml, null, "UTF-8");
            out.putNextEntry(new ZipEntry("META-INF/vault/properties.xml"));
            out.write(propsXml.toByteArray());
            out.closeEntry();
            if (withHookJar) {
                // deliberately not a valid jar: detection must not open, load or
                // instantiate anything from it
                out.putNextEntry(new ZipEntry("META-INF/vault/hooks/hook.jar"));
                out.write("not a real jar".getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
        return file;
    }

    private boolean detect(File file) throws IOException {
        try (ZipArchive archive = new ZipArchive(file)) {
            archive.open(false);
            try {
                return ZipVaultPackage.hasHookEntries(archive);
            } catch (org.apache.jackrabbit.vault.packaging.PackageException e) {
                throw new IOException(e);
            }
        }
    }

    @Test
    public void testDetectsHookJarWithoutLoadingIt() throws IOException {
        // an invalid jar would fail hook registration, but pure listing must detect it
        assertTrue(detect(createPackage(true, false)));
    }

    @Test
    public void testDetectsHookProperty() throws IOException {
        assertTrue(detect(createPackage(false, true)));
    }

    @Test
    public void testNoHooksDetectedForPlainPackage() throws IOException {
        assertFalse(detect(createPackage(false, false)));
    }

    @Test
    public void testRejectsEmbeddedHooksBeforeLoadingAsAdmin() throws Exception {
        assertRejectedBeforeLoading(createPackage(true, false), "admin", false);
    }

    @Test
    public void testRejectsExternalHooksBeforeLoadingForAllowedUser() throws Exception {
        assertRejectedBeforeLoading(createPackage(false, true), "allowed-user", false);
    }

    @Test
    public void testRejectsEmbeddedHooksDuringDryRun() throws Exception {
        assertRejectedBeforeLoading(createPackage(true, false), "admin", true);
    }

    @Test
    public void testRejectsExternalHooksDuringDryRun() throws Exception {
        assertRejectedBeforeLoading(createPackage(false, true), "allowed-user", true);
    }

    private void assertRejectedBeforeLoading(File file, String userId, boolean dryRun) throws IOException {
        Session session = mock(Session.class);
        when(session.getUserID()).thenReturn(userId);
        ClassLoader classLoader = mock(ClassLoader.class);
        ImportOptions options = new ImportOptions();
        options.setDryRun(dryRun);
        options.setHookClassLoader(classLoader);
        try (ZipVaultPackage pack = new ZipVaultPackage(file, false)) {
            PackageException exception = assertThrows(
                    PackageException.class,
                    () -> pack.extract(
                            session,
                            options,
                            new SecurityConfig(new String[] {userId}, null, true),
                            false,
                            true,
                            null));
            assertTrue(exception.getMessage().contains("test:test-package:1.0"));
            assertTrue(exception.getMessage().contains("rejectPackagesWithHooks is enabled"));
        }
        verifyNoInteractions(session, classLoader);
    }

    @Test
    public void testPlainPackageRemainsAllowed() throws Exception {
        try (ZipVaultPackage pack = new ZipVaultPackage(createPackage(false, false), false)) {
            InstallContextImpl context = pack.prepareExtract(
                    mock(Session.class), new ImportOptions(), new SecurityConfig(null, null, true), false, true, null);
            try (InstallHookProcessor hooks = context.getHooks()) {
                assertFalse(hooks.hasHooks());
            }
        }
    }

    @Test
    public void testDryRunWithHooksRemainsAllowedWhenRejectionDisabled() throws Exception {
        ImportOptions options = new ImportOptions();
        options.setDryRun(true);
        try (ZipVaultPackage pack = new ZipVaultPackage(createPackage(true, true), false)) {
            InstallContextImpl context = pack.prepareExtract(
                    mock(Session.class), options, new SecurityConfig(null, null), false, true, null);
            try (InstallHookProcessor hooks = context.getHooks()) {
                assertFalse(hooks.hasHooks());
            }
        }
    }

    @Test
    public void testDryRunWithoutHooksRemainsAllowedWhenRejectionEnabled() throws Exception {
        ImportOptions options = new ImportOptions();
        options.setDryRun(true);
        try (ZipVaultPackage pack = new ZipVaultPackage(createPackage(false, false), false)) {
            InstallContextImpl context = pack.prepareExtract(
                    mock(Session.class), options, new SecurityConfig(null, null, true), false, true, null);
            try (InstallHookProcessor hooks = context.getHooks()) {
                assertFalse(hooks.hasHooks());
            }
        }
    }

    @Test
    public void testRejectsHooksRegisteredByCustomProcessor() throws Exception {
        InstallHookProcessor processor = mock(InstallHookProcessor.class);
        when(processor.hasHooks()).thenReturn(true);
        try (ZipVaultPackage pack = new ZipVaultPackage(createPackage(false, false), false)) {
            PackageException exception = assertThrows(
                    PackageException.class,
                    () -> pack.extract(
                            mock(Session.class),
                            new CustomHookOptions(processor),
                            new SecurityConfig(null, null, true),
                            false,
                            true,
                            null));
            assertTrue(exception.getMessage().contains("rejectPackagesWithHooks is enabled"));
            verify(processor).registerHooks(pack.getArchive(), null);
            verify(processor).close();
        }
    }

    private static class CustomHookOptions extends ImportOptions implements InstallHookProcessorFactory {
        private final InstallHookProcessor processor;

        private CustomHookOptions(InstallHookProcessor processor) {
            this.processor = processor;
        }

        @Override
        public InstallHookProcessor createInstallHookProcessor() {
            return processor;
        }
    }
}

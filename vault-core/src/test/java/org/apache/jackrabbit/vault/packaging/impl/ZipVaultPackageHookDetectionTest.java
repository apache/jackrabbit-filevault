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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.jackrabbit.vault.fs.config.DefaultMetaInf;
import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.fs.io.ImportOptions;
import org.apache.jackrabbit.vault.fs.io.ZipArchive;
import org.apache.jackrabbit.vault.packaging.InstallHookProcessor;
import org.apache.jackrabbit.vault.packaging.InstallHookProcessorFactory;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.apache.jackrabbit.vault.util.Constants;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;

/**
 * Tests that {@link ZipVaultPackage} detects install hooks by pure archive listing, without
 * loading or instantiating any hook class. The detection feeds the
 * admin allowance check that must run <em>before</em> hook registration.
 */
public class ZipVaultPackageHookDetectionTest {

    private static final String FILTER_XML =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<workspaceFilter version=\"1.0\"/>\n";

    @Rule
    public TemporaryFolder tmpFolder = new TemporaryFolder();

    /**
     * Detect an invalid hook jar without trying to load its classes.
     */
    @Test
    public void testDetectsHookJarWithoutLoadingIt() throws IOException, PackageException {
        Assert.assertTrue(detect(createPackage(new Properties(), "META-INF/vault/hooks/hook.jar")));
    }

    /**
     * Detect externally declared hooks without resolving their classes.
     */
    @Test
    public void testDetectsHookProperty() throws IOException, PackageException {
        Properties properties = new Properties();
        properties.setProperty("installhook.test.class", "com.example.NonExistingHook");
        Assert.assertTrue(detect(createPackage(properties)));
    }

    /**
     * Leave packages without hooks unrestricted by hook detection.
     */
    @Test
    public void testNoHooksDetectedForPlainPackage() throws IOException, PackageException {
        Assert.assertFalse(detect(createPackage(new Properties())));
    }

    /**
     * Match registration's rule that non-jar entries are not hooks.
     */
    @Test
    public void testIgnoresNonJarHookEntries() throws IOException, PackageException {
        Assert.assertFalse(detect(createPackage(new Properties(), "META-INF/vault/hooks/readme.txt")));
    }

    /**
     * Accept archives without metadata directories or package properties.
     */
    @Test
    public void testNoHooksWithoutMetadata() throws IOException, PackageException {
        Archive archive = Mockito.mock(Archive.class);
        Archive.Entry root = Mockito.mock(Archive.Entry.class);
        Mockito.when(archive.getRoot()).thenReturn(root);
        Mockito.when(archive.getMetaInf()).thenReturn(new DefaultMetaInf());
        Assert.assertFalse(ZipVaultPackage.hasHookEntries(archive));
    }

    /**
     * Accept archives whose metadata has no vault directory.
     */
    @Test
    public void testNoHooksWithoutVaultDirectory() throws IOException, PackageException {
        Archive archive = Mockito.mock(Archive.class);
        Archive.Entry root = Mockito.mock(Archive.Entry.class);
        Archive.Entry metaInf = Mockito.mock(Archive.Entry.class);
        Mockito.when(archive.getRoot()).thenReturn(root);
        Mockito.when(root.getChild(Constants.META_INF)).thenReturn(metaInf);
        Mockito.when(archive.getMetaInf()).thenReturn(new DefaultMetaInf());
        Assert.assertFalse(ZipVaultPackage.hasHookEntries(archive));
    }

    /**
     * Propagate archive listing failures rather than treating them as hook-free.
     */
    @Test
    public void testDetectionPropagatesIoErrors() throws IOException {
        Archive archive = Mockito.mock(Archive.class);
        IOException failure = new IOException("Cannot list archive");
        Mockito.when(archive.getRoot()).thenThrow(failure);
        try {
            ZipVaultPackage.hasHookEntries(archive);
            Assert.fail("Expected archive listing to fail");
        } catch (PackageException e) {
            Assert.assertSame(failure, e.getCause());
        }
    }

    /**
     * Reject a hooked package before its processor can load the jar.
     */
    @Test
    public void testRejectsHookJarBeforeRegistration()
            throws IOException, PackageException, RepositoryException {
        HookImportOptions options = new HookImportOptions();
        assertPreparationDenied(createPackage(new Properties(), "META-INF/vault/hooks/hook.jar"),
                createSession("installer"), options);
        Mockito.verifyZeroInteractions(options.processor);
    }

    /**
     * Reject an external hook declaration before resolving its class.
     */
    @Test
    public void testRejectsHookPropertyBeforeRegistration()
            throws IOException, PackageException, RepositoryException {
        Properties properties = new Properties();
        properties.setProperty("installhook.test.class", "com.example.NonExistingHook");
        HookImportOptions options = new HookImportOptions();
        assertPreparationDenied(createPackage(properties), createSession("installer"), options);
        Mockito.verifyZeroInteractions(options.processor);
    }

    /**
     * Reject root-only packages before invoking a custom processor.
     */
    @Test
    public void testRejectsRootPackageBeforeRegistration()
            throws IOException, PackageException, RepositoryException {
        Properties properties = new Properties();
        properties.setProperty(VaultPackage.NAME_REQUIRES_ROOT, "true");
        HookImportOptions options = new HookImportOptions();
        assertPreparationDenied(createPackage(properties), createSession("installer"), options);
        Mockito.verifyZeroInteractions(options.processor);
    }

    /**
     * Keep the post-registration check for hooks supplied by custom processors.
     */
    @Test
    public void testRejectsCustomProcessorHooks()
            throws IOException, PackageException, RepositoryException {
        HookImportOptions options = new HookImportOptions();
        Mockito.when(options.processor.hasHooks()).thenReturn(true);
        assertPreparationDenied(createPackage(new Properties()), createSession("installer"), options);
        Mockito.verify(options.processor).registerHooks(Mockito.any(Archive.class), Mockito.any(ClassLoader.class));
    }

    /**
     * Preserve installation of hooked packages by administrators.
     */
    @Test
    public void testAllowsAdminToRegisterHooks()
            throws IOException, PackageException, RepositoryException {
        HookImportOptions options = new HookImportOptions();
        Mockito.when(options.processor.hasHooks()).thenReturn(true);
        try (ZipVaultPackage pkg = new ZipVaultPackage(
                createPackage(new Properties(), "META-INF/vault/hooks/hook.jar"), false)) {
            Assert.assertNotNull(pkg.prepareExtract(createSession("admin"), options));
            Mockito.verify(options.processor).registerHooks(pkg.getArchive(), options.getHookClassLoader());
        }
    }

    /**
     * Preserve installation of ordinary packages by non-administrators.
     */
    @Test
    public void testAllowsPlainPackage()
            throws IOException, PackageException, RepositoryException {
        HookImportOptions options = new HookImportOptions();
        try (ZipVaultPackage pkg = new ZipVaultPackage(createPackage(new Properties()), false)) {
            Assert.assertNotNull(pkg.prepareExtract(createSession("installer"), options));
            Mockito.verify(options.processor).registerHooks(pkg.getArchive(), options.getHookClassLoader());
        }
    }

    /**
     * Keep dry runs from registering hooks or requiring hook execution permissions.
     */
    @Test
    public void testDryRunDoesNotRegisterHooks()
            throws IOException, PackageException, RepositoryException {
        HookImportOptions options = new HookImportOptions();
        options.setDryRun(true);
        try (ZipVaultPackage pkg = new ZipVaultPackage(
                createPackage(new Properties(), "META-INF/vault/hooks/hook.jar"), false)) {
            Assert.assertNotNull(pkg.prepareExtract(createSession("installer"), options));
            Mockito.verify(options.processor, Mockito.never())
                    .registerHooks(Mockito.any(Archive.class), Mockito.any(ClassLoader.class));
        }
    }

    /**
     * Keep the root-only permission requirement during dry runs.
     */
    @Test
    public void testDryRunRejectsRootPackage()
            throws IOException, PackageException, RepositoryException {
        Properties properties = new Properties();
        properties.setProperty(VaultPackage.NAME_REQUIRES_ROOT, "true");
        HookImportOptions options = new HookImportOptions();
        options.setDryRun(true);
        assertPreparationDenied(createPackage(properties), createSession("installer"), options);
        Mockito.verifyZeroInteractions(options.processor);
    }

    private File createPackage(Properties properties, String... entries) throws IOException {
        File file = tmpFolder.newFile();
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
            props.putAll(properties);
            ByteArrayOutputStream propsXml = new ByteArrayOutputStream();
            props.storeToXML(propsXml, null, "UTF-8");
            out.putNextEntry(new ZipEntry("META-INF/vault/properties.xml"));
            out.write(propsXml.toByteArray());
            out.closeEntry();
            for (String entry : entries) {
                // Invalid jar bytes ensure detection only lists entries.
                out.putNextEntry(new ZipEntry(entry));
                out.write("not a real jar".getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
        return file;
    }

    private boolean detect(File file) throws IOException, PackageException {
        ZipArchive archive = new ZipArchive(file);
        try {
            archive.open(false);
            return ZipVaultPackage.hasHookEntries(archive);
        } finally {
            archive.close();
        }
    }

    private static Session createSession(String userId) {
        Session session = Mockito.mock(Session.class);
        Mockito.when(session.getUserID()).thenReturn(userId);
        return session;
    }

    private void assertPreparationDenied(File file, Session session, ImportOptions options)
            throws IOException, PackageException, RepositoryException {
        try (ZipVaultPackage pkg = new ZipVaultPackage(file, false)) {
            try {
                pkg.prepareExtract(session, options);
                Assert.fail("Expected package preparation to require an administrator");
            } catch (PackageException e) {
                Assert.assertEquals("Package extraction requires admin session (userid not allowed).", e.getMessage());
            }
        }
    }

    private static final class HookImportOptions extends ImportOptions implements InstallHookProcessorFactory {

        private final InstallHookProcessor processor = Mockito.mock(InstallHookProcessor.class);

        /**
         * {@inheritDoc}
         */
        @Override
        public InstallHookProcessor createInstallHookProcessor() {
            return processor;
        }
    }
}

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
package org.apache.jackrabbit.vault.fs.io;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.impl.ZipVaultPackage;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertNull;

/**
 *  Test to demonstrate JCRVLT-838.
 *
 * The test case does not use the use the JcrPackageManagerImpl.assemble(Node, JcrPackageDefinition, ProgressTrackerListener)
 * call as shown in the ticket, but it resembles the major steps from that method.
 *
 * The exception is triggered by an invalid XML in this test, but there could be other reasons as well.
 *
 */
public class ZipArchiveCloseTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @BeforeClass
    public static void setUpOnce() throws URISyntaxException {
        // Disable stack traces for cleaner output
        System.setProperty(AbstractArchive.PROPERTY_ENABLE_STACK_TRACES, "false");
    }

    /**
     * Creates a ZIP file with malformed XML in META-INF/vault/properties.xml.
     * This will cause an exception during open() after the JarFile is created
     * but before the watcher is registered.
     */
    private File createZipWithMalformedProperties() throws IOException {
        File zipFile = tempFolder.newFile("malformed.zip");

        try (FileOutputStream fos = new FileOutputStream(zipFile);
                ZipOutputStream zos = new ZipOutputStream(fos)) {

            // Add META-INF/vault directory
            ZipEntry vaultEntry = new ZipEntry("META-INF/vault/");
            zos.putNextEntry(vaultEntry);
            zos.closeEntry();

            // Add malformed properties.xml
            ZipEntry propsEntry = new ZipEntry("META-INF/vault/properties.xml");
            zos.putNextEntry(propsEntry);
            // Write malformed (missing closing tag)
            zos.write("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n".getBytes());
            zos.write("<!DOCTYPE properties SYSTEM \"http://java.sun.com/dtd/properties.dtd\">\n".getBytes());
            zos.write("<properties>\n".getBytes());
            zos.write("<entry key=\"name\">test</entry>\n".getBytes());
            zos.closeEntry();

            // Add jcr_root directory
            ZipEntry jcrRootEntry = new ZipEntry("jcr_root/");
            zos.putNextEntry(jcrRootEntry);
            zos.closeEntry();
        }

        return zipFile;
    }

    /**
     * Test demonstrating the exact bug from the stack trace by following
     * the exact call chain from JcrPackageManagerImpl.assemble().
     */
    @Test(expected = NullPointerException.class)
    public void testExactCallChainFromAssemble() throws IOException {
        File zipFile = createZipWithMalformedProperties();

        // create the ZipVaultPackage
        ZipVaultPackage pack = new ZipVaultPackage(zipFile, false, false);

        // This triggers the full call chain:
        // getId() -> getProperty() -> getPropertiesMap() -> getMetaInf() -> getArchive() -> archive.open()
        // open() will fail after creating jar but before registering watcher
        // getMetaInf() catches the exception and returns null
        // So getId() returns null WITHOUT throwing an exception
        PackageId id = pack.getId();
        assertNull(id);

        // This throws NPE because the archive's jar is not null but watcher is null
        pack.close();
    }

    /**
     * Simpler test showing the same bug at the ZipArchive level directly.
     */
    @Test(expected = NullPointerException.class)
    public void testCloseAfterPartiallyFailedOpen() throws IOException {
        File zipFile = createZipWithMalformedProperties();
        ZipArchive archive = new ZipArchive(zipFile, false);

        // Try to open - this will partially succeed (jar created)
        // but fail during entry processing (before watcher registration)
        try {
            archive.open(false);
        } catch (IOException e) {
            // Expected - malformed XML during entry processing
        }

        archive.close();
    }

    /**
     * Test demonstrating that subsequent open() calls don't fix the problem.
     *
     */
    @Test(expected = NullPointerException.class)
    public void testSecondOpenDoesNotFixWatcher() throws IOException {
        File zipFile = createZipWithMalformedProperties();
        ZipArchive archive = new ZipArchive(zipFile, false);

        // First open() partially fails
        try {
            archive.open(false);
        } catch (IOException e) {
            // jar is now set, but watcher is null
        }

        // Try to open again - this just returns immediately because jar != null
        // The watcher is NOT fixed!
        try {
            archive.open(false);
        } catch (Exception e) {
            // Might not even throw - just returns at line 105
        }

        // Still: jar != null, watcher == null
        archive.close();
    }

    /**
     * Test showing that ZipStreamArchive handles this correctly with a null check.
     */
    @Test
    public void testZipStreamArchiveHandlesFailedOpenCorrectly() throws IOException {
        File zipFile = createZipWithMalformedProperties();
        ZipStreamArchive archive = new ZipStreamArchive(new java.io.FileInputStream(zipFile));

        try {
            archive.open(true);
        } catch (IOException e) {
            // Expected exception
        }

        // This does NOT throw NPE because ZipStreamArchive has:
        // if (watcher != null) { CloseWatcher.unregister(watcher); }
        archive.close();
    }

    /**
     * Test that close() without open() is safe.
     */
    @Test
    public void testCloseWithoutOpenIsSafe() throws URISyntaxException {
        File zipFile = Paths.get(ZipArchiveCloseTest.class
                        .getResource("/test-packages/atomic-counter-test.zip")
                        .toURI())
                .toFile();
        ZipArchive archive = new ZipArchive(zipFile, false);

        // Close without open - jar is null, so no NPE
        archive.close();
        archive.close(); // Multiple closes also safe
    }
}

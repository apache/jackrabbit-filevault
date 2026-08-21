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

import org.apache.jackrabbit.vault.fs.io.ZipArchive;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
}

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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class NestedZipArchiveTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private File containerFile;

    @Before
    public void setUp() throws IOException {
        containerFile = tempFolder.newFile();
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(containerFile.toPath()));
                InputStream input = getClass().getResourceAsStream("/test-packages/atomic-counter-test.zip")) {
            zip.putNextEntry(new ZipEntry("packages/nested.zip"));
            input.transferTo(zip);
            zip.closeEntry();
        }
    }

    @Test
    public void testLazyOpenAndContainerOwnership() throws IOException {
        try (ZipArchive container = spy(new ZipArchive(containerFile));
                NestedZipArchive nested = new NestedZipArchive(container, "packages/nested.zip")) {
            verify(container, never()).open(anyBoolean());
            nested.open(true);
            nested.open(true);
            verify(container, times(1)).open(true);
            nested.close();
            nested.close();
            verify(container, never()).close();
            assertNotNull(container.getEntry("packages/nested.zip"));
        }
    }

    @Test
    public void testCloseWithoutOpen() throws IOException {
        try (ZipArchive container = spy(new ZipArchive(containerFile))) {
            NestedZipArchive nested = new NestedZipArchive(container, "packages/nested.zip");
            nested.close();
            verify(container, never()).open(anyBoolean());
            verify(container, never()).close();
        }
    }

    @Test
    public void testNestedContentDoesNotRequireOpenContainer() throws IOException {
        try (ZipArchive container = new ZipArchive(containerFile);
                NestedZipArchive nested = new NestedZipArchive(container, "packages/nested.zip")) {
            nested.open(false);
            container.close();
            assertEquals(
                    "atomic-counter-test", nested.getMetaInf().getProperties().getProperty("name"));
            try (InputStream input = nested.openInputStream(nested.getEntry("META-INF/vault/properties.xml"))) {
                assertTrue(input.read() >= 0);
            }
        }
    }

    @Test
    public void testMissingEntryAndDirectory() throws IOException {
        try (ZipArchive container = new ZipArchive(containerFile)) {
            for (String path : new String[] {"missing.zip", "packages"}) {
                try (NestedZipArchive nested = new NestedZipArchive(container, path)) {
                    IOException exception = assertThrows(IOException.class, () -> nested.open(true));
                    assertTrue(exception.getMessage().contains(path));
                }
            }
            assertNotNull(container.getEntry("packages/nested.zip"));
        }
    }

    @Test
    public void testFailedOpenClosesStreamAndCanBeRetried() throws IOException {
        try (ZipArchive container = spy(new ZipArchive(containerFile));
                NestedZipArchive nested = new NestedZipArchive(container, "packages/nested.zip")) {
            InputStream input = mock(InputStream.class);
            when(input.read(any(byte[].class), anyInt(), anyInt()))
                    .thenThrow(new IOException("Cannot read nested ZIP"));
            doReturn(input).when(container).openInputStream(any());

            IOException exception = assertThrows(IOException.class, () -> nested.open(true));
            assertEquals("Cannot read nested ZIP", exception.getMessage());
            verify(input, atLeastOnce()).close();
            verify(container, never()).close();

            doCallRealMethod().when(container).openInputStream(any());
            nested.open(true);
            assertEquals(
                    "atomic-counter-test", nested.getMetaInf().getProperties().getProperty("name"));
        }
    }

    @Test
    public void testDeepNestingWithIndependentMetadata() throws IOException {
        byte[] leafZip = createPackage("leaf", null);
        byte[] middleZip = createPackage("middle", leafZip);
        byte[] nestedZip = createPackage("nested", middleZip);
        byte[] rootZip = createPackage("root", nestedZip);
        try (Archive container = spy(new ZipStreamArchive(new ByteArrayInputStream(rootZip)));
                NestedZipArchive nested = new NestedZipArchive(container, "nested.zip");
                NestedZipArchive middle = new NestedZipArchive(nested, "nested.zip");
                NestedZipArchive leaf = new NestedZipArchive(middle, "nested.zip")) {
            assertSame(container, nested.getRootContainer());
            assertSame(container, middle.getRootContainer());
            assertSame(container, leaf.getRootContainer());
            verify(container, never()).open(anyBoolean());

            leaf.open(true);
            leaf.open(true);
            verify(container, times(1)).open(true);
            assertEquals("root", container.getMetaInf().getProperties().getProperty("name"));
            assertEquals("nested", nested.getMetaInf().getProperties().getProperty("name"));
            assertEquals("middle", middle.getMetaInf().getProperties().getProperty("name"));
            assertEquals("leaf", leaf.getMetaInf().getProperties().getProperty("name"));
            assertNotSame(nested.getMetaInf(), middle.getMetaInf());
            assertNotSame(middle.getMetaInf(), leaf.getMetaInf());
            try (InputStream input = leaf.getInputSource(leaf.getEntry("META-INF/vault/properties.xml"))
                    .getByteStream()) {
                Properties properties = new Properties();
                properties.loadFromXML(input);
                assertEquals("leaf", properties.getProperty("name"));
            }

            leaf.close();
            verify(container, never()).close();
            assertNotNull(middle.getEntry("nested.zip"));
        }
    }

    @Test
    public void testMissingMetadataIsNotInherited() throws IOException {
        byte[] rootZip = createPackage("root", createPackage(null, null));
        try (Archive container = new ZipStreamArchive(new ByteArrayInputStream(rootZip));
                NestedZipArchive nested = new NestedZipArchive(container, "nested.zip")) {
            nested.open(true);
            assertEquals("root", container.getMetaInf().getProperties().getProperty("name"));
            assertNull(nested.getMetaInf().getProperties());
            assertNotSame(
                    container.getMetaInf().getSettings(), nested.getMetaInf().getSettings());
        }
    }

    private byte[] createPackage(String name, byte[] nestedZip) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            if (name != null) {
                zip.putNextEntry(new ZipEntry("META-INF/vault/properties.xml"));
                Properties properties = new Properties();
                properties.setProperty("name", name);
                properties.storeToXML(zip, null);
                zip.closeEntry();
            }
            if (nestedZip != null) {
                zip.putNextEntry(new ZipEntry("nested.zip"));
                zip.write(nestedZip);
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }
}

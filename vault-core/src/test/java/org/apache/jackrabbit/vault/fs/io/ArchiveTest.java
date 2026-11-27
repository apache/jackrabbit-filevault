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
package org.apache.jackrabbit.vault.fs.io;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collection;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.jackrabbit.vault.fs.api.PathMapping;
import org.apache.jackrabbit.vault.fs.impl.ArchiveWrapper;
import org.apache.jackrabbit.vault.fs.impl.SubPackageFilterArchive;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Tests duplicate-entry handling in {@link AbstractArchive} implementations.
 */
@RunWith(Parameterized.class)
public class ArchiveTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private final String archiveType;

    /**
     * Exercises readers and wrappers used for registration and installation.
     * @return the archive implementations to test
     */
    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> archives() {
        return Arrays.asList(new Object[][] {
            {"zip"}, {"stream"}, {"diskStream"}, {"memory"}, {"metaOnly"},
            {"mapped"}, {"wrapped"}, {"subPackage"}
        });
    }

    /**
     * Selects the archive implementation for this test instance.
     * @param archiveType a supported archive implementation
     */
    public ArchiveTest(String archiveType) {
        this.archiveType = archiveType;
    }

    /**
     * Rejects conflicting content even when only metadata is cached.
     * @throws Exception if fixture setup or archive handling fails
     */
    @Test
    public void rejectsDuplicateFiles() throws Exception {
        byte[] zip = renameEntry(createZip("jcr_root/a.txt", "jcr_root/b.txt"),
                "jcr_root/b.txt", "jcr_root/a.txt");
        try {
            openArchive(zip);
            Assert.fail("Duplicate file entry accepted");
        } catch (IOException e) {
            Assert.assertTrue(e.getMessage(), e.getMessage().contains("jcr_root/a.txt"));
        }
    }

    /**
     * Rejects duplicated metadata before readers resolve it differently.
     * @throws Exception if fixture setup or archive handling fails
     */
    @Test
    public void rejectsDuplicateMetadata() throws Exception {
        byte[] zip = renameEntry(createZip("META-INF/vault/a.txt", "META-INF/vault/b.txt"),
                "META-INF/vault/b.txt", "META-INF/vault/a.txt");
        try {
            openArchive(zip);
            Assert.fail("Duplicate metadata entry accepted");
        } catch (IOException e) {
            Assert.assertTrue(e.getMessage(), e.getMessage().contains("META-INF/vault/a.txt"));
        }
    }

    /**
     * Keeps ordinary archives readable.
     * @throws Exception if fixture setup or archive handling fails
     */
    @Test
    public void acceptsUniqueFiles() throws Exception {
        openArchive(createZip("jcr_root/a.txt", "jcr_root/b.txt"));
    }

    /**
     * Allows repeated directory entries emitted by legitimate ZIP tools.
     * @throws Exception if fixture setup or archive handling fails
     */
    @Test
    public void acceptsDuplicateDirectories() throws Exception {
        openArchive(renameEntry(createZip("jcr_root/", "jcr_roos/", "jcr_root/a.txt"),
                "jcr_roos/", "jcr_root/"));
    }

    private void openArchive(byte[] zip) throws Exception {
        File file = temporaryFolder.newFile();
        Files.write(file.toPath(), zip);
        Archive archive;
        if ("stream".equals(archiveType)) {
            archive = new ZipStreamArchive(new ByteArrayInputStream(zip));
        } else if ("diskStream".equals(archiveType)) {
            archive = new ZipStreamArchive(new ByteArrayInputStream(zip), 1);
        } else if ("memory".equals(archiveType) || "metaOnly".equals(archiveType)) {
            MemoryArchive memory = new MemoryArchive("metaOnly".equals(archiveType));
            memory.run(new ByteArrayInputStream(zip));
            archive = memory;
        } else if ("mapped".equals(archiveType)) {
            archive = new MappedArchive(new ZipArchive(file), PathMapping.IDENTITY);
        } else if ("wrapped".equals(archiveType)) {
            archive = new ArchiveWrapper(new ZipArchive(file));
        } else if ("subPackage".equals(archiveType)) {
            archive = new SubPackageFilterArchive(new ZipArchive(file));
        } else {
            archive = new ZipArchive(file);
        }
        try {
            archive.open(true);
            Assert.assertNotNull(archive.getRoot());
            if (!"metaOnly".equals(archiveType)) {
                Assert.assertNotNull(archive.getEntry("jcr_root/a.txt"));
            }
        } finally {
            archive.close();
        }
    }

    private static byte[] createZip(String... names) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("META-INF/vault/test.txt"));
            zip.write("metadata".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            for (String name : names) {
                zip.putNextEntry(new ZipEntry(name));
                if (!name.endsWith("/")) {
                    zip.write("content".getBytes(StandardCharsets.UTF_8));
                }
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }

    private static byte[] renameEntry(byte[] zip, String from, String to) {
        // Patch both headers; ZipOutputStream itself refuses duplicate names.
        byte[] result = zip.clone();
        byte[] source = from.getBytes(StandardCharsets.UTF_8);
        byte[] target = to.getBytes(StandardCharsets.UTF_8);
        Assert.assertEquals(source.length, target.length);
        for (int i = 0; i <= result.length - source.length; i++) {
            boolean matches = true;
            for (int j = 0; j < source.length; j++) {
                if (result[i + j] != source[j]) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                System.arraycopy(target, 0, result, i, target.length);
            }
        }
        return result;
    }
}

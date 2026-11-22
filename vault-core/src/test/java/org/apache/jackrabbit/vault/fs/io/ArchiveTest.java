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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;
import java.util.TimeZone;
import java.util.stream.Collectors;
import java.util.zip.CRC32;

import org.apache.jackrabbit.vault.fs.api.PathMapping;
import org.apache.jackrabbit.vault.fs.api.VaultInputSource;
import org.apache.jackrabbit.vault.fs.config.DefaultMetaInf;
import org.apache.jackrabbit.vault.fs.impl.ArchiveWrapper;
import org.apache.jackrabbit.vault.fs.impl.SubPackageFilterArchive;
import org.apache.jackrabbit.vault.fs.io.Archive.Entry;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

@RunWith(Parameterized.class)
public class ArchiveTest {

    @ClassRule
    public static TemporaryFolder tempFolder = new TemporaryFolder();

    @Parameters(name = "{2}")
    public static Iterable<? extends Object[]> data() throws URISyntaxException, IOException {
        ArchiveFactory zsaSupplier = zipPath -> new ZipStreamArchive(Files.newInputStream(zipPath));
        ArchiveFactory zaSupplier = zipPath -> new ZipArchive(zipPath.toFile());
        ArchiveFactory znaSupplier = ZipNioArchive::new;
        ArchiveFactory faSupplier = zipPath -> {
            URI uri = Objects.requireNonNull(
                            ArchiveTest.class.getResource("/test-packages/atomic-counter-test"),
                            "Could not find class resource with name '/test-packages/atomic-counter-test'")
                    .toURI();
            return new FileArchive(new File(uri));
        };
        ArchiveFactory memArchiveSupplier = zipPath -> {
            MemoryArchive memoryArchive = new MemoryArchive(false);
            try (InputStream input = Files.newInputStream(zipPath)) {
                memoryArchive.run(input);
            }
            return memoryArchive;
        };
        ArchiveFactory mappedArchiveSupplier =
                zipPath -> new MappedArchive(new ZipArchive(zipPath.toFile()), PathMapping.IDENTITY);
        ArchiveFactory wrappedArchiveSupplier = zipPath -> new ArchiveWrapper(new ZipArchive(zipPath.toFile()));
        ArchiveFactory subPackageFilterArchiveSupplier =
                zipPath -> new SubPackageFilterArchive(new ZipArchive(zipPath.toFile()));
        return Arrays.asList(new Object[][] {
            {zsaSupplier, true, "ZipStreamArchive"},
            {zaSupplier, true, "ZipArchive"},
            {znaSupplier, true, "ZipNioArchive"},
            {memArchiveSupplier, false, "MemoryArchive"},
            {faSupplier, false, "FileArchive"},
            {mappedArchiveSupplier, true, "MappedArchive"},
            {wrappedArchiveSupplier, true, "ArchiveWrapper"},
            {subPackageFilterArchiveSupplier, true, "SubPackageFilterArchive"}
            // TODO: SubArchive, JcrArchive
        });
    }

    private final ArchiveFactory archiveSupplier;
    private final boolean hasCloseWatcher;
    private final String name;
    private Archive archive;
    private Path zipPath;

    @BeforeClass
    public static void setUpOnce() {
        System.setProperty(AbstractArchive.PROPERTY_ENABLE_STACK_TRACES, "false");
    }

    public ArchiveTest(ArchiveFactory archiveSupplier, boolean hasCloseWatcher, String name) {
        this.archiveSupplier = archiveSupplier;
        this.hasCloseWatcher = hasCloseWatcher;
        this.name = name;
    }

    @Before
    public void setUp() throws Exception {
        zipPath = Paths.get(ArchiveTest.class
                .getResource("/test-packages/atomic-counter-test.zip")
                .toURI());
        archive = archiveSupplier.create(zipPath);
    }

    @After
    public void tearDown() {
        archive.close();
    }

    @Test
    public void testReadMetadata() throws IOException {
        archive.open(true);
        Properties props = archive.getMetaInf().getProperties();
        assertEquals("Package Name", "atomic-counter-test", props.getProperty("name"));
    }

    @Test
    public void testReadEntries() throws IOException, ParseException {
        archive.open(true);
        Entry root = archive.getRoot();
        assertNotNull(root);
        assertEquals("", root.getName());
        assertTrue(root.isDirectory());
        MatcherAssert.assertThat(
                root.getChildren().stream().map(Entry::getName).collect(Collectors.toList()),
                Matchers.containsInAnyOrder("META-INF", "jcr_root"));

        Archive.Entry entry = archive.getEntry("META-INF/vault/properties.xml");
        try (InputStream i = archive.openInputStream(entry)) {
            DefaultMetaInf metaInf = new DefaultMetaInf();
            metaInf.loadProperties(i, "inputstream");
            assertEquals(
                    "Package Name",
                    "atomic-counter-test",
                    metaInf.getProperties().getProperty("name"));
        }

        VaultInputSource vaultInputSource = archive.getInputSource(entry);
        try (InputStream i = vaultInputSource.getByteStream()) {
            DefaultMetaInf metaInf = new DefaultMetaInf();
            metaInf.loadProperties(i, "inputstream");
            assertEquals(
                    "Package Name",
                    "atomic-counter-test",
                    metaInf.getProperties().getProperty("name"));
        }
        assertEquals("Wrong size of entry \"META-INF/vault/properties.xml\"", 747, vaultInputSource.getContentLength());

        if (!"FileArchive".equals(name)) {
            Calendar expectedCalendar = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX", Locale.ENGLISH);
            expectedCalendar.setTime(sdf.parse("2017-02-14T09:33:22.000+00:00"));

            Calendar actualCalendar = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
            // comparing getLastModified is tricky, as ZIP doesn't store normalized dates but just in MS-DOS format
            // (https://docs.oracle.com/javase/8/docs/api/java/util/zip/ZipEntry.html#getTime--)
            // normalize to UTC
            actualCalendar.setTimeInMillis(vaultInputSource.getLastModified()
                    + TimeZone.getDefault().getOffset(vaultInputSource.getLastModified()));
            assertEquals(sdf.format(expectedCalendar.getTime()), sdf.format(actualCalendar.getTime()));
        }
    }

    @Test
    public void testOpenTwice() throws IOException {
        archive.open(true);
        archive.open(true);
    }

    @Test
    public void testNullParameters() throws IOException {
        archive.open(true);
        // FIXME: inconsistent handling in ZipStreamArchive
        // assertNull(archive.getInputSource(null)); // ZipStreamArchive returns a wrapper on top of the null entry
        // assertNull(archive.openInputStream(null));
    }

    @Test
    public void testCloseWithoutOpen() {
        archive.close();
    }

    private void openArchive() throws Exception {
        archiveSupplier.create(zipPath).open(false);
    }

    @Test
    public void testDumpForForgottenClose() throws Exception {
        assumeTrue("Skipping test as this archive has no CloseWatcher", hasCloseWatcher);
        openArchive();
        System.gc();
        Thread.sleep(500);
        assertTrue(AbstractArchive.dumpUnclosedArchives());
    }

    @Test
    public void testRejectsDuplicateEntries() throws Exception {
        assumeTrue("Directory archives cannot contain duplicate ZIP entries", !"FileArchive".equals(name));
        File file = duplicateEntryZip();
        IOException exception = Assert.assertThrows(IOException.class, () -> {
            try (Archive duplicateArchive = archiveSupplier.create(file.toPath())) {
                duplicateArchive.open(true);
            }
        });
        assertTrue(exception.getMessage().contains("jcr_root/a.txt"));
    }

    @Test
    public void testAcceptsUniqueEntries() throws Exception {
        assumeTrue("Directory archives do not read ZIP files", !"FileArchive".equals(name));
        try (Archive uniqueArchive = archiveSupplier.create(uniqueEntryZip().toPath())) {
            uniqueArchive.open(true);
            assertNotNull(uniqueArchive.getEntry("jcr_root/a.txt"));
            assertNotNull(uniqueArchive.getEntry("jcr_root/b.txt"));
        }
    }

    @Test
    public void testAcceptsDuplicateDirectoryEntries() throws Exception {
        assumeTrue("Directory archives cannot contain duplicate ZIP entries", !"FileArchive".equals(name));
        try (Archive directoryArchive =
                archiveSupplier.create(duplicateDirectoryEntryZip().toPath())) {
            directoryArchive.open(true);
            assertNotNull(directoryArchive.getEntry("jcr_root/a.txt"));
        }
    }

    private static void writeShort(ByteArrayOutputStream out, int v) {
        out.write(v & 0xFF);
        out.write((v >> 8) & 0xFF);
    }

    private static void writeInt(ByteArrayOutputStream out, long v) {
        out.write((int) (v & 0xFF));
        out.write((int) ((v >> 8) & 0xFF));
        out.write((int) ((v >> 16) & 0xFF));
        out.write((int) ((v >> 24) & 0xFF));
    }

    /**
     * Assembles a STORED (uncompressed) zip file directly from local file headers and a central
     * directory, bypassing {@code ZipOutputStream}'s duplicate-name check so that two entries can
     * share the same name.
     *
     * @param names entry names, in local-header (stream) order; a trailing '/' marks a directory
     * @param datas entry content, {@code null} for directory entries
     * @return the raw bytes of a valid zip file
     */
    private static byte[] buildZip(List<String> names, List<byte[]> datas) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<Integer> offsets = new ArrayList<>();
        List<Long> crcs = new ArrayList<>();
        List<byte[]> nameBytesList = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            offsets.add(out.size());
            byte[] nameBytes = names.get(i).getBytes(StandardCharsets.UTF_8);
            nameBytesList.add(nameBytes);
            byte[] data = datas.get(i) == null ? new byte[0] : datas.get(i);
            CRC32 crc = new CRC32();
            crc.update(data);
            crcs.add(crc.getValue());

            writeInt(out, 0x04034b50L); // local file header signature
            writeShort(out, 20); // version needed to extract
            writeShort(out, 0); // general purpose flag
            writeShort(out, 0); // method: stored
            writeShort(out, 0); // mod time
            writeShort(out, 0x21); // mod date
            writeInt(out, crc.getValue());
            writeInt(out, data.length); // compressed size
            writeInt(out, data.length); // uncompressed size
            writeShort(out, nameBytes.length);
            writeShort(out, 0); // extra field length
            out.write(nameBytes, 0, nameBytes.length);
            out.write(data, 0, data.length);
        }
        int cdStart = out.size();
        for (int i = 0; i < names.size(); i++) {
            byte[] nameBytes = nameBytesList.get(i);
            byte[] data = datas.get(i) == null ? new byte[0] : datas.get(i);
            writeInt(out, 0x02014b50L); // central directory header signature
            writeShort(out, 20); // version made by
            writeShort(out, 20); // version needed to extract
            writeShort(out, 0); // general purpose flag
            writeShort(out, 0); // method: stored
            writeShort(out, 0); // mod time
            writeShort(out, 0x21); // mod date
            writeInt(out, crcs.get(i));
            writeInt(out, data.length);
            writeInt(out, data.length);
            writeShort(out, nameBytes.length);
            writeShort(out, 0); // extra field length
            writeShort(out, 0); // comment length
            writeShort(out, 0); // disk number start
            writeShort(out, 0); // internal attributes
            writeInt(out, 0); // external attributes
            writeInt(out, offsets.get(i)); // local header offset
            out.write(nameBytes, 0, nameBytes.length);
        }
        int cdSize = out.size() - cdStart;
        writeInt(out, 0x06054b50L); // end of central directory signature
        writeShort(out, 0); // disk number
        writeShort(out, 0); // disk with central directory
        writeShort(out, names.size()); // entries on this disk
        writeShort(out, names.size()); // total entries
        writeInt(out, cdSize);
        writeInt(out, cdStart);
        writeShort(out, 0); // comment length
        return out.toByteArray();
    }

    private File writeZipFile(List<String> names, List<byte[]> datas) throws IOException {
        File file = tempFolder.newFile();
        Files.write(file.toPath(), buildZip(names, datas));
        return file;
    }

    private File duplicateEntryZip() throws IOException {
        // two non-directory entries named "jcr_root/a.txt" with different content; a zip reader
        // resolving by local-header order sees "first" while one resolving by central-directory
        // order may see "second" (or vice versa, depending on the implementation)
        return writeZipFile(
                Arrays.asList("jcr_root/a.txt", "jcr_root/a.txt"),
                Arrays.asList("first".getBytes(StandardCharsets.UTF_8), "second".getBytes(StandardCharsets.UTF_8)));
    }

    private File uniqueEntryZip() throws IOException {
        return writeZipFile(
                Arrays.asList("jcr_root/a.txt", "jcr_root/b.txt"),
                Arrays.asList(
                        "a-content".getBytes(StandardCharsets.UTF_8), "b-content".getBytes(StandardCharsets.UTF_8)));
    }

    private File duplicateDirectoryEntryZip() throws IOException {
        // duplicate *directory* entries carry no content to disagree over and must remain
        // tolerated: some legitimate zip tools emit them more than once
        return writeZipFile(
                Arrays.asList("jcr_root/", "jcr_root/", "jcr_root/a.txt"),
                Arrays.asList(null, null, "a-content".getBytes(StandardCharsets.UTF_8)));
    }

    @FunctionalInterface
    private interface ArchiveFactory {
        Archive create(Path zipPath) throws Exception;
    }
}

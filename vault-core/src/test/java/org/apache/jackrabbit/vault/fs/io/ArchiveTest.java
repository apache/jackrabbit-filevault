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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Locale;
import java.util.Properties;
import java.util.TimeZone;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.jackrabbit.vault.fs.api.PathMapping;
import org.apache.jackrabbit.vault.fs.api.VaultInputSource;
import org.apache.jackrabbit.vault.fs.config.DefaultMetaInf;
import org.apache.jackrabbit.vault.fs.impl.ArchiveWrapper;
import org.apache.jackrabbit.vault.fs.impl.SubPackageFilterArchive;
import org.apache.jackrabbit.vault.fs.io.Archive.Entry;
import org.apache.jackrabbit.vault.packaging.integration.IntegrationTestBase;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ArchiveTest {

    @ClassRule
    public static TemporaryFolder tempFolder = new TemporaryFolder();

    @Parameters(name = "{2}" )
    public static Iterable<? extends Object[]> data() throws URISyntaxException, IOException {
        Path zipPath = Paths.get(ArchiveTest.class.getResource("/test-packages/atomic-counter-test.zip").toURI());
        Supplier<Archive> zsaSupplier = () -> new ZipStreamArchive(ArchiveTest.class.getResourceAsStream("/test-packages/atomic-counter-test.zip"));
        Supplier<Archive> zaSupplier = () -> new ZipArchive(zipPath.toFile());
        Supplier<Archive> znaSupplier = () -> new ZipNioArchive(zipPath);
        Supplier<Archive> faSupplier = () -> {
                try {
                    return new FileArchive(IntegrationTestBase.getFile(ArchiveTest.class, "/test-packages/atomic-counter-test", () -> {
                        try {
                            return tempFolder.newFile();
                        } catch (IOException e) {
                            throw new UncheckedIOException("cannot create temp file", e);
                        }
                    }));
                } catch (IOException e) {
                    throw new UncheckedIOException("cannot create temp file", e);
                }
        };
        Supplier<Archive> memArchiveSupplier = () -> { 
            MemoryArchive memoryArchive;
            try (InputStream input = ArchiveTest.class.getResourceAsStream("/test-packages/atomic-counter-test.zip")) {
                memoryArchive = new MemoryArchive(false);
                memoryArchive.run(input);
            } catch (Exception e) {
                throw new RuntimeException("Some exception while fillig the memory archive", e);
            }
            return memoryArchive;
        };
        Supplier<Archive> mappedArchiveSupplier = () -> new MappedArchive(new ZipArchive(zipPath.toFile()), PathMapping.IDENTITY);
        Supplier<Archive> wrappedArchiveSupplier = () -> new ArchiveWrapper(new ZipArchive(zipPath.toFile()));
        Supplier<Archive> subPackageFilterArchiveSupplier = () -> new SubPackageFilterArchive(new ZipArchive(zipPath.toFile()));
        return Arrays.asList(new Object[][] { 
            { zsaSupplier, true, "ZipStreamArchive" },
            { zaSupplier, true, "ZipArchive" },
            { znaSupplier, true, "ZipNioArchive" },
            { memArchiveSupplier, false, "MemoryArchive" },
            { faSupplier, false, "FileArchive" },
            { mappedArchiveSupplier, true, "MappedArchive" },
            { wrappedArchiveSupplier, true, "ArchiveWrapper" },
            { subPackageFilterArchiveSupplier, true, "SubPackageFilterArchive" }
            // TODO: SubArchive, JcrArchive
        });
    }

    private final Supplier<Archive> archiveSupplier;
    private final boolean hasCloseWatcher;
    private final String name;
    private Archive archive;

    @BeforeClass
    public static void setUpOnce() {
        System.setProperty(AbstractArchive.PROPERTY_ENABLE_STACK_TRACES, "false");
    }

    public ArchiveTest(Supplier<Archive> archiveSupplier, boolean hasCloseWatcher, String name) {
        this.archiveSupplier = archiveSupplier;
        this.hasCloseWatcher = hasCloseWatcher;
        this.name = name;
    }

    @Before
    public void setUp() {
        archive = archiveSupplier.get();
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
        MatcherAssert.assertThat(root.getChildren().stream().map(Entry::getName).collect(Collectors.toList()), Matchers.containsInAnyOrder("META-INF", "jcr_root"));

        Archive.Entry entry = archive.getEntry("META-INF/vault/properties.xml");
        try (InputStream i = archive.openInputStream(entry)) {
            DefaultMetaInf metaInf = new DefaultMetaInf();
            metaInf.loadProperties(i, "inputstream");
            assertEquals("Package Name", "atomic-counter-test", metaInf.getProperties().getProperty("name"));
        }

        VaultInputSource vaultInputSource = archive.getInputSource(entry);
        try (InputStream i = vaultInputSource.getByteStream()) {
            DefaultMetaInf metaInf = new DefaultMetaInf();
            metaInf.loadProperties(i, "inputstream");
            assertEquals("Package Name", "atomic-counter-test", metaInf.getProperties().getProperty("name"));
        }
        assertEquals("Wrong size of entry \"META-INF/vault/properties.xml\"", 747, vaultInputSource.getContentLength());

        if (!"FileArchive".equals(name)) {
            Calendar expectedCalendar = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX", Locale.ENGLISH);
            expectedCalendar.setTime(sdf.parse("2017-02-14T09:33:22.000+00:00"));
            
            Calendar actualCalendar = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
            // comparing getLastModified is tricky, as ZIP doesn't store normalized dates but just in MS-DOS format (https://docs.oracle.com/javase/8/docs/api/java/util/zip/ZipEntry.html#getTime--)
            // normalize to UTC
            actualCalendar.setTimeInMillis(vaultInputSource.getLastModified() + TimeZone.getDefault().getOffset(vaultInputSource.getLastModified()));
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
        //assertNull(archive.getInputSource(null)); // ZipStreamArchive returns a wrapper on top of the null entry
        //assertNull(archive.openInputStream(null));
    }

    @Test
    public void testCloseWithoutOpen() {
        archive.close();
    }

    private void openArchive() throws IOException {
        archiveSupplier.get().open(false);
    }

    @Test
    public void testDumpForForgottenClose() throws IOException, InterruptedException {
        assumeTrue("Skipping test as this archive has no CloseWatcher", hasCloseWatcher);
        openArchive();
        System.gc();
        Thread.sleep(500);
        assertTrue(AbstractArchive.dumpUnclosedArchives());
    }
}

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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Locale;
import java.util.Properties;
import java.util.function.Supplier;

import org.apache.jackrabbit.vault.fs.api.VaultInputSource;
import org.apache.jackrabbit.vault.fs.config.DefaultMetaInf;
import org.apache.jackrabbit.vault.fs.io.Archive.Entry;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ArchiveTest {

    @Parameters(name = "{1}" )
    public static Iterable<? extends Object[]> data() throws URISyntaxException {
        Path zipPath = Paths.get(ArchiveTest.class.getResource("/test-packages/atomic-counter-test.zip").toURI());
        Supplier<Archive> zsaSupplier = () -> new ZipStreamArchive(ArchiveTest.class.getResourceAsStream("/test-packages/atomic-counter-test.zip"));
        Supplier<Archive> zaSupplier = () -> new ZipArchive(zipPath.toFile());
        Supplier<Archive> znaSupplier = () -> new ZipNioArchive(zipPath);
        Supplier<Archive> maSupplier = () -> { 
            MemoryArchive memoryArchive;
            try (InputStream input = ArchiveTest.class.getResourceAsStream("/test-packages/atomic-counter-test.zip")) {
                memoryArchive = new MemoryArchive(false);
                memoryArchive.run(input);
            } catch (Exception e) {
                throw new RuntimeException("Some exception while fillig the memory archive", e);
            }
            return memoryArchive;
        };
        return Arrays.asList(new Object[][] { 
            { zsaSupplier, "ZipStreamArchive" },
            { zaSupplier, "ZipArchive" },
            { znaSupplier, "ZipNioArchive" },
            { maSupplier, "MemoryArchive" }
        });
    }

    private final Supplier<Archive> archiveSupplier;
    private Archive archive;

    public ArchiveTest(Supplier<Archive> archiveSupplier, String name) {
        this.archiveSupplier = archiveSupplier;
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
        assertTrue(root.isDirectory());
        assertArrayEquals(new String[]{"META-INF", "jcr_root"}, root.getChildren().stream().map(Entry::getName).toArray(String[]::new));

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
        assertEquals(747, vaultInputSource.getContentLength());
        
        // the last modified date uses the default timezone (and not GMT)
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX", Locale.ENGLISH);
        cal.setTime(sdf.parse("2017-02-14T09:33:22.000+01:00"));
        Calendar actualCalendar = Calendar.getInstance();
        actualCalendar.setTimeInMillis(vaultInputSource.getLastModified());
        assertEquals(cal.getTime(), actualCalendar.getTime());
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
}

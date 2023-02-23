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
package org.apache.jackrabbit.vault.vlt.meta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

import org.apache.jackrabbit.vault.fs.api.DumpContext;
import org.apache.jackrabbit.vault.fs.api.Dumpable;
import org.apache.jackrabbit.vault.util.MD5;
import org.apache.jackrabbit.vault.vlt.VltException;
import org.apache.jackrabbit.vault.vlt.meta.xml.XmlEntries;
import org.apache.jackrabbit.vault.vlt.meta.xml.XmlEntryInfo;
import org.apache.jackrabbit.vault.vlt.meta.xml.zip.ZipMetaDir;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class XmlEntriesTest {

    private File file;

    private boolean verbose;

    protected MetaDirectory dir;

    protected VltEntries entries;

    @TempDir
    File tempDir;

    protected void open() throws IOException, VltException {
        dir = new ZipMetaDir(file);
        entries = dir.getEntries();
    }

    protected void close() throws IOException {
        if (entries != null) {
            entries = null;
        }
        if (dir != null) {
            dir.sync();
            dir.close();
        }
    }

    protected void reopen() throws IOException, VltException {
        close();
        open();
    }

    @Test
    void testRepoAddress() throws IOException, VltException {
        open();
        assertNull(dir.getRepositoryUrl());
        dir.setRepositoryUrl("http://localhost:8080");
        dir.sync();
        reopen();
        assertEquals("http://localhost:8080", dir.getRepositoryUrl());
    }

    @Test
    void testAddEntry() throws VltException, IOException {
        open();
        assertFalse(entries.hasEntry("foo.png"));
        entries.update("foo.png", "/bla", "foo.png");
        reopen();
        assertTrue(entries.hasEntry("foo.png"));
    }

    @Test
    void testAddInfo() throws VltException, IOException {
        testAddEntry();

        assertTrue(entries.hasEntry("foo.png"));
        VltEntry e = entries.getEntry("foo.png");
        assertNull(e.base());
        VltEntryInfo base = e.create(VltEntryInfo.Type.BASE);
        e.put(base);
        reopen();
        e = entries.getEntry("foo.png");
        assertTrue(entries.hasEntry("foo.png"));
        assertNotNull(e.base());
    }

    @Test
    void testModifyInfo() throws VltException, IOException {
        testAddInfo();
        reopen();

        assertTrue(entries.hasEntry("foo.png"));
        VltEntry e = entries.getEntry("foo.png");
        VltEntryInfo base = e.base();
        assertNotNull(base);
        base.setContentType("text/plain");
        base.setDate(1000);
        base.setMd5(new MD5(2,3));
        base.setSize(4);
        ((XmlEntryInfo) base).setName("myName");
        reopen();
        e = entries.getEntry("foo.png");
        base = e.base();
        assertEquals("text/plain", base.getContentType());
        assertEquals(1000, base.getDate());
        assertEquals(new MD5(2,3), base.getMd5());
        assertEquals(4, base.getSize());
        assertEquals("myName", ((XmlEntryInfo) base).getName());
    }

    @BeforeEach
    protected void setUp() throws Exception {
        file = new File(tempDir, "vlt-test-entries.zip");
        dir = new ZipMetaDir(file);
        dir.create("/a/b/c");
        dir.close();
        dir = null;
    }

    @AfterEach
    protected void tearDown() throws Exception {
        if (entries != null && verbose) {
            PrintWriter out = new PrintWriter(System.out);
            out.println("----------------------------------------------------------");
            if (entries instanceof Dumpable) {
                ((Dumpable) entries).dump(new DumpContext(out), true);
            }
            out.flush();
        }
    }

    @Test
    void testXSS() throws VltException {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<!DOCTYPE entries [\n" +
                "   <!ENTITY % foo \"bar\">\n" +
                "]>\n" +
                "<entries path=\"/home/users/m/mCY2rm1YSMlKFlJ-NEN3\">\n" +
                " <entry name=\".content.xml\" rp=\"\" ap=\"/home/users/m/mCY2rm1YSMlKFlJ-NEN3\">\n" +
                "   <base date=\"2018-10-02T11:44:02.000+02:00\" md5=\"268b8e1f6d7b3fc9ec71226ee1a9dc70\" contentType=\"text/xml\" size=\"946\"/>\n" +
                "   <work date=\"2018-10-02T11:44:02.000+02:00\" md5=\"268b8e1f6d7b3fc9ec71226ee1a9dc70\" contentType=\"text/xml\" size=\"946\"/>\n" +
                " </entry>\n" +
                " <entry name=\"_rep_policy.xml\" rp=\"\" ap=\"/home/users/m/mCY2rm1YSMlKFlJ-NEN3/rep:policy\">\n" +
                "   <base date=\"2018-10-02T11:44:02.000+02:00\" md5=\"5a788decc1968551e2838bc46914f75a\" contentType=\"text/xml\" size=\"500\"/>\n" +
                "   <work date=\"2018-10-02T11:44:02.000+02:00\" md5=\"5a788decc1968551e2838bc46914f75a\" contentType=\"text/xml\" size=\"500\"/>\n" +
                " </entry>\n" +
                "</entries>";
        try {
            XmlEntries entries = XmlEntries.load(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            assertTrue(entries.hasEntry(".content.xml"));
            fail("XML entries with DTD should fail.");
        } catch (VltException e) {
            // ok
        }
    }
}
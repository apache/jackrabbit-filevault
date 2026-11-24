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
package org.apache.jackrabbit.vault.vlt;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;

import org.apache.jackrabbit.vault.vlt.meta.MetaDirectory;
import org.apache.jackrabbit.vault.vlt.meta.xml.XmlEntries;
import org.apache.jackrabbit.vault.vlt.meta.xml.file.FileMetaDir;
import org.apache.jackrabbit.vault.vlt.meta.xml.zip.ZipMetaDir;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Tests untrusted metadata names at {@link XmlEntries} and local file sinks.
 */
@RunWith(Parameterized.class)
public class MetadataTraversalTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private final String name;

    /**
     * Covers traversal, platform separators and working-copy control aliases.
     * @return unsafe single-file names
     */
    @Parameterized.Parameters(name = "name={0}")
    public static Collection<Object[]> names() {
        return Arrays.asList(new Object[][] {
            {""}, {"."}, {".."}, {"../evil.txt"}, {"sub/evil.txt"}, {"sub\\evil.txt"},
            {"c:evil.txt"}, {"file:stream"}, {".vlt"}, {".VLT"}, {".vlt.. "}
        });
    }

    /**
     * Selects the metadata name to reject.
     * @param name an unsafe file name
     */
    public MetadataTraversalTest(String name) {
        this.name = name;
    }

    /**
     * Rejects names before a constructor can bind them to a local path.
     * @throws Exception if validation raises an unexpected exception
     */
    @Test
    public void constructorRejectsUnsafeNameBeforeUsingParent() throws Exception {
        try {
            new VltFile(null, name, null);
            Assert.fail("Unsafe constructor name accepted: " + name);
        } catch (VltException e) {
            Assert.assertTrue(e.getMessage(), e.getMessage().contains("unsafe file name"));
        }
    }

    /**
     * Rejects unsafe entry names when reading working-copy XML.
     * @throws Exception if parsing raises an unexpected exception
     */
    @Test
    public void xmlRejectsUnsafeEntryName() throws Exception {
        assertRejectedXml("<entry name=\"" + name + "\" rp=\"\" ap=\"/content\"><base/></entry>");
    }

    /**
     * Rejects unsafe conflict and base names before later delete or revert operations.
     * @throws Exception if parsing raises an unexpected exception
     */
    @Test
    public void xmlRejectsUnsafeInfoNames() throws Exception {
        for (String type : new String[] {"base", "mine", "theirs", "work"}) {
            assertRejectedXml("<entry name=\"safe.txt\" rp=\"\" ap=\"/content\"><"
                    + type + " name=\"" + name + "\"/></entry>");
        }
    }

    /**
     * Rejects unsafe names for file-backed metadata reads and writes.
     * @throws Exception if metadata setup or cleanup fails
     */
    @Test
    public void fileMetadataRejectsUnsafeNames() throws Exception {
        assertRejectedMetadata(new FileMetaDir(new File(temporaryFolder.getRoot(), "file-meta")));
    }

    /**
     * Rejects the same names for ZIP-backed metadata reads and writes.
     * @throws Exception if metadata setup or cleanup fails
     */
    @Test
    public void zipMetadataRejectsUnsafeNames() throws Exception {
        assertRejectedMetadata(new ZipMetaDir(new File(temporaryFolder.getRoot(), "zip-meta")));
    }

    private void assertRejectedXml(String entry) throws Exception {
        String xml = "<entries path=\"/content\">" + entry + "</entries>";
        try {
            XmlEntries.load(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            Assert.fail("Unsafe XML name accepted: " + name);
        } catch (VltException e) {
            Assert.assertTrue(e.getMessage(), e.getMessage().contains("unsafe"));
        }
    }

    private void assertRejectedMetadata(MetaDirectory metadata) throws Exception {
        try {
            metadata.create("/content");
            for (boolean create : new boolean[] {false, true}) {
                try {
                    metadata.getBaseFile(name, create);
                    Assert.fail("Unsafe base name accepted: " + name);
                } catch (IOException e) {
                    Assert.assertTrue(e.getMessage(), e.getMessage().contains("unsafe meta file name"));
                }
                try {
                    metadata.getTmpFile(name, create);
                    Assert.fail("Unsafe temporary name accepted: " + name);
                } catch (IOException e) {
                    Assert.assertTrue(e.getMessage(), e.getMessage().contains("unsafe meta file name"));
                }
            }
            Assert.assertFalse(new File(temporaryFolder.getRoot(), "evil.txt").exists());
        } finally {
            metadata.close();
        }
    }
}

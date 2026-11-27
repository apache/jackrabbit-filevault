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
package org.apache.jackrabbit.vault.vlt.meta.xml.file;

import java.io.File;
import java.io.IOException;

import org.apache.jackrabbit.vault.vlt.meta.MetaFile;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Reproduces GH-13's {@code base}/{@code tmp} sink: {@code FileMetaDir.getBaseFile}/
 * {@code getTmpFile} build {@code new File(baseDir|tmpDir, name)} directly, so a traversal
 * name read from a poisoned {@code entries.xml} (e.g. {@code base/../../x}) would write or
 * read outside the meta directory. This is the one {@code MetaDirectory} implementation
 * where the sink is a real file system path rather than a zip entry name.
 */
public class FileMetaDirTraversalTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private File tempDir;

    private FileMetaDir metaDir;

    /**
     * Creates isolated file-backed metadata.
     * @throws IOException if metadata initialization fails
     */
    @Before
    public void setUp() throws IOException {
        tempDir = temporaryFolder.newFolder("metadata");
        File dir = new File(tempDir, ".vlt");
        metaDir = new FileMetaDir(dir);
        metaDir.create("/a/b/c");
    }

    /**
     * Closes metadata before its temporary directory is removed.
     * @throws IOException if metadata synchronization fails
     */
    @After
    public void tearDown() throws IOException {
        if (metaDir != null) {
            metaDir.close();
        }
    }

    /** Rejects traversal names in base storage. */
    @Test(expected = IOException.class)
    public void getBaseFileRejectsTraversalName() throws IOException {
        metaDir.getBaseFile("../../../../tmp/evil.txt", true);
    }

    /** Rejects traversal names in temporary storage. */
    @Test(expected = IOException.class)
    public void getTmpFileRejectsTraversalName() throws IOException {
        metaDir.getTmpFile("../../../../tmp/evil.txt", true);
    }

    /** Requires base filenames to address a single entry. */
    @Test(expected = IOException.class)
    public void getBaseFileRejectsPathSeparatorName() throws IOException {
        metaDir.getBaseFile("sub/evil.txt", true);
    }

    /** Rejects traversal without creating the escape target. */
    @Test
    public void traversalNameNeverCreatesFileOutsideMetaDir() throws IOException {
        File escapeTarget = new File(tempDir, "evil.txt");
        try {
            metaDir.getBaseFile("../evil.txt", true);
            Assert.fail("Traversal name accepted");
        } catch (IOException expected) {
            Assert.assertTrue(expected.getMessage().contains("../evil.txt"));
        }
        Assert.assertFalse("traversal name must not create a file outside the meta directory", escapeTarget.exists());
    }

    /** Keeps ordinary base and temporary files usable. */
    @Test
    public void getBaseFileAndGetTmpFileStillAcceptPlainNames() throws IOException {
        MetaFile base = metaDir.getBaseFile("safe.txt", true);
        MetaFile tmp = metaDir.getTmpFile("safe.txt", true);
        Assert.assertFalse(base == null || tmp == null);
    }
}

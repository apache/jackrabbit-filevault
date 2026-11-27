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
package org.apache.jackrabbit.vault.vlt.meta.xml.zip;

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
 * Reproduces GH-13's {@code base}/{@code tmp} sink for the zip-backed {@link ZipMetaDir}:
 * {@code getBaseFile}/{@code getTmpFile} address a zip entry via {@code "base/" + name} /
 * {@code "tmp/" + name}, so a traversal name read from a poisoned {@code entries.xml} must
 * still be rejected the same way as the file-backed {@code FileMetaDir} sink.
 */
public class ZipMetaDirTraversalTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private File tempDir;

    private ZipMetaDir metaDir;

    /**
     * Creates isolated ZIP-backed metadata.
     * @throws IOException if metadata initialization fails
     */
    @Before
    public void setUp() throws IOException {
        tempDir = temporaryFolder.newFolder("metadata");
        File zipFile = new File(tempDir, "test.vlt");
        metaDir = new ZipMetaDir(zipFile);
        metaDir.create("/a/b/c");
    }

    /**
     * Closes the ZIP before its temporary directory is removed.
     * @throws IOException if metadata synchronization fails
     */
    @After
    public void tearDown() throws IOException {
        if (metaDir != null) {
            metaDir.close();
        }
    }

    /** Rejects traversal names in ZIP base entries. */
    @Test(expected = IOException.class)
    public void getBaseFileRejectsTraversalName() throws IOException {
        metaDir.getBaseFile("../../../../tmp/evil.txt", true);
    }

    /** Rejects traversal names in ZIP temporary entries. */
    @Test(expected = IOException.class)
    public void getTmpFileRejectsTraversalName() throws IOException {
        metaDir.getTmpFile("../../../../tmp/evil.txt", true);
    }

    /** Requires base names to address a single ZIP entry. */
    @Test(expected = IOException.class)
    public void getBaseFileRejectsPathSeparatorName() throws IOException {
        metaDir.getBaseFile("sub/evil.txt", true);
    }

    /** Keeps ordinary ZIP base and temporary entries usable. */
    @Test
    public void getBaseFileAndGetTmpFileStillAcceptPlainNames() throws IOException {
        MetaFile base = metaDir.getBaseFile("safe.txt", true);
        MetaFile tmp = metaDir.getTmpFile("safe.txt", true);
        Assert.assertFalse(base == null || tmp == null);
    }
}

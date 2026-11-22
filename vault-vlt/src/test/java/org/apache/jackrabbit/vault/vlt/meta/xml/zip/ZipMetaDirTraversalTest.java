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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Reproduces GH-13's {@code base}/{@code tmp} sink for the zip-backed {@link ZipMetaDir}:
 * {@code getBaseFile}/{@code getTmpFile} address a zip entry via {@code "base/" + name} /
 * {@code "tmp/" + name}, so a traversal name read from a poisoned {@code entries.xml} must
 * still be rejected the same way as the file-backed {@code FileMetaDir} sink.
 */
class ZipMetaDirTraversalTest {

    @TempDir
    File tempDir;

    private ZipMetaDir metaDir;

    @BeforeEach
    void setUp() throws IOException {
        File zipFile = new File(tempDir, "test.vlt");
        metaDir = new ZipMetaDir(zipFile);
        metaDir.create("/a/b/c");
    }

    @Test
    void getBaseFileRejectsTraversalName() {
        assertThrows(IOException.class, () -> metaDir.getBaseFile("../../../../tmp/evil.txt", true));
    }

    @Test
    void getTmpFileRejectsTraversalName() {
        assertThrows(IOException.class, () -> metaDir.getTmpFile("../../../../tmp/evil.txt", true));
    }

    @Test
    void getBaseFileRejectsPathSeparatorName() {
        assertThrows(IOException.class, () -> metaDir.getBaseFile("sub/evil.txt", true));
    }

    @Test
    void getBaseFileAndGetTmpFileStillAcceptPlainNames() {
        assertDoesNotThrow(() -> {
            MetaFile base = metaDir.getBaseFile("safe.txt", true);
            MetaFile tmp = metaDir.getTmpFile("safe.txt", true);
            assertFalse(base == null || tmp == null);
        });
    }
}

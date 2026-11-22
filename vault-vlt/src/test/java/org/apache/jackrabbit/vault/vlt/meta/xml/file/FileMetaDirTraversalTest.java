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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Reproduces GH-13's {@code base}/{@code tmp} sink: {@code FileMetaDir.getBaseFile}/
 * {@code getTmpFile} build {@code new File(baseDir|tmpDir, name)} directly, so a traversal
 * name read from a poisoned {@code entries.xml} (e.g. {@code base/../../x}) would write or
 * read outside the meta directory. This is the one {@code MetaDirectory} implementation
 * where the sink is a real file system path rather than a zip entry name.
 */
class FileMetaDirTraversalTest {

    @TempDir
    File tempDir;

    private FileMetaDir metaDir;

    @BeforeEach
    void setUp() throws IOException {
        File dir = new File(tempDir, ".vlt");
        metaDir = new FileMetaDir(dir);
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
    void traversalNameNeverCreatesFileOutsideMetaDir() {
        File escapeTarget = new File(tempDir, "evil.txt");
        assertThrows(IOException.class, () -> metaDir.getBaseFile("../evil.txt", true));
        assertFalse(escapeTarget.exists(), "traversal name must not create a file outside the meta directory");
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

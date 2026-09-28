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

import static org.junit.Assert.*;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** 
 * Most tests in {@link ArchiveTest} are also applicable to {@link ZipNioArchive}. 
 * This class contains additional tests specific to {@link ZipNioArchive}.
 */
public class ZipNioArchiveTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void testDumpUnclosedArchivesClosesTmpFile() throws IOException, InterruptedException, URISyntaxException {
        Path tmpFile = createTempPackage();
        new ZipNioArchive(tmpFile, true);
        System.gc();
        Thread.sleep(100);
        // Now dump unclosed archives
        assertTrue("Couldn't find unclosed archives", AbstractArchive.dumpUnclosedArchives());
        assertFalse("Temp file should have been deleted but still exists", Files.exists(tmpFile));
    }

    @Test
    public void testDumpUnclosedArchivesClosesTmpFileAfterOpen() throws IOException, InterruptedException, URISyntaxException {
        Path tmpFile = createTempPackage();
        new ZipNioArchive(tmpFile, true).open(true);
        System.gc();
        Thread.sleep(100);
        // Now dump unclosed archives
        assertTrue("Couldn't find unclosed archives", AbstractArchive.dumpUnclosedArchives());
        assertFalse("Temp file should have been deleted but still exists", Files.exists(tmpFile));
    }

    private Path createTempPackage() throws URISyntaxException, IOException {
        Path zipFile = Paths.get(ZipArchiveCloseTest.class
                .getResource("/test-packages/atomic-counter-test.zip")
                .toURI());
        // copy to tmpFile
        Path tmpFile = tempFolder.newFile().toPath();
        Files.copy(zipFile, tmpFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return tmpFile;
    }

}

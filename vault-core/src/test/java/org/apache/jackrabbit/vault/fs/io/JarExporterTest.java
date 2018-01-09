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

import static java.util.zip.Deflater.BEST_COMPRESSION;
import static java.util.zip.Deflater.BEST_SPEED;
import static java.util.zip.Deflater.NO_COMPRESSION;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.zip.ZipException;

import javax.jcr.RepositoryException;

import org.apache.jackrabbit.vault.fs.api.AccessType;
import org.apache.jackrabbit.vault.fs.api.Artifact;
import org.apache.jackrabbit.vault.fs.api.SerializationType;
import org.apache.jackrabbit.vault.fs.api.VaultFile;
import org.junit.Test;

public class JarExporterTest {

    /**
     * This test verifies that writing entries that can be compressed together with entries that are already compressed according to
     * {@link org.apache.jackrabbit.vault.fs.impl.io.CompressionUtil} to the same file does result in a readable jar.
     * <p/>
     * There are certain environments that don't support changing the compression level for individual entries due to defects in the jdk
     * and breaking changes made in recent zlib versions.
     *
     * @link https://issues.apache.org/jira/browse/JCRVLT-257
     * @link https://github.com/madler/zlib/issues/305
     */
    @Test
    public void testEntriesWithSuppressedCompression() throws RepositoryException, IOException {
        Mocks m = new Mocks("org/apache/jackrabbit/vault/fs/io/JarExporter/testEntriesWithSuppressedCompression");
        for (int level : new int[] { NO_COMPRESSION, BEST_COMPRESSION, BEST_SPEED }) {
            File target = File.createTempFile("testEntriesWithSuppressedCompression", ".zip", null);
            ZipStreamArchive archive = null;
            try {
                JarExporter exporter = new JarExporter(target, level);
                exporter.open();
                exporter.writeFile(m.mockFile(".content.xml", "application/xml"), null);
                exporter.writeFile(m.mockFile("content/.content.xml", "application/xml"), null);
                exporter.writeFile(m.mockFile("content/dam/.content.xml", "application/xml"), null);
                // export a file that according to org.apache.jackrabbit.vault.fs.impl.io.CompressionUtil should not be compressed
                exporter.writeFile(m.mockFile("content/dam/asf_logo.png", "image/png"), null);
                exporter.close();
                // now read the zip file
                archive = new ZipStreamArchive(new FileInputStream(target));
                archive.open(false); // this will throw, when the zip file is corrupt
                // 8 entries including root and jcr_root
                assertEquals("Wrong entry count for level " + level, 8, countEntries(archive.getRoot()));
            } catch (ZipException ex) {
                throw new AssertionError("Zip failed for level " + level, ex);
            } finally {
                if (archive != null) {
                    archive.close();
                }
                target.delete();
            }
        }
    }

    private int countEntries(Archive.Entry entry) {
        int c = 1;
        for (Archive.Entry child : entry.getChildren()) {
            c += countEntries(child);
        }
        return c;
    }

    private class Mocks {

        private final String basePath;

        Mocks(String basePath) {
            this.basePath = basePath;
        }

        private VaultFile mockFile(String relPath, String contentType) throws RepositoryException, IOException {
            InputStream in = this.getClass().getClassLoader().getResourceAsStream(basePath + '/' + relPath);
            Artifact a = mockArtifact(in, contentType);
            VaultFile vaultFile = mock(VaultFile.class);
            when(vaultFile.getPath()).thenReturn("/" + relPath);
            when(vaultFile.getArtifact()).thenReturn(a);

            return vaultFile;
        }

        private Artifact mockArtifact(InputStream in, String contentType) throws RepositoryException, IOException {
            Artifact a = mock(Artifact.class);
            when(a.getSerializationType()).thenReturn(SerializationType.GENERIC);
            when(a.getContentType()).thenReturn(contentType);
            when(a.getLastModified()).thenReturn(new Date().getTime());
            when(a.getPreferredAccess()).thenReturn(AccessType.STREAM);
            when(a.getInputStream()).thenReturn(in);

            return a;
        }
    }
}

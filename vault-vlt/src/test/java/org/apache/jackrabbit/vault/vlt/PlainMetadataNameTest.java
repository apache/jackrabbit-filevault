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
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import javax.jcr.Credentials;

import org.apache.jackrabbit.vault.fs.api.RepositoryAddress;
import org.apache.jackrabbit.vault.util.RepositoryProvider;
import org.apache.jackrabbit.vault.vlt.meta.MetaDirectory;
import org.apache.jackrabbit.vault.vlt.meta.xml.XmlEntries;
import org.apache.jackrabbit.vault.vlt.meta.xml.file.FileMetaDir;
import org.apache.jackrabbit.vault.vlt.meta.xml.zip.ZipMetaDir;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Tests valid names after {@link FileSystemNameValidator} metadata validation.
 */
public class PlainMetadataNameTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    /**
     * Preserves ordinary entry names and optional conflict names.
     * @throws Exception if XML parsing fails
     */
    @Test
    public void xmlAcceptsPlainAndAbsentInfoNames() throws Exception {
        String xml = "<entries path=\"/content\"><entry name=\"safe.txt\" rp=\"\" ap=\"/content\">"
                + "<base/><mine name=\"safe.txt.mine\"/><theirs name=\"safe.txt.theirs\"/>"
                + "</entry></entries>";
        XmlEntries entries = XmlEntries.load(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        Assert.assertTrue(entries.hasEntry("safe.txt"));
    }

    /**
     * Keeps valid metadata files usable in both storage implementations.
     * @throws Exception if metadata creation or cleanup fails
     */
    @Test
    public void metadataAcceptsPlainNames() throws Exception {
        MetaDirectory[] directories = {
            new FileMetaDir(new File(temporaryFolder.getRoot(), "file-meta")),
            new ZipMetaDir(new File(temporaryFolder.getRoot(), "zip-meta"))
        };
        for (MetaDirectory directory : directories) {
            try {
                directory.create("/content");
                Assert.assertNotNull(directory.getBaseFile("safe.txt", true));
                Assert.assertNotNull(directory.getTmpFile("safe.txt", true));
                Assert.assertNull(directory.getBaseFile("missing.txt", false));
                Assert.assertNull(directory.getTmpFile("missing.txt", false));
            } finally {
                directory.close();
            }
        }
    }

    /**
     * Keeps valid working-copy file construction usable.
     * @throws Exception if working-copy initialization or cleanup fails
     */
    @Test
    public void constructorAcceptsPlainName() throws Exception {
        File workingCopy = temporaryFolder.newFolder("working-copy");
        File root = new File(workingCopy, "jcr_root");
        Assert.assertTrue(root.mkdir());
        Assert.assertTrue(new File(workingCopy, "META-INF/vault").mkdirs());
        PrintStream output = new PrintStream(new ByteArrayOutputStream());
        VltDirectory directory = null;
        try {
            VltContext context = new VltContext(workingCopy, workingCopy, new RepositoryProvider(),
                    new CredentialsStore() {
                        @Override
                        public Credentials getCredentials(RepositoryAddress address) {
                            return null;
                        }

                        @Override
                        public void storeCredentials(RepositoryAddress address, Credentials credentials) {
                        }
                    }, output);
            directory = new VltDirectory(context, root);
            directory.control("/content", "/content");
            Assert.assertEquals(new File(root, "safe.txt"), new VltFile(directory, "safe.txt", null).getFile());
        } finally {
            try {
                if (directory != null) {
                    directory.close();
                }
            } finally {
                output.close();
            }
        }
    }
}

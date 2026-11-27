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
package org.apache.jackrabbit.vault.vlt;

import javax.jcr.Credentials;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;

import org.apache.jackrabbit.vault.fs.api.RepositoryAddress;
import org.apache.jackrabbit.vault.util.RepositoryProvider;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Reproduces GH-13's constructor sink gate: even if a poisoned {@code entries.xml} somehow
 * reached {@link VltFile} construction without going through the parse-time chokepoint (see
 * {@code XmlEntriesTraversalTest}), the constructor itself must independently refuse a name
 * that is not a single safe path segment, since {@code new File(parent.getDirectory(), name)}
 * would otherwise be able to address a path outside the working copy's directory.
 */
public class VltFileConstructorTraversalTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private VltContext ctx;

    private VltDirectory dir;

    private File jcrRootDir;

    private PrintStream output;

    /**
     * Creates an isolated working copy with real control metadata.
     * @throws Exception if working-copy initialization fails
     */
    @Before
    public void setUp() throws Exception {
        File wcDir = temporaryFolder.newFolder("working-copy");
        // minimal export-root layout so VltContext resolves a valid MetaInf: jcr_root + META-INF/vault
        jcrRootDir = new File(wcDir, "jcr_root");
        jcrRootDir.mkdirs();
        new File(wcDir, "META-INF/vault").mkdirs();

        output = new PrintStream(new ByteArrayOutputStream());
        ctx = new VltContext(
                wcDir,
                wcDir,
                new RepositoryProvider(),
                new CredentialsStore() {
                    @Override
                    public Credentials getCredentials(RepositoryAddress mountpoint) {
                        return null;
                    }

                    @Override
                    public void storeCredentials(RepositoryAddress mountpoint, Credentials creds) {}
                },
                output);
        dir = new VltDirectory(ctx, jcrRootDir);
        dir.control("/content", "/content");
    }

    /**
     * Closes control metadata before the temporary working copy is removed.
     * @throws Exception if closing metadata fails
     */
    @After
    public void tearDown() throws Exception {
        try {
            if (dir != null) {
                dir.close();
            }
        } finally {
            if (output != null) {
                output.close();
            }
        }
    }

    /** Rejects names that could escape the working copy. */
    @Test(expected = VltException.class)
    public void constructorRejectsTraversalName() throws VltException {
        new VltFile(dir, "../../../../tmp/evil.txt", null);
    }

    /** Rejects both Unix and Windows path separators. */
    @Test
    public void constructorRejectsPathSeparatorName() throws VltException {
        for (String name : new String[] {"sub/evil.txt", "sub\\evil.txt"}) {
            try {
                new VltFile(dir, name, null);
                Assert.fail("Unsafe file name accepted: " + name);
            } catch (VltException expected) {
                Assert.assertTrue(expected.getMessage().contains(name));
            }
        }
    }

    /** Protects the working copy's own control file. */
    @Test(expected = VltException.class)
    public void constructorRejectsVltControlName() throws VltException {
        new VltFile(dir, ".vlt", null);
    }

    /** Keeps ordinary working-copy names usable. */
    @Test
    public void constructorAcceptsPlainFileName() throws VltException {
        new VltFile(dir, "regular-file.txt", null);
    }
}

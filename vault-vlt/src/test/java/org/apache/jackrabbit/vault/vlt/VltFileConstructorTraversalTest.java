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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Reproduces GH-13's constructor sink gate: even if a poisoned {@code entries.xml} somehow
 * reached {@link VltFile} construction without going through the parse-time chokepoint (see
 * {@code XmlEntriesTraversalTest}), the constructor itself must independently refuse a name
 * that is not a single safe path segment, since {@code new File(parent.getDirectory(), name)}
 * would otherwise be able to address a path outside the working copy's directory.
 */
class VltFileConstructorTraversalTest {

    @TempDir
    File wcDir;

    private VltContext ctx;

    private VltDirectory dir;

    private File jcrRootDir;

    @BeforeEach
    void setUp() throws Exception {
        // minimal export-root layout so VltContext resolves a valid MetaInf: jcr_root + META-INF/vault
        jcrRootDir = new File(wcDir, "jcr_root");
        jcrRootDir.mkdirs();
        new File(wcDir, "META-INF/vault").mkdirs();

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
                new PrintStream(new ByteArrayOutputStream()),
                null);
        dir = new VltDirectory(ctx, jcrRootDir);
        dir.control("/content", "/content");
    }

    @Test
    void constructorRejectsTraversalName() {
        assertThrows(VltException.class, () -> new VltFile(dir, "../../../../tmp/evil.txt", null));
    }

    @Test
    void constructorRejectsPathSeparatorName() {
        assertThrows(VltException.class, () -> new VltFile(dir, "sub/evil.txt", null));
        assertThrows(VltException.class, () -> new VltFile(dir, "sub\\evil.txt", null));
    }

    @Test
    void constructorRejectsVltControlName() {
        assertThrows(VltException.class, () -> new VltFile(dir, ".vlt", null));
    }

    @Test
    void constructorAcceptsPlainFileName() {
        assertDoesNotThrow(() -> new VltFile(dir, "regular-file.txt", null));
    }
}

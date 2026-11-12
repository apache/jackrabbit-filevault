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
import java.nio.file.Files;
import java.util.Collection;
import java.util.Collections;

import org.apache.jackrabbit.vault.fs.api.Aggregate;
import org.apache.jackrabbit.vault.fs.api.Artifact;
import org.apache.jackrabbit.vault.fs.api.DumpContext;
import org.apache.jackrabbit.vault.fs.api.RepositoryAddress;
import org.apache.jackrabbit.vault.fs.api.VaultFile;
import org.apache.jackrabbit.vault.fs.api.VaultFileSystem;
import org.apache.jackrabbit.vault.util.RepositoryProvider;
import org.apache.jackrabbit.vault.vlt.actions.Update;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * A remote node named {@code .vlt} (or a case variant of it) must never be
 * checked out, since on disk it would land exactly on the working copy's own control zip
 * ({@link VltDirectory#META_DIR_NAME}), letting a remote content author overwrite vlt's local
 * version-control state.
 */
public class ReservedRemoteNameAttackTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private PrintStream output;

    private VltDirectory dir;

    private File jcrRootDir;

    private File controlZip;

    @Before
    public void setUp() throws Exception {
        File wcDir = temporaryFolder.newFolder("working-copy");
        // minimal export-root layout so VltContext resolves a valid MetaInf: jcr_root + META-INF/vault
        jcrRootDir = new File(wcDir, "jcr_root");
        jcrRootDir.mkdirs();
        new File(wcDir, "META-INF/vault").mkdirs();

        output = new PrintStream(new ByteArrayOutputStream());
        VltContext ctx = new VltContext(
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
        // put the directory under vlt control, as a real checkout would: this physically
        // creates the ".vlt" control zip inside jcrRootDir.
        dir.control("/content", "/content");

        controlZip = new File(jcrRootDir, VltDirectory.META_DIR_NAME);
    }

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

    /**
     * Attack: an "Update" action (i.e. {@code vlt up --force}) is run for a remote child literally
     * named {@code .vlt}. Before the fix, this reached {@link Update#run} which built a
     * {@code VltFile} pointing straight at the working copy's control zip and overwrote it.
     */
    @Test
    public void applyWithRemoteSkipsNodeNamedDotVlt() throws Exception {
        assertAttackIsRejected(".vlt");
    }

    /**
     * Same attack using a case variant, relevant on case-insensitive filesystems (macOS,
     * Windows) where {@code .VLT} resolves to the very same file on disk as {@code .vlt}.
     */
    @Test
    public void applyWithRemoteSkipsNodeNamedWithDifferentCase() throws Exception {
        assertAttackIsRejected(".VLT");
    }

    @Test
    public void applyWithRemoteSkipsWindowsControlNameAliases() throws Exception {
        assertAttackIsRejected(".vlt.");
        assertAttackIsRejected(".vlt ");
        assertAttackIsRejected(".VLT.. ");
    }

    private void assertAttackIsRejected(String attackerNodeName) throws Exception {
        byte[] before = Files.readAllBytes(controlZip.toPath());

        Update update = new Update(jcrRootDir, Collections.<File>emptyList(), true);
        update.setForce(true);

        AttackerVaultFile remoteFile = new AttackerVaultFile(attackerNodeName);
        // must not throw and must not touch the control zip: the node is silently skipped,
        // the rest of the update is allowed to proceed.
        dir.applyWithRemote(update, null, remoteFile, true);

        byte[] after = Files.readAllBytes(controlZip.toPath());
        Assert.assertArrayEquals("the local vlt control zip must not be modified", before, after);
    }

    /**
     * Defense in depth: even if some future caller invoked {@link Update#run} directly,
     * bypassing the {@link VltDirectory#applyWithRemote} chokepoint, the action itself must
     * still refuse to check out a reserved name rather than silently overwriting the control zip.
     */
    @Test
    public void updateRefusesReservedNameEvenWhenChokepointIsBypassed() throws Exception {
        byte[] before = Files.readAllBytes(controlZip.toPath());

        Update update = new Update(jcrRootDir, Collections.<File>emptyList(), true);
        update.setForce(true);
        for (String name : new String[] {".vlt", ".VLT", ".vlt.", ".vlt ", ".VLT.. "}) {
            AttackerVaultFile remoteFile = new AttackerVaultFile(name);
            try {
                update.run(dir, null, remoteFile);
                Assert.fail("Update must reject the reserved name: " + name);
            } catch (VltException expected) {
                Assert.assertTrue(expected.getMessage().contains("reserved for the local vlt control state"));
            }
        }

        byte[] after = Files.readAllBytes(controlZip.toPath());
        Assert.assertArrayEquals("the local vlt control zip must not be modified", before, after);
    }

    @Test
    public void legitimateRemoteNameIsStillProcessed() throws Exception {
        Update update = new Update(jcrRootDir, Collections.<File>emptyList(), true);
        update.setForce(true);
        AttackerVaultFile remoteFile = new AttackerVaultFile("regular-file.txt");

        // a normal name must reach the attacker-controlled methods (i.e. must not be skipped),
        // proving the fix targets only the reserved name and not remote content in general.
        // the fake only implements getName(); reaching any other method proves the fix did
        // not accidentally suppress legitimate remote content too. A no-op return here would
        // mean the guard over-matched, so it must fail, not pass, silently.
        try {
            dir.applyWithRemote(update, null, remoteFile, true);
            Assert.fail("A legitimate remote name must reach remote content processing");
        } catch (UnsupportedOperationException expected) {
            Assert.assertEquals(AttackerVaultFile.class.getName(), expected.getStackTrace()[0].getClassName());
        }
    }

    @Test
    public void validatorStaysInSyncWithVltDirectoryMetaDirName() {
        Assert.assertTrue(FileSystemNameValidator.isVltControlName(VltDirectory.META_DIR_NAME));
    }

    /**
     * A {@link VaultFile} stand-in for an attacker-controlled remote node: only {@link #getName()}
     * is stubbed, since that is all the vulnerable code path is supposed to read before the fix's
     * reserved-name check short-circuits it. Every other method throws, so any invocation proves
     * the fix let the attack proceed further than it should have.
     */
    private static final class AttackerVaultFile implements VaultFile {

        private final String name;

        AttackerVaultFile(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getPath() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getRepoRelPath() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getAggregatePath() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Artifact getArtifact() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isDirectory() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isTransient() {
            throw new UnsupportedOperationException();
        }

        @Override
        public VaultFile getParent() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Aggregate getAggregate() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Aggregate getControllingAggregate() {
            throw new UnsupportedOperationException();
        }

        @Override
        public VaultFile getChild(String name) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Collection<? extends VaultFile> getChildren() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Collection<? extends VaultFile> getRelated() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean canRead() {
            throw new UnsupportedOperationException();
        }

        @Override
        public long lastModified() {
            throw new UnsupportedOperationException();
        }

        @Override
        public long length() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getContentType() {
            throw new UnsupportedOperationException();
        }

        @Override
        public VaultFileSystem getFileSystem() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void invalidate() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void dump(DumpContext ctx, boolean isLast) {
            throw new UnsupportedOperationException();
        }
    }
}

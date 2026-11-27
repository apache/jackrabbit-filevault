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
package org.apache.jackrabbit.vault.sync.impl;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;
import javax.jcr.nodetype.NodeType;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileFilter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collection;

import org.apache.jackrabbit.commons.JcrUtils;
import org.apache.jackrabbit.core.TransientRepository;
import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.util.PlatformNameFormat;
import org.junit.After;
import org.junit.Before;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Tests {@link TreeSync} with existing checkouts whose filenames predate reserved-suffix escaping. */
@RunWith(Parameterized.class)
public class TreeSyncReservedNamesTest {

    private static final String REPOSITORY_DATA = "repository bytes";

    private static final String LOCAL_DATA = "local bytes";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Parameterized.Parameter
    public SyncMode mode;

    private TransientRepository repository;

    private Session admin;

    /**
     * Creates an isolated repository and checkout for each test.
     * @throws Exception if repository initialization fails
     */
    @Before
    public void setUp() throws Exception {
        repository = new TransientRepository(temporaryFolder.newFolder("repository"));
        admin = repository.login(new SimpleCredentials("admin", "admin".toCharArray()));
    }

    /** Exercises updates in both synchronization directions. */
    @Parameterized.Parameters(name = "mode={0}")
    public static Collection<Object[]> modes() {
        return Arrays.asList(new Object[][] {{SyncMode.JCR2FS}, {SyncMode.FS2JCR}});
    }

    /** Closes the isolated repository before its temporary directory is removed. */
    @After
    public void removeContent() throws Exception {
        try {
            if (admin != null) {
                admin.logout();
            }
        } finally {
            if (repository != null) {
                repository.shutdown();
            }
        }
    }

    /** Full synchronization preserves legacy disk names and updates their existing repository peers. */
    @Test
    public void fullSyncUpdatesLegacyNames() throws Exception {
        Node root = admin.getRootNode().addNode("tmp", NodeType.NT_FOLDER);
        File directory = temporaryFolder.newFolder();
        String[] names = {"foo.binary", "foo.dir", ".content.xml", "ordinary.txt"};
        for (String name : names) {
            putFile(root, name);
            Files.write(new File(directory, name).toPath(), LOCAL_DATA.getBytes(StandardCharsets.UTF_8));
        }
        admin.save();
        treeSync().sync(root, directory);
        admin.save();
        for (String name : names) {
            assertSynchronized(name, new File(directory, name));
            if (!name.equals(PlatformNameFormat.getPlatformName(name))) {
                Assert.assertFalse(new File(directory, PlatformNameFormat.getPlatformName(name)).exists());
            }
        }
    }

    /** Event-driven single-file synchronization uses the supplied legacy disk path. */
    @Test
    public void singleSyncUpdatesLegacyName() throws Exception {
        Node root = admin.getRootNode().addNode("tmp", NodeType.NT_FOLDER);
        Node node = putFile(root, "foo.binary");
        admin.save();
        File file = temporaryFolder.newFile("foo.binary");
        Files.write(file.toPath(), LOCAL_DATA.getBytes(StandardCharsets.UTF_8));
        treeSync().syncSingle(root, node, file, false);
        admin.save();
        assertSynchronized("foo.binary", file);
        Assert.assertFalse(new File(file.getParentFile(), "foo%2ebinary").exists());
    }

    /** New escaped names continue to update a single repository file. */
    @Test
    public void fullSyncUpdatesEscapedName() throws Exception {
        Node root = admin.getRootNode().addNode("tmp", NodeType.NT_FOLDER);
        putFile(root, "foo.binary");
        admin.save();
        File directory = temporaryFolder.newFolder();
        File file = new File(directory, "foo%2ebinary");
        Files.write(file.toPath(), LOCAL_DATA.getBytes(StandardCharsets.UTF_8));
        treeSync().sync(root, directory);
        admin.save();
        assertSynchronized("foo.binary", file);
        Assert.assertFalse(new File(directory, "foo.binary").exists());
    }

    /** When both spellings exist, the canonical entry wins without deleting or importing the duplicate. */
    @Test
    public void duplicateLegacyNameDoesNotReplaceCanonicalEntry() throws Exception {
        Node root = admin.getRootNode().addNode("tmp", NodeType.NT_FOLDER);
        putFile(root, "foo.binary");
        admin.save();
        File directory = temporaryFolder.newFolder();
        File canonical = new File(directory, "foo%2ebinary");
        File legacy = new File(directory, "foo.binary");
        Files.write(canonical.toPath(), LOCAL_DATA.getBytes(StandardCharsets.UTF_8));
        Files.write(legacy.toPath(), "duplicate bytes".getBytes(StandardCharsets.UTF_8));
        treeSync().sync(root, directory);
        admin.save();
        assertSynchronized("foo.binary", canonical);
        Assert.assertEquals("duplicate bytes", new String(Files.readAllBytes(legacy.toPath()), StandardCharsets.UTF_8));
    }

    private Node putFile(Node root, String name) throws Exception {
        return JcrUtils.putFile(
                root, name, "text/plain", new ByteArrayInputStream(REPOSITORY_DATA.getBytes(StandardCharsets.UTF_8)));
    }

    private TreeSync treeSync() throws Exception {
        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        filter.add(new PathFilterSet("/tmp"));
        TreeSync tree = new TreeSync(new SyncLog(temporaryFolder.newFile()), new FileFilter() {
            @Override
            public boolean accept(File file) {
                return true;
            }
        }, filter);
        tree.setSyncMode(mode);
        return tree;
    }

    private void assertSynchronized(String name, File file) throws Exception {
        String expected = mode == SyncMode.JCR2FS ? REPOSITORY_DATA : LOCAL_DATA;
        Assert.assertEquals(expected, new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
        assertProperty("/tmp/" + name + "/jcr:content/jcr:data", expected);
    }

    private void assertProperty(String path, String value) throws RepositoryException {
        Assert.assertEquals(
                path + " should contain " + value,
                value,
                admin.getProperty(path).getString());
    }
}

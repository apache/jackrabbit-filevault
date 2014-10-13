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

package org.apache.jackrabbit.vault.packaging.integration;

import java.io.IOException;

import javax.jcr.Node;
import javax.jcr.Repository;
import javax.jcr.RepositoryException;

import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.fs.config.ConfigurationException;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.fs.io.ImportOptions;
import org.apache.jackrabbit.vault.fs.io.Importer;
import org.apache.jackrabbit.vault.fs.io.JcrArchive;
import org.apache.jackrabbit.vault.fs.io.ZipArchive;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * <code>ImportTests</code>...
 */
public class ImportTests extends IntegrationTestBase {

    public static final String TEST_ROOT = "/testroot";

    public static final String ARCHIVE_ROOT = "/archiveroot";

    @Before
    public void init() {
        clean(TEST_ROOT);
        clean(ARCHIVE_ROOT);
    }

    @Test
    public void testImport() throws IOException, RepositoryException, ConfigurationException {
        ZipArchive archive = new ZipArchive(getTempFile("testpackages/tmp.zip"));
        archive.open(true);
        Node rootNode = admin.getRootNode();
        ImportOptions opts = getDefaultOptions();
        Importer importer = new Importer(opts);
        importer.run(archive, rootNode);

        assertNodeExists("/tmp/foo/bar/tobi");
    }

    @Test
    public void testReimportLess() throws IOException, RepositoryException, ConfigurationException {
        ZipArchive archive = new ZipArchive(getTempFile("testpackages/tmp.zip"));
        archive.open(true);
        Node rootNode = admin.getRootNode();
        ImportOptions opts = getDefaultOptions();
        Importer importer = new Importer(opts);
        importer.run(archive, rootNode);

        assertNodeExists("/tmp/foo/bar/tobi");

        ZipArchive archive2 = new ZipArchive(getTempFile("testpackages/tmp_less.zip"));
        archive2.open(true);
        importer.run(archive2, rootNode);

        assertNodeMissing("/tmp/foo/bar/tobi");
    }

    @Test
    public void testFilteredImport() throws IOException, RepositoryException, ConfigurationException {
        ZipArchive archive = new ZipArchive(getTempFile("testpackages/filtered_package.zip"));
        archive.open(true);
        Node rootNode = admin.getRootNode();
        ImportOptions opts = getDefaultOptions();

        Importer importer = new Importer(opts);
        importer.run(archive, rootNode);

        assertNodeExists("/tmp");
        assertNodeExists("/tmp/foo");
        assertNodeExists("/tmp/foo/bar");
        assertNodeExists("/tmp/foo/bar/tobi");
        assertNodeMissing("/tmp/foo/bar/tom");
    }

    @Test
    public void testUnFilteredImport() throws IOException, RepositoryException, ConfigurationException {
        ZipArchive archive = new ZipArchive(getTempFile("testpackages/unfiltered_package.zip"));
        archive.open(true);
        Node rootNode = admin.getRootNode();
        ImportOptions opts = getDefaultOptions();

        Importer importer = new Importer(opts);
        importer.run(archive, rootNode);

        assertNodeExists("/tmp");
        assertNodeExists("/tmp/foo");
        assertNodeExists("/tmp/foo/bar");
        assertNodeExists("/tmp/foo/bar/tobi");
        assertNodeExists("/tmp/foo/bar/tom");
    }

    @Test
    public void testRelativeImport() throws IOException, RepositoryException, ConfigurationException {
        ZipArchive archive = new ZipArchive(getTempFile("testpackages/tmp.zip"));

        admin.getRootNode().addNode(TEST_ROOT.substring(1, TEST_ROOT.length()));
        admin.save();

        archive.open(true);
        Node rootNode = admin.getNode(TEST_ROOT);
        ImportOptions opts = getDefaultOptions();
        // manually creating filterPaths with correct coverage
        WorkspaceFilter filter = archive.getMetaInf().getFilter();
        for (PathFilterSet pathFilterSet : filter.getFilterSets()) {
            pathFilterSet.setRoot(TEST_ROOT + pathFilterSet.getRoot());
        }
        opts.setFilter(filter);
        Importer importer = new Importer(opts);
        importer.run(archive, rootNode);

        assertNodeExists(TEST_ROOT + "/tmp/foo/bar/tobi");
    }

    /**
     * Imports an empty package with a filter "/testnode" relative to "/testnode". Since this is a relative import,
     * the "/testnode" would map to "/testnode/testnode". So the import should not remove "/testnode".
     */
    @Test
    public void testRelativeEmptyImport() throws IOException, RepositoryException, ConfigurationException {
        ZipArchive archive = new ZipArchive(getTempFile("testpackages/empty_testnode.zip"));

        admin.getRootNode().addNode(TEST_ROOT.substring(1, TEST_ROOT.length()));
        admin.save();

        archive.open(true);
        Node rootNode = admin.getNode(TEST_ROOT);
        ImportOptions opts = getDefaultOptions();
        Importer importer = new Importer(opts);
        importer.run(archive, rootNode);

        assertNodeExists(TEST_ROOT);
    }

    /**
     * Creates an jcr archive at /archiveroot mapped to /testroot and imports it.
     */
    @Test
    public void testJcrArchiveImport() throws IOException, RepositoryException, ConfigurationException {
        // create Jcr Archive
        Node archiveNode = admin.getRootNode().addNode(ARCHIVE_ROOT.substring(1, ARCHIVE_ROOT.length()));
        admin.save();
        createNodes(archiveNode, 2, 4);
        admin.save();
        assertNodeExists(ARCHIVE_ROOT + "/n3/n3/n3");
        JcrArchive archive = new JcrArchive(archiveNode, TEST_ROOT);

        Node testRoot = admin.getRootNode().addNode(TEST_ROOT.substring(1, TEST_ROOT.length()));
        testRoot.addNode("dummy", "nt:folder");
        admin.save();

        archive.open(true);
        Node rootNode = admin.getNode(TEST_ROOT);
        ImportOptions opts = getDefaultOptions();
        //opts.setListener(new DefaultProgressListener());
        Importer importer = new Importer(opts);
        importer.run(archive, rootNode);
        admin.save();

        assertNodeExists(TEST_ROOT + "/n3/n3/n3");
        assertNodeMissing(TEST_ROOT + "dummy");
    }

    @Test
    public void testConcurrentModificationHandling() throws IOException, RepositoryException, PackageException, ConfigurationException {
        ZipArchive archive = new ZipArchive(getTempFile("testpackages/tags.zip"));
        archive.open(true);
        Node rootNode = admin.getRootNode();
        ImportOptions opts = getDefaultOptions();
        opts.setAutoSaveThreshold(7);
        Importer importer = new Importer(opts);
        importer.setDebugFailAfterSave(2);
        importer.run(archive, rootNode);
        admin.save();

        // count nodes
        assertNodeExists("/etc/tags");
        Node tags = admin.getNode("/etc/tags");
        int numNodes = countNodes(tags);
        assertEquals("Number of tags installed", 487, numNodes);

    }

    @Test
    public void testSNSImport() throws IOException, RepositoryException, ConfigurationException {
        ZipArchive archive = new ZipArchive(getTempFile("testpackages/test_sns.zip"));
        archive.open(true);
        Node rootNode = admin.getRootNode();
        ImportOptions opts = getDefaultOptions();
        Importer importer = new Importer(opts);
        importer.run(archive, rootNode);

        assertNodeExists("/tmp/testroot");
        assertNodeExists("/tmp/testroot/foo");
        assertProperty("/tmp/testroot/foo/name", "foo1");

        // only check for SNS nodes if SNS supported
        if (admin.getRepository().getDescriptorValue(Repository.NODE_TYPE_MANAGEMENT_SAME_NAME_SIBLINGS_SUPPORTED).getBoolean()) {
            assertNodeExists("/tmp/testroot/foo[2]");
            assertNodeExists("/tmp/testroot/foo[3]");
            assertProperty("/tmp/testroot/foo[2]/name", "foo2");
            assertProperty("/tmp/testroot/foo[3]/name", "foo3");
        } else {
            // otherwise nodes must not exist
            assertNodeMissing("/tmp/testroot/foo[2]");
            assertNodeMissing("/tmp/testroot/foo[3]");
        }

    }


    @Test
    public void testSubArchiveExtract() throws IOException, RepositoryException, ConfigurationException {
        ZipArchive archive = new ZipArchive(getTempFile("testpackages/tmp_with_thumbnail.zip"));
        archive.open(true);
        Node rootNode = admin.getRootNode();
        Node tmpNode = rootNode.addNode("tmp");
        Node fileNode = tmpNode.addNode("package.zip", "nt:file");
        Node contentNode = fileNode.addNode("jcr:content", "nt:resource");
        contentNode.setProperty("jcr:data", "");
        contentNode.setProperty("jcr:lastModified", 0);
        contentNode.addMixin("vlt:Package");
        Node defNode = contentNode.addNode("vlt:definition", "vlt:PackageDefinition");

        ImportOptions opts = getDefaultOptions();
        Archive subArchive =  archive.getSubArchive("META-INF/vault/definition", true);

        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        filter.add(new PathFilterSet(defNode.getPath()));

        Importer importer = new Importer(opts);
        importer.getOptions().setAutoSaveThreshold(Integer.MAX_VALUE);
        importer.getOptions().setFilter(filter);
        importer.run(subArchive, defNode);
        admin.save();

        assertFalse("Importer must not have any errors", importer.hasErrors());
        assertNodeExists("/tmp/package.zip/jcr:content/vlt:definition/thumbnail.png");
    }


}
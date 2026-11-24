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
package org.apache.jackrabbit.vault.packaging.integration;

import javax.jcr.Binary;
import javax.jcr.Node;
import javax.jcr.nodetype.NodeType;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collection;
import java.util.Properties;
import java.util.zip.ZipFile;

import org.apache.jackrabbit.commons.JcrUtils;
import org.apache.jackrabbit.vault.fs.Mounter;
import org.apache.jackrabbit.vault.fs.api.ImportMode;
import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.api.RepositoryAddress;
import org.apache.jackrabbit.vault.fs.api.VaultFileSystem;
import org.apache.jackrabbit.vault.fs.api.VaultFsTransaction;
import org.apache.jackrabbit.vault.fs.config.DefaultMetaInf;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.fs.io.ImportOptions;
import org.apache.jackrabbit.vault.packaging.ExportOptions;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.apache.jackrabbit.vault.util.FileInputSource;
import org.apache.jackrabbit.vault.util.PlatformNameFormat;
import org.junit.After;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Tests serialization-marker collisions in {@link PlatformNameFormat} through real package round trips. */
@RunWith(Parameterized.class)
public class ReservedArtifactNamesTest extends IntegrationTestBase {

    private static final String DATA = "plain file bytes";

    private static final String DOCVIEW =
            "<jcr:root xmlns:jcr=\"http://www.jcp.org/jcr/1.0\" jcr:primaryType=\"nt:unstructured\"/>";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Parameterized.Parameter
    public ImportMode mode;

    /** Runs every round trip with all supported import modes. */
    @Parameterized.Parameters(name = "mode={0}")
    public static Collection<Object[]> modes() {
        return Arrays.asList(new Object[][] {
            {ImportMode.REPLACE},
            {ImportMode.UPDATE},
            {ImportMode.MERGE},
        });
    }

    /** Removes the test subtree before the inherited session cleanup. */
    @After
    public void removeContent() throws Exception {
        if (admin.nodeExists("/tmp")) {
            admin.getNode("/tmp").remove();
            admin.save();
        }
    }

    /** Literal directory suffixes must never graft children onto their sibling names. */
    @Test
    public void reservedDirectoriesRoundTrip() throws Exception {
        Node root = admin.getRootNode().addNode("tmp", NodeType.NT_FOLDER);
        root.addNode("foo", NodeType.NT_FOLDER);
        String[] names = {"foo.dir", "foo.dir.dir", "foo%2edir", "foo.DIR", ".dir"};
        for (String name : names) {
            Node reserved = root.addNode(name, NodeType.NT_FOLDER);
            JcrUtils.putFile(reserved, "child.txt", "text/plain", bytes());
        }
        admin.save();
        roundTrip(
                "jcr_root/tmp/foo%2edir/child.txt",
                "jcr_root/tmp/foo.dir%2edir/child.txt",
                "jcr_root/tmp/foo%252edir/child.txt",
                "jcr_root/tmp/foo.DIR/child.txt",
                "jcr_root/tmp/%2edir/child.txt");
        Assert.assertTrue(admin.nodeExists("/tmp/foo"));
        for (String name : names) {
            assertProperty("/tmp/" + name + "/child.txt/jcr:content/jcr:data", DATA);
        }
        Assert.assertFalse(admin.nodeExists("/tmp/foo/child.txt"));
    }

    /** Literal file markers and escaped-percent lookalikes retain their names and bytes. */
    @Test
    public void reservedFilesRoundTrip() throws Exception {
        String[] names = {
            "foo.binary",
            ".binary",
            ".content.xml",
            "foo.dir",
            "foo%2ebinary",
            "%2econtent.xml",
            "foo.DIR",
            "foo.BINARY",
            "ordinary.xml"
        };
        Node root = admin.getRootNode().addNode("tmp", NodeType.NT_UNSTRUCTURED);
        root.setProperty("marker", "parent");
        root.setProperty("foo", "existing property");
        for (String name : names) {
            JcrUtils.putFile(root, name, "text/plain", bytes());
        }
        admin.save();
        // a child node literally named ".content.xml" always escapes into the .dir +
        // binary-sidecar shape (see FileAggregator#needsContentEscape), never a flat file
        File archive = exportPackage(
                "jcr_root/tmp/foo%2ebinary",
                "jcr_root/tmp/%2econtent.xml.dir/.content.xml",
                "jcr_root/tmp/%2econtent.xml.dir/_jcr_content/_jcr_data.binary",
                "jcr_root/tmp/.content.xml");
        try (ZipFile zip = new ZipFile(archive)) {
            Assert.assertNull(zip.getEntry("jcr_root/tmp/%2econtent.xml"));
        }
        admin.getNode("/tmp").remove();
        admin.save();
        importPackage(archive);
        for (String name : names) {
            assertProperty("/tmp/" + name + "/jcr:content/jcr:data", DATA);
        }
        assertProperty("/tmp/marker", "parent");
        assertProperty("/tmp/foo", "existing property");
    }

    /** A literal metadata filename stays a file even when its bytes resemble document-view XML. */
    @Test
    public void contentXmlWithDocViewBytesRoundTrips() throws Exception {
        Node root = admin.getRootNode().addNode("tmp", NodeType.NT_UNSTRUCTURED);
        root.setProperty("marker", "parent");
        JcrUtils.putFile(
                root, ".content.xml", "text/xml", new ByteArrayInputStream(DOCVIEW.getBytes(StandardCharsets.UTF_8)));
        admin.save();
        // escapes the same way regardless of content, since the name alone always collides
        // with the containing folder's own docview serialization
        File archive = exportPackage(
                "jcr_root/tmp/%2econtent.xml.dir/.content.xml",
                "jcr_root/tmp/%2econtent.xml.dir/_jcr_content/_jcr_data.binary", "jcr_root/tmp/.content.xml");
        try (ZipFile zip = new ZipFile(archive)) {
            Assert.assertNull(zip.getEntry("jcr_root/tmp/%2econtent.xml"));
        }
        admin.getNode("/tmp").remove();
        admin.save();
        importPackage(archive);
        assertProperty("/tmp/.content.xml/jcr:content/jcr:data", DOCVIEW);
        assertProperty("/tmp/marker", "parent");
        Assert.assertFalse(admin.nodeExists("/tmp/.content"));
    }

    /** Existing reserved files and sibling properties follow each mode's replacement or retention rules. */
    @Test
    public void reservedFilesImportIntoExistingContent() throws Exception {
        Node root = admin.getRootNode().addNode("tmp", NodeType.NT_UNSTRUCTURED);
        root.setProperty("foo", "existing property");
        JcrUtils.putFile(root, "foo.binary", "application/octet-stream", bytes());
        JcrUtils.putFile(root, "ordinary.txt", "text/plain", bytes());
        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        for (String path : new String[] {"/tmp/foo.binary", "/tmp/ordinary.txt"}) {
            PathFilterSet paths = new PathFilterSet(path);
            paths.setImportMode(mode);
            filter.add(paths);
        }
        File archive = exportPackage(filter, "jcr_root/tmp/foo%2ebinary", "jcr_root/tmp/ordinary.txt");
        try (ZipFile zip = new ZipFile(archive)) {
            Assert.assertNull(zip.getEntry("jcr_root/tmp/foo%2ebinary.dir/.content.xml"));
        }
        JcrUtils.putFile(
                root,
                "foo.binary",
                "application/octet-stream",
                new ByteArrayInputStream("existing bytes".getBytes(StandardCharsets.UTF_8)));
        JcrUtils.putFile(
                root,
                "ordinary.txt",
                "text/plain",
                new ByteArrayInputStream("existing bytes".getBytes(StandardCharsets.UTF_8)));
        admin.save();
        importPackage(archive);
        boolean retainsExisting = mode == ImportMode.MERGE;
        assertProperty("/tmp/foo.binary/jcr:content/jcr:data", retainsExisting ? "existing bytes" : DATA);
        assertProperty("/tmp/ordinary.txt/jcr:content/jcr:data", retainsExisting ? "existing bytes" : DATA);
        assertProperty("/tmp/foo", "existing property");
        Assert.assertFalse(admin.nodeExists("/tmp/foo"));
    }

    /** A genuine .dir artifact and its .content.xml retain metadata on a reserved-name file. */
    @Test
    public void generatedDirectoryAndDocViewMarkersRoundTrip() throws Exception {
        Node root = admin.getRootNode().addNode("tmp", NodeType.NT_FOLDER);
        Node file = root.addNode("foo.dir", NodeType.NT_FILE);
        file.addMixin("vlt:FullCoverage");
        Node content = file.addNode(Node.JCR_CONTENT, NodeType.NT_UNSTRUCTURED);
        content.setProperty("jcr:mimeType", "text/plain");
        content.setProperty("custom", "metadata");
        Binary binary = admin.getValueFactory().createBinary(bytes());
        try {
            content.setProperty("jcr:data", binary);
            admin.save();
        } finally {
            binary.dispose();
        }
        roundTrip("jcr_root/tmp/foo%2edir", "jcr_root/tmp/foo%2edir.dir/.content.xml");
        assertProperty("/tmp/foo.dir/jcr:content/jcr:data", DATA);
        assertProperty("/tmp/foo.dir/jcr:content/custom", "metadata");
        Assert.assertFalse(admin.nodeExists("/tmp/foo"));
    }

    /** Genuine binary sidecars preserve property names that themselves resemble markers. */
    @Test
    public void generatedBinaryMarkersRoundTrip() throws Exception {
        Node root = admin.getRootNode().addNode("tmp", NodeType.NT_UNSTRUCTURED);
        String[] names = {"ordinary", "foo.binary", "foo.dir", ".content.xml"};
        Binary binary = admin.getValueFactory().createBinary(bytes());
        try {
            for (String name : names) {
                root.setProperty(name, binary);
            }
            admin.save();
        } finally {
            binary.dispose();
        }
        roundTrip(
                "jcr_root/tmp/ordinary.binary",
                "jcr_root/tmp/foo%2ebinary.binary",
                "jcr_root/tmp/foo%2edir.binary",
                "jcr_root/tmp/%2econtent.xml.binary");
        for (String name : names) {
            assertProperty("/tmp/" + name, DATA);
        }
    }

    /** Ordinary files, generated metadata, and legacy binary markers remain compatible. */
    @Test
    public void ordinaryArtifactsRoundTrip() throws Exception {
        Node root = admin.getRootNode().addNode("tmp", NodeType.NT_UNSTRUCTURED);
        root.setProperty("marker", "metadata");
        JcrUtils.putFile(root, "ordinary.txt", "text/plain", bytes());
        Binary binary = admin.getValueFactory().createBinary(bytes());
        try {
            root.setProperty("data", binary);
            admin.save();
        } finally {
            binary.dispose();
        }
        roundTrip("jcr_root/tmp/.content.xml", "jcr_root/tmp/ordinary.txt", "jcr_root/tmp/data.binary");
        assertProperty("/tmp/marker", "metadata");
        assertProperty("/tmp/ordinary.txt/jcr:content/jcr:data", DATA);
        assertProperty("/tmp/data", DATA);
    }

    /** An escaped .dir suffix is part of the CLI-created directory name. */
    @Test
    public void vaultTransactionsPreserveEscapedDirectoryName() throws Exception {
        admin.getRootNode().addNode("tmp", NodeType.NT_UNSTRUCTURED);
        admin.save();
        VaultFileSystem fs = mount();
        try {
            VaultFsTransaction transaction = fs.startTransaction();
            transaction.mkdir("/tmp/foo%2edir");
            transaction.commit();
            Assert.assertTrue(admin.nodeExists("/tmp/foo.dir"));
            Assert.assertFalse(admin.nodeExists("/tmp/foo"));
        } finally {
            fs.unmount();
        }
    }

    /** An escaped .binary suffix creates a CLI file instead of a parent property. */
    @Test
    public void vaultTransactionsPreserveEscapedBinaryFileName() throws Exception {
        addFileThroughTransaction("foo%2ebinary");
        assertProperty("/tmp/foo.binary/jcr:content/jcr:data", DATA);
        Assert.assertFalse(admin.propertyExists("/tmp/foo"));
    }

    /** An escaped .content.xml name creates a CLI file instead of parsing its bytes. */
    @Test
    public void vaultTransactionsPreserveEscapedContentXmlFileName() throws Exception {
        addFileThroughTransaction("%2econtent.xml");
        assertProperty("/tmp/.content.xml/jcr:content/jcr:data", DATA);
    }

    /** CLI imports retain DocView-shaped bytes in a literal metadata filename. */
    @Test
    public void vaultTransactionsPreserveContentXmlWithDocViewBytes() throws Exception {
        addFileThroughTransaction("%2econtent.xml", DOCVIEW);
        assertProperty("/tmp/.content.xml/jcr:content/jcr:data", DOCVIEW);
        Assert.assertFalse(admin.nodeExists("/tmp/.content"));
    }

    /** CLI transactions still accept genuine document-view and binary markers. */
    @Test
    public void vaultTransactionsPreserveGeneratedMarkers() throws Exception {
        admin.getRootNode().addNode("tmp", NodeType.NT_UNSTRUCTURED);
        admin.save();
        File docView = temporaryFolder.newFile("docview.xml");
        String xml =
                "<jcr:root xmlns:jcr=\"http://www.jcp.org/jcr/1.0\" jcr:primaryType=\"nt:unstructured\" marker=\"metadata\"/>";
        Files.write(docView.toPath(), xml.getBytes(StandardCharsets.UTF_8));
        File binary = temporaryFolder.newFile("binary.bin");
        Files.write(binary.toPath(), DATA.getBytes(StandardCharsets.UTF_8));
        VaultFileSystem fs = mount();
        try {
            VaultFsTransaction transaction = fs.startTransaction();
            transaction.add("/tmp/new/.content.xml", new FileInputSource(docView));
            transaction.add("/tmp/data.binary", new FileInputSource(binary));
            transaction.commit();
            assertProperty("/tmp/new/marker", "metadata");
            assertProperty("/tmp/data", DATA);
        } finally {
            fs.unmount();
        }
    }

    private VaultFileSystem mount() throws Exception {
        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        filter.add(new PathFilterSet("/tmp"));
        return Mounter.mount(null, filter, new RepositoryAddress("http://localhost/-/jcr:root"), "/", admin);
    }

    private void addFileThroughTransaction(String platformName) throws Exception {
        addFileThroughTransaction(platformName, DATA);
    }

    private void addFileThroughTransaction(String platformName, String data) throws Exception {
        admin.getRootNode().addNode("tmp", NodeType.NT_UNSTRUCTURED);
        admin.save();
        File source = temporaryFolder.newFile("file.txt");
        Files.write(source.toPath(), data.getBytes(StandardCharsets.UTF_8));
        VaultFileSystem fs = mount();
        try {
            VaultFsTransaction transaction = fs.startTransaction();
            transaction.add("/tmp/" + platformName, new FileInputSource(source));
            transaction.commit();
        } finally {
            fs.unmount();
        }
    }

    private ByteArrayInputStream bytes() {
        return new ByteArrayInputStream(DATA.getBytes(StandardCharsets.UTF_8));
    }

    private void roundTrip(String... entries) throws Exception {
        File archive = exportPackage(entries);
        admin.getNode("/tmp").remove();
        admin.save();
        importPackage(archive);
    }

    private File exportPackage(String... entries) throws Exception {
        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        PathFilterSet paths = new PathFilterSet("/tmp");
        paths.setImportMode(mode);
        filter.add(paths);
        return exportPackage(filter, entries);
    }

    private File exportPackage(DefaultWorkspaceFilter filter, String... entries) throws Exception {
        admin.save();
        File archive = temporaryFolder.newFile("package.zip");
        DefaultMetaInf meta = new DefaultMetaInf();
        meta.setFilter(filter);
        Properties properties = new Properties();
        properties.setProperty(VaultPackage.NAME_GROUP, "jackrabbit/test");
        properties.setProperty(VaultPackage.NAME_NAME, "reserved-artifact-names");
        meta.setProperties(properties);
        ExportOptions options = new ExportOptions();
        options.setMetaInf(meta);
        packMgr.assemble(admin, options, archive).close();
        try (ZipFile zip = new ZipFile(archive)) {
            for (String entry : entries) {
                Assert.assertNotNull(entry, zip.getEntry(entry));
            }
        }
        return archive;
    }

    private void importPackage(File archive) throws Exception {
        ImportOptions importOptions = getDefaultOptions();
        importOptions.setStrict(true);
        try (VaultPackage pkg = packMgr.open(archive)) {
            Assert.assertEquals(mode, pkg.getMetaInf().getFilter().getImportMode("/tmp/foo.binary"));
            pkg.extract(admin, importOptions);
        }
    }
}

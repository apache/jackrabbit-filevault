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

import javax.jcr.Node;
import javax.jcr.RepositoryException;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.io.IOUtils;
import org.apache.jackrabbit.commons.cnd.CndImporter;
import org.apache.jackrabbit.commons.cnd.ParseException;
import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.config.AbstractVaultFsConfig;
import org.apache.jackrabbit.vault.fs.config.ConfigurationException;
import org.apache.jackrabbit.vault.fs.config.DefaultMetaInf;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.fs.filter.DefaultPathFilter;
import org.apache.jackrabbit.vault.fs.io.ImportOptions;
import org.apache.jackrabbit.vault.packaging.ExportOptions;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class FolderArtifactHandlerIT extends IntegrationTestBase {

    @Rule
    public TemporaryFolder tmpFolder = new TemporaryFolder();

    /**
     * Aggregation config with an unrestricted generic aggregate ahead of the folder aggregate. It
     * inlines non-orderable folder ancestors into one document while a descendant folder with a
     * jcr:content child is serialized separately without a placeholder in the ancestor document.
     */
    private static final String NESTED_ANCESTOR_CONFIG = "<vaultfs version=\"1.1\">\n"
            + "  <aggregates>\n"
            + "    <aggregate type=\"filefolder\" title=\"File/Folder Aggregate\"/>\n"
            + "    <aggregate type=\"generic\" title=\"Catch-all Aggregate\"/>\n"
            + "    <aggregate type=\"full\" title=\"Full Coverage Aggregate\">\n"
            + "      <matches><include nodeType=\"rep:AccessControl\" respectSupertype=\"true\"/></matches>\n"
            + "    </aggregate>\n"
            + "    <aggregate type=\"generic\" title=\"Folder Aggregate\">\n"
            + "      <matches><include nodeType=\"nt:folder\" respectSupertype=\"true\"/></matches>\n"
            + "      <contains><exclude isNode=\"true\"/></contains>\n"
            + "    </aggregate>\n"
            + "    <aggregate type=\"generic\" title=\"Default Aggregator\" isDefault=\"true\">\n"
            + "      <matches/>\n"
            + "      <contains><exclude nodeType=\"nt:hierarchyNode\" respectSupertype=\"true\"/></contains>\n"
            + "    </aggregate>\n"
            + "  </aggregates>\n"
            + "  <handlers>\n"
            + "    <handler type=\"folder\"/>\n"
            + "    <handler type=\"file\"/>\n"
            + "    <handler type=\"nodetype\"/>\n"
            + "    <handler type=\"generic\"/>\n"
            + "  </handlers>\n"
            + "</vaultfs>\n";

    @Test
    public void testExportedSeparateDescendantWithoutPlaceholder()
            throws RepositoryException, IOException, PackageException, ParseException, ConfigurationException {
        if (!admin.getWorkspace().getNodeTypeManager().hasNodeType("sling:Folder")) {
            CndImporter.registerNodeTypes(
                    new StringReader("<sling='http://sling.apache.org/jcr/sling/1.0'>\n"
                            + "[sling:Folder] > nt:folder\n"
                            + "  - * (undefined) multiple\n"
                            + "  - * (undefined)\n"
                            + "  + * (nt:base) = sling:Folder version"),
                    admin);
        }
        Node selected = admin.getRootNode()
                .addNode("testroot", "sling:Folder")
                .addNode("a", "sling:Folder")
                .addNode("b", "sling:Folder")
                .addNode("content", "sling:Folder");
        selected.setProperty("value", "packaged");
        selected.addNode("jcr:content", "nt:unstructured").setProperty("title", "packaged");
        selected.addNode("independent", "sling:Folder").setProperty("value", "source");
        admin.save();

        String root = "/testroot/a/b/content";
        File pkgFile = assembleWithConfig(root);
        try (ZipFile zip = new ZipFile(pkgFile)) {
            ZipEntry ancestorDoc = zip.getEntry("jcr_root/testroot/.content.xml");
            assertNotNull("ancestor document expected", ancestorDoc);
            String ancestorXml;
            try (InputStream in = zip.getInputStream(ancestorDoc)) {
                ancestorXml = IOUtils.toString(in, StandardCharsets.UTF_8);
            }
            assertTrue("ancestor document must inline a: " + ancestorXml, ancestorXml.contains("<a"));
            assertTrue("ancestor document must inline b: " + ancestorXml, ancestorXml.contains("<b"));
            assertFalse("no placeholder expected: " + ancestorXml, ancestorXml.contains("<content"));
            assertNull(zip.getEntry("jcr_root/testroot/a/.content.xml"));
            assertNull(zip.getEntry("jcr_root/testroot/a/b/.content.xml"));
            assertNotNull(zip.getEntry("jcr_root/testroot/a/b/content/.content.xml"));
        }

        selected.setProperty("value", "changed");
        selected.getNode("jcr:content").setProperty("title", "changed");
        selected.getNode("jcr:content").addNode("added", "nt:unstructured");
        selected.getNode("independent").setProperty("value", "destination");
        selected.addNode("destinationOnly", "sling:Folder");
        admin.save();

        try (VaultPackage pkg = packMgr.open(pkgFile)) {
            ImportOptions opts = getDefaultOptions();
            TrackingListener listener = new TrackingListener(null);
            opts.setListener(listener);
            pkg.extract(admin, opts);

            assertEquals("U", listener.getActions().get(root));
            assertProperty(root + "/value", "packaged");
            assertProperty(root + "/jcr:content/title", "packaged");
            assertNodeMissing(root + "/jcr:content/added");
            assertProperty(root + "/independent/value", "destination");
            assertNodeExists(root + "/destinationOnly");
        }
    }

    private File assembleWithConfig(String root) throws IOException, RepositoryException, ConfigurationException {
        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        PathFilterSet set = new PathFilterSet(root);
        set.addInclude(new DefaultPathFilter(root));
        set.addInclude(new DefaultPathFilter(root + "/jcr:content(/.*)?"));
        filter.add(set);
        DefaultMetaInf inf = new DefaultMetaInf();
        inf.setFilter(filter);
        inf.setConfig(AbstractVaultFsConfig.load(
                new ByteArrayInputStream(NESTED_ANCESTOR_CONFIG.getBytes(StandardCharsets.UTF_8)), "config.xml"));
        Properties props = new Properties();
        props.setProperty(VaultPackage.NAME_GROUP, "jackrabbit/test");
        props.setProperty(VaultPackage.NAME_NAME, "nested-ancestor");
        inf.setProperties(props);
        ExportOptions opts = new ExportOptions();
        opts.setMetaInf(inf);
        File pkgFile = tmpFolder.newFile("nested-ancestor.zip");
        packMgr.assemble(admin, opts, pkgFile).close();
        return pkgFile;
    }

    @Test
    public void testSeparatelySerializedDescendantWithoutPlaceholder()
            throws RepositoryException, IOException, PackageException {
        Node ancestor = admin.getRootNode()
                .addNode("testroot", "nt:unstructured")
                .addNode("a", "nt:unstructured")
                .addNode("b", "nt:unstructured");
        Node content = ancestor.addNode("content", "nt:unstructured");
        content.addMixin("mix:referenceable");
        content.setProperty("value", "old");
        content.addNode("excluded", "nt:unstructured").addNode("sentinel", "nt:unstructured");
        content.addNode("obsolete", "nt:unstructured");
        ancestor.addNode("removed", "nt:unstructured");
        String contentId = content.getIdentifier();
        admin.save();

        try (VaultPackage vltPackage =
                extractVaultPackageStrict("/test-packages/separate-descendant-without-placeholder.zip")) {
            assertEquals(contentId, admin.getNode("/testroot/a/b/content").getIdentifier());
            assertNodeExists("/testroot/a/b/content/excluded/sentinel");
            assertProperty("/testroot/a/b/content/value", "new");
            assertNodeMissing("/testroot/a/b/content/obsolete");
            assertNodeMissing("/testroot/a/b/removed");
        }
    }

    /* JCRVLT-415 */
    @Test
    public void testModifyingContainedNodeNonNtFolderPrimaryType()
            throws RepositoryException, IOException, PackageException {
        // create node "/testroot/foo" with node type "nt:unstructured"
        Node rootNode = admin.getRootNode();
        Node testNode = rootNode.addNode("testroot", "nt:unstructured");
        Node fooNode = testNode.addNode("foo", "nt:unstructured");
        fooNode.setProperty("testProperty", "test");
        admin.save();
        try (VaultPackage vltPackage = extractVaultPackageStrict("/test-packages/folder-without-docview-element.zip")) {
            // make sure the primary type from "/test/foo" got overwritten!
            assertPropertyMissing("/testroot/foo/testProperty");
            assertNodeHasPrimaryType("/testroot/foo", "nt:folder");
        }
    }

    @Test
    public void testNotModifyingContainedNodeNtFolderPrimaryType()
            throws RepositoryException, IOException, PackageException {
        // create node "/testroot/foo" with node type "nt:unstructured"
        Node rootNode = admin.getRootNode();
        Node testNode = rootNode.addNode("testroot", "nt:unstructured");
        Node fooNode = testNode.addNode("foo", "nt:folder");
        String oldId = fooNode.getIdentifier();
        admin.save();
        try (VaultPackage vltPackage = extractVaultPackageStrict("/test-packages/folder-without-docview-element.zip")) {
            assertNodeHasPrimaryType("/testroot/foo", "nt:folder");
            assertPropertyMissing("/testroot/value");
            assertEquals(oldId, admin.getNode("/testroot/foo").getIdentifier());
        }
    }

    @Test
    public void testNotModifyingIntermediateNodePrimaryType()
            throws RepositoryException, IOException, PackageException {
        // create node "/var/foo" with node type "nt:unstructured"
        Node rootNode = admin.getRootNode();
        Node testNode = rootNode.addNode("var", "nt:unstructured");
        Node fooNode = testNode.addNode("foo", "nt:unstructured");
        assertNodeHasPrimaryType("/var/foo", "nt:unstructured");
        fooNode.setProperty("testProperty", "test");
        admin.save();
        try (VaultPackage vltPackage = extractVaultPackageStrict("/test-packages/folder-without-docview-element.zip")) {
            assertNodeHasPrimaryType("/var/foo", "nt:unstructured");
            assertProperty("/var/foo/testProperty", "test");
        }
    }

    @Test
    public void testCreatingIntermediateNodesWithDefaultType()
            throws RepositoryException, IOException, PackageException {
        // create node "/var/foo" with node type "nt:unstructured"
        Node rootNode = admin.getRootNode();
        rootNode.addNode("var", "nt:unstructured");
        admin.save();
        try (VaultPackage vltPackage = extractVaultPackageStrict("/test-packages/folder-without-docview-element.zip")) {
            assertNodeHasPrimaryType("/var/foo", "nt:unstructured");
        }
    }

    @Test
    public void testCreatingIntermediateNodesWithFallbackType()
            throws RepositoryException, IOException, PackageException {
        // create node "/var/foo" with node type "nt:unstructured"
        Node rootNode = admin.getRootNode();
        rootNode.addNode("var", "nt:folder");
        admin.save();
        assertNodeHasPrimaryType("/var", "nt:folder");
        try (VaultPackage vltPackage = extractVaultPackage("/test-packages/folder-without-docview-element.zip")) {
            assertNodeHasPrimaryType("/var", "nt:folder");
            assertNodeHasPrimaryType("/var/foo", "nt:folder");
        }
    }

    // https://issues.apache.org/jira/browse/JCRVLT-542
    @Test
    public void testRootTypeOnMerge() throws RepositoryException, IOException, PackageException {
        Node rootNode = admin.getRootNode();
        Node homeNode = rootNode.getNode("home");
        homeNode.addNode("groups", "rep:AuthorizableFolder");
        admin.save();
        assertNodeHasPrimaryType("/home", "rep:AuthorizableFolder");
        assertNodeHasPrimaryType("/home/groups", "rep:AuthorizableFolder");
        // /home/groups is being installed w/o any nodetype but filter is on mode=merge so no change expected
        try (VaultPackage vltPackage = extractVaultPackage("/test-packages/test_nodetype_on_merge.zip")) {
            assertNodeHasPrimaryType("/home", "rep:AuthorizableFolder");
            assertNodeHasPrimaryType("/home/groups", "rep:AuthorizableFolder");
        }
    }

    // https://issues.apache.org/jira/browse/JCRVLT-544
    @Test
    public void testFallbackToNtFolderForRestrictedDefaultNodeType()
            throws IOException, PackageException, RepositoryException, ParseException {
        try (Reader reader = new InputStreamReader(getStream("cqComponent.cnd"), StandardCharsets.US_ASCII)) {
            CndImporter.registerNodeTypes(reader, admin);
        }
        admin.getRootNode().addNode("testroot", "cq:Component"); // this has nt:base as default child node type
        try (VaultPackage vltPackage = extractVaultPackage("/test-packages/intermediate_folders.zip")) {
            assertNodeHasPrimaryType("/testroot/myfolder", "nt:folder");
        }
    }
}

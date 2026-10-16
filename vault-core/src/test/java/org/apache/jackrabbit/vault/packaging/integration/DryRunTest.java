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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.jcr.NamespaceRegistry;
import javax.jcr.Node;

import org.apache.jackrabbit.commons.JcrUtils;
import org.apache.jackrabbit.commons.cnd.CndImporter;
import org.apache.jackrabbit.vault.fs.api.ArtifactType;
import org.apache.jackrabbit.vault.fs.api.ItemFilterSet;
import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.api.SerializationType;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.fs.impl.ArtifactSetImpl;
import org.apache.jackrabbit.vault.fs.impl.io.DocViewSAXImporter;
import org.apache.jackrabbit.vault.fs.impl.io.FileArtifactHandler;
import org.apache.jackrabbit.vault.fs.impl.io.InputSourceArtifact;
import org.apache.jackrabbit.vault.fs.io.ImportOptions;
import org.apache.jackrabbit.vault.packaging.Dependency;
import org.apache.jackrabbit.vault.packaging.JcrPackage;
import org.apache.jackrabbit.vault.packaging.JcrPackageDefinition;
import org.apache.jackrabbit.vault.packaging.impl.JcrPackageManagerImpl;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests that dry runs of {@link JcrPackage} leave persistent repository state unchanged.
 */
public class DryRunTest extends IntegrationTestBase {

    /** Verifies namespace declarations in generic docview content remain unregistered. */
    @Test
    public void genericDocViewDoesNotRegisterNamespace() throws Exception {
        verifyNamespaceDryRun();
    }

    /** Verifies namespace declarations in a file aggregate remain unregistered. */
    @Test
    public void fileDocViewDoesNotRegisterNamespace() throws Exception {
        verifyFileHandlerDryRun(ArtifactType.PRIMARY);
    }

    /** Verifies existing namespaces still allow content to be simulated and then installed. */
    @Test
    public void registeredNamespaceStillImports() throws Exception {
        String prefix = "dry" + UUID.randomUUID().toString().replace("-", "");
        String uri = "urn:filevault:" + prefix;
        NamespaceRegistry registry = admin.getWorkspace().getNamespaceRegistry();
        registry.registerNamespace(prefix, uri);
        try (JcrPackage pack = namespacePackage(prefix, uri)) {
            pack.install(dryRunOptions());
            Assert.assertEquals(uri, registry.getURI(prefix));
            Assert.assertFalse(admin.nodeExists("/testroot"));
            pack.install(getDefaultOptions());
            Assert.assertEquals(
                    "value",
                    admin.getNode("/testroot").getProperty(prefix + ":test").getString());
        }
    }

    /** Verifies the original importer constructor retains real-import namespace registration. */
    @Test
    public void legacyDocViewConstructorStillRegistersNamespace() throws Exception {
        String prefix = "dry" + UUID.randomUUID().toString().replace("-", "");
        String uri = "urn:filevault:" + prefix;
        ArtifactSetImpl artifacts = new ArtifactSetImpl();
        artifacts.setCoverage(ItemFilterSet.INCLUDE_ALL);
        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        filter.add(new PathFilterSet("/testroot"));
        DocViewSAXImporter importer = new DocViewSAXImporter(
                admin.getRootNode(),
                "testroot",
                artifacts,
                filter);
        importer.startPrefixMapping(prefix, uri);
        Assert.assertEquals(prefix, admin.getWorkspace().getNamespaceRegistry().getPrefix(uri));
        importer.endPrefixMapping(prefix);
    }

    /** Verifies extracting nested archives does not register packages, even recursively. */
    @Test
    public void nestedPackagesAreNotRegistered() throws Exception {
        try (JcrPackage pack = uploadPackage("testpackages/subsubtest.zip")) {
            Assert.assertEquals(0, pack.extractSubpackages(dryRunOptions()).length);
            Assert.assertFalse(admin.hasPendingChanges());
            admin.save();
            Assert.assertEquals(1, packMgr.listPackages().size());
            Assert.assertFalse(pack.isInstalled());
            Assert.assertTrue(pack.extractSubpackages(getDefaultOptions()).length > 0);
        }
    }

    /** Verifies non-recursive nested extraction also leaves the registry unchanged. */
    @Test
    public void nonRecursiveNestedPackagesAreNotRegistered() throws Exception {
        try (JcrPackage pack = uploadPackage("testpackages/subtest.zip")) {
            ImportOptions options = dryRunOptions();
            options.setNonRecursive(true);
            Assert.assertEquals(0, pack.extractSubpackages(options).length);
            Assert.assertFalse(admin.hasPendingChanges());
            admin.save();
            Assert.assertEquals(1, packMgr.listPackages().size());
        }
    }

    /** Verifies a pre-existing sub-package is not moved to a different package root. */
    @Test
    public void existingSubpackageIsNotMoved() throws Exception {
        prepareSubpackageContent();
        packMgr = new JcrPackageManagerImpl(admin, new String[] {"/var/packages", "/etc/packages"});
        try (JcrPackage pack = uploadPackage("testpackages/subtest.zip")) {
            pack.extractSubpackages(getDefaultOptions());
            admin.getWorkspace().move("/var/packages/my_packages/sub_a.zip", "/etc/packages/my_packages/sub_a.zip");
            String id = admin.getNode("/etc/packages/my_packages/sub_a.zip").getIdentifier();
            pack.install(dryRunOptions());
            Assert.assertTrue(admin.nodeExists("/etc/packages/my_packages/sub_a.zip"));
            Assert.assertEquals(
                    id, admin.getNode("/etc/packages/my_packages/sub_a.zip").getIdentifier());
            Assert.assertFalse(admin.nodeExists("/var/packages/my_packages/sub_a.zip"));
            Assert.assertFalse(admin.hasPendingChanges());
            pack.install(getDefaultOptions());
            Assert.assertFalse(admin.nodeExists("/etc/packages/my_packages/sub_a.zip"));
            Assert.assertTrue(admin.nodeExists("/var/packages/my_packages/sub_a.zip"));
        }
    }

    /** Verifies a duplicate sub-package is not removed during simulation. */
    @Test
    public void duplicateSubpackageIsNotRemoved() throws Exception {
        prepareSubpackageContent();
        packMgr = new JcrPackageManagerImpl(admin, new String[] {"/var/packages", "/etc/packages"});
        try (JcrPackage pack = uploadPackage("testpackages/subtest.zip")) {
            pack.extractSubpackages(getDefaultOptions());
            admin.getWorkspace().copy("/var/packages/my_packages/sub_a.zip", "/etc/packages/my_packages/sub_a.zip");
            pack.install(dryRunOptions());
            Assert.assertTrue(admin.nodeExists("/etc/packages/my_packages/sub_a.zip"));
            Assert.assertTrue(admin.nodeExists("/var/packages/my_packages/sub_a.zip"));
            Assert.assertFalse(admin.hasPendingChanges());
            pack.install(getDefaultOptions());
            Assert.assertFalse(admin.nodeExists("/etc/packages/my_packages/sub_a.zip"));
        }
    }

    /** Verifies installed state, dependencies and parent metadata are not rewritten. */
    @Test
    public void existingSubpackageMetadataIsNotChanged() throws Exception {
        prepareSubpackageContent();
        try (JcrPackage pack = uploadPackage("testpackages/subtest.zip")) {
            pack.extractSubpackages(getDefaultOptions());
            try (JcrPackage sub = packMgr.open(PACKAGE_ID_SUB_A)) {
                sub.install(getDefaultOptions());
                Calendar unpacked = sub.getDefinition().getLastUnpacked();
                Dependency[] dependencies = sub.getDefinition().getDependencies();
                pack.install(dryRunOptions());
                try (JcrPackage current = packMgr.open(PACKAGE_ID_SUB_A)) {
                    Assert.assertEquals(unpacked, current.getDefinition().getLastUnpacked());
                    Assert.assertArrayEquals(
                            dependencies, current.getDefinition().getDependencies());
                }
                Assert.assertFalse(pack.getDefinition().getNode().hasProperty(JcrPackageDefinition.PN_SUB_PACKAGES));
                Assert.assertFalse(admin.hasPendingChanges());
                admin.save();
                admin.refresh(false);
                try (JcrPackage current = packMgr.open(PACKAGE_ID_SUB_A)) {
                    Assert.assertEquals(unpacked, current.getDefinition().getLastUnpacked());
                    Assert.assertArrayEquals(
                            dependencies, current.getDefinition().getDependencies());
                }
            }
        }
    }

    /** Verifies raw pre-existing sub-package files are not unwrapped and saved. */
    @Test
    public void rawSubpackageIsNotUnwrapped() throws Exception {
        prepareSubpackageContent();
        try (JcrPackage pack = uploadPackage("testpackages/subtest.zip")) {
            pack.extractSubpackages(getDefaultOptions());
            Node content = admin.getNode("/etc/packages/my_packages/sub_a.zip/jcr:content");
            content.getNode("vlt:definition").remove();
            content.removeMixin("vlt:Package");
            admin.save();
            ImportOptions options = dryRunOptions();
            options.setNonRecursive(true);
            pack.install(options);
            Assert.assertFalse(content.hasNode("vlt:definition"));
            Assert.assertFalse(content.isNodeType("vlt:Package"));
            Assert.assertFalse(admin.hasPendingChanges());
            pack.install(getDefaultOptions());
            Assert.assertTrue(admin.nodeExists("/etc/packages/my_packages/sub_a.zip/jcr:content/vlt:definition"));
        }
    }

    /** Verifies the file handler's special docview path also propagates the dry-run flag. */
    @Test
    public void specialFileDocViewDoesNotRegisterNamespace() throws Exception {
        verifyFileHandlerDryRun(ArtifactType.FILE);
    }

    /** Verifies parse-time namespaces on filter-excluded nodes do not change the registry. */
    @Test
    public void excludedNamespaceIsNotRegistered() throws Exception {
        String prefix = "dry" + UUID.randomUUID().toString().replace("-", "");
        String uri = "urn:filevault:" + prefix;
        String xml =
                "<jcr:root xmlns:jcr=\"http://www.jcp.org/jcr/1.0\" jcr:primaryType=\"nt:unstructured\"><excluded xmlns:"
                        + prefix + "=\"" + uri + "\" jcr:primaryType=\"nt:unstructured\" " + prefix
                        + ":test=\"value\"/></jcr:root>";
        String filterXml =
                "<workspaceFilter version=\"1.0\"><filter root=\"/testroot\"><exclude pattern=\"/testroot/excluded(/.*)?\"/></filter></workspaceFilter>";
        try (JcrPackage pack = docViewPackage(xml, filterXml)) {
            ImportOptions options = dryRunOptions();
            options.setStrict(false);
            pack.install(options);
            Assert.assertFalse(
                    Arrays.asList(admin.getWorkspace().getNamespaceRegistry().getURIs())
                            .contains(uri));
            Assert.assertFalse(admin.hasPendingChanges());
        }
    }

    /** Verifies non-recursive simulation leaves no metadata for a later save to persist. */
    @Test
    public void nonRecursiveSubpackageMetadataIsNotChanged() throws Exception {
        prepareSubpackageContent();
        try (JcrPackage pack = uploadPackage("testpackages/subtest.zip")) {
            pack.extractSubpackages(getDefaultOptions());
            try (JcrPackage sub = packMgr.open(PACKAGE_ID_SUB_A)) {
                sub.install(getDefaultOptions());
                Calendar unpacked = sub.getDefinition().getLastUnpacked();
                Dependency[] dependencies = sub.getDefinition().getDependencies();
                ImportOptions options = dryRunOptions();
                options.setNonRecursive(true);
                pack.install(options);
                try (JcrPackage current = packMgr.open(PACKAGE_ID_SUB_A)) {
                    Assert.assertEquals(unpacked, current.getDefinition().getLastUnpacked());
                    Assert.assertArrayEquals(
                            dependencies, current.getDefinition().getDependencies());
                }
                Assert.assertFalse(admin.hasPendingChanges());
                admin.save();
                admin.refresh(false);
                try (JcrPackage current = packMgr.open(PACKAGE_ID_SUB_A)) {
                    Assert.assertEquals(unpacked, current.getDefinition().getLastUnpacked());
                }
            }
        }
    }

    private void verifyFileHandlerDryRun(ArtifactType artifactType) throws Exception {
        String prefix = "dry" + UUID.randomUUID().toString().replace("-", "");
        String uri = "urn:filevault:" + prefix;
        String xml = "<jcr:root xmlns:jcr=\"http://www.jcp.org/jcr/1.0\" xmlns:" + prefix + "=\"" + uri
                + "\" jcr:primaryType=\"nt:unstructured\"/>";
        try (JcrPackage pack = docViewPackage(
                xml, "<workspaceFilter version=\"1.0\"><filter root=\"/testroot\"/></workspaceFilter>")) {
            ArtifactSetImpl artifacts = new ArtifactSetImpl();
            artifacts.setCoverage(ItemFilterSet.INCLUDE_ALL);
            artifacts.add(new InputSourceArtifact(
                    null,
                    "testroot",
                    ".xml",
                    artifactType,
                    pack.getPackage()
                            .getArchive()
                            .getInputSource(pack.getPackage().getArchive().getEntry("jcr_root/testroot/.content.xml")),
                    SerializationType.XML_DOCVIEW));
            artifacts.add(new InputSourceArtifact(
                    null,
                    "testroot/file.txt",
                    "",
                    ArtifactType.FILE,
                    pack.getPackage()
                            .getArchive()
                            .getInputSource(pack.getPackage().getArchive().getEntry("jcr_root/testroot/file.txt")),
                    SerializationType.GENERIC));
            DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
            filter.add(new PathFilterSet("/testroot"));
            FileArtifactHandler handler = new FileArtifactHandler();
            handler.setDryRun(true);
            handler.accept(filter, admin.getRootNode(), "", artifacts);
            Assert.assertFalse(
                    Arrays.asList(admin.getWorkspace().getNamespaceRegistry().getURIs())
                            .contains(uri));
            admin.refresh(false);
            Assert.assertFalse(admin.nodeExists("/testroot"));
            handler.setDryRun(false);
            handler.accept(filter, admin.getRootNode(), "", artifacts);
            admin.save();
            Assert.assertEquals(
                    prefix, admin.getWorkspace().getNamespaceRegistry().getPrefix(uri));
            Assert.assertTrue(admin.nodeExists("/testroot"));
        }
    }

    private void prepareSubpackageContent() throws Exception {
        try (JcrPackage pack = installPackage("testpackages/test_a-1.0.zip")) {
            Assert.assertTrue(pack.isInstalled());
        }
        if (!admin.getWorkspace().getNodeTypeManager().hasNodeType("sling:OrderedFolder")) {
            CndImporter.registerNodeTypes(
                    new StringReader(
                            "<sling='http://sling.apache.org/jcr/sling/1.0'>\n[sling:OrderedFolder] > nt:folder orderable"),
                    admin);
        }
        JcrUtils.getOrCreateByPath("/etc/packages/my_packages", "nt:folder", admin);
        admin.save();
    }

    private void verifyNamespaceDryRun() throws Exception {
        String prefix = "dry" + UUID.randomUUID().toString().replace("-", "");
        String uri = "urn:filevault:" + prefix;
        NamespaceRegistry registry = admin.getWorkspace().getNamespaceRegistry();
        Assert.assertFalse(Arrays.asList(registry.getURIs()).contains(uri));
        try (JcrPackage pack = namespacePackage(prefix, uri)) {
            ImportOptions options = dryRunOptions();
            options.setStrict(false);
            pack.install(options);
            Assert.assertFalse(Arrays.asList(registry.getURIs()).contains(uri));
            Assert.assertFalse(admin.nodeExists("/testroot"));
            Assert.assertFalse(admin.hasPendingChanges());
            pack.install(getDefaultOptions());
            Assert.assertEquals(prefix, registry.getPrefix(uri));
            Assert.assertEquals(
                    "value",
                    admin.getNode("/testroot").getProperty(prefix + ":test").getString());
        }
    }

    private JcrPackage namespacePackage(String prefix, String uri) throws Exception {
        String xml = "<jcr:root xmlns:jcr=\"http://www.jcp.org/jcr/1.0\" xmlns:" + prefix + "=\"" + uri
                + "\" jcr:primaryType=\"nt:unstructured\" " + prefix + ":test=\"value\"/>";
        return docViewPackage(xml, "<workspaceFilter version=\"1.0\"><filter root=\"/testroot\"/></workspaceFilter>");
    }

    private JcrPackage docViewPackage(String docView, String filterXml) throws Exception {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("META-INF/vault/filter.xml", filterXml);
        entries.put(
                "META-INF/vault/properties.xml",
                "<?xml version=\"1.0\"?><!DOCTYPE properties SYSTEM \"http://java.sun.com/dtd/properties.dtd\"><properties><entry key=\"group\">dryrun</entry><entry key=\"name\">namespace</entry></properties>");
        entries.put("jcr_root/testroot/.content.xml", docView);
        entries.put("jcr_root/testroot/file.txt", "content");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return packMgr.upload(new ByteArrayInputStream(bytes.toByteArray()), false);
    }

    private ImportOptions dryRunOptions() {
        ImportOptions options = getDefaultOptions();
        options.setDryRun(true);
        return options;
    }
}

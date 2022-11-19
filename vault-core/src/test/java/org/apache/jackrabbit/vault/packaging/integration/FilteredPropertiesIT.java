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

import static org.junit.Assert.assertNotNull;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Properties;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.fs.config.ConfigurationException;
import org.apache.jackrabbit.vault.fs.config.DefaultMetaInf;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.fs.filter.DefaultPathFilter;
import org.apache.jackrabbit.vault.packaging.ExportOptions;
import org.apache.jackrabbit.vault.packaging.JcrPackage;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.apache.jackrabbit.vault.util.JcrConstants;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Covers testing the filtering of properties both during export and import
 */
public class FilteredPropertiesIT extends IntegrationTestBase {

    @Rule
    public TemporaryFolder folder= new TemporaryFolder();

    @Before
    public void setUp() throws Exception {
        super.setUp();
        setupTmpFooBarWithProperties(admin);
        admin.save();
    }

    @Test
    public void noPropertyFiltered_deprecated() throws IOException, RepositoryException, PackageException {
        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        filter.add(new PathFilterSet("/tmp"));
        filter.addPropertyFilterSet(new PathFilterSet("/tmp"));
        // export and extract
        File pkgFile = assemblePackage(filter);
        clean("/tmp");
        try (VaultPackage vp = packMgr.open(pkgFile)) {
            vp.extract(admin, getDefaultOptions());
            // validate the extracted content
            assertPropertiesExist("/tmp", "p1", "p2", "p3");
            assertPropertiesExist("/tmp/foo", "p1", "p2", "p3");
            assertPropertiesExist("/tmp/foo/bar", "p1", "p2", "p3");
        } finally {
            pkgFile.delete();
        }
    }

    @Test
    public void noPropertyFiltered() throws IOException, RepositoryException, PackageException {
        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        filter.add(new PathFilterSet("/tmp"));

        // export and extract
        File pkgFile = assemblePackage(filter);
        clean("/tmp");
        try (VaultPackage vp = packMgr.open(pkgFile)) {
            vp.extract(admin, getDefaultOptions());
            // validate the extracted content
            assertPropertiesExist("/tmp", "p1", "p2", "p3");
            assertPropertiesExist("/tmp/foo", "p1", "p2", "p3");
            assertPropertiesExist("/tmp/foo/bar", "p1", "p2", "p3");
        } finally {
            pkgFile.delete();
        }
    }

    @Test
    public void filterPropertyP1OnFoo_deprecated() throws IOException, RepositoryException, PackageException, ConfigurationException {
        PathFilterSet properties = new PathFilterSet("/tmp");
        properties.addExclude(new DefaultPathFilter("/tmp/foo/p1"));

        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        filter.add(new PathFilterSet("/tmp"), properties);

        // export and extract
        File pkgFile = assemblePackage(filter);
        clean("/tmp");
        try (VaultPackage vp = packMgr.open(pkgFile)) {
            vp.extract(admin, getDefaultOptions());
            // validate the extracted content
            assertPropertiesExist("/tmp", "p1", "p2", "p3");
            assertPropertiesExist("/tmp/foo", "p2", "p3");
            assertPropertiesMissg("/tmp/foo", "p1");
            assertPropertiesExist("/tmp/foo/bar", "p1", "p2", "p3");
        } finally {
            pkgFile.delete();
        }
    }

    @Test
    public void filterPropertyP1OnFoo() throws IOException, RepositoryException, PackageException, ConfigurationException {
        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        filter.add(new PathFilterSet("/tmp"));

        PathFilterSet properties = new PathFilterSet("/tmp");
        properties.addExclude(new DefaultPathFilter("/tmp/foo/p1"));
        filter.addPropertyFilterSet(properties);
        // export and extract
        File pkgFile = assemblePackage(filter);
        clean("/tmp");
        try (VaultPackage vp = packMgr.open(pkgFile)) {
            vp.extract(admin, getDefaultOptions());
            // validate the extracted content
            assertPropertiesExist("/tmp", "p1", "p2", "p3");
            assertPropertiesExist("/tmp/foo", "p2", "p3");
            assertPropertiesMissg("/tmp/foo", "p1");
            assertPropertiesExist("/tmp/foo/bar", "p1", "p2", "p3");
        } finally {
            pkgFile.delete();
        }
    }

    @Test
    public void filterPropertyPxOnFoo_deprecated() throws IOException, RepositoryException, PackageException, ConfigurationException {
        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        filter.add(new PathFilterSet("/tmp"));

        PathFilterSet properties = new PathFilterSet("/tmp");
        properties.addExclude(new DefaultPathFilter("/tmp/foo/p.*"));
        filter.addPropertyFilterSet(properties);
        // export and extract
        File pkgFile = assemblePackage(filter);
        clean("/tmp");
        try (VaultPackage vp = packMgr.open(pkgFile)) {
            vp.extract(admin, getDefaultOptions());
            // validate the extracted content
            assertPropertiesExist("/tmp", "p1", "p2", "p3");
            assertPropertiesMissg("/tmp/foo", "p1", "p2", "p3");
            assertPropertiesExist("/tmp/foo/bar", "p1", "p2", "p3");
        } finally {
            pkgFile.delete();
        }
    }

    @Test
    public void filterPropertyPxOnFoo() throws IOException, RepositoryException, PackageException, ConfigurationException {

        PathFilterSet properties = new PathFilterSet("/tmp");
        properties.addExclude(new DefaultPathFilter("/tmp/foo/p.*"));

        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        filter.add(new PathFilterSet("/tmp"), properties);

        // export and extract
        File pkgFile = assemblePackage(filter);
        clean("/tmp");
        try (VaultPackage vp = packMgr.open(pkgFile)) {
            vp.extract(admin, getDefaultOptions());
            // validate the extracted content
            assertPropertiesExist("/tmp", "p1", "p2", "p3");
            assertPropertiesMissg("/tmp/foo", "p1", "p2", "p3");
            assertPropertiesExist("/tmp/foo/bar", "p1", "p2", "p3");
        } finally {
            pkgFile.delete();
        }
    }

    @Test
    public void filterPropertyWithTwoRoots() throws IOException, RepositoryException, PackageException, ConfigurationException {
        PathFilterSet properties = new PathFilterSet("/tmp");
        properties.addExclude(new DefaultPathFilter("/tmp/foo/p.*"));

        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        filter.add(new PathFilterSet("/foo"));
        filter.add(new PathFilterSet("/tmp"), properties);

        // export and extract
        File pkgFile = assemblePackage(filter);
        clean("/tmp");
        try (VaultPackage vp = packMgr.open(pkgFile)) {
            vp.extract(admin, getDefaultOptions());
            // validate the extracted content
            assertPropertiesExist("/tmp", "p1", "p2", "p3");
            assertPropertiesMissg("/tmp/foo", "p1", "p2", "p3");
            assertPropertiesExist("/tmp/foo/bar", "p1", "p2", "p3");
        } finally {
            pkgFile.delete();
        }
    }

    @Test
    public void filterPropertyWithTwoRoots_deprecated() throws IOException, RepositoryException, PackageException, ConfigurationException {

        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        filter.add(new PathFilterSet("/foo"));
        filter.addPropertyFilterSet(new PathFilterSet("/foo"));
        filter.add(new PathFilterSet("/tmp"));

        PathFilterSet properties = new PathFilterSet("/tmp");
        properties.addExclude(new DefaultPathFilter("/tmp/foo/p.*"));
        filter.addPropertyFilterSet(properties);

        // export and extract
        File pkgFile = assemblePackage(filter);
        clean("/tmp");
        try (VaultPackage vp = packMgr.open(pkgFile)) {
            vp.extract(admin, getDefaultOptions());
            // validate the extracted content
            assertPropertiesExist("/tmp", "p1", "p2", "p3");
            assertPropertiesMissg("/tmp/foo", "p1", "p2", "p3");
            assertPropertiesExist("/tmp/foo/bar", "p1", "p2", "p3");
        } finally {
            pkgFile.delete();
        }
    }

    @Test
    public void filterPropertyFromSource() throws IOException, RepositoryException, PackageException, ConfigurationException {
        String src = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<workspaceFilter version=\"1.0\">\n" +
                "    <filter root=\"/foo\"/>\n" +
                "    <filter root=\"/tmp\">\n" +
                "        <exclude pattern=\"/tmp/foo/p.*\" matchProperties=\"true\"/>\n" +
                "    </filter>\n" +
                "</workspaceFilter>";

        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        filter.load(new ByteArrayInputStream(src.getBytes("utf-8")));

        // export and extract
        File pkgFile = assemblePackage(filter);
        clean("/tmp");
        try (VaultPackage vp = packMgr.open(pkgFile)) {
            vp.extract(admin, getDefaultOptions());
            // validate the extracted content
            assertPropertiesExist("/tmp", "p1", "p2", "p3");
            assertPropertiesMissg("/tmp/foo", "p1", "p2", "p3");
            assertPropertiesExist("/tmp/foo/bar", "p1", "p2", "p3");
        } finally {
            pkgFile.delete();
        }
    }

    @Test
    public void filterPropertyFromSourceWithRelativePropertyFilter() throws IOException, RepositoryException, PackageException, ConfigurationException {
        String src = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<workspaceFilter version=\"1.0\">\n" +
                "    <filter root=\"/tmp/foo\">\n" +
                "        <exclude pattern=\".*/p1\" matchProperties=\"true\"/>\n" +
                "    </filter>\n" +
                "</workspaceFilter>";

        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        filter.load(new ByteArrayInputStream(src.getBytes("utf-8")));

        // export and extract
        File pkgFile = assemblePackage(filter);
        // do not clean but modify a property
        admin.getNode("/tmp/foo").setProperty("p1", "newv1");
        assertProperty("/tmp/foo/p1", "newv1");
        admin.save();
        try (VaultPackage vp = packMgr.open(pkgFile)) {
            vp.extract(admin, getDefaultOptions());
            // validate the extracted content has not overwritten p1
            assertProperty("/tmp/foo/p1", "newv1");
        } finally {
            pkgFile.delete();
        }
    }

    @Test
    public void filterRelativeProperties_deprecated() throws IOException, RepositoryException, PackageException, ConfigurationException {
        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        filter.add(new PathFilterSet("/tmp"));

        PathFilterSet properties = new PathFilterSet("/tmp");
        properties.addExclude(new DefaultPathFilter(".*/p1"));
        filter.addPropertyFilterSet(properties);
        // export and extract
        File pkgFile = assemblePackage(filter);
        clean("/tmp");
        try (VaultPackage vp = packMgr.open(pkgFile)) {
            vp.extract(admin, getDefaultOptions());
            // validate the extracted content
            assertPropertiesExist("/tmp", "p2", "p3");
            assertPropertiesMissg("/tmp", "p1");
            assertPropertiesExist("/tmp/foo", "p2", "p3");
            assertPropertiesMissg("/tmp/foo", "p1");
            assertPropertiesExist("/tmp/foo/bar", "p2", "p3");
            assertPropertiesMissg("/tmp/foo/bar", "p1");
        } finally {
            pkgFile.delete();
        }
    }

    @Test
    public void filterRelativeProperties() throws IOException, RepositoryException, PackageException, ConfigurationException {
        PathFilterSet properties = new PathFilterSet("/tmp");
        properties.addExclude(new DefaultPathFilter(".*/p1"));

        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        filter.add(new PathFilterSet("/tmp"), properties);

        // export and extract
        File pkgFile = assemblePackage(filter);
        clean("/tmp");
        try (VaultPackage vp = packMgr.open(pkgFile)) {
            vp.extract(admin, getDefaultOptions());
            // validate the extracted content
            assertPropertiesExist("/tmp", "p2", "p3");
            assertPropertiesMissg("/tmp", "p1");
            assertPropertiesExist("/tmp/foo", "p2", "p3");
            assertPropertiesMissg("/tmp/foo", "p1");
            assertPropertiesExist("/tmp/foo/bar", "p2", "p3");
            assertPropertiesMissg("/tmp/foo/bar", "p1");
        } finally {
            pkgFile.delete();
        }
    }

    @Test
    public void filterRelativePropertiesDeepNoPropertyFilter() throws IOException, RepositoryException, PackageException {
        PathFilterSet props = new PathFilterSet("/tmp");
        PathFilterSet nodes = new PathFilterSet("/tmp");

        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        filter.add(nodes, props);

        File pkgFile = assemblePackage(filter);
        clean("/tmp");
        try (VaultPackage vp = packMgr.open(pkgFile)) {
            vp.extract(admin, getDefaultOptions());
            // validate the extracted content
            assertPropertiesExist("/tmp", "p1", "p2", "p3");
            assertPropertiesExist("/tmp/foo", "p1", "p2", "p3");
            assertPropertiesExist("/tmp/foo/bar", "p1", "p2", "p3");
        } finally {
            pkgFile.delete();
        }
    }

    @Test
    public void filterRelativePropertiesShallowNoPropertyFilter() throws IOException, RepositoryException, PackageException, ConfigurationException {
        PathFilterSet nodes = new PathFilterSet("/tmp");
        nodes.addInclude(new DefaultPathFilter("/tmp"));

        PathFilterSet props = new PathFilterSet("/tmp");

        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        filter.add(nodes, props);
        File pkgFile = assemblePackage(filter);
        clean("/tmp");
        try (VaultPackage vp = packMgr.open(pkgFile)) {
            vp.extract(admin, getDefaultOptions());
            // validate the extracted content
            assertPropertiesExist("/tmp", "p1", "p2", "p3");
            assertNodeMissing("/tmp/foo");
            assertNodeMissing("/tmp/foo/bar");
        } finally {
            pkgFile.delete();
        }
    }

    @Test
    public void filterRelativePropertiesShallowWithPropertyFilter() throws IOException, RepositoryException, PackageException, ConfigurationException {
        PathFilterSet props = new PathFilterSet("/tmp");
        props.addExclude(new DefaultPathFilter(".*/p1"));

        PathFilterSet nodes = new PathFilterSet("/tmp");
        nodes.addInclude(new DefaultPathFilter("/tmp"));

        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        filter.add(nodes, props);
        File pkgFile = assemblePackage(filter);
        clean("/tmp");
        try (VaultPackage vp = packMgr.open(pkgFile)) {
            vp.extract(admin, getDefaultOptions());
            // validate the extracted content
            assertPropertiesExist("/tmp", "p2", "p3");
            assertPropertiesMissg("/tmp", "p1");
            assertNodeMissing("/tmp/foo");
            assertNodeMissing("/tmp/foo/bar");
        } finally {
            pkgFile.delete();
        }
    }

    @Test
    public void filterRelativePropertiesSingleSet_NotDeep_no_propertyFilter_addNodes() throws IOException, RepositoryException, PackageException, ConfigurationException {
        PathFilterSet nodes = new PathFilterSet("/tmp");

        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        nodes.addInclude(new DefaultPathFilter("/tmp"));

        filter.add(nodes);

        // export and extract
        File pkgFile = assemblePackage(filter);
        clean("/tmp");
        try (VaultPackage vp = packMgr.open(pkgFile)) {
            vp.extract(admin, getDefaultOptions());
            // validate the extracted content
            assertPropertiesExist("/tmp", "p1", "p2", "p3");
            assertNodeMissing("/tmp/foo");
            assertNodeMissing("/tmp/foo/bar");
        } finally {
            pkgFile.delete();
        }
    }

    @Test
    public void filterRelativePropertiesSingleSet_NotDeep_with_xml() throws IOException, RepositoryException, PackageException, ConfigurationException {
        String src = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<workspaceFilter version=\"1.0\">\n" +
                "    <filter root=\"/tmp\">\n" +
                "        <include pattern=\"/tmp\"/>\n" +
                "    </filter>\n" +
                "</workspaceFilter>\n";

        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        filter.load(new ByteArrayInputStream(src.getBytes("utf-8")));

        // export and extract
        File pkgFile = assemblePackage(filter);
        clean("/tmp");
        try (VaultPackage vp = packMgr.open(pkgFile)) {
            vp.extract(admin, getDefaultOptions());
            // validate the extracted content
            assertPropertiesExist("/tmp", "p1", "p2", "p3");
            assertNodeMissing("/tmp/foo");
            assertNodeMissing("/tmp/foo/bar");
        } finally {
            pkgFile.delete();
        }
    }

    @Test
    public void importWithExcludedPropertiesContainedInSerializationNew() throws IOException, PackageException, RepositoryException, ConfigurationException {
        clean("/testroot2");
        clean("/testroot3");
        
        // the package itself contains more properties than are supposed to be installed during import
        String filterSrc = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<workspaceFilter version=\"1.0\">\n" +
                "    <filter root=\"/testroot3\">\n" +
                "        <exclude pattern=\"/testroot3/jcr:mixinTypes\" matchProperties=\"true\"/>\n" +
                "        <exclude pattern=\"/testroot3/jcr:lastModified.*\" matchProperties=\"true\"/>\n" +
                "        <exclude pattern=\"/testroot3/jcr:title\" matchProperties=\"true\"/>\n" +
                "        <exclude pattern=\"/testroot3/someUnprotectedStringProperty\" matchProperties=\"true\"/>\n" +
                "    </filter>\n" +
                "</workspaceFilter>";

        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        filter.load(new ByteArrayInputStream(filterSrc.getBytes(StandardCharsets.UTF_8)));
        File tmpFile = folder.newFile();
        try (VaultPackage pkg = loadVaultPackage("/test-packages/protected_properties.zip")) {
            try (VaultPackage pkgWithAdjustedFilter = packMgr.rewrap(createExportOptions(filter), pkg, tmpFile)) {
                try (JcrPackage uploadedPackage = packMgr.upload(tmpFile, false, true, null)) {
                    assertNotNull(uploadedPackage);
                    uploadedPackage.install(getDefaultOptions());
                    admin.save();
                    assertProperty("/testroot3/someUnprotectedStringProperty2", "foo");
                    assertPropertyMissing("/testroot3/jcr:mixinTypes");
                    assertPropertyMissing("/testroot3/someUnprotectedStringProperty");
                    assertPropertyMissing("/testroot3/jcr:title");
                    // some protected properties are never imported, independent of their excludes
                    // assertProperty("/testroot3/someProtectedBooleanProperty");

                    // now test update on existing node
                    Node node = admin.getNode("/testroot3");
                    // modify some properties
                    node.addMixin(JcrConstants.MIX_LAST_MODIFIED);
                    node.setProperty(JcrConstants.JCR_TITLE, "title");
                    admin.save();
                    // install again
                    uploadedPackage.install(getDefaultOptions());
                    // make sure excluded properties are not extended or overwritten
                    assertProperty("/testroot3/jcr:mixinTypes", new String[] { JcrConstants.MIX_LAST_MODIFIED } );
                    assertProperty("/testroot3/jcr:title", "title");
                }
            }
        }
    }

    
    /**
     * Setup the path /tmp/foo/bar with properties set at each level
     */
    private void setupTmpFooBarWithProperties(Session session)
            throws RepositoryException {
        Node root = session.getRootNode();
        Node tmp = setupProperties(root.addNode("tmp"));
        Node foo = setupProperties(tmp.addNode("foo"));
        setupProperties(foo.addNode("bar"));
    }

    private Node setupProperties(Node node)
            throws RepositoryException {
        node.setProperty("p1", "v1");
        node.setProperty("p2", "v2");
        node.setProperty("p3", "v3");
        return node;
    }

    private File assemblePackage(WorkspaceFilter filter)
            throws IOException, RepositoryException {

        File tmpFile = Files.createTempFile("vaulttest", ".zip").toFile();
        packMgr.assemble(admin, createExportOptions(filter), tmpFile).close();
        return tmpFile;
    }

    private static @NotNull ExportOptions createExportOptions(WorkspaceFilter filter) {
        ExportOptions options = new ExportOptions();
        DefaultMetaInf meta = new DefaultMetaInf();
        meta.setFilter(filter);

        Properties props = new Properties();
        props.setProperty(VaultPackage.NAME_GROUP, "jackrabbit/test");
        props.setProperty(VaultPackage.NAME_NAME, "filtered-export-package");
        meta.setProperties(props);

        options.setMetaInf(meta);
        return options;
    }

    private void assertPropertiesExist(String rootPath, String... propNames)
            throws RepositoryException {
        for (String propName : propNames) {
            String propPath = String.format("%s/%s", rootPath, propName);
            assertPropertyExists(propPath);
        }
    }

    private void assertPropertiesMissg(String rootPath, String... propNames)
            throws RepositoryException {
        for (String propName : propNames) {
            String propPath = String.format("%s/%s", rootPath, propName);
            assertPropertyMissing(propPath);
        }
    }

}

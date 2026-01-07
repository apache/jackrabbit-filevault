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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;

import org.apache.jackrabbit.commons.JcrUtils;
import org.apache.jackrabbit.vault.packaging.JcrPackage;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.apache.jackrabbit.vault.util.MimeTypes;
import org.junit.Test;

/**
 * {@code TestPackageInstall}...
 */
public class ImportModeIT extends IntegrationTestBase {

    /**
     * Installs a package with the filter: "/tmp/foo", mode="replace"
     */
    @Test
    public void testReplace() throws RepositoryException, IOException, PackageException {
        Node tmp = admin.getRootNode().addNode("tmp");
        Node foo = tmp.addNode("foo");
        foo.addNode("old");
        admin.save();
        assertNodeExists("/tmp/foo/old");
        assertNodeMissing("/tmp/foo/bar");

        JcrPackage pack = packMgr.upload(getStream("/test-packages/tmp_mode_replace.zip"), false);
        pack.extract(getDefaultOptions());

        assertNodeMissing("/tmp/foo/old");
        assertNodeExists("/tmp/foo/bar");
        assertProperty("/tmp/foo/bar/testProperty", "new");
    }

    /**
     * Installs a package with the filter: "/tmp/foo", mode="merge"
     */
    @Test
    public void testMerge() throws RepositoryException, IOException, PackageException {
        Node tmp = admin.getRootNode().addNode("tmp");
        Node foo = tmp.addNode("foo");
        foo.addNode("old");
        Node bar = foo.addNode("bar");
        bar.setProperty("testProperty", "old");
        admin.save();
        assertNodeExists("/tmp/foo/old");
        assertNodeMissing("/tmp/foo/new");

        JcrPackage pack = packMgr.upload(getStream("/test-packages/tmp_mode_merge.zip"), false);
        pack.extract(getDefaultOptions());

        assertNodeExists("/tmp/foo/old");
        assertNodeExists("/tmp/foo/bar");
        assertNodeExists("/tmp/foo/new");
        assertProperty("/tmp/foo/bar/testProperty", "old");
    }

    /**
     * Installs a package with the filter: "/tmp/foo", mode="update"
     */
    @Test
    public void testUpdate() throws RepositoryException, IOException, PackageException {
        Node tmp = admin.getRootNode().addNode("tmp");
        Node foo = tmp.addNode("foo");
        foo.addNode("old");
        Node bar = foo.addNode("bar");
        bar.setProperty("testProperty", "old");
        admin.save();
        assertNodeExists("/tmp/foo/old");
        assertNodeMissing("/tmp/foo/new");

        JcrPackage pack = packMgr.upload(getStream("/test-packages/tmp_mode_update.zip"), false);
        pack.extract(getDefaultOptions());

        assertNodeExists("/tmp/foo/old");
        assertNodeExists("/tmp/foo/bar");
        assertNodeExists("/tmp/foo/new");
        assertProperty("/tmp/foo/bar/testProperty", "new");
    }

    @Test
    public void testAllImportModesWithGenericArtifactHandler()
            throws RepositoryException, IOException, PackageException {
        // initial state
        Node parent = admin.getRootNode().addNode("testroot");
        setUpNode(parent, "replace");
        setUpNode(parent, "merge");
        setUpNode(parent, "update");
        setUpNode(parent, "merge_properties");
        setUpNode(parent, "update_properties");
        admin.save();

        assertProperty("/testroot/replace/propertyold", "old");
        assertProperty("/testroot/replace/propertyupdate", "old");
        assertNodeExists("/testroot/replace/old");
        assertProperty("/testroot/replace/existing/propertyold", "old");

        extractVaultPackage("/test-packages/import_modes_test_generichandler_docview.zip");

        // test update, creation and deletion of properties and nodes
        // Replace
        assertProperty("/testroot/replace/propertyupdate", "new");
        assertProperty("/testroot/replace/propertynew", "new");
        assertPropertyMissing("/testroot/replace/propertyold");
        assertProperty("/testroot/replace/existing/propertynew", "new");
        assertPropertyMissing("/testroot/replace/existing/propertyold");
        assertNodeExists("/testroot/replace/new");
        assertNodeMissing("/testroot/replace/old");

        // Update (neither delete existing nodes nor properties)
        assertProperty("/testroot/update/propertyupdate", "new");
        assertProperty("/testroot/update/propertynew", "new");
        assertProperty("/testroot/update/propertyold", "old");
        assertProperty("/testroot/update/existing/propertynew", "new");
        assertProperty("/testroot/update/existing/propertyold", "old");
        assertNodeExists("/testroot/update/new");
        assertNodeExists("/testroot/update/old");

        // Merge (don't touch existing nodes, except for adding new children)
        assertProperty("/testroot/merge/propertyupdate", "old");
        assertProperty("/testroot/merge/propertynew", "new");
        assertProperty("/testroot/merge/propertyold", "old");
        assertProperty("/testroot/merge/existing/propertynew", "new");
        assertProperty("/testroot/merge/existing/propertyold", "old");
        assertNodeExists(
                "/testroot/merge/new"); // works, because import mode for testroot (docview root level) is REPLACE
        assertNodeExists("/testroot/merge/old");

        // Property Update (neither delete existing nodes nor properties, but update them and add new properties/nodes)
        assertProperty("/testroot/update_properties/propertyupdate", "new");
        assertProperty("/testroot/update_properties/propertynew", "new");
        assertProperty("/testroot/update_properties/propertyold", "old");
        assertProperty("/testroot/update_properties/existing/propertynew", "new");
        assertProperty("/testroot/update_properties/existing/propertyold", "old");
        assertNodeExists("/testroot/update_properties/new");
        assertNodeExists("/testroot/update_properties/old");

        // Property Merge (don't touch existing nodes nor properties, only add new properties/nodes)
        assertProperty("/testroot/merge_properties/propertyupdate", "old");
        assertProperty("/testroot/merge_properties/propertynew", "new");
        assertProperty("/testroot/merge_properties/propertyold", "old");
        assertProperty("/testroot/merge_properties/existing/propertynew", "new");
        assertProperty("/testroot/merge_properties/existing/propertyold", "old");
        assertNodeExists("/testroot/merge_properties/new");
        assertNodeExists("/testroot/merge_properties/old");
    }

    @Test
    public void testAllImportModesFullCoverageWithGenericArtifactHandler()
            throws RepositoryException, IOException, PackageException {
        // initial state
        Node parent = admin.getRootNode().addNode("testroot");
        setUpNode(parent, "replace");
        setUpNode(parent, "merge");
        setUpNode(parent, "update");
        setUpNode(parent, "merge_properties");
        setUpNode(parent, "update_properties");
        admin.save();

        assertProperty("/testroot/replace/propertyold", "old");
        assertProperty("/testroot/replace/propertyupdate", "old");
        assertNodeExists("/testroot/replace/old");
        assertProperty("/testroot/replace/existing/propertyold", "old");

        // full coverage nodes on another level
        extractVaultPackage("/test-packages/import_modes_test_generichandler2_docview.zip");

        // test update, creation and deletion of properties and nodes
        // Replace
        assertProperty("/testroot/replace/propertyupdate", "new");
        assertProperty("/testroot/replace/propertynew", "new");
        assertPropertyMissing("/testroot/replace/propertyold");
        assertProperty("/testroot/replace/existing/propertynew", "new");
        assertPropertyMissing("/testroot/replace/existing/propertyold");
        assertNodeExists("/testroot/replace/new");
        assertNodeMissing("/testroot/replace/old");

        // Update (neither delete existing nodes nor properties)
        assertProperty("/testroot/update/propertyupdate", "new");
        assertProperty("/testroot/update/propertynew", "new");
        assertProperty("/testroot/update/propertyold", "old");
        assertProperty("/testroot/update/existing/propertynew", "new");
        assertProperty("/testroot/update/existing/propertyold", "old");
        assertNodeExists("/testroot/update/new");
        assertNodeExists("/testroot/update/old");

        // Merge (don't touch existing nodes, except for adding new children)
        assertProperty("/testroot/merge/propertyupdate", "old");
        assertPropertyMissing("/testroot/merge/propertynew"); // not imported as whole docview is skipped
        assertProperty("/testroot/merge/propertyold", "old");
        assertPropertyMissing("/testroot/merge/existing/propertynew"); // not imported as whole docview is skipped
        assertProperty("/testroot/merge/existing/propertyold", "old");
        assertNodeMissing("/testroot/merge/new"); // not imported as whole docview is skipped
        assertNodeExists("/testroot/merge/old");

        // Property Update (neither delete existing nodes nor properties, but update them and add new properties/nodes)
        assertProperty("/testroot/update_properties/propertyupdate", "new");
        assertProperty("/testroot/update_properties/propertynew", "new");
        assertProperty("/testroot/update_properties/propertyold", "old");
        assertProperty("/testroot/update_properties/existing/propertynew", "new");
        assertProperty("/testroot/update_properties/existing/propertyold", "old");
        assertNodeExists("/testroot/update_properties/new");
        assertNodeExists("/testroot/update_properties/old");

        // Property Merge (don't touch existing nodes nor properties, only add new properties/nodes)
        assertProperty("/testroot/merge_properties/propertyupdate", "old");
        assertProperty("/testroot/merge_properties/propertynew", "new");
        assertProperty("/testroot/merge_properties/propertyold", "old");
        assertProperty("/testroot/merge_properties/existing/propertynew", "new");
        assertProperty("/testroot/merge_properties/existing/propertyold", "old");
        assertNodeExists("/testroot/merge_properties/new");
        assertNodeExists("/testroot/merge_properties/old");
    }

    @Test
    public void testAllImportModesWithFileArtifactHandler() throws RepositoryException, IOException, PackageException {
        // initial state
        Node parent = admin.getRootNode().addNode("testroot");
        setUpFileNode(parent, "replace");
        setUpFileNode(parent, "merge");
        setUpFileNode(parent, "update");
        setUpFileNode(parent, "merge_properties");
        setUpFileNode(parent, "update_properties");
        admin.save();

        assertProperty("/testroot/replace/jcr:content/jcr:data", "test");

        extractVaultPackage("/test-packages/import_modes_test_filehandler.zip");

        // test update, creation and deletion of properties and nodes
        // Replace
        assertProperty("/testroot/replace/jcr:content/jcr:data", "new");

        // Update (neither delete existing nodes nor properties)
        assertProperty("/testroot/update/jcr:content/jcr:data", "new");

        // Merge (don't touch existing nodes, except for adding new children)
        assertProperty("/testroot/merge/jcr:content/jcr:data", "test");

        // Property Update (neither delete existing nodes nor properties, but update them and add new properties/nodes)
        assertProperty("/testroot/update_properties/jcr:content/jcr:data", "new");

        // Property Merge (don't touch existing nodes nor properties, only add new properties/nodes)
        assertProperty("/testroot/merge_properties/jcr:content/jcr:data", "test");
    }

    @Test
    public void testMergingUpdatingNewRestrictedProperties() throws RepositoryException, IOException, PackageException {
        // initial state
        Node parent = admin.getRootNode().addNode("testroot");
        // existing nodes are heavily restricted (nt:file)
        setUpFileNode(parent, "replace");
        setUpFileNode(parent, "merge_properties");
        setUpFileNode(parent, "update_properties");
        admin.save();

        extractVaultPackageStrict("/test-packages/import_modes_test_generichandler_docview.zip");

        // Replace
        assertProperty("/testroot/replace/propertyupdate", "new");
        assertProperty("/testroot/replace/propertynew", "new");
        assertPropertyMissing("/testroot/replace/propertyold");
        assertProperty("/testroot/replace/existing/propertynew", "new");
        assertPropertyMissing("/testroot/replace/existing/propertyold");
        assertNodeExists("/testroot/replace/new");
        assertNodeMissing("/testroot/replace/old");

        // Property Update (skip adding new properties and child nodes as incompatible with existing primary type)
        assertPropertyMissing("/testroot/update_properties/propertynew");
        assertPropertyExists("/testroot/update_properties/jcr:content/jcr:data");
        assertNodeExists("/testroot/update_properties/jcr:content");

        // Property Merge (skip adding new properties and child nodes as incompatible with existing primary type)
        assertPropertyMissing("/testroot/merge_properties/propertynew");
        assertPropertyExists("/testroot/merge_properties/jcr:content/jcr:data");
        assertNodeExists("/testroot/merge_properties/jcr:content");
    }

    @Test
    public void testExistingNodesNotCoveredByArtifact() throws RepositoryException, IOException, PackageException {
        Node parent = admin.getRootNode().addNode("testroot");
        setUpNode(parent, "replace");
        setUpNode(parent, "merge");
        setUpNode(parent, "update");
        setUpNode(parent, "merge_properties");
        setUpNode(parent, "update_properties");

        admin.save();
        extractVaultPackageStrict("/test-packages/import_modes_test_missing_artifacts.zip");
        // node in repo removed with replace, not removed for all other import modes
        assertNodeMissing("/testroot/replace");
        assertNodeExists("/testroot/merge");
        assertNodeExists("/testroot/update");
        assertNodeExists("/testroot/merge_properties");
        assertNodeExists("/testroot/update_properties");
    }

    private void setUpNode(Node parent, String name) throws RepositoryException {
        Node node = parent.addNode(name);
        node.setProperty("propertyold", "old");
        node.setProperty("propertyupdate", "old");
        node.addNode("old");
        Node existing = node.addNode("existing");
        existing.setProperty("propertyold", "old");
    }

    private void setUpFileNode(Node parent, String name) throws RepositoryException, IOException {
        try (ByteArrayInputStream input = new ByteArrayInputStream("test".getBytes(StandardCharsets.US_ASCII))) {
            JcrUtils.putFile(parent, name, MimeTypes.APPLICATION_OCTET_STREAM, input, Calendar.getInstance());
        }
    }
}

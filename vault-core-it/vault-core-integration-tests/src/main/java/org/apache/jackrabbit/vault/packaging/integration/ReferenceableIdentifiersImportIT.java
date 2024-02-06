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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import javax.jcr.Node;
import javax.jcr.PathNotFoundException;
import javax.jcr.PropertyIterator;
import javax.jcr.ReferentialIntegrityException;
import javax.jcr.RepositoryException;
import javax.jcr.nodetype.NodeType;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.jackrabbit.JcrConstants;
import org.apache.jackrabbit.commons.JcrUtils;
import org.apache.jackrabbit.vault.fs.api.IdConflictPolicy;
import org.apache.jackrabbit.vault.fs.api.ImportMode;
import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.config.ConfigurationException;
import org.apache.jackrabbit.vault.fs.config.DefaultMetaInf;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.fs.filter.DefaultPathFilter;
import org.apache.jackrabbit.vault.fs.io.ImportOptions;
import org.apache.jackrabbit.vault.fs.io.Importer;
import org.apache.jackrabbit.vault.fs.io.ZipArchive;
import org.apache.jackrabbit.vault.packaging.ExportOptions;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.apache.jackrabbit.vault.packaging.PackageProperties;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.apache.jackrabbit.vault.util.PathUtil;
import org.junit.Assert;
import org.junit.Test;

/**
 * Installs a package with the filter: "/tmp/referenceable", mode="replace" The
 * package contains two referenceable nodes: {@code /tmp/referenceable} and
 * {@code /tmp/referenceable/child-referenceable}. Both are setting property
 * {@code someproperty="somevalue"}.
 * <p>
 * Furthermore creates nodes, exports them, renames the test node, and attempts
 * a re-import with differing policies.
 * 
 */
public class ReferenceableIdentifiersImportIT extends IntegrationTestBase {

    private static final String PROPERTY_NAME = "someproperty";
    private static final String PROPERTY_VALUE = "somevalue";
    private static final String UUID_REFERENCEABLE = "352c89a4-304f-4b87-9bed-e09275597df1";
    private static final String UUID_REFERENCEABLE_CHILD = "a201bd6b-25b9-4255-b7db-6fc4c3ddb32d";

    @Test
    public void testOverwriteIdentifierOfReplacedNode() throws RepositoryException, IOException, PackageException {
        // create referenceable node manually 
        Node referenceableNode = JcrUtils.getOrCreateByPath("/tmp/referenceable", null, JcrConstants.NT_UNSTRUCTURED, admin, true);
        referenceableNode.addMixin(JcrConstants.MIX_REFERENCEABLE);
        
        // create (non-referenceable) child node
        JcrUtils.getOrCreateByPath("/tmp/referenceable/child", null, JcrConstants.NT_UNSTRUCTURED, admin, true);
        admin.save();
        
        // check UUID prior package installation
        assertNotEquals(UUID_REFERENCEABLE, referenceableNode.getIdentifier());
        
        // overwrite with new referenceable node (different UUID)
        extractVaultPackageStrict("/test-packages/referenceable.zip");

        assertNodeExists("/tmp/referenceable");
        assertProperty("/tmp/referenceable/" + PROPERTY_NAME, PROPERTY_VALUE);
        // check its UUID
        Node node = admin.getNode("/tmp/referenceable");
        //assertEquals(UUID_REFERENCEABLE, node.getIdentifier());
        node = admin.getNode("/tmp/referenceable/child");
        assertEquals(UUID_REFERENCEABLE_CHILD, node.getIdentifier());
    }

    @Test
    public void testIdentifierCollisionOutsideFilter() throws RepositoryException, IOException, PackageException {
        extractVaultPackageStrict("/test-packages/referenceable.zip");

        assertNodeExists("/tmp/referenceable");
        // check its UUID
        Node node = admin.getNode("/tmp/referenceable");
        assertEquals(UUID_REFERENCEABLE, node.getIdentifier());

        // now move node (it keeps its old ID)
        admin.move("/tmp/referenceable", "/tmp/referenceable-old");
        admin.save();

        // install package again (with default policy IdConflictPolicy.FAIL)
        Exception e = assertThrows(Exception.class, () -> { extractVaultPackageStrict("/test-packages/referenceable.zip"); });
        assertEquals(ReferentialIntegrityException.class, ExceptionUtils.getRootCause(e).getClass());
        admin.refresh(false);

        // now try to remove the referenced node (with policy IdConflictPolicy.CREATE_NEW_ID)
        ImportOptions options = getDefaultOptions();
        options.setStrict(true);
        options.setIdConflictPolicy(IdConflictPolicy.CREATE_NEW_ID);
        extractVaultPackage("/test-packages/referenceable.zip", options);
        // check its UUID
        node = admin.getNode("/tmp/referenceable");
        assertNotEquals(UUID_REFERENCEABLE, node.getIdentifier());

        // now try to remove the referenced node (with default policy IdConflictPolicy.FORCE_REMOVE_CONFLICTING_ID)
        options.setIdConflictPolicy(IdConflictPolicy.FORCE_REMOVE_CONFLICTING_ID);
        extractVaultPackage("/test-packages/referenceable.zip", options);
    }

    @Test
    public void testIdentifierCollisionInsideFilter() throws RepositoryException, IOException, PackageException {
        extractVaultPackageStrict("/test-packages/referenceable.zip");

        assertNodeExists("/tmp/referenceable");
        // check its UUID
        Node node = admin.getNode("/tmp/referenceable/child");
        assertEquals(UUID_REFERENCEABLE_CHILD, node.getIdentifier());

        // now move node (it keeps its old ID)
        admin.move("/tmp/referenceable/child", "/tmp/referenceable/collision");
        admin.save();

        // now add a reference to the node (which is covered by the filter)
        Node referenceNode = JcrUtils.getOrCreateByPath("/tmp/referenceable/reference", JcrConstants.NT_UNSTRUCTURED, admin);
        Node referenceableNode = admin.getNode("/tmp/referenceable/collision");
        referenceNode.setProperty(PROPERTY_NAME, referenceableNode);
        admin.save();
        assertProperty("/tmp/referenceable/reference/" + PROPERTY_NAME, UUID_REFERENCEABLE_CHILD);

        PropertyIterator propIter = referenceableNode.getReferences();
        assertTrue(propIter.hasNext());

        // install package again (with default policy IdConflictPolicy.FAIL)
        extractVaultPackageStrict("/test-packages/referenceable.zip");
        assertNodeExists("/tmp/referenceable");
        // check its UUID
        node = admin.getNode("/tmp/referenceable/child");
        assertEquals(UUID_REFERENCEABLE_CHILD, node.getIdentifier());
        // now move node (it keeps its old ID)
        admin.move("/tmp/referenceable/child", "/tmp/referenceable/collision");
        admin.save();

        // now try to remove the referenced node (with policy IdConflictPolicy.CREATE_NEW_ID)
        ImportOptions options = getDefaultOptions();
        options.setStrict(true);
        options.setIdConflictPolicy(IdConflictPolicy.CREATE_NEW_ID);
        extractVaultPackage("/test-packages/referenceable.zip", options);
        // check its UUID
        node = admin.getNode("/tmp/referenceable/child");
        assertNotEquals(UUID_REFERENCEABLE_CHILD, node.getIdentifier()); // is a new id
        // now move node (it keeps its old ID)
        admin.move("/tmp/referenceable/child", "/tmp/referenceable/collision");
        admin.save();

        // now try to remove the referenced node (with default policy IdConflictPolicy.FORCE_REMOVE_CONFLICTING_ID)
        options.setIdConflictPolicy(IdConflictPolicy.FORCE_REMOVE_CONFLICTING_ID);
        extractVaultPackage("/test-packages/referenceable.zip", options);
        // check its UUID
        node = admin.getNode("/tmp/referenceable/child");
        assertEquals(UUID_REFERENCEABLE_CHILD, node.getIdentifier());
    }

    @Test
    public void testIdentifierCollisionInsideFilterWithReferencesOutsideFilter() throws RepositoryException, IOException, PackageException {
        // initial installation
        extractVaultPackageStrict("/test-packages/referenceable.zip");

        assertNodeExists("/tmp/referenceable");
        // check its UUID
        Node node = admin.getNode("/tmp/referenceable/child");
        assertEquals(UUID_REFERENCEABLE_CHILD, node.getIdentifier());

        // now move node (it keeps its old ID)
        admin.move("/tmp/referenceable/child", "/tmp/referenceable/collision");
        admin.save();
        
        // now add a reference to the node (which is not covered by the filter)
        Node referenceNode = JcrUtils.getOrCreateByPath("/tmp/reference", JcrConstants.NT_UNSTRUCTURED, admin);
        Node referenceableNode = admin.getNode("/tmp/referenceable/collision");
        referenceNode.setProperty(PROPERTY_NAME, referenceableNode);
        admin.save();
        assertProperty("/tmp/reference/" + PROPERTY_NAME, UUID_REFERENCEABLE_CHILD);

        // install package again (with default policy IdConflictPolicy.FAIL)
        Exception e = assertThrows(Exception.class, () -> { extractVaultPackageStrict("/test-packages/referenceable.zip"); } );
        assertEquals(ReferentialIntegrityException.class, e.getClass());
        admin.refresh(false);
        assertProperty("/tmp/reference/" + PROPERTY_NAME, UUID_REFERENCEABLE_CHILD);
        referenceableNode = admin.getNode("/tmp/reference").getProperty(PROPERTY_NAME).getNode();
        assertEquals("/tmp/referenceable/collision", referenceableNode.getPath());

        // now try to remove the referenced node (with policy IdConflictPolicy.CREATE_NEW_ID)
        ImportOptions options = getDefaultOptions();
        options.setStrict(true);
        options.setIdConflictPolicy(IdConflictPolicy.CREATE_NEW_ID);
        e = assertThrows(Exception.class, () -> { extractVaultPackage("/test-packages/referenceable.zip", options);});
        assertEquals(ReferentialIntegrityException.class, e.getClass());
        admin.refresh(false);
        assertProperty("/tmp/reference/" + PROPERTY_NAME, UUID_REFERENCEABLE_CHILD);
        referenceableNode = admin.getNode("/tmp/reference").getProperty(PROPERTY_NAME).getNode();
        assertEquals("/tmp/referenceable/collision", referenceableNode.getPath());

        // now try to remove the referenced node (with default policy IdConflictPolicy.FORCE_REMOVE_CONFLICTING_ID)
        options.setIdConflictPolicy(IdConflictPolicy.FORCE_REMOVE_CONFLICTING_ID);
        extractVaultPackage("/test-packages/referenceable.zip", options);
        // make sure that reference does still exist but now again points to the original path
        assertProperty("/tmp/reference/" + PROPERTY_NAME, UUID_REFERENCEABLE_CHILD);
        referenceableNode = admin.getNode("/tmp/reference").getProperty(PROPERTY_NAME).getNode();
        assertEquals("/tmp/referenceable/child", referenceableNode.getPath());
    }

    @Test
    public void testReplaceReferencedNonConflictingIdentifier() throws RepositoryException, IOException, PackageException {
        // create referenceable node manually (other identifier)
        Node referenceableNode = JcrUtils.getOrCreateByPath("/tmp/referenceable", JcrConstants.NT_UNSTRUCTURED, admin);
        referenceableNode.addMixin(JcrConstants.MIX_REFERENCEABLE);
        admin.save();
        String identifier = referenceableNode.getIdentifier();

        // now create REFERENCE property towards referenceable node
        Node referenceNode = JcrUtils.getOrCreateByPath("/tmp/reference", JcrConstants.NT_UNSTRUCTURED, admin);
        referenceNode.setProperty(PROPERTY_NAME, referenceableNode);
        assertProperty("/tmp/reference/" + PROPERTY_NAME, identifier);
        admin.save();

        // now try to remove the referenced node (with default policy IdConflictPolicy.FAIL)
        Exception e = assertThrows(Exception.class, () -> { extractVaultPackageStrict("/test-packages/referenceable.zip");});
        assertEquals(ReferentialIntegrityException.class, ExceptionUtils.getRootCause(e).getClass());
        admin.refresh(false);

        // now try to remove the referenced node (with policy IdConflictPolicy.CREATE_NEW_ID)
        ImportOptions options = getDefaultOptions();
        options.setStrict(true);
        options.setIdConflictPolicy(IdConflictPolicy.CREATE_NEW_ID);
        e = assertThrows(Exception.class, () -> { extractVaultPackage("/test-packages/referenceable.zip", options);});
        assertEquals(ReferentialIntegrityException.class, ExceptionUtils.getRootCause(e).getClass());
        admin.refresh(false);

        // now try to remove the referenced node (with default policy IdConflictPolicy.FORCE_REMOVE_CONFLICTING_ID)
        options.setIdConflictPolicy(IdConflictPolicy.FORCE_REMOVE_CONFLICTING_ID);
        extractVaultPackage("/test-packages/referenceable.zip", options);
    }

    @Test
    public void testOverwriteSameReferencedIdentifier() throws RepositoryException, IOException, PackageException {
        extractVaultPackageStrict("/test-packages/referenceable.zip");

        assertNodeExists("/tmp/referenceable");
        Node node = admin.getNode("/tmp/referenceable");

        // now create REFERENCE property towards referenceable node
        Node referenceNode = JcrUtils.getOrCreateByPath("/tmp/reference", JcrConstants.NT_UNSTRUCTURED, admin);
        referenceNode.setProperty(PROPERTY_NAME, node);
        assertProperty("/tmp/reference/" + PROPERTY_NAME, UUID_REFERENCEABLE);
        admin.save();

        // now import again (with default policy IdConflictPolicy.FAIL)
        extractVaultPackageStrict("/test-packages/referenceable.zip");

        // now try to remove the referenced node (with policy IdConflictPolicy.CREATE_NEW_ID)
        ImportOptions options = getDefaultOptions();
        options.setStrict(true);
        options.setIdConflictPolicy(IdConflictPolicy.CREATE_NEW_ID);
        extractVaultPackage("/test-packages/referenceable.zip", options);

        // now try to remove the referenced node (with default policy IdConflictPolicy.FORCE_REMOVE_CONFLICTING_ID)
        options.setIdConflictPolicy(IdConflictPolicy.FORCE_REMOVE_CONFLICTING_ID);
        extractVaultPackage("/test-packages/referenceable.zip", options);
    }

    @Test
    public void testReferentialIntegrity() throws RepositoryException {
        // create referenceable node manually
        Node referenceableNode = JcrUtils.getOrCreateByPath("/tmp/referenceable", JcrConstants.NT_UNSTRUCTURED, admin);
        referenceableNode.addMixin(JcrConstants.MIX_REFERENCEABLE);
        admin.save();
        String identifier = referenceableNode.getIdentifier();

        // now create REFERENCE property towards referenceable node
        Node referenceNode = JcrUtils.getOrCreateByPath("/tmp/reference", JcrConstants.NT_UNSTRUCTURED, admin);
        referenceNode.setProperty(PROPERTY_NAME, referenceableNode);
        assertProperty("/tmp/reference/" + PROPERTY_NAME, identifier);
        admin.save();

        // try to remove referenceable node -> fails with RIE
        Assert.assertThrows(ReferentialIntegrityException.class, () -> { referenceableNode.remove();  admin.save();});
    }

    // tests that import the variant referenceable-dup, which contains a
    // duplicate node "duplicate" with the same jcr:uuid as "referenceable"

    @Test
    public void testImportDupPolicyFail() throws RepositoryException, IOException, PackageException {
        // TODO: this is supposed to fail the installation
        testImportDup(IdConflictPolicy.FAIL);
        Node referenceableNode = getNodeOrNull("/tmp/differentparentconflicts/referenceable/child");
        Node duplicateNode = getNodeOrNull("/tmp/differentparentconflicts/duplicate/child");
        if (duplicateNode == null && referenceableNode != null) {
            assertTrue(referenceableNode.isNodeType(JcrConstants.MIX_REFERENCEABLE));
            assertEquals(referenceableNode.getIdentifier(), UUID_REFERENCEABLE_CHILD);
        } else if (duplicateNode != null && referenceableNode == null) {
            assertTrue(duplicateNode.isNodeType(JcrConstants.MIX_REFERENCEABLE));
            assertEquals(duplicateNode.getIdentifier(), UUID_REFERENCEABLE_CHILD);
        } else {
            fail("both nodes imported");
        }
    }

    @Test
    public void testImportDupPolicyCreateNewId() throws RepositoryException, IOException, PackageException {
        testImportDup(IdConflictPolicy.CREATE_NEW_ID);
        Node referenceableNode = getNodeOrNull("/tmp/differentparentconflicts/referenceable/child");
        Node duplicateNode = getNodeOrNull("/tmp/differentparentconflicts/duplicate/child");
        assertNotNull("'duplicate' not imported", duplicateNode);
        assertNotNull("'referencable' not imported", referenceableNode);
        assertTrue(referenceableNode.isNodeType(JcrConstants.MIX_REFERENCEABLE));
        String refref = referenceableNode.getIdentifier();
        assertTrue(duplicateNode.isNodeType(JcrConstants.MIX_REFERENCEABLE));
        String dupref = duplicateNode.getIdentifier();
        assertNotEquals("identifiers should be different", refref, dupref);

        // For this test, Jackrabbit and Oak behave differently; for now, we
        // just observe the behavior (and the test ensures, that it doesn't
        // change without us noticing)
        if (isOak()) {
            assertTrue("identifiers should be new", !UUID_REFERENCEABLE_CHILD.equals(refref) && !UUID_REFERENCEABLE_CHILD.equals(dupref));
        } else {
            int newUUIDs = 0;
            if (!UUID_REFERENCEABLE_CHILD.equals(refref)) {
                newUUIDs += 1;
            }
            if (!UUID_REFERENCEABLE_CHILD.equals(dupref)) {
                newUUIDs += 1;
            }
            assertEquals("for Jackrabbit classic, exactly one changed UUID was expected", 1, newUUIDs);
        }
    }

    @Test
    public void testImportDupPolicyForceRemove() throws RepositoryException, IOException, PackageException {
        testImportDup(IdConflictPolicy.FORCE_REMOVE_CONFLICTING_ID);
        Node referenceableNode = getNodeOrNull("/tmp/differentparentconflicts/referenceable/child");
        Node duplicateNode = getNodeOrNull("/tmp/differentparentconflicts/duplicate/child");
        if (duplicateNode == null && referenceableNode != null) {
            assertTrue(referenceableNode.isNodeType(JcrConstants.MIX_REFERENCEABLE));
            assertEquals(UUID_REFERENCEABLE_CHILD, referenceableNode.getIdentifier());
        } else if (duplicateNode != null && referenceableNode == null) {
            assertTrue(duplicateNode.isNodeType(JcrConstants.MIX_REFERENCEABLE));
            assertEquals(UUID_REFERENCEABLE_CHILD, duplicateNode.getIdentifier());
        } else {
            fail("both nodes imported");
        }
    }

    @Test
    public void testImportDupPolicyLegacy() throws RepositoryException, IOException, PackageException {
        testImportDup(IdConflictPolicy.LEGACY);
        // behaviour for same parent conflicts: remove the conflicting one (i.e. the first one) with the new one (references point to new one afterwards)
        Node referenceableNode = getNodeOrNull("/tmp/sameparentconflicts/referenceable");
        Node duplicateNode = getNodeOrNull("/tmp/sameparentconflicts/duplicate");
        if (duplicateNode == null && referenceableNode != null) {
            assertTrue(referenceableNode.isNodeType(JcrConstants.MIX_REFERENCEABLE));
            assertEquals(UUID_REFERENCEABLE, referenceableNode.getIdentifier());
            admin.move(referenceableNode.getPath(), "/tmp/sameparentconflicts/referenceable-outsidepackage");
        } else if (duplicateNode != null && referenceableNode == null) {
            assertTrue(duplicateNode.isNodeType(JcrConstants.MIX_REFERENCEABLE));
            assertEquals(UUID_REFERENCEABLE, duplicateNode.getIdentifier());
            admin.move(duplicateNode.getPath(), "/tmp/sameparentconflicts/referenceable-outsidepackage");
        } else {
            fail("both nodes imported");
        }
        // behaviour for non-sibling conflicts: assign the new one a new UUID
        referenceableNode = getNodeOrNull("/tmp/differentparentconflicts/referenceable/child");
        duplicateNode = getNodeOrNull("/tmp/differentparentconflicts/duplicate/child");
        assertNotNull(referenceableNode);
        assertNotNull(duplicateNode);
        assertTrue(referenceableNode.isNodeType(JcrConstants.MIX_REFERENCEABLE));
        Set<String> uuids = new HashSet<>(Arrays.asList(referenceableNode.getIdentifier(), duplicateNode.getIdentifier()));
        assertTrue(uuids.contains(UUID_REFERENCEABLE_CHILD)); // one must have kept the old id
        assertEquals(2, uuids.size());

        // create a reference towards an identifier used by the package (but used now outside the packag filter)
        referenceableNode = admin.getNode("/tmp/sameparentconflicts/referenceable-outsidepackage");
        Node referenceNode = JcrUtils.getOrCreateByPath("/tmp/reference", JcrConstants.NT_UNSTRUCTURED, admin);
        referenceNode.setProperty(PROPERTY_NAME, referenceableNode);

        // now reinstall
        testImportDup(IdConflictPolicy.LEGACY);
        referenceableNode = getNodeOrNull("/tmp/sameparentconflicts/referenceable");
        duplicateNode = getNodeOrNull("/tmp/sameparentconflicts/duplicate");
        assertNull(referenceableNode); // package must not contain new conflicting nodes
        assertNull(duplicateNode);
    }

    @Test
    public void testInstallPackage_CREATE_NEW_ID() throws Exception {
        assertIdConflictPolicyBehaviour(IdConflictPolicy.CREATE_NEW_ID, null, null, true, true);
    }

    @Test
    public void testInstallPackage_FAIL() throws Exception {
        assertIdConflictPolicyBehaviour(IdConflictPolicy.FAIL, RepositoryException.class, null, false, false);
    }

    @Test
    public void testInstallPackage_FORCE_REMOVE_CONFLICTING_ID() throws Exception {
        assertIdConflictPolicyBehaviour(IdConflictPolicy.FORCE_REMOVE_CONFLICTING_ID, null, null, false, false);
    }

    @Test
    public void testInstallPackage_LEGACY() throws Exception {
        assertIdConflictPolicyBehaviour(IdConflictPolicy.LEGACY, RepositoryException.class, IllegalStateException.class, false, false);
    }

    private void assertIdConflictPolicyBehaviour(IdConflictPolicy policy, Class<?> expectedException, Class<?> expectedRootCause,
            boolean expectNewId, boolean expectRenamedNodeKept) throws Exception {

        String TEST_ROOT = "testroot";

        Node testRoot = getNodeOrNull("/" + TEST_ROOT);
        if (testRoot == null) {
            testRoot = admin.getRootNode().addNode(TEST_ROOT);
        }

        String srcName = String.format("%s-%x.txt", policy, System.nanoTime());
        String srcPath = PathUtil.append(testRoot.getPath(), srcName);

        Node asset = testRoot.addNode(srcName, NodeType.NT_FOLDER);
        JcrUtils.putFile(asset, "binary.txt", "text/plain", new ByteArrayInputStream("Hello, world!".getBytes()));

        asset.addMixin(NodeType.MIX_REFERENCEABLE);
        admin.save();

        String id1 = asset.getIdentifier();

        File pkgFile = exportContentPackage(srcPath);

        String dstPath = srcPath + "-renamed";
        admin.move(srcPath, dstPath);
        assertNodeMissing(srcPath);

        try {
            installContentPackage(pkgFile, policy);
        } catch (Exception ex) {
            if (expectedException == null) {
                throw ex;
            } else {
                assertTrue("expected: " + expectedException + ", but got: " + ex.getClass(), expectedException.isInstance(ex));
                if (expectedRootCause != null) {
                    Throwable rc = ExceptionUtils.getRootCause(ex);
                    assertTrue("expected: " + expectedRootCause + ", but got: " + rc.getClass(), expectedRootCause.isInstance(rc));
                }
                // expected exception -> test done
                return;
            }
        }

        if (expectRenamedNodeKept) {
            assertNodeExists(dstPath);
        } else {
            assertNodeMissing(dstPath);
        }

        assertNodeExists(srcPath);
        assertNodeExists(srcPath + "/binary.txt");

        Node asset2 = testRoot.getNode(srcName);
        String id2 = asset2.getIdentifier();
        if (expectNewId) {
            assertNotEquals(id1, id2);
        } else {
            assertEquals(id1, id2);
        }
    }

    private File exportContentPackage(String path) throws Exception {
        ExportOptions opts = new ExportOptions();
        DefaultMetaInf inf = new DefaultMetaInf();
        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();

        PathFilterSet pfs = new PathFilterSet(path);
        pfs.addInclude(new DefaultPathFilter(path + "/.*"));
        filter.add(pfs);

        inf.setFilter(filter);
        Properties props = new Properties();
        props.setProperty(PackageProperties.NAME_GROUP, "jackrabbit/test");
        props.setProperty(PackageProperties.NAME_NAME, "test-package");
        inf.setProperties(props);

        opts.setMetaInf(inf);
        File pkgFile = File.createTempFile("testImportMovedResource", ".zip");
        try (VaultPackage pkg = packMgr.assemble(admin, opts, pkgFile)) {
            return pkg.getFile();
        }
    }

    private void installContentPackage(File pkgFile, IdConflictPolicy policy)
            throws RepositoryException, IOException, ConfigurationException {

        try (ZipArchive archive = new ZipArchive(pkgFile);) {
            archive.open(true);
            ImportOptions opts = getDefaultOptions();
            opts.setIdConflictPolicy(policy);
            opts.setFilter(archive.getMetaInf().getFilter());
            opts.setImportMode(ImportMode.UPDATE_PROPERTIES);

            opts.setStrict(true);
            Importer importer = new Importer(opts);

            importer.run(archive, admin.getRootNode());
        }
    }

    private Node getNodeOrNull(String path) throws RepositoryException {
        try {
            return admin.getNode(path);
        } catch (PathNotFoundException ex) {
            return null;
        }
    }

    private void testImportDup(IdConflictPolicy policy) throws IOException, PackageException, RepositoryException {
        ImportOptions options = getDefaultOptions();
        options.setStrict(true);
        options.setIdConflictPolicy(policy);
        extractVaultPackage("/test-packages/referenceable-dup.zip", options);
    }
}
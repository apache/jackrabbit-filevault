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
import javax.jcr.PathNotFoundException;
import javax.jcr.PropertyIterator;
import javax.jcr.ReferentialIntegrityException;
import javax.jcr.RepositoryException;
import javax.jcr.nodetype.NodeType;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.jackrabbit.JcrConstants;
import org.apache.jackrabbit.commons.JcrUtils;
import org.apache.jackrabbit.vault.fs.api.IdConflictPolicy;
import org.apache.jackrabbit.vault.fs.api.ImportMode;
import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.config.DefaultMetaInf;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.fs.filter.DefaultPathFilter;
import org.apache.jackrabbit.vault.fs.io.ImportOptions;
import org.apache.jackrabbit.vault.packaging.ExportOptions;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.apache.jackrabbit.vault.packaging.PackageProperties;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.apache.jackrabbit.vault.packaging.impl.ZipVaultPackage;
import org.apache.jackrabbit.vault.util.PathUtil;
import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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
        Node referenceableNode =
                JcrUtils.getOrCreateByPath("/tmp/referenceable", null, JcrConstants.NT_UNSTRUCTURED, admin, true);
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
        assertEquals(UUID_REFERENCEABLE, node.getIdentifier());
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
        Exception e =
                assertThrows(Exception.class, () -> extractVaultPackageStrict("/test-packages/referenceable.zip"));
        assertEquals(
                ReferentialIntegrityException.class,
                ExceptionUtils.getRootCause(e).getClass());
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
        Node referenceNode =
                JcrUtils.getOrCreateByPath("/tmp/referenceable/reference", JcrConstants.NT_UNSTRUCTURED, admin);
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
    public void testIdentifierCollisionInsideFilterWithReferencesOutsideFilter()
            throws RepositoryException, IOException, PackageException {
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
        Exception e =
                assertThrows(Exception.class, () -> extractVaultPackageStrict("/test-packages/referenceable.zip"));
        assertEquals(ReferentialIntegrityException.class, e.getClass());
        admin.refresh(false);
        assertProperty("/tmp/reference/" + PROPERTY_NAME, UUID_REFERENCEABLE_CHILD);
        referenceableNode =
                admin.getNode("/tmp/reference").getProperty(PROPERTY_NAME).getNode();
        assertEquals("/tmp/referenceable/collision", referenceableNode.getPath());

        // now try to remove the referenced node (with policy IdConflictPolicy.CREATE_NEW_ID)
        ImportOptions options = getDefaultOptions();
        options.setStrict(true);
        options.setIdConflictPolicy(IdConflictPolicy.CREATE_NEW_ID);
        e = assertThrows(Exception.class, () -> extractVaultPackage("/test-packages/referenceable.zip", options));
        assertEquals(ReferentialIntegrityException.class, e.getClass());
        admin.refresh(false);
        assertProperty("/tmp/reference/" + PROPERTY_NAME, UUID_REFERENCEABLE_CHILD);
        referenceableNode =
                admin.getNode("/tmp/reference").getProperty(PROPERTY_NAME).getNode();
        assertEquals("/tmp/referenceable/collision", referenceableNode.getPath());

        // now try to remove the referenced node (with default policy IdConflictPolicy.FORCE_REMOVE_CONFLICTING_ID)
        options.setIdConflictPolicy(IdConflictPolicy.FORCE_REMOVE_CONFLICTING_ID);
        extractVaultPackage("/test-packages/referenceable.zip", options);
        // make sure that reference does still exist but now again points to the original path
        assertProperty("/tmp/reference/" + PROPERTY_NAME, UUID_REFERENCEABLE_CHILD);
        referenceableNode =
                admin.getNode("/tmp/reference").getProperty(PROPERTY_NAME).getNode();
        assertEquals("/tmp/referenceable/child", referenceableNode.getPath());
    }

    @Test
    public void testReplaceReferencedNonConflictingIdentifier()
            throws RepositoryException, IOException, PackageException {
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
        Exception e =
                assertThrows(Exception.class, () -> extractVaultPackageStrict("/test-packages/referenceable.zip"));
        assertEquals(
                ReferentialIntegrityException.class,
                ExceptionUtils.getRootCause(e).getClass());
        admin.refresh(false);

        // now try to remove the referenced node (with policy IdConflictPolicy.CREATE_NEW_ID)
        ImportOptions options = getDefaultOptions();
        options.setStrict(true);
        options.setIdConflictPolicy(IdConflictPolicy.CREATE_NEW_ID);
        e = assertThrows(Exception.class, () -> extractVaultPackage("/test-packages/referenceable.zip", options));
        assertEquals(
                ReferentialIntegrityException.class,
                ExceptionUtils.getRootCause(e).getClass());
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
        Assert.assertThrows(ReferentialIntegrityException.class, () -> {
            referenceableNode.remove();
            admin.save();
        });
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
            assertEquals(UUID_REFERENCEABLE_CHILD, referenceableNode.getIdentifier());
        } else if (duplicateNode != null && referenceableNode == null) {
            assertTrue(duplicateNode.isNodeType(JcrConstants.MIX_REFERENCEABLE));
            assertEquals(UUID_REFERENCEABLE_CHILD, duplicateNode.getIdentifier());
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
            assertTrue(
                    "identifiers should be new",
                    !UUID_REFERENCEABLE_CHILD.equals(refref) && !UUID_REFERENCEABLE_CHILD.equals(dupref));
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
        // behaviour for same parent conflicts: remove the conflicting one (i.e. the first one) with the new one
        // (references point to new one afterwards)
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
        Set<String> uuids =
                new HashSet<>(Arrays.asList(referenceableNode.getIdentifier(), duplicateNode.getIdentifier()));
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

    // constants for behavior target state to be tested

    private enum TARGET_STATE {
        CONFLICT_TARGET_MOVED,
        CONFLICT_TARGET_PRESENT,
        NO_CONFLICT_TARGET_GONE,
        CONFLICT_TARGET_UNCHANGED,
        NO_CONFLICT_TARGET_UNCHANGED
    }

    // make boolean expectations readable

    private static final Boolean ID_NEW = true;
    private static final Boolean ID_KEPT = false;

    private static final Boolean RENAMED_NODE_KEPT = true;
    private static final Boolean RENAMED_NODE_GONE = false;

    private static final Boolean NA = null; // "not applicable"

    // tests for the various combinations of IdConflictPolicy and target state

    @Test
    public void testInstallPackageTargetMoved_CREATE_NEW_ID() throws Exception {
        assertIdConflictPolicyBehaviour(
                IdConflictPolicy.CREATE_NEW_ID, TARGET_STATE.CONFLICT_TARGET_MOVED, ID_NEW, RENAMED_NODE_KEPT);
    }

    @Test
    public void testInstallPackageTargetPresent_CREATE_NEW_ID() throws Exception {
        assertIdConflictPolicyBehaviour(
                IdConflictPolicy.CREATE_NEW_ID, TARGET_STATE.CONFLICT_TARGET_PRESENT, ID_NEW, NA);
    }

    @Test
    public void testInstallPackageNoConflictTargetGone_CREATE_NEW_ID() throws Exception {
        // CREATE_NEW_ID behavior is incorrect in Jackrabbit classic, see
        // https://issues.apache.org/jira/browse/OAK-1244
        assertIdConflictPolicyBehaviour(
                IdConflictPolicy.CREATE_NEW_ID, TARGET_STATE.NO_CONFLICT_TARGET_GONE, isOak() ? ID_NEW : ID_KEPT, NA);
    }

    @Test
    public void testInstallPackageNoConflictTargetUnchanged_CREATE_NEW_ID() throws Exception {
        // CREATE_NEW_ID behavior is incorrect in Jackrabbit classic, see
        // https://issues.apache.org/jira/browse/OAK-1244
        assertIdConflictPolicyBehaviour(
                IdConflictPolicy.CREATE_NEW_ID, TARGET_STATE.NO_CONFLICT_TARGET_UNCHANGED, ID_KEPT, NA);
    }

    @Test
    public void testInstallPackageTargetMoved_FAIL() throws Exception {
        assertIdConflictPolicyBehaviour(
                IdConflictPolicy.FAIL, TARGET_STATE.CONFLICT_TARGET_MOVED, RepositoryException.class, null);
    }

    @Test
    public void testInstallPackageTargetPresent_FAIL() throws Exception {
        assertIdConflictPolicyBehaviour(IdConflictPolicy.FAIL, TARGET_STATE.CONFLICT_TARGET_PRESENT, ID_NEW, NA);
    }

    @Test
    public void testInstallPackageNoConflictTargetGone_FAIL() throws Exception {
        assertIdConflictPolicyBehaviour(IdConflictPolicy.FAIL, TARGET_STATE.NO_CONFLICT_TARGET_GONE, ID_KEPT, NA);
    }

    @Test
    public void testInstallPackageNoConflictTargetUnchanged_FAIL() throws Exception {
        assertIdConflictPolicyBehaviour(IdConflictPolicy.FAIL, TARGET_STATE.NO_CONFLICT_TARGET_UNCHANGED, ID_KEPT, NA);
    }

    @Test
    public void testInstallPackageTargetMoved_FORCE_REMOVE_CONFLICTING_ID() throws Exception {
        assertIdConflictPolicyBehaviour(
                IdConflictPolicy.FORCE_REMOVE_CONFLICTING_ID,
                TARGET_STATE.CONFLICT_TARGET_MOVED,
                ID_KEPT,
                RENAMED_NODE_GONE);
    }

    @Test
    public void testInstallPackageTargetPresent_FORCE_REMOVE_CONFLICTING_ID() throws Exception {
        assertIdConflictPolicyBehaviour(
                IdConflictPolicy.FORCE_REMOVE_CONFLICTING_ID, TARGET_STATE.CONFLICT_TARGET_PRESENT, ID_NEW, NA);
    }

    @Test
    public void testInstallPackageNoConflictTargetGone_FORCE_REMOVE_CONFLICTING_ID() throws Exception {
        assertIdConflictPolicyBehaviour(
                IdConflictPolicy.FORCE_REMOVE_CONFLICTING_ID, TARGET_STATE.NO_CONFLICT_TARGET_GONE, ID_KEPT, NA);
    }

    @Test
    public void testInstallPackageNoConflictTargetUnchanged_FORCE_REMOVE_CONFLICTING_ID() throws Exception {
        assertIdConflictPolicyBehaviour(
                IdConflictPolicy.FORCE_REMOVE_CONFLICTING_ID, TARGET_STATE.NO_CONFLICT_TARGET_UNCHANGED, ID_KEPT, NA);
    }

    @Test
    public void testInstallPackageTargetMoved_LEGACY() throws Exception {
        assertIdConflictPolicyBehaviour(
                IdConflictPolicy.LEGACY,
                TARGET_STATE.CONFLICT_TARGET_MOVED,
                RepositoryException.class,
                IllegalStateException.class);
    }

    @Test
    public void testInstallPackageTargetPresent_LEGACY() throws Exception {
        assertIdConflictPolicyBehaviour(IdConflictPolicy.LEGACY, TARGET_STATE.CONFLICT_TARGET_PRESENT, ID_NEW, NA);
    }

    @Test
    public void testInstallPackageNoConflict_LEGACY() throws Exception {
        assertIdConflictPolicyBehaviour(IdConflictPolicy.LEGACY, TARGET_STATE.NO_CONFLICT_TARGET_GONE, ID_KEPT, NA);
    }

    @Test
    public void testInstallPackageNoConflictTargetGone_LEGACY() throws Exception {
        assertIdConflictPolicyBehaviour(IdConflictPolicy.LEGACY, TARGET_STATE.NO_CONFLICT_TARGET_GONE, ID_KEPT, NA);
    }

    @Test
    public void testInstallPackageNoConflictTargetUnchanged_LEGACY() throws Exception {
        assertIdConflictPolicyBehaviour(
                IdConflictPolicy.LEGACY, TARGET_STATE.NO_CONFLICT_TARGET_UNCHANGED, ID_KEPT, NA);
    }

    // postcondition: exception
    private void assertIdConflictPolicyBehaviour(
            IdConflictPolicy policy, TARGET_STATE dstState, Class<?> expectedException, Class<?> expectedRootCause)
            throws Exception {
        assertIdConflictPolicyBehaviour(policy, dstState, expectedException, expectedRootCause, null, null);
    }

    // postcondition: no exception, check state after
    private void assertIdConflictPolicyBehaviour(
            IdConflictPolicy policy, TARGET_STATE dstState, Boolean expectNewId, Boolean expectRenamedNodeKept)
            throws Exception {
        assertIdConflictPolicyBehaviour(policy, dstState, null, null, expectNewId, expectRenamedNodeKept);
    }

    private void assertIdConflictPolicyBehaviour(
            IdConflictPolicy policy,
            TARGET_STATE dstState,
            Class<?> expectedException,
            Class<?> expectedRootCause,
            Boolean expectNewId,
            Boolean expectRenamedNodeKept)
            throws Exception {

        // create initial state and export

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

        // modify existing state as requested

        String dstPath = null;

        switch (dstState) {
            case CONFLICT_TARGET_MOVED:
                dstPath = srcPath + "-renamed";
                admin.move(srcPath, dstPath);
                admin.save();
                assertNodeMissing(srcPath);
                break;

            case CONFLICT_TARGET_PRESENT:
                admin.removeItem(srcPath);
                admin.save();
                assertNodeMissing(srcPath);
                // same place, new ID
                Node newAsset = testRoot.addNode(srcName, NodeType.NT_FOLDER);
                JcrUtils.putFile(
                        newAsset, "binary.txt", "text/plain", new ByteArrayInputStream("Hello, new world!".getBytes()));
                newAsset.addMixin(NodeType.MIX_REFERENCEABLE);
                admin.save();
                assertNodeExists(srcPath);
                assertNotEquals(id1, newAsset.getIdentifier());
                break;

            case NO_CONFLICT_TARGET_GONE:
                admin.removeItem(srcPath);
                admin.save();
                assertNodeMissing(srcPath);
                break;

            case CONFLICT_TARGET_UNCHANGED:
            case NO_CONFLICT_TARGET_UNCHANGED:
                break;

            default:
                fail();
        }

        // re-import and check post-conditions

        try (ZipVaultPackage pack = new ZipVaultPackage(pkgFile, true)) {
            ImportOptions opts = getDefaultOptions();
            opts.setIdConflictPolicy(policy);
            opts.setImportMode(ImportMode.UPDATE_PROPERTIES);
            opts.setStrict(true);
            pack.extract(admin, opts);
            if (expectedException != null) {
                fail("expected: " + expectedException + ", but no exception was thrown");
            }
        } catch (Exception ex) {
            if (expectedException == null) {
                throw ex;
            } else {
                assertTrue(
                        "expected: " + expectedException + ", but got: " + ex.getClass(),
                        expectedException.isInstance(ex));
                if (expectedRootCause != null) {
                    Throwable rc = ExceptionUtils.getRootCause(ex);
                    assertTrue(
                            "expected: " + expectedRootCause + ", but got: " + rc.getClass(),
                            expectedRootCause.isInstance(rc));
                }
                // expected exception -> test done
                return;
            }
        }

        if (expectRenamedNodeKept != null) {
            if (expectRenamedNodeKept) {
                assertNodeExists(dstPath);
            } else {
                assertNodeMissing(dstPath);
            }
        }

        assertNodeExists(srcPath);
        assertNodeExists(srcPath + "/binary.txt");

        Node asset2 = testRoot.getNode(srcName);
        String id2 = asset2.getIdentifier();
        if (expectNewId) {
            assertNotEquals("expect Identifier changed on target node", id1, id2);
        } else {
            assertEquals("expect Identifier NOT changed on target node", id1, id2);
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

    @Test
    public void testUuidPreservedWithLegacyPolicy() throws Exception {
        // 1. Install package with UUID from package
        extractVaultPackageStrict("/test-packages/referenceable.zip");
        Node node = admin.getNode("/tmp/referenceable");
        String originalUuid = node.getIdentifier();
        assertEquals(UUID_REFERENCEABLE, originalUuid);

        // 2. Change the UUID manually (simulating an existing node with different UUID)
        // This simulates the scenario where node exists with UUID1, package has UUID2
        node.remove();
        admin.save();
        node = JcrUtils.getOrCreateByPath("/tmp/referenceable", null, JcrConstants.NT_UNSTRUCTURED, admin, true);
        node.addMixin(JcrConstants.MIX_VERSIONABLE);
        node.setProperty(PROPERTY_NAME, PROPERTY_VALUE); // Keep same property
        admin.save();
        String currentUuid = node.getIdentifier();
        assertNotEquals("Node should have different UUID than package", UUID_REFERENCEABLE, currentUuid);

        // 3. Re-import with LEGACY policy and UPDATE_PROPERTIES mode
        // Expected: UUID should be preserved (not replaced with package UUID)
        // Bug: UUID gets replaced unconditionally at line 996
        ImportOptions opts = getDefaultOptions();
        opts.setIdConflictPolicy(IdConflictPolicy.LEGACY);
        opts.setImportMode(ImportMode.REPLACE);
        extractVaultPackage("/test-packages/referenceable.zip", opts);

        // 4. Verify UUID is preserved (not replaced)
        node = admin.getNode("/tmp/referenceable");
        String finalUuid = node.getIdentifier();
        assertEquals("UUID should be preserved with LEGACY policy", currentUuid, finalUuid);
        assertNotEquals("UUID should NOT be replaced with package UUID", UUID_REFERENCEABLE, finalUuid);
    }
}

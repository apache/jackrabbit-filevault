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

import java.io.IOException;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.apache.jackrabbit.vault.packaging.PackageException;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.junit.Test;

public class FolderArtifactHandlerIT extends IntegrationTestBase {

    /* JCRVLT-415 */
    @Test
    public void testModifyingContainedNodeNonNtFolderPrimaryType() throws RepositoryException, IOException, PackageException {
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
    public void testNotModifyingContainedNodeNtFolderPrimaryType() throws RepositoryException, IOException, PackageException {
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
    public void testNotModifyingIntermediateNodePrimaryType() throws RepositoryException, IOException, PackageException {
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
    public void testCreatingIntermediateNodesWithDefaultType() throws RepositoryException, IOException, PackageException {
        // create node "/var/foo" with node type "nt:unstructured"
        Node rootNode = admin.getRootNode();
        Node testNode = rootNode.addNode("var", "nt:unstructured");
        admin.save();
        try (VaultPackage vltPackage = extractVaultPackageStrict("/test-packages/folder-without-docview-element.zip")) {
            assertNodeHasPrimaryType("/var/foo", "nt:unstructured");
        }
    }

    @Test
    public void testCreatingIntermediateNodesWithFallbackType() throws RepositoryException, IOException, PackageException {
        // create node "/var/foo" with node type "nt:unstructured"
        Node rootNode = admin.getRootNode();
        Node testNode = rootNode.addNode("var", "nt:folder");
        admin.save();
        assertNodeHasPrimaryType("/var", "nt:folder");
        try (VaultPackage vltPackage = extractVaultPackage("/test-packages/folder-without-docview-element.zip")) {
            assertNodeHasPrimaryType("/var", "nt:folder");
            assertNodeHasPrimaryType("/var/foo", "nt:folder");
        }
    }
}

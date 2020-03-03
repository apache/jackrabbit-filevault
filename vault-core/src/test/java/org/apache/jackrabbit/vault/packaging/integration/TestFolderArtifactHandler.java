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
import javax.jcr.RepositoryException;

import org.apache.jackrabbit.vault.packaging.PackageException;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.junit.Test;

public class TestFolderArtifactHandler extends IntegrationTestBase {

    /* JCRVLT-415 */
    @Test
    public void testModifyingNonNtFolderPrimaryType() throws RepositoryException, IOException, PackageException {
        // create node "/test/foo" with node type "nt:unstructured"
        Node rootNode = admin.getRootNode();
        Node testNode = rootNode.addNode("test", "nt:unstructured");
        Node fooNode = testNode.addNode("foo", "nt:unstructured");
        fooNode.setProperty("testProperty", "test");
        try (VaultPackage vltPackage = extractVaultPackage("/test-packages/folder-without-docview-element.zip")) {
            // make sure the primary type from "/test/foo" got overwritten!
            assertPropertyMissing("/test/foo/testProperty");
            assertNodeHasPrimaryType("/test/foo", "nt:folder");
        }
    }

    @Test
    public void testNotModifyingNtFolderPrimaryType() throws RepositoryException, IOException, PackageException {
        // create node "/test/foo" with node type "nt:unstructured"
        Node rootNode = admin.getRootNode();
        Node testNode = rootNode.addNode("test", "nt:unstructured");
        testNode.setProperty("value", "huhu");
        Node fooNode = testNode.addNode("foo", "nt:folder");
        try (VaultPackage vltPackage = extractVaultPackage("/test-packages/folder-without-docview-element.zip")) {
            assertNodeHasPrimaryType("/test/foo", "nt:folder");
            assertPropertyMissing("/test/value");
        }
    }

    @Test
    public void testNotModifyingIntermediatePrimaryTypes() throws RepositoryException, IOException, PackageException {
        // create node "/test/foo" with node type "nt:unstructured"
        Node rootNode = admin.getRootNode();
        Node testNode = rootNode.addNode("test2", "nt:unstructured");
        Node fooNode = testNode.addNode("foo", "nt:unstructured");
        fooNode.setProperty("testProperty", "test");
        try (VaultPackage vltPackage = extractVaultPackage("/test-packages/folder-without-docview-element.zip")) {
            assertNodeHasPrimaryType("/test2/foo", "nt:unstructured");
            assertProperty("/test2/foo/testProperty", "test");
        }
    }
}

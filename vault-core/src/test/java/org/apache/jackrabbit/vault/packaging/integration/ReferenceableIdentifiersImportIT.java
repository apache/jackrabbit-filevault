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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;

import javax.jcr.Node;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;

import org.apache.jackrabbit.JcrConstants;
import org.apache.jackrabbit.vault.fs.io.ImportOptions;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.junit.Test;

public class ReferenceableIdentifiersImportIT extends IntegrationTestBase {

    private static final String UUID_REFERENCEABLE = "352c89a4-304f-4b87-9bed-e09275597df1";

    @Test
    public void testImportDupDefault() throws RepositoryException, IOException, PackageException {
        testImportDup();
        Node referenceableNode = getNodeOrNull("/tmp/referenceable");
        Node duplicateNode = getNodeOrNull("/tmp/duplicate");
        if (duplicateNode == null && referenceableNode != null) {
            assertTrue(referenceableNode.isNodeType(JcrConstants.MIX_REFERENCEABLE));
            assertEquals(referenceableNode.getIdentifier(), UUID_REFERENCEABLE);
        } else if (duplicateNode != null && referenceableNode == null) {
            assertTrue(duplicateNode.isNodeType(JcrConstants.MIX_REFERENCEABLE));
            assertEquals(duplicateNode.getIdentifier(), UUID_REFERENCEABLE);
        } else {
            fail("both nodes imported");
        }
    }

    private Node getNodeOrNull(String path) throws RepositoryException {
        try {
            return admin.getNode(path);
        } catch (PathNotFoundException ex) {
            return null;
        }
    }

    private void testImportDup() throws IOException, RepositoryException, PackageException {
        ImportOptions options = getDefaultOptions();
        options.setStrict(true);
        extractVaultPackage("/test-packages/referenceable-dup.zip", options);
    }
}
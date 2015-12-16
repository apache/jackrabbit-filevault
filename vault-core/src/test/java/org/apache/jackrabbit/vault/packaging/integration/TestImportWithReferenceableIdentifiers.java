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

import java.io.IOException;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.apache.jackrabbit.vault.packaging.JcrPackage;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.junit.Test;

/**
 * Installs a package with the filter: "/tmp/referenceable", mode="replace"
 * The package contains two referenceable nodes:
 * {@code /tmp/referenceable} and
 * {@code /tmp/referenceable/child-referenceable}.
 * Both are setting property {@code someproperty="somevalue"}.
 */
public class TestImportWithReferenceableIdentifiers extends IntegrationTestBase {
	
	private static final String PROPERTY_NAME = "someproperty";
	private static final String PROPERTY_VALUE = "somevalue";
	private static final String OVERWRITTEN_PROPERTY_VALUE = "someothervalue";

    @Test
    public void testCreateNewIdentifierInCaseOfCollisions() throws RepositoryException, IOException, PackageException {

        JcrPackage pack = packMgr.upload(getStream("testpackages/test_referenceable.zip"), false);
        pack.extract(getDefaultOptions());

        assertNodeExists("/tmp/referenceable");
        assertProperty("/tmp/referenceable/" + PROPERTY_NAME, PROPERTY_VALUE);
        // check its UUID
        Node node = admin.getNode("/tmp/referenceable");
        assertEquals("352c89a4-304f-4b87-9bed-e09275597df1", node.getIdentifier());
        // modify property
        node.setProperty(PROPERTY_NAME, OVERWRITTEN_PROPERTY_VALUE);
        
        // now move node (it keeps its old ID)
        admin.move("/tmp/referenceable", "/referenceable-old");
        try {
	        // install package again
	        pack.extract(getDefaultOptions());
	        assertNodeExists("/tmp/referenceable"); // this node was not recreated because the moved node was touched by the package installation
	        
	        // check its UUID (must be different)
	        node = admin.getNode("/tmp/referenceable");
	        assertNotEquals("352c89a4-304f-4b87-9bed-e09275597df1", node.getIdentifier()); // must be different
	        assertNodeExists("/tmp/referenceable");
	        assertProperty("/tmp/referenceable/" + PROPERTY_NAME, PROPERTY_VALUE);
	        
	        // node with old UUID should not have been touched
	        assertProperty("/referenceable-old/" + PROPERTY_NAME, OVERWRITTEN_PROPERTY_VALUE);
        } finally {
        	clean("/referenceable-old");
        }
    }
    
    @Test
    public void testReinstallOfMovedReferencableNodeKeepingSameParent() throws RepositoryException, IOException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("testpackages/test_referenceable.zip"), false);
        pack.extract(getDefaultOptions());

        assertNodeExists("/tmp/referenceable");
        // check its UUID
        Node node = admin.getNode("/tmp/referenceable");
        assertEquals("352c89a4-304f-4b87-9bed-e09275597df1", node.getIdentifier());
        
        // now move node (it keeps its old ID)
        admin.move("/tmp/referenceable", "/tmp/referenceable-old");
        
        // install package again
        pack.extract(getDefaultOptions());
        assertNodeExists("/tmp/referenceable"); // this test breaks here, because this node was not recreated by the installation
        // the problem is in DocViewSAXImporter line 777
    }
}
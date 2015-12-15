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

import org.apache.jackrabbit.vault.packaging.JcrPackage;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.junit.Test;

/**
 * <code>TestPackageInstall</code>...
 */
public class TestImportWithConflictingUUID extends IntegrationTestBase {

    /**
     * Installs a package with the filter: "/tmp/foo", mode="replace"
     */
    @Test
    public void testTakeOverUUIDFromPackage() throws RepositoryException, IOException, PackageException {

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
        assertNodeExists("/tmp/referenceable"); // this node was not recreated because the moved node was touched by the package installation
    }
    
    
 

}
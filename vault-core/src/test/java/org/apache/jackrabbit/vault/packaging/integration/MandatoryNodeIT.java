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

import javax.jcr.ItemExistsException;
import javax.jcr.Node;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.lock.LockException;
import javax.jcr.nodetype.ConstraintViolationException;
import javax.jcr.nodetype.NoSuchNodeTypeException;
import javax.jcr.nodetype.NodeType;
import javax.jcr.version.VersionException;

import org.apache.jackrabbit.vault.packaging.PackageException;
import org.junit.Assert;
import org.junit.Test;


public class MandatoryNodeIT extends IntegrationTestBase {
 
    @Test
    public void testMandatoryResidualChildNodesReplace()
            throws RepositoryException, IOException, PackageException {

        // some node types use both residual (name constraint = "*") and mandatory child nodes but according to
        // JCR 2.0 (https://s.apache.org/jcr-2.0-spec/2.0/3_Repository_Model.html#3.7.2.4%20Mandatory)
        // this is not allowed and is subsequently not enforced

        // package set ups folder structure with mandatory child nodes
        extractVaultPackage("/test-packages/wcm-rollout-config-1.zip");

        assertNodeExists("/libs/msm/wcm/rolloutconfigs/activate/targetActivate");

        assertNodeExists("/libs/msm/wcm/rolloutconfigs/deactivate/targetDeactivate");

        assertNodeExists("/libs/msm/wcm/rolloutconfigs/default/contentUpdate");
        assertNodeExists("/libs/msm/wcm/rolloutconfigs/default/contentCopy");
        assertNodeExists("/libs/msm/wcm/rolloutconfigs/default/contentDelete");

        // packages removes/replaces some mandatory child nodes
        extractVaultPackage("/test-packages/wcm-rollout-config-2.zip");

        // check if older nodes are replaced
        assertNodeMissing("/libs/msm/wcm/rolloutconfigs/default/contentUpdate");
        assertNodeMissing("/libs/msm/wcm/rolloutconfigs/default/contentDelete");
        // check if newly added node exist
        assertNodeExists("/libs/msm/wcm/rolloutconfigs/default/activate");
        assertNodeExists("/libs/msm/wcm/rolloutconfigs/default/contentCopy");

        // check if the only node is replaced
        assertNodeMissing("/libs/msm/wcm/rolloutconfigs/activate/targetActivate");
        assertNodeExists("/libs/msm/wcm/rolloutconfigs/activate/targetNewActivate");

        // check if the only node is removed
        assertNodeMissing("/libs/msm/wcm/rolloutconfigs/deactivate/targetDeactivate");
    }

    @Test
    public void testMandatoryNonResidualChildNodesReplace() throws ItemExistsException, PathNotFoundException, NoSuchNodeTypeException, LockException, VersionException, ConstraintViolationException, RepositoryException, IOException, PackageException {
        Node testnode = admin.getRootNode().addNode("testroot", NodeType.NT_FILE); // mandatory child node: jcr:content
        try {
            admin.save();
            Assert.fail("Saving a node with a missing mandatory (but non-residual) child node must fail");
        } catch (ConstraintViolationException e) {
            // expected
        }
        testnode.addNode("jcr:content", "nt:unstructured"); 
        admin.save();
        
        assertNodeExists("/testroot/jcr:content");
        
        // package tries to delete mandatory child node
        try {
            extractVaultPackage("/test-packages/mandatory-non-residual-childnode.zip");
            Assert.fail("Deleting mandatory (but non-residual) child node must fail");
        } catch (ConstraintViolationException e) {
            // expected
            admin.refresh(false);
        }
        assertNodeExists("/testroot/jcr:content");
    }
}

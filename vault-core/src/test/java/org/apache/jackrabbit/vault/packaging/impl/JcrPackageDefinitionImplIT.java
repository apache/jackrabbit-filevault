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

package org.apache.jackrabbit.vault.packaging.impl;

import java.io.IOException;

import javax.jcr.ItemExistsException;
import javax.jcr.Node;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.lock.LockException;
import javax.jcr.nodetype.ConstraintViolationException;
import javax.jcr.version.VersionException;

import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.packaging.PackageProperties;
import org.apache.jackrabbit.vault.packaging.integration.IntegrationTestBase;
import org.junit.Assert;
import org.junit.Test;


public class JcrPackageDefinitionImplIT extends IntegrationTestBase {

    @Test
    public void testUnwrapAndComparePackageProperties() throws ItemExistsException, PathNotFoundException, VersionException, ConstraintViolationException, LockException, RepositoryException, IOException {
        // new node
        Node node = admin.getRootNode().addNode("packagedefinitiontest");
        JcrPackageDefinitionImpl packageDefinition = new JcrPackageDefinitionImpl(node);
        try (Archive archive = getFileArchive("/test-packages/principalbased.zip")) {
            archive.open(false);
            packageDefinition.unwrap(archive, false);
            // now compare the package properties
            assertPackagePropertiesEquals(archive.getMetaInf().getPackageProperties(), packageDefinition);
            
            Assert.assertEquals("bar", archive.getMetaInf().getPackageProperties().getProperty("foo"));
            // custom properties are not part of the unwrapped node
            Assert.assertNull(packageDefinition.getProperty("foo"));
        }
    }

    void assertPackagePropertiesEquals(PackageProperties expectedProperties, PackageProperties actualProperties) {
        Assert.assertEquals("lastModified is different", expectedProperties.getLastModified(), actualProperties.getLastModified());
        Assert.assertEquals("lastModifiedBy is different", expectedProperties.getLastModifiedBy(), actualProperties.getLastModifiedBy());
        Assert.assertEquals("created is different", expectedProperties.getCreated(), actualProperties.getCreated());
        Assert.assertEquals("createdBy is different", expectedProperties.getCreatedBy(), actualProperties.getCreatedBy());
        Assert.assertEquals("lastWrapped is different", expectedProperties.getLastWrapped(), actualProperties.getLastWrapped());
        Assert.assertEquals("lastWrappedBy is different", expectedProperties.getLastWrappedBy(), actualProperties.getLastWrappedBy());
        Assert.assertEquals("description is different", expectedProperties.getDescription(), actualProperties.getDescription());
        Assert.assertEquals("acHandling is different", expectedProperties.getACHandling(), actualProperties.getACHandling());
        Assert.assertEquals("id (group, name or version) is different", expectedProperties.getId(), actualProperties.getId());
        Assert.assertArrayEquals("dependencies are different", expectedProperties.getDependencies(), actualProperties.getDependencies());
        Assert.assertEquals("requiresRestart is different", expectedProperties.requiresRestart(), actualProperties.requiresRestart());
        Assert.assertEquals("requiresRoot is different", expectedProperties.requiresRoot(), actualProperties.requiresRoot());
    }
}

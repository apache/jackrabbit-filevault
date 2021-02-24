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
import static org.junit.Assert.assertNotNull;

import java.io.IOException;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.nodetype.NodeType;

import org.apache.jackrabbit.commons.JcrUtils;
import org.apache.jackrabbit.vault.fs.io.ImportOptions;
import org.apache.jackrabbit.vault.packaging.JcrPackage;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.junit.Assume;
import org.junit.Test;

/**
 * {@code TestEmptyPackage}...
 */
public class AtomicCounterIT extends IntegrationTestBase {

    /**
     * Tests if installing a package with a mix:atomicCounter works
     */
    @Test
    public void installAtomicCounter() throws RepositoryException, IOException, PackageException {
        Assume.assumeTrue(isOak());

        JcrPackage pack = packMgr.upload(getStream("/test-packages/atomic-counter-test.zip"), false);
        assertNotNull(pack);
        ImportOptions opts = getDefaultOptions();
        pack.install(opts);

        assertProperty("/tmp/testroot/oak:counter", "42");
    }

    /**
     * Tests if installing a package with a mix:atomicCounter works (update)
     */
    @Test
    public void updateAtomicCounter() throws RepositoryException, IOException, PackageException {
        Assume.assumeTrue(isOak());

        Node tmp = JcrUtils.getOrAddNode(admin.getRootNode(), "tmp", NodeType.NT_UNSTRUCTURED);
        Node testroot = JcrUtils.getOrAddNode(tmp, "testroot", NodeType.NT_UNSTRUCTURED);
        testroot.addMixin("mix:atomicCounter");
        testroot.setProperty("oak:increment", 5);
        admin.save();
        assertEquals(5L, testroot.getProperty("oak:counter").getLong());

        JcrPackage pack = packMgr.upload(getStream("/test-packages/atomic-counter-test.zip"), false);
        assertNotNull(pack);
        ImportOptions opts = getDefaultOptions();
        pack.install(opts);

        assertProperty("/tmp/testroot/oak:counter", "42");
    }

}
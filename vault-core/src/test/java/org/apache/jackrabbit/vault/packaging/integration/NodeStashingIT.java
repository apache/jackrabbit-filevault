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

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.apache.jackrabbit.vault.fs.api.ImportMode;
import org.apache.jackrabbit.vault.fs.api.ProgressTrackerListener;
import org.apache.jackrabbit.vault.fs.api.ProgressTrackerListener.Mode;
import org.apache.jackrabbit.vault.fs.io.ImportOptions;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.junit.Test;

public class NodeStashingIT extends IntegrationTestBase {

    private static final String TESTNS = "https://issues.apache.org/jira/browse/JCRVLT-684";

    @Test
    public void testStashMixinMandatoryChildNodeModeReplace() throws RepositoryException, IOException, PackageException {
        testStashMixinMandatoryChildNode(ImportMode.REPLACE);
    }

    @Test
    public void testStashMixinMandatoryChildNodeModeMergeProperties() throws RepositoryException, IOException, PackageException {
        testStashMixinMandatoryChildNode(ImportMode.MERGE_PROPERTIES);
    }

    private void testStashMixinMandatoryChildNode(ImportMode mode) throws RepositoryException, IOException, PackageException {

        assumeTrue(isOak());

        ImportOptions options = getDefaultOptions();
        options.setImportMode(mode);

        // import test structure from package
        extractVaultPackage("/test-packages/stashing/create.zip", options);

        assertNodeExists("/tmp/stash");
        assertNodeExists("/tmp/stash/{" + TESTNS + "}mandatoryChildNode");

        Node node1 = admin.getNode("/tmp/stash");
        String id1 = node1.getIdentifier();
        assertTrue(node1.isNodeType("{" + TESTNS + "}noChildNodes"));
        assertTrue(node1.isNodeType("{" + TESTNS + "}hasMandatoryChildNode"));

        // update same path but without mixin allowing child nodes and different
        // UUID so that node stashing kicks in
        Collector col = new Collector();
        options.setListener(col);

        extractVaultPackage("/test-packages/stashing/update.zip", options);

        // child node should be retained
        assertNodeExists("/tmp/stash");
        assertNodeExists("/tmp/stash/{" + TESTNS + "}mandatoryChildNode");

        Node node2 = admin.getNode("/tmp/stash");
        String id2 = node2.getIdentifier();

        // make sure it's really the new node
        assertNotEquals("imported node should have different identifier", id1, id2);

        // make sure mixin type was restored
        assertTrue(node2.isNodeType("{" + TESTNS + "}hasMandatoryChildNode"));

        // before JCRVLT-697, this would have been only one node
        String expected = "saving approx 3 nodes...";
        assertTrue("Expected message '" + expected + "' not seen in: " + col.actions, col.actions.contains(expected));
    }

    private static class Collector implements ProgressTrackerListener {
        private final List<String> actions = new LinkedList<>();

        public void onMessage(Mode mode, String action, String path) {
            if (Mode.PATHS == mode) {
                actions.add(action);
            }
        }

        public void onError(Mode mode, String path, Exception e) {
        }
    }
}
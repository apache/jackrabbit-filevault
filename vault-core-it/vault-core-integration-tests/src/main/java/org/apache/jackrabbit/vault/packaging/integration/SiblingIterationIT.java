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

import org.apache.jackrabbit.oak.commons.junit.LogCustomizer;
import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.fs.config.DefaultMetaInf;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.fs.impl.AggregateImpl;
import org.apache.jackrabbit.vault.packaging.ExportOptions;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.event.Level;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.nodetype.NodeType;
import java.io.File;
import java.io.IOException;
import java.util.Properties;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests checking whether siblings are traversed when not needed.
 */
public class SiblingIterationIT extends IntegrationTestBase {

    // constants for test resource layout
    private static final String ROOT = "testroot-sibling-iteration";
    private static final String DO_FIND_ME = "do-find-me";
    private static final String DO_NOT_FIND_ME = "do-not-find-me";

    @Before
    public void createTestResources() throws RepositoryException {
        Node rootNode = admin.getRootNode();
        Node testNode = rootNode.addNode(ROOT, NodeType.NT_FOLDER);

        for (String name : new String[] {DO_FIND_ME, DO_NOT_FIND_ME}) {
            Node f = testNode.addNode(name, NodeType.NT_FILE);
            Node c = f.addNode("jcr:content", NodeType.NT_RESOURCE);
            c.setProperty("jcr:data", "");
        }

        admin.save();
    }

    @After
    public void deleteTestResources() throws RepositoryException {
        Node rootNode = admin.getRootNode().getNode(ROOT);
        rootNode.remove();
        admin.save();
    }

    @Test
    // single filter with root below test resource
    public void testSingleMatchingFilterBelow() throws RepositoryException, IOException {
        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        filter.add(new PathFilterSet("/" + ROOT + "/" + DO_FIND_ME));

        // check that we did not iterate
        internalTestSiblingIteration(filter, false);
    }

    @Test
    // two filters, root below test resource, one unrelated
    public void testOneMatchingOneNonMatchingFilter() throws RepositoryException, IOException {
        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        filter.add(new PathFilterSet("/" + ROOT + "/" + DO_FIND_ME));
        filter.add(new PathFilterSet("/" + ROOT + "xyz"));

        internalTestSiblingIteration(filter, false);
    }

    @Test
    // two filters, root below test resource, one unrelated
    public void testOneMatchingOneNonDeeperMatchingFilter() throws RepositoryException, IOException {
        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        filter.add(new PathFilterSet("/" + ROOT + "/" + DO_FIND_ME));
        filter.add(new PathFilterSet("/" + ROOT + "xyz" + DO_FIND_ME + "/" + "something-else"));

        internalTestSiblingIteration(filter, false);
    }

    private void internalTestSiblingIteration(WorkspaceFilter filter, boolean expectIterated) throws RepositoryException, IOException {

        ExportOptions opts = new ExportOptions();
        DefaultMetaInf inf = new DefaultMetaInf();
        inf.setFilter(filter);
        Properties props = new Properties();
        props.setProperty(VaultPackage.NAME_GROUP, "jackrabbit/test");
        props.setProperty(VaultPackage.NAME_NAME, "test-package");
        inf.setProperties(props);
        opts.setMetaInf(inf);

        LogCustomizer clog = LogCustomizer.forLogger(AggregateImpl.class).enable(Level.TRACE).
               contains("checking ").create();

        File tmpFile = File.createTempFile("vaulttest-sibling-iteration", "zip");

        try {
            clog.starting();
            try (VaultPackage pkg = packMgr.assemble(admin, opts, tmpFile)) {

                assertNotNull(DO_FIND_ME + " should be part of the package", pkg.getArchive().getEntry("jcr_root" + "/" + ROOT + "/" + DO_FIND_ME));
                assertNull(pkg.getArchive().getEntry("jcr_root" + "/" + ROOT + "/" + DO_NOT_FIND_ME));

                String entries = clog.getLogs().toString();

                // independent of filers: one included, one not
                assertTrue("trace should contain entry for '" + DO_FIND_ME + "' got: " + entries,
                        entries.contains("/" + ROOT + "/" + DO_FIND_ME));
                assertNull(pkg.getArchive().getEntry("jcr_root" + "/" + ROOT + "/" + DO_NOT_FIND_ME));

                // dependent on filters: verify expected iteration
                if (!expectIterated) {
                    assertFalse("trace should not contain entry for '" + DO_NOT_FIND_ME + "', got: " + entries,
                            entries.contains("/" + ROOT + "/" + DO_NOT_FIND_ME));
                } else {
                    assertTrue("trace should contain entry for '" + DO_NOT_FIND_ME + "', got: " + entries,
                            entries.contains("/" + ROOT + "/" + DO_NOT_FIND_ME));
                }
            }
        } finally {
            clog.finished();
            tmpFile.delete();
        }
    }
}

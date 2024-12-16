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
import org.apache.jackrabbit.vault.fs.config.DefaultMetaInf;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.fs.impl.AggregateImpl;
import org.apache.jackrabbit.vault.packaging.ExportOptions;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.junit.BeforeClass;
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

public class ManySiblingsIT extends IntegrationTestBase {

    @BeforeClass
    public static void initRepository() throws RepositoryException, IOException {
        initRepository(true, false); // always use BlobStore with Oak
    }

    // JCRVLT-789
    @Test
    public void testManySiblings() throws RepositoryException, IOException {

        Node rootNode = admin.getRootNode();
        Node testNode = rootNode.addNode("testroot", NodeType.NT_FOLDER);

        int count = 10;

        String format = "many-%04d";
        String found = String.format(format, 3);
        String notFound = String.format(format, 5);

        for (int i = 0; i < count; i++) {
            Node f = testNode.addNode(String.format(format, i), NodeType.NT_FILE);
            Node c = f.addNode("jcr:content", NodeType.NT_RESOURCE);
            c.setProperty("jcr:data", "");
        }

        admin.save();

        ExportOptions opts = new ExportOptions();
        DefaultMetaInf inf = new DefaultMetaInf();
        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        filter.add(new PathFilterSet("/testroot/" + found));
        inf.setFilter(filter);
        Properties props = new Properties();
        props.setProperty(VaultPackage.NAME_GROUP, "jackrabbit/test");
        props.setProperty(VaultPackage.NAME_NAME, "test-package");
        inf.setProperties(props);
        opts.setMetaInf(inf);

        LogCustomizer clog = LogCustomizer.forLogger(AggregateImpl.class).enable(Level.TRACE).
               contains("checking ").create();

        try {
            File tmpFile = File.createTempFile("vaulttest-many", "zip");
            clog.starting();
            try (VaultPackage pkg = packMgr.assemble(admin, opts, tmpFile)) {

                assertNotNull(pkg.getArchive().getEntry("jcr_root" + "/testroot/" + found));
                assertNull(pkg.getArchive().getEntry("jcr_root" + "/testroot/" + notFound));

                // check that we did not descend into notFound
                String entries = clog.getLogs().toString();
                assertTrue("trace should contain entry for '" + found + "' got: " + entries,
                        entries.contains("/testroot/" + found));
                assertFalse("trace should not contain entry for '" + notFound + "', got: " + entries,
                        entries.contains("/testroot/" + notFound));
            }
        } finally {
            clog.finished();
        }
    }
}

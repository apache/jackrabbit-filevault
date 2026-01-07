/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.jackrabbit.vault.packaging.integration;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.nodetype.NodeType;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

import org.apache.commons.io.IOUtils;
import org.apache.jackrabbit.util.ISO9075;
import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.config.DefaultMetaInf;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.packaging.ExportOptions;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class SerializationIT extends IntegrationTestBase {

    @Test
    public void exportJcrXmlTest() throws RepositoryException, IOException, PackageException {
        Node testRoot = admin.getRootNode().addNode("testroot", NodeType.NT_UNSTRUCTURED);
        Node nodeA = testRoot.addNode("a", NodeType.NT_UNSTRUCTURED);
        Node xmlText = nodeA.addNode("jcr:xmltext", NodeType.NT_UNSTRUCTURED);
        xmlText.setProperty("jcr:xmlcharacters", "Hello, World.");
        admin.save();

        ExportOptions opts = new ExportOptions();
        DefaultMetaInf inf = new DefaultMetaInf();
        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        filter.add(new PathFilterSet("/testroot/a"));
        inf.setFilter(filter);
        Properties props = new Properties();
        props.setProperty(VaultPackage.NAME_GROUP, "jackrabbit/test");
        props.setProperty(VaultPackage.NAME_NAME, "test-package");
        inf.setProperties(props);

        opts.setMetaInf(inf);
        File tmpFile = File.createTempFile("vaulttest", "zip");
        VaultPackage pkg = packMgr.assemble(admin, opts, tmpFile);

        // check if entries are present
        Archive.Entry e = pkg.getArchive().getEntry("/jcr_root/testroot/.content.xml");
        assertNotNull("entry should exist", e);
        String src = IOUtils.toString(pkg.getArchive().getInputSource(e).getByteStream(), "utf-8");
        String expected = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<jcr:root xmlns:jcr=\"http://www.jcp.org/jcr/1.0\" xmlns:nt=\"http://www.jcp.org/jcr/nt/1.0\"\n"
                + "    jcr:primaryType=\"nt:unstructured\">\n"
                + "    <a jcr:primaryType=\"nt:unstructured\">\n"
                + "        <jcr:xmltext\n"
                + "            jcr:primaryType=\"nt:unstructured\"\n"
                + "            jcr:xmlcharacters=\"Hello, World.\"/>\n"
                + "    </a>\n"
                + "</jcr:root>\n";
        assertEquals("content.xml must be correct", expected, src);
        pkg.close();
        tmpFile.delete();
    }

    @Test
    public void exportProblematicWhitespaceTest() throws RepositoryException, IOException, PackageException {
        // name containing non-ASCII whitespace character; disallowed in
        // Jackrabbit Classic, allowed in Jackrabbit Oak
        String testName = "x\u200ay";
        try {
            Node testRoot = admin.getRootNode().addNode("testroot", NodeType.NT_UNSTRUCTURED);
            testRoot.addNode(testName, NodeType.NT_UNSTRUCTURED);
            admin.save();
        } catch (RepositoryException ex) {
            // if we can't add that node, there's nothing to test
            return;
        }

        String encodedName = ISO9075.encode(testName);
        ExportOptions opts = new ExportOptions();
        DefaultMetaInf inf = new DefaultMetaInf();
        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        filter.add(new PathFilterSet("/testroot/a"));
        inf.setFilter(filter);
        Properties props = new Properties();
        props.setProperty(VaultPackage.NAME_GROUP, "jackrabbit/test");
        props.setProperty(VaultPackage.NAME_NAME, "test-package");
        inf.setProperties(props);

        opts.setMetaInf(inf);
        File tmpFile = File.createTempFile("vaulttest", "zip");
        try {
            VaultPackage pkg = packMgr.assemble(admin, opts, tmpFile);

            // check if entries are present
            Archive.Entry e = pkg.getArchive().getEntry("/jcr_root/testroot/.content.xml");
            assertNotNull("entry should exist", e);
            String src = IOUtils.toString(pkg.getArchive().getInputSource(e).getByteStream(), "utf-8");
            String expected = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                    + "<jcr:root xmlns:jcr=\"http://www.jcp.org/jcr/1.0\" xmlns:nt=\"http://www.jcp.org/jcr/nt/1.0\"\n"
                    + "    jcr:primaryType=\"nt:unstructured\">\n"
                    + "    <"
                    + encodedName + "/>\n" + "</jcr:root>\n";
            assertEquals("content.xml must be correct, containing '" + encodedName + "'", expected, src);
            pkg.close();
        } catch (RepositoryException ex) {
            // expected until JCRVLT-700 is resolved
            assertTrue(ex.getMessage().contains("not allowed in name"));
        }

        tmpFile.delete();
    }
}

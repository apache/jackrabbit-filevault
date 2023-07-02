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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.Properties;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.nodetype.NodeType;

import org.apache.jackrabbit.commons.JcrUtils;
import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.config.DefaultMetaInf;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.packaging.ExportOptions;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.junit.Before;
import org.junit.Test;

/**
 * Test export / import of specially escaped node names.
 */
public class EscapedExportIT extends IntegrationTestBase {

    private final static String[] FILE_NAMES = {
            "_jcr_myfile.data",
            "jcr:myfile.data",
            "東京.data",
            "Spr16_PR_T_001_x0009_VS_R1.data"
    };

    @Before
    public void setUp() throws Exception {
        super.setUp();
    }

    @Test
    public void unicodeEscapedFilesExportAndImportOk() throws IOException, RepositoryException, PackageException {
        String data = "Hello, World.";
        Node tmp = admin.getRootNode().addNode("tmp", NodeType.NT_FOLDER);
        for (String fileName: FILE_NAMES) {
            JcrUtils.putFile(tmp, fileName, "text/plain", new ByteArrayInputStream(data.getBytes()));
        }
        admin.save();

        assembleAndReinstallPackage();

        // validate the extracted content
        for (String fileName: FILE_NAMES) {
            assertProperty("/tmp/" + fileName + "/jcr:content/jcr:data", data);
        }

    }

    @Test
    public void unicodeEscapedNodesExportAndImportOk() throws IOException, RepositoryException, PackageException {
        Node tmp = admin.getRootNode().addNode("tmp", NodeType.NT_FOLDER);
        Node test = tmp.addNode("test", NodeType.NT_FILE);
        test.addMixin("vlt:FullCoverage");
        Node content = test.addNode(Node.JCR_CONTENT, NodeType.NT_UNSTRUCTURED);
        for (String fileName: FILE_NAMES) {
            content.addNode(fileName);
        }
        admin.save();

        assembleAndReinstallPackage();

        // validate the extracted content
        for (String fileName: FILE_NAMES) {
            assertNodeExists("/tmp/test/jcr:content/" + fileName);
        }

    }

    @Test
    public void unicodeEscapedPropertiesExportAndImportOk() throws IOException, RepositoryException, PackageException {
        String data = "Hello, World.";
        Node tmp = admin.getRootNode().addNode("tmp", NodeType.NT_FOLDER);
        Node test = tmp.addNode("test", NodeType.NT_FILE);
        test.addMixin("vlt:FullCoverage");
        Node content = test.addNode(Node.JCR_CONTENT, NodeType.NT_UNSTRUCTURED);
        for (String fileName: FILE_NAMES) {
            content.setProperty(fileName, data);
        }
        admin.save();

        assembleAndReinstallPackage();

        // validate the extracted content
        for (String fileName: FILE_NAMES) {
            assertProperty("/tmp/test/jcr:content/" + fileName, data);
        }

    }

    private void assembleAndReinstallPackage() throws IOException, PackageException, RepositoryException {
        File pkgFile = File.createTempFile("vaulttest", ".zip");

        try {
            ExportOptions options = new ExportOptions();
            DefaultMetaInf meta = new DefaultMetaInf();
            DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
            filter.add(new PathFilterSet("/tmp"));
            meta.setFilter(filter);

            Properties props = new Properties();
            props.setProperty(VaultPackage.NAME_GROUP, "jackrabbit/test");
            props.setProperty(VaultPackage.NAME_NAME, "filtered-export-package");
            meta.setProperties(props);
            options.setMetaInf(meta);

            packMgr.assemble(admin, options, pkgFile).close();

            clean("/tmp");
            try (VaultPackage vp = packMgr.open(pkgFile)) {
                vp.extract(admin, getDefaultOptions());
            }
        } finally {
            pkgFile.delete();
        }
    }

}
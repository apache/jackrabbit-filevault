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

import java.io.File;
import java.io.IOException;
import java.util.Properties;
import java.util.regex.Pattern;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.fs.config.ConfigurationException;
import org.apache.jackrabbit.vault.fs.config.DefaultMetaInf;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.fs.filter.DefaultPathFilter;
import org.apache.jackrabbit.vault.packaging.ExportOptions;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestExportWithQuotedPattern extends IntegrationTestBase {

    @Before
    public void setUp() throws Exception {
        super.setUp();
        setupContent(admin);
        admin.save();
    }

    private static final String NAME = "f(o";

    @Test
    public void quotedPattern() throws IOException, RepositoryException, PackageException, ConfigurationException {
        String path = "/tmp/" + NAME;

        PathFilterSet nodes = new PathFilterSet(path);
        nodes.addInclude(new DefaultPathFilter(quote(path)));

        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        filter.add(nodes);

        // export and extract
        File pkgFile = assemblePackage(filter);

        clean(path);
        try (VaultPackage vp = packMgr.open(pkgFile)) {
            vp.extract(admin, getDefaultOptions());
            // validate the extracted content
            assertNodeExists(path);
            assertNodeMissing(path + "/foo");
            assertNodeMissing(path + "/bar");
        } finally {
            pkgFile.delete();
        }
    }

    private String quote(String path) {
        if (path == null) {
            return null;
        } else if (path.startsWith("/")) {
            return "/" + Pattern.quote(path.substring(1));
        } else {
            return Pattern.quote(path);
        }
    }

    private File assemblePackage(WorkspaceFilter filter)
            throws IOException, RepositoryException {

        File tmpFile = File.createTempFile("vaulttest", ".zip");

        ExportOptions options = new ExportOptions();
        DefaultMetaInf meta = new DefaultMetaInf();
        meta.setFilter(filter);

        Properties props = new Properties();
        props.setProperty(VaultPackage.NAME_GROUP, "jackrabbit/test");
        props.setProperty(VaultPackage.NAME_NAME, "quoted-pattern-export-package");
        meta.setProperties(props);

        options.setMetaInf(meta);

        packMgr.assemble(admin, options, tmpFile).close();
        return tmpFile;
    }

    private void setupContent(Session session)
            throws RepositoryException {

        Node root = session.getRootNode();
        Node tmp = root.addNode("tmp", "nt:folder");
        Node foo = tmp.addNode(NAME, "nt:folder");
        Node bar = foo.addNode("bar", "nt:folder");
        Node zoo = foo.addNode("zoo", "nt:folder");
    }

    public void assertNodeExists(String path) throws RepositoryException {
        assertTrue(path + " should exist", admin.nodeExists(path));
    }

    public void assertNodeMissing(String path) throws RepositoryException {
        assertFalse(path + " should not exist", admin.nodeExists(path));
    }
}

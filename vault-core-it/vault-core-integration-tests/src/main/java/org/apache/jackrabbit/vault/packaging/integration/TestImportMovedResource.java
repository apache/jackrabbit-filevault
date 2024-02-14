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

import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.apache.jackrabbit.vault.fs.api.IdConflictPolicy;
import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.config.ConfigurationException;
import org.apache.jackrabbit.vault.fs.config.DefaultMetaInf;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.fs.io.ImportOptions;
import org.apache.jackrabbit.vault.fs.io.Importer;
import org.apache.jackrabbit.vault.fs.io.ZipArchive;
import org.apache.jackrabbit.vault.packaging.ExportOptions;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.junit.Before;
import org.junit.Test;

import static javax.jcr.nodetype.NodeType.MIX_REFERENCEABLE;
import static javax.jcr.nodetype.NodeType.NT_UNSTRUCTURED;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;
import static org.apache.jackrabbit.commons.JcrUtils.getOrCreateByPath;
import static org.apache.jackrabbit.util.Text.getRelativeParent;
import static org.apache.jackrabbit.vault.packaging.PackageProperties.NAME_GROUP;
import static org.apache.jackrabbit.vault.packaging.PackageProperties.NAME_NAME;
import static org.apache.jackrabbit.vault.util.PathUtil.append;

public class TestImportMovedResource extends IntegrationTestBase {

    public static final String TEST_ROOT = "testroot";

    private Node testRoot;

    @Before
    public void before() throws RepositoryException {
        testRoot = admin.getRootNode().addNode(TEST_ROOT);
    }


    @Test
    public void testRename() throws Exception {
        String srcName = randomAlphanumeric(10);
        String srcPath = append(testRoot.getPath(), srcName);
        String dstPath = srcPath + "-renamed";
        test(srcPath, dstPath);
    }

    @Test
    public void testMove() throws Exception {
        String name = randomAlphanumeric(10);

        String srcParent = append(testRoot.getPath(), randomAlphanumeric(10));
        String srcPath = append(srcParent, name);

        String dstParent = append(testRoot.getPath(), randomAlphanumeric(10));
        String dstPath = append(dstParent, name);

        test(srcPath, dstPath);
    }

    @Test
    public void testMoveAndRename() throws Exception {
        String srcName = randomAlphanumeric(10);

        String srcParent = append(testRoot.getPath(), randomAlphanumeric(10));
        String srcPath = append(srcParent, srcName);

        String dstParent = append(testRoot.getPath(), randomAlphanumeric(10));
        String dstPath = append(dstParent, srcName + "-renamed");

        test(srcPath, dstPath);
    }



    private void test(String srcPath, String dstPath) throws RepositoryException, IOException, ConfigurationException {
        getOrCreateByPath(getRelativeParent(srcPath, 1), NT_UNSTRUCTURED, NT_UNSTRUCTURED, admin, true);
        getOrCreateByPath(getRelativeParent(dstPath, 1), NT_UNSTRUCTURED, NT_UNSTRUCTURED, admin, true);
        Node original = getOrCreateByPath(srcPath, NT_UNSTRUCTURED, NT_UNSTRUCTURED, admin, true);
        original.addMixin(MIX_REFERENCEABLE);
        File pkgFile = export(srcPath);
        admin.move(srcPath, dstPath);
        assertNodeMissing(srcPath);
        install(pkgFile);
        assertNodeExists(srcPath);
    }


    private File export(String path) throws IOException, RepositoryException {
        ExportOptions opts = new ExportOptions();
        DefaultMetaInf inf = new DefaultMetaInf();
        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        filter.add(new PathFilterSet(path));
        inf.setFilter(filter);
        Properties props = new Properties();
        props.setProperty(NAME_GROUP, "jackrabbit/test");
        props.setProperty(NAME_NAME, "test-package");
        inf.setProperties(props);
        opts.setMetaInf(inf);
        File pkgFile = File.createTempFile("testImportMovedResource", ".zip");
        try(VaultPackage pkg = packMgr.assemble(admin, opts, pkgFile)) {
            return pkg.getFile();
        }
    }

    private void install(File pkgFile) throws RepositoryException, IOException, ConfigurationException {
        ZipArchive archive = new ZipArchive(pkgFile);
        archive.open(true);
        ImportOptions opts = getDefaultOptions();
        opts.setIdConflictPolicy(IdConflictPolicy.LEGACY);
        opts.setFilter(new DefaultWorkspaceFilter());
        opts.setStrict(true);
        Importer importer = new Importer(opts);
        importer.run(archive, admin.getRootNode());

    }
}

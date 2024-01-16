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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.Properties;

import javax.jcr.Binary;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.ValueFactory;
import javax.jcr.nodetype.NodeType;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.jackrabbit.vault.fs.api.IdConflictPolicy;
import org.apache.jackrabbit.vault.fs.api.ImportMode;
import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.config.ConfigurationException;
import org.apache.jackrabbit.vault.fs.config.DefaultMetaInf;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.fs.filter.DefaultPathFilter;
import org.apache.jackrabbit.vault.fs.io.ImportOptions;
import org.apache.jackrabbit.vault.fs.io.Importer;
import org.apache.jackrabbit.vault.fs.io.ZipArchive;
import org.apache.jackrabbit.vault.packaging.ExportOptions;
import org.apache.jackrabbit.vault.packaging.PackageProperties;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.apache.jackrabbit.vault.util.PathUtil;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IdConflictPolicyIT extends IntegrationTestBase {

    public static final String TEST_ROOT = "testroot";

    private static final Logger log = LoggerFactory.getLogger(IdConflictPolicyIT.class);

    private Node testRoot;

    @Before
    public void before() throws RepositoryException, Exception {
        testRoot = admin.getRootNode().addNode(TEST_ROOT);
    }

    @Test
    public void testInstallPackage_CREATE_NEW_ID() throws Exception {
        test(IdConflictPolicy.CREATE_NEW_ID, null, null, true, true);
    }

    @Test
    public void testInstallPackage_FAIL() throws Exception {
        test(IdConflictPolicy.FAIL, RepositoryException.class, null, false, false);
    }

    @Test
    public void testInstallPackage_FORCE_REMOVE_CONFLICTING_ID() throws Exception {
        test(IdConflictPolicy.FORCE_REMOVE_CONFLICTING_ID, null, null, false, false);
    }

    @Test
    public void testInstallPackage_LEGACY() throws Exception {
        test(IdConflictPolicy.LEGACY, RepositoryException.class, IllegalStateException.class, false, false);
    }

    private void test(IdConflictPolicy policy, Class<?> expectedException, Class<?> expectedRootCause, boolean expectNewId,
            boolean expectRenamedNodeKept) throws Exception {
        String srcName = String.format("%s-%x.txt", policy, System.nanoTime());
        String srcPath = PathUtil.append(testRoot.getPath(), srcName);

        Node asset = testRoot.addNode(srcName, NodeType.NT_FOLDER);
        addFileNode(asset, "binary.txt");

        asset.addMixin(NodeType.MIX_REFERENCEABLE);
        admin.save();

        String id1 = asset.getIdentifier();

        File pkgFile = exportContentPackage(srcPath);

        String dstPath = srcPath + "-renamed";
        admin.move(srcPath, dstPath);
        assertNodeMissing(srcPath);

        try {
            installContentPackage(pkgFile, policy);
        } catch (Exception ex) {
            if (expectedException == null) {
                throw ex;
            } else {
                assertTrue("expected: " + expectedException + ", but got: " + ex.getClass(), expectedException.isInstance(ex));
                if (expectedRootCause != null) {
                    Throwable rc = ExceptionUtils.getRootCause(ex);
                    assertTrue("expected: " + expectedRootCause + ", but got: " + rc.getClass(), expectedRootCause.isInstance(rc));
                }
                // expected exception -> test done
                return;
            }
        }

        if (expectRenamedNodeKept) {
            assertNodeExists(dstPath);
        } else {
            assertNodeMissing(dstPath);
        }

        assertNodeExists(srcPath);
        assertNodeExists(srcPath + "/binary.txt");

        Node asset2 = testRoot.getNode(srcName);
        String id2 = asset2.getIdentifier();
        if (expectNewId) {
            assertNotEquals(id1, id2);
        } else {
            assertEquals(id1, id2);
        }
    }

    private Node addFileNode(Node parent, String name) throws Exception {

        ValueFactory valueFactory = parent.getSession().getValueFactory();
        Binary contentValue = valueFactory.createBinary(new ByteArrayInputStream("Hello, world!".getBytes()));
        Node fileNode = parent.addNode(name, NodeType.NT_FILE);
        Node resNode = fileNode.addNode("jcr:content", "nt:resource");
        resNode.setProperty("jcr:mimeType", "text/plain");
        resNode.setProperty("jcr:data", contentValue);

        return fileNode;
    }

    private File exportContentPackage(String path) throws Exception {
        ExportOptions opts = new ExportOptions();
        DefaultMetaInf inf = new DefaultMetaInf();
        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();

        PathFilterSet pfs = new PathFilterSet(path);
        pfs.addInclude(new DefaultPathFilter(path + "/.*"));
        filter.add(pfs);

        inf.setFilter(filter);
        Properties props = new Properties();
        props.setProperty(PackageProperties.NAME_GROUP, "jackrabbit/test");
        props.setProperty(PackageProperties.NAME_NAME, "test-package");
        inf.setProperties(props);

        opts.setMetaInf(inf);
        File pkgFile = File.createTempFile("testImportMovedResource", ".zip");
        try (VaultPackage pkg = packMgr.assemble(admin, opts, pkgFile)) {
            return pkg.getFile();
        }
    }

    private void installContentPackage(File pkgFile, IdConflictPolicy policy)
            throws RepositoryException, IOException, ConfigurationException {

        try (ZipArchive archive = new ZipArchive(pkgFile);) {
            archive.open(true);
            ImportOptions opts = getDefaultOptions();
            opts.setIdConflictPolicy(policy);
            opts.setFilter(archive.getMetaInf().getFilter());
            opts.setImportMode(ImportMode.UPDATE_PROPERTIES);

            opts.setStrict(true);
            Importer importer = new Importer(opts);

            log.info("importing");
            importer.run(archive, admin.getRootNode());
        }
    }
}

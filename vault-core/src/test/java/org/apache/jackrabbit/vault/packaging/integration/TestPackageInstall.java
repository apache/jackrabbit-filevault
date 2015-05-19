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

import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;

import org.apache.commons.io.FileUtils;
import org.apache.jackrabbit.vault.fs.io.ImportOptions;
import org.apache.jackrabbit.vault.packaging.InstallContext;
import org.apache.jackrabbit.vault.packaging.JcrPackage;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.tika.io.IOUtils;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * <code>TestPackageInstall</code>...
 */
public class TestPackageInstall extends IntegrationTestBase {

    /**
     * Installs a package that contains and checks if everything is correct.
     */
    @Test
    public void testUpload() throws RepositoryException, IOException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("testpackages/tmp.zip"), false);
        assertNotNull(pack);
        assertNodeExists("/etc/packages/my_packages/tmp.zip");

        // upload already unrwapps it, so check if definition is ok
        assertNodeExists("/etc/packages/my_packages/tmp.zip/jcr:content/vlt:definition");

        // todo: check definition props

    }

    /**
     * Test if rewrap of a small package works
     */
    @Test
    public void testRewrap() throws RepositoryException, IOException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("testpackages/tmp.zip"), false);
        assertNotNull(pack);

        ImportOptions opts = getDefaultOptions();
        pack.install(opts);

        packMgr.rewrap(pack, opts.getListener());
    }

    /**
     * Tests if unwrapping an already installed package preserves the status
     */
    @Test
    public void testUnwrapPreserveInstall() throws RepositoryException, IOException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("testpackages/tmp.zip"), true, true);
        assertNotNull(pack);
        assertTrue(pack.isValid());
        assertNodeExists("/etc/packages/my_packages/tmp.zip");
        pack.install(getDefaultOptions());
        assertNodeExists("/tmp/foo");

        long lastUnpacked = pack.getDefinition().getLastUnpacked().getTimeInMillis();
        assertTrue(lastUnpacked > 0);

        // now upload again, but don't install
        pack = packMgr.upload(getStream("testpackages/tmp.zip"), true, true);
        assertNotNull(pack);
        PackageId pkgId = pack.getDefinition().getId();
        assertTrue(pack.isValid());
        assertTrue(pack.isInstalled());
        assertEquals(lastUnpacked, pack.getDefinition().getLastUnpacked().getTimeInMillis());

        // now re-acquire package and test again
        pack = packMgr.open(pkgId);
        assertTrue(pack.isValid());
        assertTrue(pack.isInstalled());
        assertEquals(lastUnpacked, pack.getDefinition().getLastUnpacked().getTimeInMillis());
    }

    /**
     * Installs a package that contains and checks if everything is correct.
     */
    @Test
    public void testUploadWithThumbnail() throws RepositoryException, IOException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("testpackages/tmp_with_thumbnail.zip"), false);
        assertNotNull(pack);
        assertNodeExists("/etc/packages/my_packages/tmp.zip");

        // upload already unrwapps it, so check if definition is ok
        assertNodeExists("/etc/packages/my_packages/tmp.zip/jcr:content/vlt:definition/thumbnail.png");
    }

    /**
     * Installs a package that contains /tmp/fullcoverage/a/aa using a vlt:FullCoverage mixin.
     * check if the package manager installs that node type although not present in the package.
     */
    @Test
    public void testFullCoverageNT() throws RepositoryException, IOException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("testpackages/fullcoverage.zip"), false);
        assertNotNull(pack);

        // just extract - no snapshots
        pack.extract(getDefaultOptions());
        assertNodeExists("/tmp/fullcoverage/a/aa");

        admin.getWorkspace().getNodeTypeManager().getNodeType("vlt:FullCoverage");
    }

    /**
     * Installs a package that contains a folder below a jcr:resource which is augmented by a
     * mixin that should allow a sub folder.
     */
    @Test
    public void testDeepMixin() throws RepositoryException, IOException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("testpackages/deepmixintest.zip"), false);
        assertNotNull(pack);

        // just extract - no snapshots
        pack.extract(getDefaultOptions());
        assertNodeExists("/etc/designs/apache/images/backgroundImage.png/jcr:content/dam:thumbnails/dam:thumbnail_48.png");
    }

    /**
     * Installs a package that contains a folder a filter to a jcr:content[nt:unstructured] node.
     * See bug #42562
     */
    @Test
    public void testJcrContent() throws RepositoryException, IOException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("testpackages/tmp_testpage_jcr_content.zip"), false);
        assertNotNull(pack);

        // just extract - no snapshots
        pack.extract(getDefaultOptions());
        assertNodeExists("/tmp/testpage/jcr:content/foo");
    }

    /**
     * Installs a package that just adds a property to the root node.
     */
    @Test
    public void testRootImport() throws RepositoryException, IOException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("testpackages/testrootimport.zip"), false);
        assertNotNull(pack);

        // just extract - no snapshots
        pack.extract(getDefaultOptions());
        assertProperty("/testproperty", "hello");
    }

    /**
     * Installs a package with an install hook
     */
    @Test
    public void testHook() throws RepositoryException, IOException, PackageException {
        if (admin.nodeExists("/testroot")) {
            admin.getNode("/testroot").remove();
        }
        admin.getRootNode().addNode("testroot", "nt:unstructured").addNode("testnode", "nt:unstructured");
        admin.save();
        JcrPackage pack = packMgr.upload(getStream("testpackages/test_hook.zip"), false);
        assertNotNull(pack);
        pack.install(getDefaultOptions());
        assertTrue(admin.propertyExists("/testroot/hook-example"));
    }

    /**
     * Installs a package with an install hook
     */
    @Test
    public void testHookFail() throws RepositoryException, IOException, PackageException {
        if (admin.nodeExists("/testroot")) {
            admin.getNode("/testroot").remove();
        }
        admin.save();
        JcrPackage pack = packMgr.upload(getStream("testpackages/test_hook.zip"), false);
        assertNotNull(pack);
        try {
            pack.install(getDefaultOptions());
            fail("installing failing hook should fail");
        } catch (PackageException e) {
            // ok
        }
    }

    /**
     * Installs a package with an invalid hook
     */
    @Test
    public void testInvalidHook() throws RepositoryException, IOException {
        JcrPackage pack = packMgr.upload(getStream("testpackages/invalid_hook.zip"), false);
        assertNotNull(pack);

        try {
            pack.install(getDefaultOptions());
            fail("Package install should fail.");
        } catch (PackageException e) {
            // ok
        }
    }

    /**
     * Installs a package with an external hook
     */
    @Test
    public void testExternalHook() throws RepositoryException, IOException, PackageException {
        if (!admin.nodeExists("/testroot")) {
            admin.getRootNode().addNode("testroot", "nt:unstructured");
            admin.save();
        }

        JcrPackage pack = packMgr.upload(getStream("testpackages/external_hook.zip"), false);
        assertNotNull(pack);

        pack.install(getDefaultOptions());

        assertProperty("/testroot/TestHook1", InstallContext.Phase.END.toString());
        assertProperty("/testroot/TestHook2", InstallContext.Phase.END.toString());
    }

    /**
     * Installs a package with no properties
     */
    @Test
    public void testNoProperties() throws RepositoryException, IOException, PackageException {
        File tmpFile = File.createTempFile("vlttest", "zip");
        IOUtils.copy(getStream("testpackages/tmp_no_properties.zip"), FileUtils.openOutputStream(tmpFile));
        JcrPackage pack = packMgr.upload(tmpFile, true, true, "testpackage", false);
        assertNotNull(pack);

        pack.install(getDefaultOptions());
    }

    @Test
    public void testDeepContentImport() throws IOException, RepositoryException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("testpackages/tmp_test_deep.zip"), false);
        assertNotNull(pack);
        pack.install(getDefaultOptions());

        assertNodeExists("/tmp/test/content/foo/jcr:content/a/b/foo.jsp/jcr:content");
        assertNodeExists("/tmp/test/content/foo/jcr:content/a/c/resource");
        assertNodeExists("/tmp/test/content/foo/jcr:content/a/d");
        assertNodeExists("/tmp/test/content/foo/jcr:content/a/folder/file.txt/jcr:content");
    }

    /**
     * installs a package that contains a node with childnode ordering and full-coverage sub nodes.
     * see JCRVLT-24
     */
    @Test
    public void testChildNodeOrder() throws IOException, RepositoryException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("testpackages/test_childnodeorder.zip"), false);
        assertNotNull(pack);
        pack.install(getDefaultOptions());

        assertNodeExists("/tmp/ordertest/test/rail/items/modes/items");
        NodeIterator iter = admin.getNode("/tmp/ordertest/test/rail/items/modes/items").getNodes();
        StringBuilder names = new StringBuilder();
        while (iter.hasNext()) {
            names.append(iter.nextNode().getName()).append(",");
        }
        assertEquals("child order", "a,d,b,c,", names.toString());
    }

    /**
     * installs a package that contains a node with childnode ordering and full-coverage sub nodes.
     * see JCRVLT-44
     */
    @Test
    public void testChildNodeOrder2() throws IOException, RepositoryException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("testpackages/test_childnodeorder2.zip"), false);
        assertNotNull(pack);
        pack.install(getDefaultOptions());

        assertNodeExists("/tmp/test/en");
        NodeIterator iter = admin.getNode("/tmp/test/en").getNodes();
        StringBuilder names = new StringBuilder();
        while (iter.hasNext()) {
            names.append(iter.nextNode().getName()).append(",");
        }
        assertEquals("child order", "jcr:content,toolbar,products,services,company,events,support,community,blog,", names.toString());
    }

    // todo: upload with version
    // todo: install / uninstall
    // todo: sub packages
    // todo: rename

}
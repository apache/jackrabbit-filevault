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
import java.io.IOException;
import java.io.InputStream;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.nodetype.NodeType;

import org.apache.jackrabbit.commons.JcrUtils;
import org.apache.jackrabbit.vault.packaging.JcrPackage;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;

/**
 * <code>TestEmptyPackage</code>...
 */
public class TestEmptyPackage extends IntegrationTestBase {

    /**
     * Installs a package that contains /tmp/foo/bar/tobi and then installs one
     * that is empty but contains a filter for '/tmp'. expect /tmp to be removed.
     */
    @Test
    public void installEmptyLevel1() throws RepositoryException, IOException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("testpackages/tmp.zip"), false);
        assertNotNull(pack);
        pack.install(getDefaultOptions());
        assertNodeExists("/tmp/foo/bar/tobi");

        // now install the empty package
        pack = packMgr.upload(getStream("testpackages/empty_tmp.zip"), false);
        assertNotNull(pack);
        pack.install(getDefaultOptions());
        assertNodeMissing("/tmp");
    }

    /**
     * Installs a package that contains /tmp/foo/bar/tobi and then installs one
     * that is empty but contains a filter for '/tmp/foo'. expect /tmp/foo to be removed.
     */
    @Test
    public void installEmptyLevel2() throws RepositoryException, IOException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("testpackages/tmp.zip"), false);
        assertNotNull(pack);
        pack.install(getDefaultOptions());
        assertNodeExists("/tmp/foo/bar/tobi");

        // now install the empty package
        pack = packMgr.upload(getStream("testpackages/empty_tmp_foo.zip"), false);
        assertNotNull(pack);
        pack.install(getDefaultOptions());
        assertNodeExists("/tmp");
        assertNodeMissing("/tmp/foo");
    }

    /**
     * Installs a package that contains /tmp/foo/bar/tobi and then installs one
     * that is empty but contains a filter for '/tmp/foo/bar'. expect /tmp/foo/bar to be removed.
     */
    @Test
    public void installEmptyLevel3() throws RepositoryException, IOException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("testpackages/tmp.zip"), false);
        assertNotNull(pack);
        pack.install(getDefaultOptions());
        assertNodeExists("/tmp/foo/bar/tobi");

        // now install the empty package
        pack = packMgr.upload(getStream("testpackages/empty_tmp_foo_bar.zip"), false);
        assertNotNull(pack);
        pack.install(getDefaultOptions());
        assertNodeExists("/tmp/foo");
        assertNodeMissing("/tmp/foo/bar");
    }

    /**
     * Installs a package that contains /tmp/test/content/foo/foo.jsp and then creates a new node
     * /tmp/test/content/bar/bar.jsp. Tests if after reinstall the new node was deleted.
     */
    @Test
    public void installEmptyFolder() throws RepositoryException, IOException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("testpackages/tmp_test_folders.zip"), false);
        assertNotNull(pack);
        pack.install(getDefaultOptions());
        assertNodeExists("/tmp/test/content/foo/foo.jsp");

        // create new node
        Node content = admin.getNode("/tmp/test/content");
        Node bar = content.addNode("bar", NodeType.NT_FOLDER);
        InputStream is = new ByteArrayInputStream("hello, world.".getBytes());
        JcrUtils.putFile(bar, "bar.jsp", "text/plain", is);
        admin.save();

        // now re-install package
        pack.install(getDefaultOptions());

        assertNodeMissing("/tmp/test/content/bar");
        assertNodeMissing("/tmp/test/content/bar/bar.jsp");
    }

    /**
     * Installs a package that contains /tmp and then uninstalls it.
     */
    @Test
    public void installUninstallLevel1() throws RepositoryException, IOException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("testpackages/tmp.zip"), false);
        assertNotNull(pack);
        pack.install(getDefaultOptions());
        assertNodeExists("/tmp/foo/bar/tobi");

        pack.uninstall(getDefaultOptions());
        assertNodeMissing("/tmp");
    }

    /**
     * Installs a package that contains /tmp/foo and then uninstalls it.
     * expect the intermediate node /tmp remains.
     */
    @Test
    public void installUninstallLevel2() throws RepositoryException, IOException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("testpackages/tmp_foo.zip"), false);
        assertNotNull(pack);
        pack.install(getDefaultOptions());
        assertNodeExists("/tmp/foo/bar/tobi");

        pack.uninstall(getDefaultOptions());
        assertNodeExists("/tmp");
        assertNodeMissing("/tmp/foo");
    }

    /**
     * Installs a package that contains /tmp/foo/bar and then unnstalls it.
     * expect the intermediate node /tmp/foo remains.
     */
    @Test
    public void installUninstallLevel3() throws RepositoryException, IOException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("testpackages/tmp_foo_bar.zip"), false);
        assertNotNull(pack);
        pack.install(getDefaultOptions());
        assertNodeExists("/tmp/foo/bar/tobi");

        pack.uninstall(getDefaultOptions());
        assertNodeExists("/tmp/foo");
        assertNodeMissing("/tmp/foo/bar");
    }


    /**
     * Installs a package that contains /tmp, then and empty one, then unnstalls it.
     * expect the original /tmp/foo/bar/tobi remaining.
     */
    @Test
    public void installUninstallSubsequent() throws RepositoryException, IOException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("testpackages/tmp.zip"), false);
        assertNotNull(pack);
        pack.install(getDefaultOptions());
        assertNodeExists("/tmp/foo/bar/tobi");

        // now install the empty package
        JcrPackage pack2 = packMgr.upload(getStream("testpackages/empty_tmp_foo_bar.zip"), false);
        assertNotNull(pack2);
        pack2.install(getDefaultOptions());

        assertNodeExists("/tmp/foo");
        assertNodeMissing("/tmp/foo/bar");

        pack2.uninstall(getDefaultOptions());
        assertNodeExists("/tmp/foo/bar/tobi");
    }

    /**
     * Installs a package that contains /tmp/foo/bar/test.txt and then uninstalls it.
     */
    @Test
    public void installUninstallFile() throws RepositoryException, IOException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("testpackages/tmp_foo_bar_test.zip"), false);
        assertNotNull(pack);
        pack.install(getDefaultOptions());
        assertNodeExists("/tmp/foo/bar/test.txt");

        pack.uninstall(getDefaultOptions());
        assertNodeExists("/tmp/foo/bar");
        assertNodeMissing("/tmp/foo/bar/test.txt");
    }


    /**
     * Installs a package that contains /tmp/foo/bar/test.txt but no filter for it.
     */
    @Test
    public void installInstallNoFilter() throws RepositoryException, IOException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("testpackages/tmp_foo_bar_test_nofilter.zip"), false);
        assertNotNull(pack);
        pack.install(getDefaultOptions());
        assertNodeExists("/tmp/foo/bar/test.txt");

        pack.uninstall(getDefaultOptions());
        assertNodeExists("/tmp/foo/bar/test.txt");
    }


    /**
     * Installs a package that contains no filter and no .content.xml files.
     */
    @Test
    public void installInstallMinimal() throws RepositoryException, IOException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("testpackages/tmp_foo_bar_test_minimal.zip"), false);
        assertNotNull(pack);
        pack.install(getDefaultOptions());
        assertNodeExists("/tmp/foo/bar/test.txt");

        pack.uninstall(getDefaultOptions());
        assertNodeExists("/tmp/foo/bar/test.txt");
    }


}
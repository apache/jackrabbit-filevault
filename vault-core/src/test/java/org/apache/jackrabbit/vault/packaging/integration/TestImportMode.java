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

import java.io.IOException;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.apache.jackrabbit.vault.packaging.JcrPackage;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.junit.Test;

/**
 * <code>TestPackageInstall</code>...
 */
public class TestImportMode extends IntegrationTestBase {

    /**
     * Installs a package with the filter: "/tmp/foo", mode="replace"
     */
    @Test
    public void testReplace() throws RepositoryException, IOException, PackageException {
        Node tmp = admin.getRootNode().addNode("tmp");
        Node foo = tmp.addNode("foo");
        Node old = foo.addNode("old");
        admin.save();
        assertNodeExists("/tmp/foo/old");
        assertNodeMissing("/tmp/foo/bar");

        JcrPackage pack = packMgr.upload(getStream("testpackages/tmp_mode_replace.zip"), false);
        pack.extract(getDefaultOptions());

        assertNodeMissing("/tmp/foo/old");
        assertNodeExists("/tmp/foo/bar");
        assertProperty("/tmp/foo/bar/testProperty", "new");
    }

    /**
     * Installs a package with the filter: "/tmp/foo", mode="merge"
     */
    @Test
    public void testMerge() throws RepositoryException, IOException, PackageException {
        Node tmp = admin.getRootNode().addNode("tmp");
        Node foo = tmp.addNode("foo");
        Node old = foo.addNode("old");
        Node bar = foo.addNode("bar");
        bar.setProperty("testProperty", "old");
        admin.save();
        assertNodeExists("/tmp/foo/old");
        assertNodeMissing("/tmp/foo/new");

        JcrPackage pack = packMgr.upload(getStream("testpackages/tmp_mode_merge.zip"), false);
        pack.extract(getDefaultOptions());

        assertNodeExists("/tmp/foo/old");
        assertNodeExists("/tmp/foo/bar");
        assertNodeExists("/tmp/foo/new");
        assertProperty("/tmp/foo/bar/testProperty", "old");
    }

    /**
     * Installs a package with the filter: "/tmp/foo", mode="update"
     */
    @Test
    public void testUpdate() throws RepositoryException, IOException, PackageException {
        Node tmp = admin.getRootNode().addNode("tmp");
        Node foo = tmp.addNode("foo");
        Node old = foo.addNode("old");
        Node bar = foo.addNode("bar");
        bar.setProperty("testProperty", "old");
        admin.save();
        assertNodeExists("/tmp/foo/old");
        assertNodeMissing("/tmp/foo/new");

        JcrPackage pack = packMgr.upload(getStream("testpackages/tmp_mode_update.zip"), false);
        pack.extract(getDefaultOptions());

        assertNodeExists("/tmp/foo/old");
        assertNodeExists("/tmp/foo/bar");
        assertNodeExists("/tmp/foo/new");
        assertProperty("/tmp/foo/bar/testProperty", "new");
    }

}
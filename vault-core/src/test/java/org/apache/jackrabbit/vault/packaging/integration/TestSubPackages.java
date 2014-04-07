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

import javax.jcr.RepositoryException;

import org.apache.jackrabbit.vault.fs.io.ImportOptions;
import org.apache.jackrabbit.vault.packaging.JcrPackage;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;

/**
 * Tests that covers sub packages. the package "my_packages:subtest" contains 2 sub packages
 * "my_packages:sub_a" and "my_packages:sub_b" which each contain 1 node /tmp/a and /tmp/b respectively.
 */
public class TestSubPackages extends IntegrationTestBase {

    /**
     * Installs a package that contains sub packages non recursive
     */
    @Test
    public void testNonRecursive() throws RepositoryException, IOException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("testpackages/subtest.zip"), false);
        assertNotNull(pack);

        // install
        ImportOptions opts = getDefaultOptions();
        opts.setNonRecursive(true);
        pack.install(opts);

        // check for sub packages
        assertNodeExists("/etc/packages/my_packages/sub_a.zip");
        assertNodeExists("/etc/packages/my_packages/sub_b.zip");

        // check for snapshots
        assertNodeMissing("/etc/packages/my_packages/.snapshot/sub_a.zip");
        assertNodeMissing("/etc/packages/my_packages/.snapshot/sub_b.zip");

        assertNodeMissing("/tmp/a");
        assertNodeMissing("/tmp/b");
    }

    /**
     * Installs a package that contains sub packages recursive but has a sub package handling that ignores A
     */
    @Test
    public void testRecursiveIgnoreA() throws RepositoryException, IOException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("testpackages/subtest_ignore_a.zip"), false);
        assertNotNull(pack);

        // install
        ImportOptions opts = getDefaultOptions();
        opts.setNonRecursive(false);
        pack.install(opts);

        // check for sub packages
        assertNodeExists("/etc/packages/my_packages/sub_a.zip"); // todo: ignore should ignore A completely
        assertNodeExists("/etc/packages/my_packages/sub_b.zip");

        assertNodeMissing("/tmp/a");
        assertNodeExists("/tmp/b");
    }

    /**
     * Installs a package that contains sub packages recursive but has a sub package handling that ignores A
     */
    @Test
    public void testRecursiveAddA() throws RepositoryException, IOException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("testpackages/subtest_add_a.zip"), false);
        assertNotNull(pack);

        // install
        ImportOptions opts = getDefaultOptions();
        opts.setNonRecursive(false);
        pack.install(opts);

        // check for sub packages
        assertNodeExists("/etc/packages/my_packages/sub_a.zip");
        assertNodeExists("/etc/packages/my_packages/sub_b.zip");

        assertNodeMissing("/tmp/a");
        assertNodeExists("/tmp/b");
    }

    /**
     * Installs a package that contains sub packages recursive but has a sub package handling that only extracts A
     */
    @Test
    public void testRecursiveExtractA() throws RepositoryException, IOException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("testpackages/subtest_extract_a.zip"), false);
        assertNotNull(pack);

        // install
        ImportOptions opts = getDefaultOptions();
        opts.setNonRecursive(false);
        pack.install(opts);

        // check for sub packages
        assertNodeExists("/etc/packages/my_packages/sub_a.zip");
        assertNodeExists("/etc/packages/my_packages/sub_b.zip");

        // check for snapshots
        assertNodeMissing("/etc/packages/my_packages/.snapshot/sub_a.zip");
        assertNodeExists("/etc/packages/my_packages/.snapshot/sub_b.zip");

        assertNodeExists("/tmp/a");
        assertNodeExists("/tmp/b");
    }


    /**
     * Installs a package that contains sub packages recursive
     */
    @Test
    public void testRecursive() throws RepositoryException, IOException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("testpackages/subtest.zip"), false);
        assertNotNull(pack);

        // install
        ImportOptions opts = getDefaultOptions();
        opts.setNonRecursive(false);
        pack.install(opts);

        // check for sub packages
        assertNodeExists("/etc/packages/my_packages/sub_a.zip");
        assertNodeExists("/etc/packages/my_packages/sub_b.zip");

        // check for snapshots
        assertNodeExists("/etc/packages/my_packages/.snapshot/sub_a.zip");
        assertNodeExists("/etc/packages/my_packages/.snapshot/sub_b.zip");

        assertNodeExists("/tmp/a");
        assertNodeExists("/tmp/b");
    }


    /**
     * Uninstalls a package that contains sub packages non recursive
     */
    @Test
    public void testUninstallNonRecursive() throws RepositoryException, IOException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("testpackages/subtest.zip"), false);
        assertNotNull(pack);

        // install
        ImportOptions opts = getDefaultOptions();
        opts.setNonRecursive(false);
        pack.install(opts);

        assertNodeExists("/tmp/a");
        assertNodeExists("/tmp/b");

        // uninstall
        opts.setNonRecursive(true);
        pack.uninstall(opts);

        assertNodeMissing("/etc/packages/my_packages/sub_a.zip");
        assertNodeMissing("/etc/packages/my_packages/sub_b.zip");
        assertNodeExists("/tmp/a");
        assertNodeExists("/tmp/b");

    }

    /**
     * Uninstalls a package that contains sub packages recursive
     */
    @Test
    public void testUninstallRecursive() throws RepositoryException, IOException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("testpackages/subtest.zip"), false);
        assertNotNull(pack);

        // install
        ImportOptions opts = getDefaultOptions();
        opts.setNonRecursive(false);
        pack.install(opts);

        assertNodeExists("/tmp/a");
        assertNodeExists("/tmp/b");

        // uninstall
        opts.setNonRecursive(false);
        pack.uninstall(opts);

        assertNodeMissing("/etc/packages/my_packages/sub_a.zip");
        assertNodeMissing("/etc/packages/my_packages/sub_b.zip");
        assertNodeMissing("/tmp/a");
        assertNodeMissing("/tmp/b");

    }

    /**
     * Uninstalls a package that contains sub packages where a snapshot of a sub package was deleted
     */
    @Test
    public void testUninstallMissingSnapshot() throws RepositoryException, IOException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("testpackages/subtest.zip"), false);
        assertNotNull(pack);

        // install
        ImportOptions opts = getDefaultOptions();
        opts.setNonRecursive(false);
        pack.install(opts);

        assertNodeExists("/tmp/a");
        assertNodeExists("/tmp/b");

        admin.getNode("/etc/packages/my_packages/.snapshot/sub_a.zip").remove();
        admin.save();

        // uninstall
        opts.setNonRecursive(false);
        pack.uninstall(opts);

        assertNodeMissing("/etc/packages/my_packages/sub_a.zip");
        assertNodeMissing("/etc/packages/my_packages/sub_b.zip");
        assertNodeExists("/tmp/a");
        assertNodeMissing("/tmp/b");

    }


}
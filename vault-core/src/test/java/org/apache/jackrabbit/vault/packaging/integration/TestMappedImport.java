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

import org.apache.jackrabbit.vault.fs.api.MultiPathMapping;
import org.apache.jackrabbit.vault.fs.io.ImportOptions;
import org.apache.jackrabbit.vault.packaging.JcrPackage;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.junit.Ignore;
import org.junit.Test;

/**
 */
public class TestMappedImport extends IntegrationTestBase {

    @Test
    public void testSimple() throws RepositoryException, IOException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("testpackages/tmp_foo.zip"), false);
        ImportOptions opts = getDefaultOptions();

        MultiPathMapping mapping = new MultiPathMapping();
        mapping.link("/tmp/foo", "/tmp/mapped");
        opts.setPathMapping(mapping);
        pack.extract(opts);

        assertNodeMissing("/tmp/foo");
        assertNodeExists("/tmp/mapped");
    }

    /**
     * Tests if uninstalling a remapped package works
     */
    @Ignore("JCRVLT-80")
    @Test
    public void testSimpleUninstall() throws RepositoryException, IOException, PackageException {
        assertNodeMissing("/tmp/foo");
        assertNodeMissing("/tmp/mapped");

        JcrPackage pack = packMgr.upload(getStream("testpackages/tmp_foo.zip"), false);
        PackageId id = pack.getDefinition().getId();
        ImportOptions opts = getDefaultOptions();

        MultiPathMapping mapping = new MultiPathMapping();
        mapping.link("/tmp/foo", "/tmp/mapped");
        opts.setPathMapping(mapping);
        pack.install(opts);
        pack.close();

        assertNodeMissing("/tmp/foo");
        assertNodeExists("/tmp/mapped");

        pack = packMgr.open(id);
        pack.uninstall(getDefaultOptions());
        assertNodeMissing("/tmp/foo");
        assertNodeMissing("/tmp/mapped");
    }

    /**
     * Tests if installing the same package at multiple places works
     */
    @Test
    public void testMultiInstall() throws RepositoryException, IOException, PackageException {
        assertNodeMissing("/tmp/foo");
        assertNodeMissing("/tmp/mapped0");
        assertNodeMissing("/tmp/mapped1");

        JcrPackage pack = packMgr.upload(getStream("testpackages/tmp_foo.zip"), false);
        PackageId id = pack.getDefinition().getId();
        ImportOptions opts = getDefaultOptions();

        MultiPathMapping mapping = new MultiPathMapping();
        mapping.link("/tmp/foo", "/tmp/mapped0");
        opts.setPathMapping(mapping);
        pack.install(opts);
        pack.close();

        assertNodeMissing("/tmp/foo");
        assertNodeExists("/tmp/mapped0");

        pack = packMgr.open(id);
        opts = getDefaultOptions();
        mapping = new MultiPathMapping();
        mapping.link("/tmp/foo", "/tmp/mapped1");
        opts.setPathMapping(mapping);
        pack.install(opts);
        pack.close();

        assertNodeMissing("/tmp/foo");
        assertNodeExists("/tmp/mapped1");

    }

    @Test
    public void testSimpleDelete() throws RepositoryException, IOException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("testpackages/tmp_foo.zip"), false);
        ImportOptions opts = getDefaultOptions();

        MultiPathMapping mapping = new MultiPathMapping();
        mapping.link("/tmp/foo", "/tmp/mapped");
        opts.setPathMapping(mapping);
        pack.extract(opts);

        assertNodeMissing("/tmp/foo");
        assertNodeExists("/tmp/mapped");

        pack = packMgr.upload(getStream("testpackages/empty_tmp_foo.zip"), false);
        pack.extract(opts);

        assertNodeMissing("/tmp/mapped");
    }

    /**
     * Tests if remapping within a generic artifact works.
     * This is currently not implemented.
     */
    @Ignore("JCRVLT-79")
    @Test
    public void testGenericArtifact() throws RepositoryException, IOException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("testpackages/fullcoverage.zip"), false);
        ImportOptions opts = getDefaultOptions();

        MultiPathMapping mapping = new MultiPathMapping();
        mapping.link("/tmp/fullcoverage/a", "/tmp/fullcoverage/mapped");
        opts.setPathMapping(mapping);
        pack.extract(opts);

        assertNodeMissing("/tmp/fullcoverage/a");
        assertNodeExists("/tmp/fullcoverage/mapped");
    }

    @Test
    public void testNested() throws RepositoryException, IOException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("testpackages/tmp_foo.zip"), false);
        ImportOptions opts = getDefaultOptions();

        MultiPathMapping mapping = new MultiPathMapping();
        mapping.link("/tmp/foo", "/tmp/mapped");
        mapping.link("/tmp/mapped/bar/tobi", "/tmp/mapped/bar/roby");
        opts.setPathMapping(mapping);
        pack.extract(opts);

        assertNodeMissing("/tmp/mapped/bar/tobi");
        assertNodeExists("/tmp/mapped/bar/roby");
    }

    /**
     * Tests if a non-trivial rename remapping works.
     * This is currently not supported
     */
    @Ignore("JCRVLT-78")
    @Test
    public void testNonRename() throws RepositoryException, IOException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("testpackages/tmp_foo.zip"), false);
        ImportOptions opts = getDefaultOptions();

        MultiPathMapping mapping = new MultiPathMapping();
        mapping.link("/tmp/foo", "/libs/foo");
        opts.setPathMapping(mapping);
        pack.extract(opts);

        assertNodeExists("/tmp/foo");
        assertNodeExists("/libs/foo");
    }
}
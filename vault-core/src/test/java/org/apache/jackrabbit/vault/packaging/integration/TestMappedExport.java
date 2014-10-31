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

import javax.jcr.RepositoryException;

import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.fs.config.DefaultMetaInf;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.packaging.ExportOptions;
import org.apache.jackrabbit.vault.packaging.JcrPackage;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * <code>TestEmptyPackage</code>...
 */
public class TestMappedExport extends IntegrationTestBase {

    @Test
    public void exportMapped() throws RepositoryException, IOException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("testpackages/tmp_foo_bar_test.zip"), false);
        assertNotNull(pack);
        pack.extract(getDefaultOptions());
        assertNodeExists("/tmp/foo/bar/test.txt");

        ExportOptions opts = new ExportOptions();
        DefaultMetaInf inf = new DefaultMetaInf();
        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        filter.add(new PathFilterSet("/tmp/foo/bar"));
        inf.setFilter(filter);
        Properties props = new Properties();
        props.setProperty(VaultPackage.NAME_GROUP, "jackrabbit/test");
        props.setProperty(VaultPackage.NAME_NAME, "test-package");
        inf.setProperties(props);

        opts.setMetaInf(inf);
        opts.setMountPath("/tmp/foo");
        opts.setRootPath("/content/geometrixx");
        File tmpFile = File.createTempFile("vaulttest", "zip");
        VaultPackage pkg = packMgr.assemble(admin, opts, tmpFile);

        // check if entries are present
        WorkspaceFilter newFilter = pkg.getMetaInf().getFilter();
        assertTrue(newFilter.contains("/content/geometrixx/bar/test.txt"));
        assertNotNull(pkg.getArchive().getEntry("jcr_root/content/geometrixx/bar/test.txt"));
        pkg.close();
        tmpFile.delete();
    }

}
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
import java.util.Collections;
import java.util.Properties;

import javax.jcr.RepositoryException;

import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.config.DefaultMetaInf;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.packaging.ExportOptions;
import org.apache.jackrabbit.vault.packaging.JcrPackage;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;

public class ManifestCreationExportIT extends IntegrationTestBase {

    @Test
    public void exportPackageCreatesManifest() throws RepositoryException, IOException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("/test-packages/tmp_foo_bar_test.zip"), false);
        assertNotNull(pack);
        pack.extract(getDefaultOptions());
        assertNodeExists("/tmp/foo/bar/test.txt");

        ExportOptions opts = new ExportOptions();
        DefaultMetaInf inf = new DefaultMetaInf();
        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        filter.add(new PathFilterSet("/tmp/foo/bar"));
        filter.add(new PathFilterSet("/tmp/foo/zoo"));
        inf.setFilter(filter);
        Properties props = new Properties();
        props.setProperty(VaultPackage.NAME_GROUP, "jackrabbit/test");
        props.setProperty(VaultPackage.NAME_NAME, "test-package");
        props.setProperty(VaultPackage.NAME_DESCRIPTION, "This is a test package.");
        props.setProperty(VaultPackage.NAME_DEPENDENCIES, "foo:bar:[1.0,2.0)");
        inf.setProperties(props);

        opts.setMetaInf(inf);
        File tmpFile = File.createTempFile("e-vaulttest", ".zip");
        try (VaultPackage pkg = packMgr.assemble(admin, opts, tmpFile)) {
            String expected =
                    "Content-Package-Dependencies:foo:bar:[1.0,2.0)\n" +
                    "Content-Package-Description:This is a test package.\n" +
                    "Content-Package-Id:jackrabbit/test:test-package\n" +
                    "Content-Package-Roots:/tmp/foo/bar,/tmp/foo/zoo\n" +
                    "Content-Package-Type:content\n" +
                    "Manifest-Version:1.0";
            verifyManifest(tmpFile, Collections.<String>emptySet(), expected);
        } finally {
            tmpFile.delete();
        }
    }

}
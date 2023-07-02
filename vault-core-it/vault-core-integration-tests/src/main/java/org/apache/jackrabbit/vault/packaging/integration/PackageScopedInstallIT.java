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

import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.fs.io.ImportOptions;
import org.apache.jackrabbit.vault.packaging.JcrPackage;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.apache.jackrabbit.vault.packaging.ScopedWorkspaceFilter;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;

public class PackageScopedInstallIT extends IntegrationTestBase {

    @Before
    public void beforeEach() {
        clean("/libs");
        clean("/tmp");
    }

    /**
     * Installs a package that contains mixed content
     */
    @Test
    public void testMixedContent() throws RepositoryException, IOException, PackageException {
        assertNodeMissing("/libs/foo");
        assertNodeMissing("/tmp/foo");

        JcrPackage pack = packMgr.upload(getStream("/test-packages/mixed_package.zip"), false);
        assertNotNull(pack);

        // just extract - no snapshots
        ImportOptions opts = getDefaultOptions();
        pack.extract(opts);
        assertNodeExists("/tmp/foo");
        assertNodeExists("/libs/foo");
    }

    /**
     * Installs a package that contains mixed content but filtered for apps
     */
    @Test
    public void testApplication() throws RepositoryException, IOException, PackageException {
        assertNodeMissing("/libs/foo");
        assertNodeMissing("/tmp/foo");

        JcrPackage pack = packMgr.upload(getStream("/test-packages/mixed_package.zip"), false);
        assertNotNull(pack);

        // just extract - no snapshots
        ScopedWorkspaceFilter filter = ScopedWorkspaceFilter.createApplicationScoped(
                (DefaultWorkspaceFilter) pack.getDefinition().getMetaInf().getFilter());

        ImportOptions opts = getDefaultOptions();
        opts.setFilter(filter);
        pack.extract(opts);
        assertNodeMissing("/tmp/foo");
        assertNodeExists("/libs/foo");
    }

    /**
     * Installs a package that contains mixed content but filtered for content
     */
    @Test
    public void testContent() throws RepositoryException, IOException, PackageException {
        assertNodeMissing("/libs/foo");
        assertNodeMissing("/tmp/foo");

        JcrPackage pack = packMgr.upload(getStream("/test-packages/mixed_package.zip"), false);
        assertNotNull(pack);

        // just extract - no snapshots
        ScopedWorkspaceFilter filter = ScopedWorkspaceFilter.createContentScoped((
                DefaultWorkspaceFilter) pack.getDefinition().getMetaInf().getFilter());
        ImportOptions opts = getDefaultOptions();
        opts.setFilter(filter);
        pack.extract(opts);
        assertNodeExists("/tmp/foo");
        assertNodeMissing("/libs/foo");
    }


}
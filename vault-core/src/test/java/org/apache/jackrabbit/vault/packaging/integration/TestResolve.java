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

import org.apache.jackrabbit.vault.packaging.Dependency;
import org.apache.jackrabbit.vault.packaging.JcrPackage;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * <code>TestPackageInstall</code>...
 */
public class TestResolve extends IntegrationTestBase {

    /**
     * Installs some packages and tests the resolve method
     */
    @Test
    public void testResolve() throws RepositoryException, IOException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("testpackages/package_1.0.zip"), false);
        assertNotNull(pack);
        pack.install(getDefaultOptions());

        pack = packMgr.upload(getStream("testpackages/package_2.0.zip"), false);
        assertNotNull(pack);
        pack.install(getDefaultOptions());

        pack = packMgr.upload(getStream("testpackages/empty_tmp.zip"), false);
        assertNotNull(pack);
        pack.install(getDefaultOptions());

        PackageId id = packMgr.resolve(Dependency.fromString("my_packages:package:[1.0,2.0]"), true);
        assertEquals(PackageId.fromString("my_packages:package:2.0"), id);

        id = packMgr.resolve(Dependency.fromString("my_packages:package:[1.0,2.0)"), true);
        assertEquals(PackageId.fromString("my_packages:package:1.0"), id);
    }

    /**
     * uploads and installs some packages and tests the resolve method
     */
    @Test
    public void testResolvePartial() throws RepositoryException, IOException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("testpackages/package_1.0.zip"), false);
        assertNotNull(pack);
        pack.install(getDefaultOptions());

        pack = packMgr.upload(getStream("testpackages/package_2.0.zip"), false);
        assertNotNull(pack);
        // do NOT install
        //pack.install(getDefaultOptions());

        pack = packMgr.upload(getStream("testpackages/empty_tmp.zip"), false);
        assertNotNull(pack);
        pack.install(getDefaultOptions());

        PackageId id = packMgr.resolve(Dependency.fromString("my_packages:package:[1.0,2.0]"), true);
        assertEquals(PackageId.fromString("my_packages:package:1.0"), id);

        id = packMgr.resolve(Dependency.fromString("my_packages:package:[1.0,2.0]"), false);
        assertEquals(PackageId.fromString("my_packages:package:2.0"), id);

    }
}
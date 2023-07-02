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
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.apache.jackrabbit.vault.packaging.PackageExistsException;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.PackageType;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.apache.jackrabbit.vault.packaging.registry.PackageRegistry;
import org.apache.jackrabbit.vault.packaging.registry.RegisteredPackage;
import org.apache.jackrabbit.vault.packaging.registry.impl.JcrPackageRegistry;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PackageTypesIT extends IntegrationTestBase {

    private PackageRegistry registry;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();

        registry = new JcrPackageRegistry(admin);
    }

    private void verifyViaRegistry(String type) throws IOException, PackageExistsException {
        PackageId id = registry.register(getStream("/test-packages/packagetype/" + type + "-pkg.zip"), false);
        RegisteredPackage pkg = registry.open(id);
        PackageType result  = pkg.getPackage().getProperties().getPackageType();
        PackageType expected = "notype".equals(type)
                ? null
                : PackageType.valueOf(type.toUpperCase());
        assertEquals("Package type", expected, result);
    }

    private void verifyPackageTypeViaPackageCreation(WorkspaceFilter filter, PackageType expected)
            throws IOException, RepositoryException {

        ExportOptions options = new ExportOptions();
        DefaultMetaInf meta = new DefaultMetaInf();
        meta.setFilter(filter);

        Properties props = new Properties();
        props.setProperty(VaultPackage.NAME_GROUP, "jackrabbit/test");
        props.setProperty(VaultPackage.NAME_NAME, "package-types-export-package");
        meta.setProperties(props);

        options.setMetaInf(meta);

        File tmpFile = File.createTempFile("vaulttest", "zip");
        try (VaultPackage pkg = packMgr.assemble(admin, options, tmpFile)) {
            PackageType result = pkg.getProperties().getPackageType();
            assertEquals("Package type", expected, result);
        } finally {
            tmpFile.delete();
        }
    }


    /**
     * checks if 'application' package type is correct read from package using the registry.
     */
    @Test
    public void test_type_application_via_registry() throws IOException, PackageException {
        verifyViaRegistry("application");
    }

    /**
     * checks if 'content' package type is correct read from package using the registry.
     */
    @Test
    public void test_type_content_via_registry() throws IOException, PackageException {
        verifyViaRegistry("content");
    }

    /**
     * checks if 'container' package type is correct read from package using the registry.
     */
    @Test
    public void test_type_container_via_registry() throws IOException, PackageException {
        verifyViaRegistry("container");
    }

    /**
     * checks if 'mixed' package type is correct read from package using the registry.
     */
    @Test
    public void test_type_mixed_via_registry() throws IOException, PackageException {
        verifyViaRegistry("mixed");
    }

    /**
     * checks if missing package type is correct read from package using the registry.
     */
    @Test
    public void test_missing_type_via_registry() throws IOException, PackageException {
        verifyViaRegistry("notype");
    }

    /**
     * checks if assembling an "application" package adds the correct package type to the properties.
     */
    @Test
    public void test_export_application() throws IOException, RepositoryException {
        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        filter.add(new PathFilterSet("/libs/foo"));
        filter.add(new PathFilterSet("/apps/foo"));
        verifyPackageTypeViaPackageCreation(filter, PackageType.APPLICATION);
    }

    /**
     * checks if assembling a "content" package adds the correct package type to the properties.
     */
    @Test
    public void test_export_content() throws IOException, RepositoryException {
        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        filter.add(new PathFilterSet("/content/foo"));
        filter.add(new PathFilterSet("/cont/foo"));
        verifyPackageTypeViaPackageCreation(filter, PackageType.CONTENT);
    }

    /**
     * checks if assembling a "mixed" package adds the correct package type to the properties.
     */
    @Test
    public void test_export_mixed() throws IOException, RepositoryException {
        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        filter.add(new PathFilterSet("/content/foo"));
        filter.add(new PathFilterSet("/libs/foo"));
        verifyPackageTypeViaPackageCreation(filter, PackageType.MIXED);
    }
}
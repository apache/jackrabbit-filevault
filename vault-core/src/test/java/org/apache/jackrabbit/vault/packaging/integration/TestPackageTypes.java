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

import org.apache.jackrabbit.vault.packaging.PackageException;
import org.apache.jackrabbit.vault.packaging.PackageExistsException;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.PackageType;
import org.apache.jackrabbit.vault.packaging.registry.PackageRegistry;
import org.apache.jackrabbit.vault.packaging.registry.RegisteredPackage;
import org.apache.jackrabbit.vault.packaging.registry.impl.JcrPackageRegistry;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Test the Package registry interface
 */
public class TestPackageTypes extends IntegrationTestBase {

    private PackageRegistry registry;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();

        registry = new JcrPackageRegistry(admin);
    }

    private void verifyViaRegistry(String type) throws IOException, PackageExistsException {
        PackageId id = registry.register(getStream("testpackages/packagetype/" + type + "-pkg.zip"), false);
        RegisteredPackage pkg = registry.open(id);
        PackageType result  = pkg.getPackage().getProperties().getPackageType();
        PackageType expected = "notype".equals(type)
                ? null
                : PackageType.valueOf(type.toUpperCase());
        assertEquals("Package type", expected, result);
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

}
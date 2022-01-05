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
package org.apache.jackrabbit.vault.packaging.impl;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.apache.jackrabbit.vault.packaging.PackageExistsException;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.integration.IntegrationTestBase;
import org.apache.jackrabbit.vault.packaging.registry.PackageRegistry;
import org.apache.jackrabbit.vault.packaging.registry.RegisteredPackage;
import org.apache.jackrabbit.vault.packaging.registry.impl.JcrRegisteredPackage;
import org.apache.jackrabbit.vault.packaging.registry.impl.MockPackageRegistry;
import org.junit.Assert;
import org.junit.Test;
import org.osgi.util.converter.Converter;
import org.osgi.util.converter.Converters;

public class PackagingImplIT extends IntegrationTestBase {

    @Test
    public void testGetCompositePackageRepository() throws IOException, PackageExistsException {
        PackagingImpl packaging = new PackagingImpl();
        packaging.registries = Arrays.asList(new MockPackageRegistry("package1"), new MockPackageRegistry("package2"));
        // init configuration
        Converter c = Converters.standardConverter();
        Map<String, String> map = new HashMap<>();
        map.put("authIdsForHookExecution", "");
        map.put("authIdsForRootInstallation", "");
        packaging.config = c.convert(map).to(PackagingImpl.Config.class);
        // add new package to MockPackageRegistry
        PackageRegistry compositeRegistry = packaging.getCompositePackageRegistry(admin, false);
        Assert.assertTrue(compositeRegistry.contains(PackageId.fromString("package1")));
        Assert.assertTrue(compositeRegistry.contains(PackageId.fromString("package2")));
        // use real package
        try (InputStream input = getClass().getResourceAsStream("/test-packages/package_1.0.zip")) {
            compositeRegistry.register(input, false);
        }
        RegisteredPackage registeredPackage = compositeRegistry.open(MockPackageRegistry.NEW_PACKAGE_ID);
        Assert.assertNotNull(registeredPackage);
        Assert.assertFalse(registeredPackage instanceof JcrRegisteredPackage);
        compositeRegistry = packaging.getCompositePackageRegistry(admin, true);
        try (InputStream input = getClass().getResourceAsStream("/test-packages/package_2.0.zip")) {
            compositeRegistry.register(input, false);
        }
        registeredPackage = compositeRegistry.open(PackageId.fromString("my_packages:package:2.0"));
        Assert.assertNotNull(registeredPackage);
        Assert.assertTrue(registeredPackage instanceof JcrRegisteredPackage);
    }
}

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
package org.apache.jackrabbit.vault.packaging.registry.impl;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.io.input.NullInputStream;
import org.apache.jackrabbit.vault.packaging.Dependency;
import org.apache.jackrabbit.vault.packaging.NoSuchPackageException;
import org.apache.jackrabbit.vault.packaging.PackageExistsException;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Test;

public class CompositePackageRegistryTest {

    private static final PackageId PACKAGE1 = PackageId.fromString("my.group:package1:1.0");
    private static final PackageId PACKAGE2 = PackageId.fromString("my.group:package1:2.0");
    private static final PackageId PACKAGE3 = PackageId.fromString("my.group:package2:1.0");
    private static final PackageId PACKAGE4 = PackageId.fromString("my.group:package3:1.0");

    @Test
    public void testPackagesContainsAndRemove() throws IOException, NoSuchPackageException {
        CompositePackageRegistry registry = new CompositePackageRegistry(
                new MockPackageRegistry(PACKAGE1, PACKAGE3),
                new MockPackageRegistry(PACKAGE2)
                );
        Assert.assertEquals(Stream.of(PACKAGE1, PACKAGE2, PACKAGE3).collect(Collectors.toSet()), registry.packages());
        Assert.assertTrue(registry.contains(PACKAGE1));
        Assert.assertTrue(registry.contains(PACKAGE2));
        Assert.assertTrue(registry.contains(PACKAGE3));
        Assert.assertFalse(registry.contains(PACKAGE4));
        registry.remove(PACKAGE2);
        Assert.assertFalse(registry.contains(PACKAGE2));
    }

    @Test(expected = NoSuchPackageException.class)
    public void testRemoveNonExistingPackage() throws NoSuchPackageException, IOException {
        CompositePackageRegistry registry = new CompositePackageRegistry(
                new MockPackageRegistry(PACKAGE1),
                new MockPackageRegistry(PACKAGE3)
                );
        registry.remove(PACKAGE2);
    }

    @Test(expected = IllegalStateException.class)
    public void testPackageIdInMultipleRegistries() throws NoSuchPackageException, IOException {
        new CompositePackageRegistry(
                new MockPackageRegistry(PACKAGE1),
                new MockPackageRegistry(PACKAGE1)
                );
    }

    @Test(expected=PackageExistsException.class)
    public void testRegisterWithExistingPackageIdInOtherRegistry() throws PackageExistsException, IOException {
        CompositePackageRegistry registry = new CompositePackageRegistry(
                new MockPackageRegistry(PACKAGE1),
                new MockPackageRegistry(MockPackageRegistry.NEW_PACKAGE_ID)
                );
        File file = File.createTempFile("vlt", null);
        try {
            registry.register(file, false);
        } finally {
            file.delete();
        }
    }

    @Test
    public void testRegisterFile() throws PackageExistsException, IOException {
        CompositePackageRegistry registry = new CompositePackageRegistry(
                new MockPackageRegistry(PACKAGE1),
                new MockPackageRegistry(PACKAGE3)
                );
        File file = File.createTempFile("vlt", null);
        try {
            registry.register(file, false);
        } finally {
            file.delete();
        }
        Assert.assertTrue(registry.contains(MockPackageRegistry.NEW_PACKAGE_ID));
    }

    @Test
    public void testRegisterInputStream() throws PackageExistsException, IOException {
        CompositePackageRegistry registry = new CompositePackageRegistry(
                new MockPackageRegistry(PACKAGE1),
                new MockPackageRegistry(PACKAGE3)
                );
        try (InputStream input = new NullInputStream(0)){
            registry.register(input, false);
        }
        Assert.assertTrue(registry.contains(MockPackageRegistry.NEW_PACKAGE_ID));
    }

    @Test
    public void testRegisterExternal() throws PackageExistsException, IOException {
        CompositePackageRegistry registry = new CompositePackageRegistry(
                new MockPackageRegistry(PACKAGE1),
                new MockPackageRegistry(PACKAGE3)
                );
        File file = File.createTempFile("vlt", null);
        try {
            registry.registerExternal(file, false);
        } finally {
            file.delete();
        }
        Assert.assertTrue(registry.contains(MockPackageRegistry.NEW_PACKAGE_ID));
    }

    @Test
    public void testOpen() throws IOException {
        CompositePackageRegistry registry = new CompositePackageRegistry(
                new MockPackageRegistry(PACKAGE1),
                new MockPackageRegistry(PACKAGE2)
                );
        Assert.assertNotNull(registry.open(PACKAGE2));
        Assert.assertNull(registry.open(PACKAGE3));
    }

    @Test
    public void testDependencyReport() throws IOException, NoSuchPackageException {
        CompositePackageRegistry registry = new CompositePackageRegistry(
                new MockPackageRegistry(PACKAGE1),
                new MockPackageRegistry(PACKAGE2)
                );
        Assert.assertNotNull(registry.analyzeDependencies(PACKAGE2, true));
    }

    @Test(expected = NoSuchPackageException.class)
    public void testDependencyReportForNonExistingPackage() throws IOException, NoSuchPackageException {
        CompositePackageRegistry registry = new CompositePackageRegistry(
                new MockPackageRegistry(PACKAGE1),
                new MockPackageRegistry(PACKAGE2)
                );
        registry.analyzeDependencies(PACKAGE3, true);
    }

    @Test
    public void testResolve() throws IOException {
        CompositePackageRegistry registry = new CompositePackageRegistry(
                new MockPackageRegistry(PACKAGE1),
                new MockPackageRegistry(PACKAGE2)
                );
        Dependency dependency = Dependency.fromString("my.group:package1:2.0");
        Assert.assertEquals(PACKAGE2, registry.resolve(dependency, true));
        dependency = Dependency.fromString("my.group:unknown-package1:2.0");
        Assert.assertNull(registry.resolve(dependency, true));
    }

    @Test
    public void testUsage() throws IOException {
        MockPackageRegistry registry1 = new MockPackageRegistry();
        registry1.addPackageWithDependencies("package1", "my.group:package1:2.0");
        MockPackageRegistry registry2 = new MockPackageRegistry();
        
        registry2.addPackageWithDependencies("package3", "my.group:package1:2.0");
        registry2.addPackageWithDependencies("package2", "my.group:package1:2.0");
        CompositePackageRegistry compositeRegistry = new CompositePackageRegistry(registry1, registry2);
        MatcherAssert.assertThat(Arrays.asList(compositeRegistry.usage(PackageId.fromString("my.group:package1:2.0"))), 
                Matchers.containsInAnyOrder(PackageId.fromString("package1"), PackageId.fromString("package2"), PackageId.fromString("package3")));
    }
}

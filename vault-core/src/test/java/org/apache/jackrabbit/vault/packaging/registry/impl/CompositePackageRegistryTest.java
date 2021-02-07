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
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.io.input.NullInputStream;
import org.apache.jackrabbit.vault.packaging.Dependency;
import org.apache.jackrabbit.vault.packaging.NoSuchPackageException;
import org.apache.jackrabbit.vault.packaging.PackageExistsException;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.registry.DependencyReport;
import org.apache.jackrabbit.vault.packaging.registry.ExecutionPlanBuilder;
import org.apache.jackrabbit.vault.packaging.registry.PackageRegistry;
import org.apache.jackrabbit.vault.packaging.registry.RegisteredPackage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

public class CompositePackageRegistryTest {

    public static final class MockPackageRegistry implements PackageRegistry {

        private static final PackageId NEW_PACKAGE_ID = PackageId.fromString("group:newpackage:1.0");
        private Set<PackageId> containedPackageIds;

        public MockPackageRegistry(String... packageIds) {
            this(Stream.of(packageIds).map(PackageId::fromString).collect(Collectors.toList()));
        }

        public MockPackageRegistry(PackageId... packageIds) {
            this(Arrays.asList(packageIds));
        }

        MockPackageRegistry(Collection<PackageId> packageIds) {
            this.containedPackageIds = new HashSet<>(packageIds);
        }

        @Override
        public boolean contains(@NotNull PackageId id) throws IOException {
            return containedPackageIds.contains(id);
        }

        @Override
        public @NotNull Set<PackageId> packages() throws IOException {
            return new HashSet<>(containedPackageIds);
        }

        @Override
        public @Nullable RegisteredPackage open(@NotNull PackageId id) throws IOException {
            if (containedPackageIds.contains(id)) {
                return Mockito.mock(RegisteredPackage.class);
            } else {
                return null;
            }
        }

        @Override
        public @NotNull PackageId register(@NotNull InputStream in, boolean replace) throws IOException, PackageExistsException {
            this.containedPackageIds.add(NEW_PACKAGE_ID);
            return NEW_PACKAGE_ID;
        }

        @Override
        public @NotNull PackageId register(@NotNull File file, boolean replace) throws IOException, PackageExistsException {
            this.containedPackageIds.add(NEW_PACKAGE_ID);
            return NEW_PACKAGE_ID;
        }

        @Override
        public @NotNull PackageId registerExternal(@NotNull File file, boolean replace)
                throws IOException, PackageExistsException {
            this.containedPackageIds.add(NEW_PACKAGE_ID);
            return NEW_PACKAGE_ID;
        }

        @Override
        public void remove(@NotNull PackageId id) throws IOException, NoSuchPackageException {
            if (!containedPackageIds.remove(id)) {
                throw new NoSuchPackageException("Could not find package with id " + id);
            }
        }

        @Override
        public @NotNull DependencyReport analyzeDependencies(@NotNull PackageId id, boolean onlyInstalled)
                throws IOException, NoSuchPackageException {
            if (containedPackageIds.contains(id)) {
                return Mockito.mock(DependencyReport.class);
            } else {
                throw new NoSuchPackageException("Could not find package with id " + id);
            }
        }

        @Override
        public @Nullable PackageId resolve(@NotNull Dependency dependency, boolean onlyInstalled) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public @NotNull PackageId[] usage(@NotNull PackageId id) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public @NotNull ExecutionPlanBuilder createExecutionPlan() {
            throw new UnsupportedOperationException();
        }

    }

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
}

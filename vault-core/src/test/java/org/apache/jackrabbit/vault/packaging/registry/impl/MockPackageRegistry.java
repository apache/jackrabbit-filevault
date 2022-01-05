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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
import org.mockito.Mockito;

public final class MockPackageRegistry implements PackageRegistry {

    public static final PackageId NEW_PACKAGE_ID = PackageId.fromString("group:newpackage:1.0");
    private Map<PackageId, List<Dependency>> containedPackageIdsAndDependencies;

    public MockPackageRegistry() {
        this.containedPackageIdsAndDependencies = new HashMap<>();
    }

    public MockPackageRegistry(String... packageIds) {
        this(Stream.of(packageIds).map(PackageId::fromString).collect(Collectors.toList()));
    }

    public MockPackageRegistry(PackageId... packageIds) {
        this(Arrays.asList(packageIds));
    }

    MockPackageRegistry(Collection<PackageId> packageIds) {
        this.containedPackageIdsAndDependencies = packageIds.stream().collect(Collectors.<PackageId, PackageId, List<Dependency>>toMap(packageId -> packageId, packageId -> Collections.emptyList()));
    }

    void addPackageWithDependencies(String packageId, String... dependencies) {
        this.containedPackageIdsAndDependencies.put(PackageId.fromString(packageId), Stream.of(dependencies).map(Dependency::fromString).collect(Collectors.toList()));
    }

    @Override
    public boolean contains(@NotNull PackageId id) throws IOException {
        return containedPackageIdsAndDependencies.containsKey(id);
    }

    @Override
    public @NotNull Set<PackageId> packages() throws IOException {
        return new HashSet<>(containedPackageIdsAndDependencies.keySet());
    }

    @Override
    public @Nullable RegisteredPackage open(@NotNull PackageId id) throws IOException {
        if (containedPackageIdsAndDependencies.containsKey(id)) {
            return Mockito.mock(RegisteredPackage.class);
        } else {
            return null;
        }
    }

    @Override
    public @NotNull PackageId register(@NotNull InputStream in, boolean replace) throws IOException, PackageExistsException {
        this.containedPackageIdsAndDependencies.put(NEW_PACKAGE_ID, Collections.emptyList());
        return NEW_PACKAGE_ID;
    }

    @Override
    public @NotNull PackageId register(@NotNull File file, boolean replace) throws IOException, PackageExistsException {
        this.containedPackageIdsAndDependencies.put(NEW_PACKAGE_ID, Collections.emptyList());
        return NEW_PACKAGE_ID;
    }

    @Override
    public @NotNull PackageId registerExternal(@NotNull File file, boolean replace)
            throws IOException, PackageExistsException {
        this.containedPackageIdsAndDependencies.put(NEW_PACKAGE_ID, Collections.emptyList());
        return NEW_PACKAGE_ID;
    }

    @Override
    public void remove(@NotNull PackageId id) throws IOException, NoSuchPackageException {
        if (containedPackageIdsAndDependencies.remove(id) == null) {
            throw new NoSuchPackageException("Could not find package with id " + id);
        }
    }

    @Override
    public @NotNull DependencyReport analyzeDependencies(@NotNull PackageId id, boolean onlyInstalled)
            throws IOException, NoSuchPackageException {
        if (containedPackageIdsAndDependencies.containsKey(id)) {
            DependencyReport report = Mockito.mock(DependencyReport.class);
            Mockito.when(report.getUnresolvedDependencies()).thenReturn(new Dependency[0]);
            Mockito.when(report.getResolvedDependencies()).thenReturn(new PackageId[0]);
            return report;
        } else {
            throw new NoSuchPackageException("Could not find package with id " + id);
        }
    }

    @Override
    public @Nullable PackageId resolve(@NotNull Dependency dependency, boolean onlyInstalled) throws IOException {
        for (PackageId packageId : containedPackageIdsAndDependencies.keySet()) {
            if (dependency.matches(packageId)) {
                return packageId;
            }
        }
        return null;
    }

    @Override
    public @NotNull PackageId[] usage(@NotNull PackageId id) throws IOException {
        List<PackageId> dependentPackages = new ArrayList<>();
        for (Entry<PackageId, List<Dependency>> packageIdAndDependencies : containedPackageIdsAndDependencies.entrySet()) {
            for (Dependency dependency : packageIdAndDependencies.getValue()) {
                if (dependency.matches(id)) {
                    dependentPackages.add(packageIdAndDependencies.getKey());
                }
            }
        }
        return dependentPackages.toArray(new PackageId[0]);
    }

    @Override
    public @NotNull ExecutionPlanBuilder createExecutionPlan() {
        throw new UnsupportedOperationException();
    }

}
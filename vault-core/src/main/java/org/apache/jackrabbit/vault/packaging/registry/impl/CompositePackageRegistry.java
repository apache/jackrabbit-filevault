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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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

/**
 * Package registry wrapping a number of {@link PackageRegistry} instances.
 * This registry only works, if each package id contained in any of the registries
 * is unique among all registries.
 * In case duplicates are detected exceptions are thrown.
 * The first given registry is used for registering new packages.
 */
public class CompositePackageRegistry implements PackageRegistry {

    private final List<PackageRegistry> registries;
    private final PackageRegistry primaryRegistry;
    
    CompositePackageRegistry(PackageRegistry... registries) throws IOException {
        this(Arrays.asList(registries));
    }

    public CompositePackageRegistry(List<PackageRegistry> registries) throws IOException {
        this.registries = registries;
        this.primaryRegistry = registries.get(0);
        enforcePackageIdsUniqueness(this.registries);
    }

    private void enforcePackageIdsUniqueness(List<PackageRegistry> registries) throws IOException {
        for (int n=0; n<registries.size(); n++) {
            for (int i=n+1; i<registries.size(); i++) {
                registries.get(n).packages().retainAll(registries.get(i).packages());
                Set<PackageId> packageIntersection = registries.get(n).packages();
                packageIntersection.retainAll(registries.get(i).packages());
                if (!packageIntersection.isEmpty()) {
                    String duplicatePackageIds = packageIntersection.stream().map(packageId -> packageId.toString()).collect(Collectors.joining(", "));
                    throw new IllegalStateException("The following package ids exist in registry " + registries.get(i) + " and registry " + registries.get(n) + ": " +duplicatePackageIds);
                }
            }
        }
    }

    private void enforcePackageIdsUniqueness(PackageId packageId, PackageRegistry sourcePackageRegistry) throws PackageExistsException, IOException {
        for (PackageRegistry registry : registries) {
            if (registry == sourcePackageRegistry) {
                continue;
            }
            if (registry.contains(packageId)) {
                try {
                    sourcePackageRegistry.remove(packageId);
                } catch (NoSuchPackageException e) {
                    throw new IOException("Could not remove duplicate package id " + packageId, e);
                }
                throw new PackageExistsException("The package id " + packageId + " already exists in another registry " + registry);
            }
        }
    }
 
    @Override
    public boolean contains(@NotNull PackageId id) throws IOException {
        for (PackageRegistry registry : registries) {
            if (registry.contains(id)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public @NotNull Set<PackageId> packages() throws IOException {
        Set<PackageId> allPackages = new HashSet<>();
        for (PackageRegistry registry : registries) {
            allPackages.addAll(registry.packages());
        }
        return allPackages;
    }

    @Override
    public @Nullable RegisteredPackage open(@NotNull PackageId id) throws IOException {
        for (PackageRegistry registry : registries) {
            if (registry.contains(id)) {
                return registry.open(id);
            }
        }
        return null;
    }

    @Override
    public @NotNull PackageId register(@NotNull InputStream in, boolean replace) throws IOException, PackageExistsException {
        PackageId packageId = primaryRegistry.register(in, replace);
        enforcePackageIdsUniqueness(packageId, primaryRegistry);
        return packageId;
    }

    @Override
    public @NotNull PackageId register(@NotNull File file, boolean replace) throws IOException, PackageExistsException {
        PackageId packageId = primaryRegistry.register(file, replace);
        enforcePackageIdsUniqueness(packageId, primaryRegistry);
        return packageId;
    }

    @Override
    public @NotNull PackageId registerExternal(@NotNull File file, boolean replace) throws IOException, PackageExistsException {
        PackageId packageId = primaryRegistry.registerExternal(file, replace);
        enforcePackageIdsUniqueness(packageId, primaryRegistry);
        return packageId;
    }

    @Override
    public void remove(@NotNull PackageId id) throws IOException, NoSuchPackageException {
        for (PackageRegistry registry : registries) {
            if (registry.contains(id)) {
                registry.remove(id);
                return;
            }
        }
        throw new NoSuchPackageException("No registry contains the given package id " + id);
    }

    @Override
    public @NotNull DependencyReport analyzeDependencies(@NotNull PackageId id, boolean onlyInstalled)
            throws IOException, NoSuchPackageException {
        for (PackageRegistry registry : registries) {
            if (registry.contains(id)) {
                return registry.analyzeDependencies(id, onlyInstalled);
            }
        }
        throw new NoSuchPackageException("No registry contains the given package id " + id);
    }

    @Override
    public @Nullable PackageId resolve(@NotNull Dependency dependency, boolean onlyInstalled) throws IOException {
        PackageId packageId = null;
        for (PackageRegistry registry : registries) {
            packageId = registry.resolve(dependency, onlyInstalled);
            if (packageId != null) {
                return packageId;
            }
        }
        return packageId;
    }

    @Override
    public @NotNull PackageId[] usage(@NotNull PackageId id) throws IOException {
        List<PackageId> dependentPackageIds = new ArrayList<>();
        for (PackageRegistry registry : registries) {
            PackageId[] packageIds = registry.usage(id);
            if (packageIds.length > 0) {
                dependentPackageIds.addAll(Arrays.asList(packageIds));
            }
        }
        return dependentPackageIds.toArray(new PackageId[0]);
    }

    @Override
    public @NotNull ExecutionPlanBuilder createExecutionPlan() {
        return new ExecutionPlanBuilderImpl(this);
    }

}

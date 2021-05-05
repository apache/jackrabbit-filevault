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
package org.apache.jackrabbit.vault.packaging.registry;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import org.apache.jackrabbit.vault.packaging.Dependency;
import org.apache.jackrabbit.vault.packaging.NoSuchPackageException;
import org.apache.jackrabbit.vault.packaging.PackageExistsException;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.annotation.versioning.ProviderType;

/**
 * The package registry is the next generation {@link org.apache.jackrabbit.vault.packaging.PackageManager} and
 * {@link org.apache.jackrabbit.vault.packaging.JcrPackageManager}. It defines storage independent methods to
 * register (upload), enumerate and remove packages. The installation lifecycle of the packages is provided via
 * {@link ExecutionPlan}s.
 */
@ProviderType
public interface PackageRegistry {

    /**
     * Checks if this registry contains the package with the given id.
     * @param id the package id.
     * @return {@code true} if the package is registered.
     * @throws IOException if an I/O error occurs.
     */
    boolean contains(@NotNull PackageId id) throws IOException;

    /**
     * Returns as set of all packages registered in this registry.
     * @return a set of package ids.
     * @throws IOException if an I/O error occurs.
     */
    @NotNull
    Set<PackageId> packages() throws IOException;

    /**
     * Opens the package with the given id.
     * @param id the package id
     * @return the package or {@code null} if it does not exists.
     * @throws IOException if an I/O error occurs.
     */
    @Nullable
    RegisteredPackage open(@NotNull PackageId id) throws IOException;

    /**
     * Registers a package provided via an input stream. The method fails, if a package with the same id already exists,
     * and {@code replace} is set to {@code false}. otherwise the existing package is replaced.
     *
     * @param in the input stream to the package data
     * @param replace {@code true} if existing package should be replaced.
     * @return the new package id.
     * @throws IOException if an I/O error occurs.
     * @throws PackageExistsException if the package exists and {@code replace} is {@code false}.
     */
    @NotNull
    PackageId register(@NotNull InputStream in, boolean replace) throws IOException, PackageExistsException;

    /**
     * Registers a package provided via a file. The method fails, if a package with the same id already exists,
     * and {@code replace} is set to {@code false}; otherwise the existing package is replaced.
     *
     * @param file the file to the package data
     * @param replace {@code true} if existing package should be replaced.
     * @return the new package id.
     * @throws IOException if an I/O error occurrs.
     * @throws PackageExistsException if the package exists and {@code replace} is {@code false}.
     */
    @NotNull
    PackageId register(@NotNull File file, boolean replace) throws IOException, PackageExistsException;

    /**
     * Registers a package provided via an external file. The binary data of the package will not be copied into the
     * underlying persistence but only be referenced. Removing such a linked package afterwards will not delete the
     * original file.
     *
     * The method fails, if a package with the same id already exists,
     * and {@code replace} is set to {@code false}; otherwise the existing package is replaced.
     *
     * @param file the file to the package data.
     * @param replace {@code true} if existing package should be replaced.
     * @return the new package id.
     * @throws IOException if an I/O error occurrs.
     * @throws PackageExistsException if the package exists and {@code replace} is {@code false}.
     */
    @NotNull
    PackageId registerExternal(@NotNull File file, boolean replace) throws IOException, PackageExistsException;

    /**
     * Removes the package from this registry.
     * @param id the id of the package to remove
     * @throws IOException if an I/O error occurrs.
     * @throws NoSuchPackageException if the package does not exist
     */
    void remove(@NotNull PackageId id) throws IOException, NoSuchPackageException;

    /**
     * Creates a dependency report that lists the resolved and unresolved dependencies.
     * @param id the package id.
     * @param onlyInstalled if {@code true} only installed packages are used for resolution
     * @return the report
     * @throws IOException if an error accessing the repository occurrs
     * @throws NoSuchPackageException if the package with the given {@code id} does not exist.
     */
    @NotNull
    DependencyReport analyzeDependencies(@NotNull PackageId id, boolean onlyInstalled) throws IOException, NoSuchPackageException;

    /**
     * Tries to resolve the given dependency and returns the id of the package that matches the dependency filter best.
     * @param dependency the dependency to resolve against.
     * @param onlyInstalled if {@code true} only installed packages are respected.
     * @return the package id or {@code null}
     * @throws IOException if an I/O error occurrs.
     */
    @Nullable
    PackageId resolve(@NotNull Dependency dependency, boolean onlyInstalled) throws IOException;

    /**
     * Returns the package ids of installed packages that depend on the given package.
     *
     * @param id the package id to search for
     * @return the array of package ids.
     * @throws IOException if an I/O error occurs.
     */
    @NotNull
    PackageId[] usage(@NotNull PackageId id) throws IOException;

    /**
     * Creates a new execution plan builder. The builder allows to create an execution plan for package installation
     * related tasks.
     *
     * @return a new builder
     */
    @NotNull
    ExecutionPlanBuilder createExecutionPlan();
}

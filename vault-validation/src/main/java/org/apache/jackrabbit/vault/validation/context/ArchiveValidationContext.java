/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.jackrabbit.vault.validation.context;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;

import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.packaging.PackageInfo;
import org.apache.jackrabbit.vault.packaging.PackageProperties;
import org.apache.jackrabbit.vault.validation.spi.ValidationContext;
import org.jetbrains.annotations.NotNull;

/**
 * Implements a {@link ValidationContext} based on a given {@link Archive}.
 */
public class ArchiveValidationContext extends AbstractValidationContext {

    private final WorkspaceFilter filter;

    @NotNull
    private final PackageProperties properties;

    @NotNull
    private final Path archivePath;

    @NotNull
    private final Collection<PackageInfo> resolvedDependencies;

    public ArchiveValidationContext(
            @NotNull Archive archive, @NotNull Path archivePath, @NotNull DependencyResolver resolver)
            throws IOException {
        this.archivePath = archivePath;
        properties = archive.getMetaInf().getPackageProperties();
        this.filter = archive.getMetaInf().getFilter();
        if (filter == null) {
            throw new IllegalStateException("Archive '" + archivePath + "' does not contain a filter.xml.");
        }
        this.resolvedDependencies = resolver.resolvePackageInfo(
                getProperties().getDependencies(), getProperties().getDependenciesLocations());
    }

    @Override
    public @NotNull PackageProperties getProperties() {
        return properties;
    }

    @Override
    public @NotNull WorkspaceFilter getFilter() {
        return filter;
    }

    @Override
    public ValidationContext getContainerValidationContext() {
        return null;
    }

    public @NotNull Path getPackageRootPath() {
        return archivePath;
    }

    @Override
    public @NotNull Collection<PackageInfo> getDependenciesPackageInfo() {
        return this.resolvedDependencies;
    }
}

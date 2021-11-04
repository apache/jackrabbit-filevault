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
package org.apache.jackrabbit.vault.validation.spi;

import java.nio.file.Path;
import java.util.Collection;

import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.packaging.PackageInfo;
import org.apache.jackrabbit.vault.packaging.PackageProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.annotation.versioning.ProviderType;

/**
 * The validation context encapsulates information about the package for which the validation is triggered.
 * This class is used from both validators (SPI) and validation API but for historical reasons is located in (wrong) package {@code org.apache.jackrabbit.vault.validation.spi}.
 */
@ProviderType
public interface ValidationContext {
    /**
     * Returns the workspace filter
     * @return the filter
     */
    @NotNull WorkspaceFilter getFilter();

    /**
     * Returns the package properties.
     * 
     * @return the package properties or some exception in case none could be found (will always point to the root package's properties).
     */
    @NotNull PackageProperties getProperties();

    /**
     * Returns the validation context of the container package.
     * @return the validation context of the container in case this is the context of a sub package otherwise {@code null}.
     */
    @Nullable ValidationContext getContainerValidationContext();

    /**
     * Returns the root path of the package.
     * @return either the path to the ZIP file or a directory containing an exploded package.
     */
    @NotNull Path getPackageRootPath();

    /**
     * PackageInfo for all resolved package dependencies.
     * In contrast to {@link PackageProperties#getDependencies()} the resolved dependencies also
     * carry the main metadata of the dependencies.
     * @return the package info of all resolved package dependencies (i.e. the ones for which an artifact was found).
     */
    @NotNull Collection<PackageInfo> getDependenciesPackageInfo();

    /**
     * 
     * @return {@code true} in case the validation is incremental (i.e. does not cover all files in a package). This should relax some validations.
     */
    default boolean isIncremental() {
        return false;
    }
}

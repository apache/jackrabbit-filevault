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
import java.util.Set;

import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.packaging.PackageInfo;
import org.apache.jackrabbit.vault.packaging.PackageProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.annotation.versioning.ProviderType;

/**
 * The validation context encapsulates information about the package for which the validation is triggered.
 * In addition validators may use it to pass information to other validators via attributes.
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
     * @return the validation context of the container in case this is the context of a subpackage otherwise {@code null}.
     */
    @Nullable ValidationContext getContainerValidationContext();

    /**
     * Returns the root path of the package.
     * @return either the path to the ZIP file or a directory containing an exploded package. For subpackages starts with the package root path of the root container and appends each sub container package root path with the default separator.
     */
    @NotNull Path getPackageRootPath();

    /**
     * {@link PackageInfo} for all resolved package dependencies.
     * In contrast to {@link PackageProperties#getDependencies()} the resolved dependencies also
     * carry the main metadata (next to their coordinates).
     * @return the package info of all resolved package dependencies (i.e. the ones for which an artifact was found).
     */
    @NotNull Collection<PackageInfo> getDependenciesPackageInfo();

    /**
     * 
     * @return {@code true} in case only validation messages specific to subpackages should be emitted.
     */
    default boolean isSubpackageOnlyValidation() {
        return false;
    }
    /**
     * 
     * @return {@code true} in case the validation is incremental (i.e. does not cover all files in a package). This should relax some validations.
     */
    default boolean isIncremental() {
        return false;
    }

    /**
     * Sets an attribute with the given name to the given value.
     * @param name the name of the attribute to set
     * @param value the value to set the attribute to
     * @return the old value of the attribute with the given name or {@code null} if there was no value set
     * @see #getAttribute(String)
     * @since 3.7.1
     */
    Object setAttribute(String name, Object value);

    /**
     * Retrieves the value of the attribute with the given name.
     * @param name the name of the attribute to retrieve
     * @return the value of the attribute with the given name or {@code null} if there was no value set
     * @see #setAttribute(String, Object)
     * @see #getAttributeNames()
     * @since 3.7.1
     */
    Object getAttribute(String name);

    /**
     * Returns a set of all attribute names which have been set before. 
     * @return a set of attribute names which contain values
     * @see #getAttribute(String)
     * @see #setAttribute(String, Object)
     * @since 3.7.1
     */
    Set<String> getAttributeNames();
}

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

package org.apache.jackrabbit.vault.packaging;

import java.io.IOException;
import java.util.Calendar;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.apache.jackrabbit.vault.fs.api.ProgressTrackerListener;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.fs.config.MetaInf;
import org.apache.jackrabbit.vault.fs.io.AccessControlHandling;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.annotation.versioning.ProviderType;

/**
 * Specifies the interface of a package definition stored in the repository.
 */
@ProviderType
public interface JcrPackageDefinition extends PackageProperties {

    /**
     * Property name of the last unpacked date
     */
    String PN_LAST_UNPACKED = "lastUnpacked";

    /**
     * Property name of the last unpacked user id
     */
    String PN_LAST_UNPACKED_BY = "lastUnpackedBy";

    /**
     * Property name of the creation date
     */
    String PN_CREATED = "jcr:created";

    /**
     * Property name of the creation user id
     */
    String PN_CREATED_BY = "jcr:createdBy";

    /**
     * Property name of the last modification date
     */
    String PN_LASTMODIFIED = "jcr:lastModified";

    /**
     * Property name of the last modification user id
     */
    String PN_LASTMODIFIED_BY = "jcr:lastModifiedBy";

    /**
     * Property name of the last wrapped date
     * @since 2.2.22
     */
    String PN_LAST_WRAPPED = "lastWrapped";

    /**
     * Property name of the last wrapped user id
     * @since 2.2.22
     */
    String PN_LAST_WRAPPED_BY = "lastWrappedBy";

    /**
     * Property name of the package description
     */
    String PN_DESCRIPTION = "jcr:description";

    /**
     * Property name of the package version
     */
    String PN_VERSION = "version";

    /**
     * Property name of the build count
     */
    String PN_BUILD_COUNT = "buildCount";

    /**
     * Property name of the 'name'
     * @since 2.2
     */
    String PN_NAME = "name";

    /**
     * Property name of the 'group'
     * @since 2.2
     */
    String PN_GROUP = "group";

    /**
     * Property name of the "requires root" flag
     */
    String PN_REQUIRES_ROOT = "requiresRoot";

    /**
     * Property name of the "require restart" flag
     */
    String PN_REQUIRES_RESTART = "requiresRestart";

    /**
     * Property name of the package dependencies
     */
    String PN_DEPENDENCIES = "dependencies";

    /**
     * Property name of the sub packages (only used in snapshots)
     */
    String PN_SUB_PACKAGES = "subPackages";

    /**
     * Property name of the last unwrapped date
     */
    String PN_LAST_UNWRAPPED = "lastUnwrapped";

    /**
     * Property name of the last unwrapped user id
     */
    String PN_LAST_UNWRAPPED_BY = "lastUnwrappedBy";

    /**
     * Property name of the access control handling mode
     */
    String PN_AC_HANDLING = "acHandling";

    /**
     * Property name of the cnd pattern filter
     */
    String PN_CND_PATTERN = "cndPattern";

    /**
     * Node name of the filter node
     */
    String NN_FILTER = "filter";

    /**
     * Property name of the filter root
     */
    String PN_ROOT = "root";

    /**
     * Property name of the filter root
     */
    String PN_MODE = "mode";

    /**
     * Property name of the path filter rules
     */
    String PN_RULES = "rules";

    /**
     * Property name of the property filter rules
     */
    String PN_PROPERTY_RULES = "propertyRules";

    /**
     * Property name of the rule type
     */
    String PN_TYPE = "type";

    /**
     * Property name of the rule pattern
     */
    String PN_PATTERN = "pattern";

    /**
     * Property name of the disable intermediate save flag
     */
    String PN_DISABLE_INTERMEDIATE_SAVE = "noIntermediateSaves";

    /**
     * Returns the underlying node
     * @return the node
     */
    @NotNull
    Node getNode();

    /**
     * Writes the properties derived from the package id to the content
     * @param id the package id
     * @param autoSave if {@code true} the changes are saved automatically.
     * @since 2.2
     */
    void setId(@NotNull PackageId id, boolean autoSave);

    /**
     * Checks if this definition is unwrapped, i.e. if the definition structured
     * was extracted from a VaultPackage.
     * @return {@code true} if unwrapped.
     */
    boolean isUnwrapped();

    /**
     * Checks if the definition was modified since it was last wrapped.
     * new packages are considered modified.
     * @return {@code true} if modified
     */
    boolean isModified();

    /**
     * Unwraps the package definition to the underlying node.
     * @param pack the package
     * @param force if {@code true} unwrapping is forced
     * @param autoSave if {@code true} modifications are saved automatically
     * @throws RepositoryException if an error occurs
     * @throws IOException if an I/O error occurs
     */
    void unwrap(@Nullable VaultPackage pack, boolean force, boolean autoSave)
                    throws RepositoryException, IOException;

    /**
     * Dumps the coverage of this definition to the given listener
     * @param listener the listener
     * @throws RepositoryException if an error occurrs
     */
    void dumpCoverage(@NotNull ProgressTrackerListener listener) throws RepositoryException;

    /**
     * Sets the dependencies to this definition and stores it in a node representation.
     * @param dependencies the package dependencies
     * @param autoSave if {@code true} the modifications are saved automatically.
     * @since 3.1.32
     */
    void setDependencies(@NotNull Dependency[] dependencies, boolean autoSave);

    /**
     * Generic method to retrieve a string property of this definition.
     * @param name the name of the property.
     * @return the property value or {@code null} if it does not exist.
     */
    @Nullable
    String get(@NotNull String name);

    /**
     * Generic method to retrieve a boolean property of this definition.
     * @param name the name of the property.
     * @return the property value or {@code null} if it does not exist.
     */
    boolean getBoolean(@NotNull String name);

    /**
     * Generic method to retrieve a date property of this definition.
     * @param name the name of the property.
     * @return the property value or {@code null} if it does not exist.
     */
    @Nullable
    Calendar getCalendar(@NotNull String name);

    /**
     * Generic method to set a string property to this definition.
     * @param name the name of the property
     * @param value the value or {@code null} to clear the property
     * @param autoSave if {@code true} the modifications are saved automatically.
     */
    void set(@NotNull String name, @Nullable String value, boolean autoSave);

    /**
     * Generic method to set a date property to this definition.
     * @param name the name of the property
     * @param value the value or {@code null} to clear the property
     * @param autoSave if {@code true} the modifications are saved automatically.
     */
    void set(@NotNull String name, @Nullable Calendar value, boolean autoSave);

    /**
     * Generic method to set a boolean property to this definition.
     * @param name the name of the property
     * @param value the value
     * @param autoSave if {@code true} the modifications are saved automatically.
     */
    void set(@NotNull String name, boolean value, boolean autoSave);

    /**
     * Touches the last modified and last modified by property.
     * @param now calendar or {@code null}
     * @param autoSave if {@code true} the modifications are saved automatically.
     */
    void touch(@Nullable Calendar now, boolean autoSave);

    /**
     * Sets the filter to this definition and stores it in a node representation.
     *
     * @param filter the filter to set
     * @param autoSave if {@code true} the modifications are saved automatically.
     */
    void setFilter(@Nullable WorkspaceFilter filter, boolean autoSave);

    /**
     * Returns the last unwrapped date
     * @return the last unwrapped date
     */
    @Nullable
    Calendar getLastUnwrapped();

    /**
     * Returns the last unwrapped user id
     * @return the last unwrapped user id
     */
    @Nullable
    String getLastUnwrappedBy();

    /**
     * Returns the date when the package was unpacked
     * @return the unpacked date
     */
    @Nullable
    Calendar getLastUnpacked();

    /**
     * Returns the user id who unpacked the package
     * @return the unpacked user id
     */
    @Nullable
    String getLastUnpackedBy();

    /**
     * Returns the access control handling defined in the definition, or {@code null}
     * if not defined.
     * @return the access control handling or {@code null}
     * @since 2.3.2
     * @deprecated Use {@link PackageProperties#getACHandling} retrieved via {@link #getMetaInf()} and {@link MetaInf#getPackageProperties()}.
     */
    @Nullable
    @Deprecated
    AccessControlHandling getAccessControlHandling();

    /**
     * Returns the meta inf of this package
     * @return the meta inf
     * @throws RepositoryException if an error occurs.
     */
    @NotNull
    MetaInf getMetaInf() throws RepositoryException;

}

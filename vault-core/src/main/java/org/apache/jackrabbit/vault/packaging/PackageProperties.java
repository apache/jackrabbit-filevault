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

import java.net.URI;
import java.util.Calendar;
import java.util.Map;

import org.apache.jackrabbit.vault.fs.api.VaultFsConfig;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.fs.io.AccessControlHandling;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.annotation.versioning.ProviderType;

/**
 * The package properties provide extra/meta information about the package to install. The properties are usually
 * stored in the {@code META-INF/vault/properties.xml} or in the jar's manifest.
 *
 * @since 3.1
 */
@ProviderType
public interface PackageProperties {

    /**
     * Name of the last modified meta-inf property
     */
    String NAME_LAST_MODIFIED = "lastModified";

    /**
     * Name of the last modified by meta-inf property
     */
    String NAME_LAST_MODIFIED_BY = "lastModifiedBy";

    /**
     * Name of the group property
     * @since 2.2
     */
    String NAME_GROUP = "group";

    /**
     * Name of the 'name' property
     * @since 2.2
     */
    String NAME_NAME = "name";

    /**
     * Name of the build count meta-inf property
     */
    String NAME_BUILD_COUNT = "buildCount";

    /**
     * Name of the version meta-inf property
     */
    String NAME_VERSION = "version";

    /**
     * Name of the dependencies meta-inf property
     */
    String NAME_DEPENDENCIES = "dependencies";

    /**
     * Name of the meta-inf property for locations of package dependencies. Each location has the format {@code <packageId>=<uri>}.
     * Multiple locations are separated by ",".
     */
    String NAME_DEPENDENCIES_LOCATIONS = "dependencies-locations";

    /**
     * Name of the created meta-inf property
     */
    String NAME_CREATED = "created";

    /**
     * Name of the created by meta-inf property
     */
    String NAME_CREATED_BY = "createdBy";

    /**
     * Name of the last wrapped meta-inf property
     * @since 2.2.22
     */
    String NAME_LAST_WRAPPED = "lastWrapped";

    /**
     * Name of the last wrapped by meta-inf property
     * @since 2.2.22
     */
    String NAME_LAST_WRAPPED_BY = "lastWrappedBy";

    /**
     * Name of the 'acHandling' by meta-inf property.
     * @see org.apache.jackrabbit.vault.fs.io.AccessControlHandling
     */
    String NAME_AC_HANDLING = "acHandling";

    /**
     * Name of the 'cndPattern' by meta-inf property.
     * @since 2.3.12
     */
    String NAME_CND_PATTERN = "cndPattern";

    /**
     * Name of the description meta-inf property
     */
    String NAME_DESCRIPTION = "description";

    /**
     * Name of the flag that indicates that only admin sessions
     * can extract this package.
     */
    String NAME_REQUIRES_ROOT = "requiresRoot";

    /**
     * Name of the flag that indicates that the system needs a restart after
     * package extraction.
     */
    String NAME_REQUIRES_RESTART = "requiresRestart";

    /**
     * Name of the flag that indicates to disable intermediate saves.
     */
    String NAME_DISABLE_INTERMEDIATE_SAVE = "noIntermediateSaves";

    /**
     * Name of the flag that configures the sub package handling.
     * @since 3.1
     */
    String NAME_SUB_PACKAGE_HANDLING = "subPackageHandling";

    /**
     * Name of the flag that defines if the package is supposed to contains/overwrite Oak index definitions.
     * @since 3.2.10
     */
    String NAME_ALLOW_INDEX_DEFINITIONS = "allowIndexDefinitions";

    /**
     * Name of the flag that configures whether to use binary references instead of actual binary
     */
    String NAME_USE_BINARY_REFERENCES = VaultFsConfig.NAME_USE_BINARY_REFERENCES;

    /**
     * Name of the package-type property
     */
    String NAME_PACKAGE_TYPE = "packageType";

    /**
     * the prefix for an install hook property. eg:
     * 'installhook.test1.class = ....'
     */
    String PREFIX_INSTALL_HOOK = "installhook.";

    /** 
     * The manifest header key which indicates the package type
     * @see #NAME_PACKAGE_TYPE
     */
    String MF_KEY_PACKAGE_TYPE = "Content-Package-Type";

    /**
     * The manifest header key for the package id in the form {@code <group>:<name>:<version>}
     * @see #NAME_GROUP
     * @see #NAME_NAME
     * @see #NAME_VERSION
     */
    String MF_KEY_PACKAGE_ID = "Content-Package-Id";

    /**
     * The manifest header key for the package dependencies.
     * @see #NAME_DEPENDENCIES
     */
    String MF_KEY_PACKAGE_DEPENDENCIES = "Content-Package-Dependencies";
    
    /**
     * The manifest header key for locations of package dependencies. Each location has the format {@code <packageId>=<uri>}.
     * Multiple locations are separated by ",".
     * @see #NAME_DEPENDENCIES_LOCATIONS
     */
    String MF_KEY_PACKAGE_DEPENDENCIES_LOCATIONS = "Content-Package-Dependencies-Locations";

    /**
     * The manifest header key for all filter roots separated by ','.
     * @see WorkspaceFilter
     */
    String MF_KEY_PACKAGE_ROOTS = "Content-Package-Roots";

    /**
     * The manifest header key for the package description.
     * @see #NAME_DESCRIPTION
     */
    String MF_KEY_PACKAGE_DESC = "Content-Package-Description";

    /**
     * The manifest header key containing all necessary imports for this package
     */
    String MF_KEY_IMPORT_PACKAGE = "Import-Package";

    /**
     * Returns the id of this package or {@code null} if the id can't
     * be determined.
     * @return the id of this package.
     */
    PackageId getId();

    /**
     * Returns the last modification date or {@code null} if n/a.
     * @return last modification date or {@code null}
     */
    Calendar getLastModified();

    /**
     * Returns the user that last modified the package or {@code null} if n/a.
     * @return the user or {@code null}
     */
    String getLastModifiedBy();

    /**
     * Returns the date when this package was built or {@code null} if n/a.
     * @return the creation date
     */
    Calendar getCreated();

    /**
     * Returns the user that built this package or null if n/a.
     * @return the creator
     */
    String getCreatedBy();

    /**
     * Returns the date when this package was wrapped or {@code null} if n/a.
     * @return the wrapped date
     * @since 2.2.22
     */
    Calendar getLastWrapped();

    /**
     * Returns the user that wrapped this package or null if n/a.
     * @return the wrapper
     * @since 2.2.22
     */
    String getLastWrappedBy();

    /**
     * Returns a description of this package or {@code null} if n/a
     * @return a description
     */
    String getDescription();

    /**
     * Returns {@code true} if this package can only be extracted by a
     * admin session.
     * @return {@code true} if this package requires an admin session for extraction.
     */
    boolean requiresRoot();

    /**
     * Returns {@code true} if this package requires a restart after installation.
     * @return {@code true} if this package requires a restart after installation.
     */
    boolean requiresRestart();

    /**
     * Returns an unmodifiable list of dependencies
     * @return list of dependencies
     */
    Dependency[] getDependencies();

    /**
     * 
     * @return all external hooks registered in a package (key = name, value = fully qualified class name)
     */
    Map<String, String> getExternalHooks();

    /**
     * Returns the access control handling defined in this package.
     * @return the access control handling.
     */
    AccessControlHandling getACHandling();

    /**
     * Returns the sub package handling configuration
     * @return the sub package handling configuration.
     */
    SubPackageHandling getSubPackageHandling();

    /**
     * Returns the date property with the given name or {@code null} if it does not exist or if the value cannot be
     * converted to a date.
     * @param name the property name
     * @return the property value or {@code null}
     */
    Calendar getDateProperty(String name);

    /**
     * Returns the property with the given name or {@code null} if it does not exist.
     * @param name the property name
     * @return the property value or {@code null}
     */
    String getProperty(String name);

    /**
     * Returns the package type or {@code null} if not package type was specified for this package.
     * @return the package type
     */
    @Nullable
    PackageType getPackageType();

    /**
     * Returns a map of dependency locations where key = package id and value = uri of package dependency with that id.
     * @return dependencies locations as map
     */
    @NotNull Map<PackageId, URI> getDependenciesLocations();

    /**
     * Returns the build count of this package
     * @return the build count.
     */
    long getBuildCount();
}

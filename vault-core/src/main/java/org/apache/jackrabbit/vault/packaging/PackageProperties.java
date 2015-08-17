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

import java.util.Calendar;

import org.apache.jackrabbit.vault.fs.api.VaultFsConfig;
import org.apache.jackrabbit.vault.fs.io.AccessControlHandling;

/**
 * The package properties provide extra/meta information about the package to install. The properties are usually
 * store in the {@code META-INF/vault/properties.xml} or in the jar's manifest.
 *
 * @since 3.1
 */
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
     * Name of the flag that indicates in only admin sessions
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
     * Name of the flag that configures the sub package handling
     * @since 3.1
     */
    String NAME_SUB_PACKAGE_HANDLING = "subPackageHandling";

    /**
     * Name of the flag that configures whether to use binary references instead of actual binary
     */
    String NAME_USE_BINARY_REFERENCES = VaultFsConfig.NAME_USE_BINARY_REFERENCES;

    /**
     * the prefix for an install hook property. eg:
     * 'installhook.test1.class = ....'
     */
    String PREFIX_INSTALL_HOOK = "installhook.";

    /**
     * Returns the id of this package or <code>null</code> if the id can't
     * be determined.
     * @return the id of this package.
     */
    PackageId getId();

    /**
     * Returns the last modification date or <code>null</code> if n/a.
     * @return last modification date or <code>null</code>
     */
    Calendar getLastModified();

    /**
     * Returns the user that last modified the package or <code>null</code> if n/a.
     * @return the user or <code>null</code>
     */
    String getLastModifiedBy();

    /**
     * Returns the date when this package was built or <code>null</code> if n/a.
     * @return the creation date
     */
    Calendar getCreated();

    /**
     * Returns the user that built this package or null if n/a.
     * @return the creator
     */
    String getCreatedBy();

    /**
     * Returns the date when this package was wrapped or <code>null</code> if n/a.
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
     * Returns a description of this package or <code>null</code> if n/a
     * @return a description
     */
    String getDescription();

    /**
     * Returns <code>true</code> if this package can only be extracted by a
     * admin session.
     * @return <code>true</code> if this package requires an admin session for extraction.
     */
    boolean requiresRoot();

    /**
     * Returns an unmodifiable list of dependencies
     * @return list of dependencies
     */
    Dependency[] getDependencies();

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
    public String getProperty(String name);

}
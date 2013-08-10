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

import java.io.File;
import java.util.Calendar;

import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.jackrabbit.vault.fs.config.MetaInf;
import org.apache.jackrabbit.vault.fs.io.AccessControlHandling;
import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.fs.io.ImportOptions;

/**
 * Defines a vault package. A vault package is a binary assembled representation
 * of a vault export.
 */
public interface VaultPackage {

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
     * @see AccessControlHandling
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
     * Checks if this package is valid.
     * @return <code>true</code> if this package is valid.
     */
    boolean isValid();

    /**
     * Checks if this package is closed.
     * @return <code>true</code> if this package is closed.
     */
    boolean isClosed();

    /**
     * Returns the meta inf that was either loaded or specified during build.
     * @return the meta inf or <code>null</code>.
     */
    MetaInf getMetaInf();

    /**
     * Returns the size of the package or -1 if n/a.
     * @return the size
     */
    long getSize();

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
     * Extracts the package contents to the repository
     *
     * @param session repository session
     * @param opts import options
     * @throws RepositoryException if a repository error during installation occurs.
     * @throws PackageException if an error during packaging occurs
     * @throws IllegalStateException if the package is not valid.
     */
    void extract(Session session, ImportOptions opts) throws RepositoryException, PackageException;

    /**
     * Returns the underlying file or <code>null</code> if not available.
     * @return the file
     * @since 2.0
     */
    File getFile();

    /**
     * Closes this package and releases underlying data.
     */
    void close();

    /**
     * Returns the underlying package archive
     * @return the archive or <code>null</code> if already closed
     */
    Archive getArchive();
}
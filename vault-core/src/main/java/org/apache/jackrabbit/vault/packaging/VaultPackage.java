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
 * <p/>
 * Note that VaultPackage currently extends from PackageProperties to keep the interface backwards compatible.
 */
public interface VaultPackage extends PackageProperties {

    /**
     * Returns the id of this package or <code>null</code> if the id can't
     * be determined.
     * @return the id of this package.
     */
    PackageId getId();

    /**
     * Returns the properties of this package.
     * @return the properties.
     * @since 3.1
     */
    PackageProperties getProperties();

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
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
import java.io.IOException;
import java.io.OutputStream;

import javax.jcr.RepositoryException;
import javax.jcr.Session;

/**
 * The package manager is used to deal with packages. the following operations
 * are defined:
 *
 * <dl>
 * <dt>open</dt>
 * <dd>read underlying data and validate them</dd>
 *
 * <dt>close</dt>
 * <dd>release underlying data. the package is unusable afterwards</dd>
 *
 * <dt>upload</dt>
 * <dd>import the package from a binary source to the system. for example
 * create a new jcr node structure or create a file.
 * </dd>
 *
 * <dt>export</dt>
 * <dd>export the package in binary format.
 * </dd>
 *
 * <dt>unwrap</dt>
 * <dd>extract the meta information of the binary data and store it in the
 * repository</dd>
 *
 * <dt>assemble</dt>
 * <dd>create a vault export of the repository using the package definition
 * and filter.</dd>
 *
 * <dt>extract</dt>
 * <dd>extract the packaged content to the repository.</dd>
 *
 * <dt>install</dt>
 * <dd>install the packaged content to the repository but create a snapshot if needed.</dd>
 *
 * <dt>snapshot</dt>
 * <dd>assemble snapshot information that can be used for a later uninstall. this
 * is done by assembling the content using the same filter definition.</dd>
 *
 * <dt>uninstall</dt>
 * <dd>revert changes to the repository of a previous installation.</dd>
 *
 * </dl>
 */
public interface PackageManager {

    /**
     * Opens the given file and creates a package
     * @param file the file
     * @return the package
     * @throws IOException if an error occurrs
     */
    VaultPackage open(File file) throws IOException;

    /**
     * Opens the given file and creates a package
     * @param file the file
     * @param strict if <code>true</code> the import is more strict in respect to errors.
     * @return the package
     * @throws IOException if an error occurrs
     */
    VaultPackage open(File file, boolean strict) throws IOException;

    /**
     * Assembles a package using the given meta information and file to
     * store to. if file is <code>null</code> a temp file is generated.
     *
     * @param s the repository session
     * @param opts export options
     * @param file the file to write to
     * @return the newly created vault package
     *
     * @throws IOException if an I/O error occurs.
     * @throws RepositoryException if a repository error during building occurs.
     * @throws IllegalStateException if the package is not new.
     */
    VaultPackage assemble(Session s, ExportOptions opts, File file)
            throws IOException, RepositoryException;

    /**
     * Assembles a package using the given meta information. The package
     * is directly streamed to the given output stream.
     *
     * @param s the repository session
     * @param opts the export options
     * @param out the output stream to write to
     * @throws IOException if an I/O error occurs.
     * @throws RepositoryException if a repository error during building occurs.
     * @throws IllegalStateException if the package is not new.
     */
    void assemble(Session s, ExportOptions opts, OutputStream out)
            throws IOException, RepositoryException;

    /**
     * Re-wraps a package using the given meta information and file to
     * store to. if file is <code>null</code> a temp file is generated.
     *
     * @param opts export options
     * @param src source package
     * @param file the file to write to
     * @return the newly created vault package
     *
     * @throws IOException if an I/O error occurs.
     * @throws RepositoryException if a repository error during building occurs.
     * @throws IllegalStateException if the package is not new.
     */
    VaultPackage rewrap(ExportOptions opts, VaultPackage src, File file)
            throws IOException, RepositoryException;

    /**
     * Re-wraps the given package with the definition provided in the export
     * options.
     * @param opts export options
     * @param src source package
     * @param out destination output stream
     * @throws IOException if an I/O error occurs
     */
    void rewrap(ExportOptions opts, VaultPackage src, OutputStream out)
            throws IOException;
}
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

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.RepositoryException;

import org.apache.jackrabbit.vault.fs.io.ImportOptions;

/**
 * Specifies the interface of a vault package stored in the repository.
 */
public interface JcrPackage extends Comparable<JcrPackage> {

    /**
     * Nodetype name of a package node
     */
    String NT_VLT_PACKAGE = "vlt:Package";

    /**
     * Nodetype name of a definition node
     */
    String NT_VLT_PACKAGE_DEFINITION = "vlt:PackageDefinition";

    /**
     * Nodename of the definition node
     */
    String NN_VLT_DEFINITION = "vlt:definition";

    /**
     * default mime type of a package
     */
    String MIME_TYPE = "application/zip";

    /**
     * Returns the package definition of this package
     * @return the package definition or <code>null</code> if this package is
     *         not valid.
     * @throws RepositoryException if an error occurrs
     */

    JcrPackageDefinition getDefinition() throws RepositoryException;

    /**
     * Checks if the underlying node contains the correct structure.
     * @return <code>true</code> if this package is valid.
     */
    boolean isValid();

    /**
     * Returns the underlying node
     * @return the node
     */
    Node getNode();

    /**
     * Checks if this package is sealed. this is the case, if it was not
     * modified since it was unwrapped.
     * @return <code>true</code> if this package is sealed.
     */
    boolean isSealed();

    /**
     * Returns the vault package stored in the data of this package
     * @return the package
     * @throws RepositoryException if an error occurs
     * @throws IOException if an I/O error occurs
     */
    VaultPackage getPackage() throws RepositoryException, IOException;

    /**
     * Extracts the package contents to the repository
     *
     * @param opts import options
     * @throws RepositoryException if a repository error during installation occurs.
     * @throws PackageException if an error during packaging occurs
     * @throws IllegalStateException if the package is not valid.
     * @throws IOException if an I/O error occurs
     * @since 2.3.14
     */
    void extract(ImportOptions opts)
            throws RepositoryException, PackageException, IOException;

    /**
     * Installs the package contents to the repository but creates a snapshot if
     * necessary.
     *
     * @param opts import options
     * @throws RepositoryException if a repository error during installation occurs.
     * @throws PackageException if an error during packaging occurs
     * @throws IllegalStateException if the package is not valid.
     * @throws IOException if an I/O error occurs
     *
     * @since 2.3.14
     */
    void install(ImportOptions opts)
            throws RepositoryException, PackageException, IOException;

    /**
     * Creates a snapshot of this package.
     *
     * @param opts export options
     * @param replace if <code>true</code> any existing snapshot is replaced.
     * @return a package that represents the snapshot of this package.
     * @throws RepositoryException if a repository error during installation occurs.
     * @throws PackageException if an error during packaging occurs
     * @throws IllegalStateException if the package is not valid.
     * @throws IOException if an I/O error occurs
     *
     * @since 2.0
     */
    JcrPackage snapshot(ExportOptions opts, boolean replace)
            throws RepositoryException, PackageException, IOException;

    /**
     * Returns the snapshot that was taken when installing this package.
     * @return the snapshot package or <code>null</code>
     * @throws RepositoryException if an error occurs.
     *
     * @since 2.0
     */
    JcrPackage getSnapshot() throws RepositoryException;

    /**
     * Reverts the changes of a prior installation of this package.
     *
     * @param opts import options
     * @throws RepositoryException if a repository error during installation occurs.
     * @throws PackageException if an error during packaging occurs or if no
     *         snapshot is available.
     * @throws IllegalStateException if the package is not valid.
     * @throws IOException if an I/O error occurs
     *
     * @since 2.3.14
     */
    void uninstall(ImportOptions opts)
            throws RepositoryException, PackageException, IOException;

    /**
     * Checks if the package id is correct in respect to the installation path
     * and adjusts it accordingly.
     *
     * @param autoFix <code>true</code> to automatically fix the id
     * @param autoSave <code>true</code> to save changes immediately
     * @return <code>true</code> if id is correct.
     * @throws RepositoryException if an error occurs.
     *
     * @since 2.2.18
     */
    boolean verifyId(boolean autoFix, boolean autoSave) throws RepositoryException;

    /**
     * Checks if this package is installed.
     *
     * Note: the default implementation only checks the {@link org.apache.jackrabbit.vault.packaging.JcrPackageDefinition#getLastUnpacked()}
     * date. If the package is replaced since it was installed. this method will return <code>false</code>.
     *
     * @return <code>true</code> if this package is installed.
     * @throws RepositoryException if an error occurs.
     *
     * @since 2.4.6
     */
    boolean isInstalled() throws RepositoryException;

    /**
     * Returns the size of the underlying package.
     * @return the size in bytes or -1 if not valid.
     */
    long getSize();

    /**
     * Closes this package and destroys all temporary data.
     */
    void close();

    /**
     * Returns the jcr:data property of the package
     * @return the jcr:data property
     * @throws RepositoryException if an error occurrs
     */
    Property getData() throws RepositoryException;

    /**
     * Returns the definition node or <code>null</code> if not exists
     * @return the definition node.
     * @throws RepositoryException if an error occurrs
     */
    Node getDefNode() throws RepositoryException;

}
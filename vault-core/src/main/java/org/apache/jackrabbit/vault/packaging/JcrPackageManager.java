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
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import javax.jcr.ItemExistsException;
import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.apache.jackrabbit.vault.fs.api.ProgressTrackerListener;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;

/**
 * Extends the {@link PackageManager} by repository specific operations.
 */
public interface JcrPackageManager extends PackageManager {

    /**
     * Opens a package with the given package id.
     *
     * @param id the package id.
     * @return the new package or <code>null</code> it the package does not exist or is not valid.
     * @throws RepositoryException if an error occurs
     * @since 2.3.22
     */
    JcrPackage open(PackageId id) throws RepositoryException;

    /**
     * Opens a package that is based on the given node.
     *
     * @param node the underlying node
     * @return the new package or <code>null</code> it the package is not
     *         valid.
     * @throws RepositoryException if an error occurs
     */
    JcrPackage open(Node node) throws RepositoryException;

    /**
     * Opens a package that is based on the given node. If <code>allowInvalid</code>
     * is <code>true</code> also invalid packages are returned, but only if the
     * node is file like (i.e. is nt:hierarchyNode and has a
     * jcr:content/jcr:data property).
     *
     * @param node the underlying node
     * @param allowInvalid if <code>true</code> invalid packages are openend, too.
     * @return the new package or <code>null</code> it the package is not
     *         valid unless <code>allowInvalid</code> is <code>true</code>.
     * @throws RepositoryException if an error occurs
     */
    JcrPackage open(Node node, boolean allowInvalid) throws RepositoryException;

    /**
     * Finds the id of the package that matches the given dependency best.
     * If <code>onlyInstalled</code> is <code>true</code> only installed packages are searched.
     *
     * @param dependency dependency information
     * @param onlyInstalled if <code>true</code> only installed packages are searched.
     *
     * @return the id of the matching package or <code>null</code> if not found.
     * @throws RepositoryException if an error occurs
     *
     * @since 2.4.6
     */
    PackageId resolve(Dependency dependency, boolean onlyInstalled) throws RepositoryException;

    /**
     * Uploads a package. The location is chosen from the installation path of
     * the package. if the package does not provide such a path, the nameHint
     * is respected and the package is placed below the package root.
     * if the package already exists at that path it is not installed and
     * <code>null</code> is returned unless <code>replace</code> is <code>true</code>.
     *
     * @param file package file to upload
     * @param isTmpFile indicates if the given file is a temp file and can be
     *        deleted when the package is closed
     * @param replace if <code>true</code> existing packages are replaced.
     * @param nameHint hint for the name if package does not provide one
     * @return the new jcr package or <code>null</code> if not installed
     * @throws RepositoryException if an error occurrs
     * @throws IOException if an I/O error occurrs
     */
    JcrPackage upload(File file, boolean isTmpFile, boolean replace, String nameHint)
            throws RepositoryException, IOException;

    /**
     * Uploads a package. The location is chosen from the installation path of
     * the package. if the package does not provide such a path, the nameHint
     * is respected and the package is placed below the package root.
     * if the package already exists at that path it is not uploaded a
     * {@link ItemExistsException} is thrown unless <code>replace</code> is
     * <code>true</code>.
     *
     * @param file package file to upload
     * @param isTmpFile indicates if the given file is a temp file and can be
     *        deleted when the package is closed
     * @param replace if <code>true</code> existing packages are replaced.
     * @param nameHint hint for the name if package does not provide one
     * @param strict if <code>true</code> import is more strict in regards to errors
     * @return the new jcr package
     * @throws RepositoryException if an error occurrs
     * @throws IOException if an I/O error occurrs
     */
    JcrPackage upload(File file, boolean isTmpFile, boolean replace, String nameHint, boolean strict)
            throws RepositoryException, IOException;

    /**
     * Uploads a package. The location is chosen from the installation path of
     * the package. if the package does not provide such a path an IOException is thrown.
     * if the package already exists at that path it is not uploaded a
     * {@link ItemExistsException} is thrown unless <code>replace</code> is
     * <code>true</code>.
     *
     * @param in input stream that provides the content of the package. note that after this method returns,
     *        the input stream is closed in any case.
     * @param replace if <code>true</code> existing packages are replaced.
     * @return the new jcr package
     * @throws RepositoryException if an error occurrs
     * @throws IOException if an I/O error occurrs
     */
    JcrPackage upload(InputStream in, boolean replace) throws RepositoryException, IOException;

    /**
     * Uploads a package. The location is chosen from the installation path of
     * the package. if the package does not provide such a path an IOException is thrown.
     * if the package already exists at that path it is not uploaded a
     * {@link ItemExistsException} is thrown unless <code>replace</code> is
     * <code>true</code>.
     *
     * @param in input stream that provides the content of the package. note that after this method returns,
     *        the input stream is closed in any case.
     * @param replace if <code>true</code> existing packages are replaced.
     * @param strict if <code>true</code> import is more strict in regards to errors
     * @return the new jcr package
     * @throws RepositoryException if an error occurrs
     * @throws IOException if an I/O error occurrs
     */
    JcrPackage upload(InputStream in, boolean replace, boolean strict) throws RepositoryException, IOException;

    /**
     * Creates a new package below the given folder.
     *
     * @param folder parent folder
     * @param name name of the new package
     * @return a new jcr package
     * @throws RepositoryException if a repository error occurrs
     * @throws IOException if an I/O exception occurs
     */
    JcrPackage create(Node folder, String name)
            throws RepositoryException, IOException;

    /**
     * Creates a new package with the new group and name.
     *
     * @param group group of the new package
     * @param name name of the new package
     * @return a new jcr package
     * @throws RepositoryException if a repository error occurrs
     * @throws IOException if an I/O exception occurs
     * @since 2.2.5
     */
    JcrPackage create(String group, String name)
            throws RepositoryException, IOException;

    /**
     * Creates a new package with the new group, name and version.
     *
     * @param group group of the new package
     * @param name name of the new package
     * @param version version of the new package; can be <code>null</code>
     * @return a new jcr package
     * @throws RepositoryException if a repository error occurrs
     * @throws IOException if an I/O exception occurs
     * @since 2.3
     */
    JcrPackage create(String group, String name, String version)
            throws RepositoryException, IOException;

    /**
     * Removes a package and its snaphost if present.
     * @param pack the package to remove
     * @throws RepositoryException if a repository error occurrs
     * @since 2.2.7
     */
    void remove(JcrPackage pack) throws RepositoryException;

    /**
     * Renames the given package with a new group id and name. Please note that
     * the package is moved and the internal 'path' is adjusted in the deinition,
     * but the package is not rewrapped.
     *
     * @param pack the package to rename
     * @param groupId the new group id or <code>null</code>
     * @param name the new name or <code>null</code>
     * @return the renamed package
     * @throws RepositoryException if an error occurs
     * @throws PackageException if the package is not unwrapped.
     *
     * @since 2.0
     */
    JcrPackage rename(JcrPackage pack, String groupId, String name)
            throws PackageException, RepositoryException;

    /**
     * Renames the given package with a new group id, name and version. Please
     * note that the package is moved and the internal 'path' is adjusted in
     * the definition, but the package is not rewrapped.
     *
     * @param pack the package to rename
     * @param groupId the new group id or <code>null</code>
     * @param name the new name or <code>null</code>
     * @param version the new version or <code>null</code>
     * @return the renamed package
     * @throws RepositoryException if an error occurs
     * @throws PackageException if the package is not unwrapped.
     *
     * @since 2.3
     */
    JcrPackage rename(JcrPackage pack, String groupId, String name, String version)
            throws PackageException, RepositoryException;

    /**
     * Assembles a package.
     * @param pack the package to assemble
     * @param listener a progress listener
     * @throws PackageException if a package error occurs
     * @throws RepositoryException if a repository error occurs
     * @throws IOException if an I/O error occurs
     */
    void assemble(JcrPackage pack, ProgressTrackerListener listener)
            throws PackageException, RepositoryException, IOException;

    /**
     * Assembles a package.
     * @param packNode the node of the package
     * @param definition the definition of the package
     * @param listener a progress listener
     * @throws PackageException if a package error occurs
     * @throws RepositoryException if a repository error occurs
     * @throws IOException if an I/O error occurs
     */
    void assemble(Node packNode, JcrPackageDefinition definition,
                         ProgressTrackerListener listener)
            throws PackageException, RepositoryException, IOException;

    /**
     * Assembles a package directly to a output stream
     * @param definition the definition of the package
     * @param listener a progress listener
     * @param out the output stream to write to
     * @throws RepositoryException if a repository error occurs
     * @throws IOException if an I/O error occurs
     * @throws PackageException if a package error occurs
     */
    void assemble(JcrPackageDefinition definition,
                         ProgressTrackerListener listener, OutputStream out)
            throws IOException, RepositoryException, PackageException;

    /**
     * Rewraps the package in respect to its underlying definition.
     * @param pack the package to rewrap
     * @param listener the progress listener
     * @throws PackageException if a package error occurs
     * @throws RepositoryException if a repository error occurs
     * @throws IOException if an I/O error occurs
     */
    void rewrap(JcrPackage pack, ProgressTrackerListener listener)
            throws PackageException, RepositoryException, IOException;

    /**
     * Returns the configured package root node.
     * @return the package root node
     * @throws RepositoryException if an error occurs
     */
    Node getPackageRoot() throws RepositoryException;

    /**
     * Returns the configured package root node.
     * @param noCreate do not create missing root if <code>true</code>
     * @return the package root node or <code>null</code> if not present and noCreate is <code>true</code>.
     * @throws RepositoryException if an error occurs
     */
    Node getPackageRoot(boolean noCreate) throws RepositoryException;

    /**
     * Returns the list of all packages installed below the package root.
     *
     * @return a list of packages
     * @throws RepositoryException if an error occurs
     */
    List<JcrPackage> listPackages() throws RepositoryException;

    /**
     * Returns the list of all packages installed below the package root that are
     * included in the filter.
     *
     * @param filter filter for packages
     * @return a list of packages
     * @throws RepositoryException if an error occurs
     */
    List<JcrPackage> listPackages(WorkspaceFilter filter) throws RepositoryException;


    /**
     * Returns the list of all packages installed below the package root that
     * match the given group. if <code>group</code> is <code>null</code> all
     * packages are returned.
     *
     * @param group the group filter
     * @param built if <code>true</code> only packages with size > 0 are listed
     * @return the list of packages
     * @throws RepositoryException if an error occurs
     */
    List<JcrPackage> listPackages(String group, boolean built) throws RepositoryException;
}
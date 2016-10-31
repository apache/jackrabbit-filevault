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

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
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
     * @return the new package or {@code null} it the package does not exist or is not valid.
     * @throws RepositoryException if an error occurs
     * @since 2.3.22
     */
    @CheckForNull
    JcrPackage open(@Nonnull PackageId id) throws RepositoryException;

    /**
     * Opens a package that is based on the given node.
     *
     * @param node the underlying node
     * @return the new package or {@code null} it the package is not
     *         valid.
     * @throws RepositoryException if an error occurs
     */
    @CheckForNull
    JcrPackage open(@Nonnull Node node) throws RepositoryException;

    /**
     * Opens a package that is based on the given node. If {@code allowInvalid}
     * is {@code true} also invalid packages are returned, but only if the
     * node is file like (i.e. is nt:hierarchyNode and has a
     * jcr:content/jcr:data property).
     *
     * @param node the underlying node
     * @param allowInvalid if {@code true} invalid packages are openend, too.
     * @return the new package or {@code null} it the package is not
     *         valid unless {@code allowInvalid} is {@code true}.
     * @throws RepositoryException if an error occurs
     */
    @CheckForNull
    JcrPackage open(@Nonnull Node node, boolean allowInvalid) throws RepositoryException;

    /**
     * Finds the id of the package that matches the given dependency best.
     * If {@code onlyInstalled} is {@code true} only installed packages are searched.
     *
     * @param dependency dependency information
     * @param onlyInstalled if {@code true} only installed packages are searched.
     *
     * @return the id of the matching package or {@code null} if not found.
     * @throws RepositoryException if an error occurs
     *
     * @since 2.4.6
     */
    @CheckForNull
    PackageId resolve(@Nonnull Dependency dependency, boolean onlyInstalled) throws RepositoryException;

    /**
     * Returns the package ids of installed packages that depend on the given package.
     *
     * @param id the package id to search for
     * @return the array of package ids.
     * @throws RepositoryException if an error occurs
     *
     * @since 3.1.32
     */
    @Nonnull
    PackageId[] usage(@Nonnull PackageId id) throws RepositoryException;

    /**
     * Uploads a package. The location is chosen from the installation path of
     * the package. if the package does not provide such a path, the nameHint
     * is respected and the package is placed below the package root.
     * if the package already exists at that path it is not installed and
     * {@code null} is returned unless {@code replace} is {@code true}.
     *
     * @param file package file to upload
     * @param isTmpFile indicates if the given file is a temp file and can be
     *        deleted when the package is closed
     * @param replace if {@code true} existing packages are replaced.
     * @param nameHint hint for the name if package does not provide one
     * @return the new jcr package
     * @throws RepositoryException if an error occurrs
     * @throws IOException if an I/O error occurrs
     */
    @Nonnull
    JcrPackage upload(@Nonnull File file, boolean isTmpFile, boolean replace, @Nullable String nameHint)
            throws RepositoryException, IOException;

    /**
     * Uploads a package. The location is chosen from the installation path of
     * the package. if the package does not provide such a path, the nameHint
     * is respected and the package is placed below the package root.
     * if the package already exists at that path it is not uploaded a
     * {@link ItemExistsException} is thrown unless {@code replace} is
     * {@code true}.
     *
     * @param file package file to upload
     * @param isTmpFile indicates if the given file is a temp file and can be
     *        deleted when the package is closed
     * @param replace if {@code true} existing packages are replaced.
     * @param nameHint hint for the name if package does not provide one
     * @param strict if {@code true} import is more strict in regards to errors
     * @return the new jcr package
     * @throws RepositoryException if an error occurrs
     * @throws IOException if an I/O error occurrs
     */
    @Nonnull
    JcrPackage upload(@Nonnull File file, boolean isTmpFile, boolean replace, @Nullable String nameHint, boolean strict)
            throws RepositoryException, IOException;

    /**
     * Uploads a package. The location is chosen from the installation path of
     * the package. if the package does not provide such a path an IOException is thrown.
     * if the package already exists at that path it is not uploaded a
     * {@link ItemExistsException} is thrown unless {@code replace} is
     * {@code true}.
     *
     * @param in input stream that provides the content of the package. note that after this method returns,
     *        the input stream is closed in any case.
     * @param replace if {@code true} existing packages are replaced.
     * @return the new jcr package
     * @throws RepositoryException if an error occurrs
     * @throws IOException if an I/O error occurrs
     */
    @Nonnull
    JcrPackage upload(@Nonnull InputStream in, boolean replace) throws RepositoryException, IOException;

    /**
     * Uploads a package. The location is chosen from the installation path of
     * the package. if the package does not provide such a path an IOException is thrown.
     * if the package already exists at that path it is not uploaded a
     * {@link ItemExistsException} is thrown unless {@code replace} is
     * {@code true}.
     *
     * @param in input stream that provides the content of the package. note that after this method returns,
     *        the input stream is closed in any case.
     * @param replace if {@code true} existing packages are replaced.
     * @param strict if {@code true} import is more strict in regards to errors
     * @return the new jcr package
     * @throws RepositoryException if an error occurrs
     * @throws IOException if an I/O error occurrs
     */
    @Nonnull
    JcrPackage upload(@Nonnull InputStream in, boolean replace, boolean strict) throws RepositoryException, IOException;

    /**
     * Creates a new package below the given folder.
     *
     * @param folder parent folder or {@code null} for the package root
     * @param name name of the new package
     * @return a new jcr package
     * @throws RepositoryException if a repository error occurrs
     * @throws IOException if an I/O exception occurs
     */
    @Nonnull
    JcrPackage create(@Nullable Node folder, @Nonnull String name)
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
    @Nonnull
    JcrPackage create(@Nonnull String group, @Nonnull String name)
            throws RepositoryException, IOException;

    /**
     * Creates a new package with the new group, name and version.
     *
     * @param group group of the new package
     * @param name name of the new package
     * @param version version of the new package; can be {@code null}
     * @return a new jcr package
     * @throws RepositoryException if a repository error occurrs
     * @throws IOException if an I/O exception occurs
     * @since 2.3
     */
    @Nonnull
    JcrPackage create(@Nonnull String group, @Nonnull String name, @Nullable String version)
            throws RepositoryException, IOException;

    /**
     * Removes a package and its snapshots if present.
     * @param pack the package to remove
     * @throws RepositoryException if a repository error occurrs
     * @since 2.2.7
     */
    void remove(@Nonnull JcrPackage pack) throws RepositoryException;

    /**
     * Renames the given package with a new group id and name. Please note that
     * the package is moved and the internal 'path' is adjusted in the definition,
     * but the package is not rewrapped.
     *
     * @param pack the package to rename
     * @param groupId the new group id or {@code null}
     * @param name the new name or {@code null}
     * @return the renamed package
     * @throws RepositoryException if an error occurs
     * @throws PackageException if the package is not unwrapped.
     *
     * @since 2.0
     */
    @Nonnull
    JcrPackage rename(@Nonnull JcrPackage pack, @Nullable String groupId, @Nullable String name)
            throws PackageException, RepositoryException;

    /**
     * Renames the given package with a new group id, name and version. Please
     * note that the package is moved and the internal 'path' is adjusted in
     * the definition, but the package is not rewrapped.
     *
     * @param pack the package to rename
     * @param groupId the new group id or {@code null}
     * @param name the new name or {@code null}
     * @param version the new version or {@code null}
     * @return the renamed package
     * @throws RepositoryException if an error occurs
     * @throws PackageException if the package is not unwrapped.
     *
     * @since 2.3
     */
    @Nonnull
    JcrPackage rename(@Nonnull JcrPackage pack, @Nullable String groupId, @Nullable String name, @Nullable String version)
            throws PackageException, RepositoryException;

    /**
     * Assembles a package.
     * @param pack the package to assemble
     * @param listener a progress listener
     * @throws PackageException if a package error occurs
     * @throws RepositoryException if a repository error occurs
     * @throws IOException if an I/O error occurs
     */
    void assemble(@Nonnull JcrPackage pack, @Nullable ProgressTrackerListener listener)
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
    void assemble(@Nonnull Node packNode, @Nonnull JcrPackageDefinition definition, @Nullable ProgressTrackerListener listener)
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
    void assemble(@Nonnull JcrPackageDefinition definition, @Nullable ProgressTrackerListener listener, @Nonnull OutputStream out)
            throws IOException, RepositoryException, PackageException;

    /**
     * Rewraps the package in respect to its underlying definition.
     * @param pack the package to rewrap
     * @param listener the progress listener
     * @throws PackageException if a package error occurs
     * @throws RepositoryException if a repository error occurs
     * @throws IOException if an I/O error occurs
     */
    void rewrap(@Nonnull JcrPackage pack, @Nullable ProgressTrackerListener listener)
            throws PackageException, RepositoryException, IOException;

    /**
     * Returns the configured package root node.
     * @return the package root node
     * @throws RepositoryException if an error occurs
     */
    @Nonnull
    Node getPackageRoot() throws RepositoryException;

    /**
     * Returns the configured package root node.
     * @param noCreate do not create missing root if {@code true}
     * @return the package root node or {@code null} if not present and noCreate is {@code true}.
     * @throws RepositoryException if an error occurs
     */
    @CheckForNull
    Node getPackageRoot(boolean noCreate) throws RepositoryException;

    /**
     * Returns the list of all packages installed below the package root.
     *
     * @return a list of packages
     * @throws RepositoryException if an error occurs
     */
    @Nonnull
    List<JcrPackage> listPackages() throws RepositoryException;

    /**
     * Returns the list of all packages installed below the package root that are
     * included in the filter.
     *
     * @param filter filter for packages
     * @return a list of packages
     * @throws RepositoryException if an error occurs
     */
    @Nonnull
    List<JcrPackage> listPackages(@Nullable WorkspaceFilter filter) throws RepositoryException;

    /**
     * Returns the list of all packages installed below the package root that
     * match the given group. if {@code group} is {@code null} all
     * packages are returned.
     *
     * @param group the group filter
     * @param built if {@code true} only packages with size &gt; 0 are listed
     * @return the list of packages
     * @throws RepositoryException if an error occurs
     */
    @Nonnull
    List<JcrPackage> listPackages(@Nullable String group, boolean built) throws RepositoryException;
}
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
import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.fs.io.ImportOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.annotation.versioning.ProviderType;

/**
 * Extends the {@link PackageManager} by repository specific operations.
 */
@ProviderType
public interface JcrPackageManager extends PackageManager {

    /**
     * Opens a package with the given package id.
     *
     * @param id the package id.
     * @return the new package or {@code null} it the package does not exist or is not valid.
     * @throws RepositoryException if an error occurs
     * @since 2.3.22
     */
    @Nullable
    JcrPackage open(@NotNull PackageId id) throws RepositoryException;

    /**
     * Opens a package that is based on the given node.
     *
     * @param node the underlying node
     * @return the new package or {@code null} it the package is not
     *         valid.
     * @throws RepositoryException if an error occurs
     */
    @Nullable
    JcrPackage open(@NotNull Node node) throws RepositoryException;

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
    @Nullable
    JcrPackage open(@NotNull Node node, boolean allowInvalid) throws RepositoryException;

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
    @Nullable
    PackageId resolve(@NotNull Dependency dependency, boolean onlyInstalled) throws RepositoryException;

    /**
     * Returns the package ids of installed packages that depend on the given package.
     *
     * @param id the package id to search for
     * @return the array of package ids.
     * @throws RepositoryException if an error occurs
     *
     * @since 3.1.32
     */
    @NotNull
    PackageId[] usage(@NotNull PackageId id) throws RepositoryException;

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
    @NotNull
    JcrPackage upload(@NotNull File file, boolean isTmpFile, boolean replace, @Nullable String nameHint)
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
    @NotNull
    JcrPackage upload(@NotNull File file, boolean isTmpFile, boolean replace, @Nullable String nameHint, boolean strict)
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
    @NotNull
    JcrPackage upload(@NotNull InputStream in, boolean replace) throws RepositoryException, IOException;

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
    @NotNull
    JcrPackage upload(@NotNull InputStream in, boolean replace, boolean strict) throws RepositoryException, IOException;

    /**
     * Creates a new package below the given folder.
     *
     * @param folder parent folder or {@code null} for the package root
     * @param name name of the new package
     * @return a new jcr package
     * @throws RepositoryException if a repository error occurrs
     * @throws IOException if an I/O exception occurs
     */
    @NotNull
    JcrPackage create(@Nullable Node folder, @NotNull String name)
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
    @NotNull
    JcrPackage create(@NotNull String group, @NotNull String name)
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
    @NotNull
    JcrPackage create(@NotNull String group, @NotNull String name, @Nullable String version)
            throws RepositoryException, IOException;

    /**
     * Extracts a package directly from the given archive without uploading it to the repository first.
     * A package node is created but w/o any content. The resulting package cannot be downloaded, uninstalled or
     * re-installed.
     * <p>
     * If the package defines unsatisfied dependencies {@link DependencyHandling} might cause the extraction to fail.
     * <p>
     * If the package contains sub-packages, they will follow the same behaviour, i.e. they will not be uploaded to the
     * repository but directly installed unless {@link ImportOptions#setNonRecursive(boolean)} is set to true, in which
     * case the sub packages will be uploaded.
     * <p>
     * The method will throw an {@link ItemExistsException} if a package with the same id already exists, unless
     * {@code replace} is set to {@code true}.
     * <p>
     *
     * @param archive the input archive that contains the package.
     * @param options the import options
     * @param replace if {@code true} existing packages are replaced.
     * @return an array of the package(s) that were extracted.
     * @throws RepositoryException if an error occurs
     * @throws IOException if an I/O error occurrs
     * @throws PackageException if an internal error occurrs
     * @throws IOException if an I/O exception occurs
     */
    @NotNull
    PackageId[] extract(@NotNull Archive archive, @NotNull ImportOptions options, boolean replace)
            throws RepositoryException, PackageException, IOException;

    /**
     * Removes a package and its snapshots if present.
     * @param pack the package to remove
     * @throws RepositoryException if a repository error occurrs
     * @since 2.2.7
     */
    void remove(@NotNull JcrPackage pack) throws RepositoryException;

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
    @NotNull
    JcrPackage rename(@NotNull JcrPackage pack, @Nullable String groupId, @Nullable String name)
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
    @NotNull
    JcrPackage rename(@NotNull JcrPackage pack, @Nullable String groupId, @Nullable String name, @Nullable String version)
            throws PackageException, RepositoryException;

    /**
     * Assembles a package.
     * @param pack the package to assemble
     * @param listener a progress listener
     * @throws PackageException if a package error occurs
     * @throws RepositoryException if a repository error occurs
     * @throws IOException if an I/O error occurs
     */
    void assemble(@NotNull JcrPackage pack, @Nullable ProgressTrackerListener listener)
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
    void assemble(@NotNull Node packNode, @NotNull JcrPackageDefinition definition, @Nullable ProgressTrackerListener listener)
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
    void assemble(@NotNull JcrPackageDefinition definition, @Nullable ProgressTrackerListener listener, @NotNull OutputStream out)
            throws IOException, RepositoryException, PackageException;

    /**
     * Rewraps the package in respect to its underlying definition.
     * @param pack the package to rewrap
     * @param listener the progress listener
     * @throws PackageException if a package error occurs
     * @throws RepositoryException if a repository error occurs
     * @throws IOException if an I/O error occurs
     */
    void rewrap(@NotNull JcrPackage pack, @Nullable ProgressTrackerListener listener)
            throws PackageException, RepositoryException, IOException;

    /**
     * Returns the configured package root node.
     * @return the package root node
     * @throws RepositoryException if an error occurs
     */
    @NotNull
    Node getPackageRoot() throws RepositoryException;

    /**
     * Returns the configured package root node.
     * @param noCreate do not create missing root if {@code true}
     * @return the package root node or {@code null} if not present and noCreate is {@code true}.
     * @throws RepositoryException if an error occurs
     */
    @Nullable
    Node getPackageRoot(boolean noCreate) throws RepositoryException;

    /**
     * Returns the list of all packages installed below the package root.
     *
     * @return a list of packages
     * @throws RepositoryException if an error occurs
     */
    @NotNull
    List<JcrPackage> listPackages() throws RepositoryException;

    /**
     * Returns the list of all packages installed below the package root that are
     * included in the filter.
     *
     * @param filter filter for packages
     * @return a list of packages
     * @throws RepositoryException if an error occurs
     */
    @NotNull
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
    @NotNull
    List<JcrPackage> listPackages(@Nullable String group, boolean built) throws RepositoryException;
}

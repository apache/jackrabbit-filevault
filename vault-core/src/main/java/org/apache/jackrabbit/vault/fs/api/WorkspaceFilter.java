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

package org.apache.jackrabbit.vault.fs.api;

import java.io.InputStream;
import java.util.List;
import java.util.Set;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.annotation.versioning.ProviderType;

/**
 * {@code WorkspaceFilter} defined a filter for items (node or property).
 */
@ProviderType
public interface WorkspaceFilter extends Dumpable {

    /**
     * Returns a list of path filter sets for node items.
     * @return the list of path filter sets.
     */
    @NotNull
    List<PathFilterSet> getFilterSets();

    /**
     * Returns a list of path filter sets for property items.
     * @return the list of path filter sets.
     */
    @NotNull
    List<PathFilterSet> getPropertyFilterSets();

    /**
     * Returns the filter set that covers the respective node path
     * @param path the path
     * @return the filter set or {@code null}
     */
    @Nullable
    PathFilterSet getCoveringFilterSet(@NotNull String path);

    /**
     * Returns the import mode for the given node path.
     * @param path path to check
     * @return the import mode or {@link ImportMode#REPLACE} if the given path
     *         is not covered by this filter.
     */
    @NotNull
    ImportMode getImportMode(@NotNull String path);

    /**
     * Checks if the given node path is contained (and by that also covered)
     * in this workspace filter.
     * It returns {@code true} if any of the filter sets contain the path
     * and it's not globally ignored.
     * <p>
     * If {@link #contains(String)} returns {@code true} for one path, 
     * also {@link #covers(String)} would return {@code true} for the same path, 
     * but not vice-versa.
     *
     * @param path to check
     * @return {@code true} if the given path is included in this filter.
     */
    boolean contains(@NotNull String path);

    /**
     * Checks if the given node path is covered in this workspace filter.
     * It only returns {@code true} if at least one of the sets covers
     * the path and is not globally ignored.
     * <p>
     * Still {@link WorkspaceFilter#contains(String)} might return {@code false}
     * for the same path in case there is  some exclusion patterns matching
     * the given path.
     *
     * @param path the path to check
     * @return {@code true} if the given path is covered by this filter.
     */
    boolean covers(@NotNull String path);

    /**
     * Checks if the given node path is an ancestor of any of the filter sets.
     *
     * @param path the item to check
     * @return {@code true} if the given item is an ancestor
     */
    boolean isAncestor(@NotNull String path);

    /**
     * Matches the given path with all filter roots. For each, if it is an ancestor,
     * add the name of the first path segment of the remaining filter root "below" path
     * to the result set.
     * <p>
     * Will return {@code null} if that segment can not be determined for some filters.
     *
     * @param path Path to check
     * @return first path segments of non-matched paths, or {@code null} when result set
     * can not be computed.
     */
    default @Nullable Set<String> getDirectChildNamesTowardsFilterRoots(@NotNull String path) {
        return null;
    }

    /**
     * Checks if the given node path is globally ignored.
     *
     * @param path the path to check.
     * @return {@code true} if the item is globally ignored.
     */
    boolean isGloballyIgnored(@NotNull String path);

    /**
     * Returns the source xml that constructs this filter
     * It is the obligation of the caller to close the returned input stream.
     * 
     * @return the source xml
     */
    @NotNull
    InputStream getSource();

    /**
     * Returns the source xml that constructs this filter
     * @return the source xml
     */
    @NotNull
    String getSourceAsString();

    /**
     * Translates this workspace filter using the given path mapping.
     *
     * @param mapping the path mapping
     * @return a new workspace filter
     * @since 2.4.10
     */
    @NotNull
    WorkspaceFilter translate(@Nullable PathMapping mapping);

    /**
     * Dumps the coverage of this filter against the given node to the listener.
     * @param rootNode root node
     * @param listener listener which receives coverage information
     * @throws RepositoryException if an error occurs
     */
    void dumpCoverage(@NotNull Node rootNode, @NotNull ProgressTrackerListener listener)
            throws RepositoryException;

    /**
     * Dumps the coverage of this filter using the given session. If {@code skipJcrContent} is {@code true}
     * the jcr:content nodes are excluded from traversal and reporting.
     *
     * @param session session
     * @param listener listener which receives coverage information
     * @param skipJcrContent {@code true} to skip jcr:content nodes
     * @throws RepositoryException if an error occurs
     */
    void dumpCoverage(@NotNull Session session, @NotNull ProgressTrackerListener listener, boolean skipJcrContent)
            throws RepositoryException;

    /**
     * Tests if the given workspace filter includes the given property. If the filter does not cover the property,
     * it returns {@code true}.
     *
     * @param propertyPath the path to the property
     * @return {@code true} if the property is included in the filter
     */
    boolean includesProperty(String propertyPath);
}

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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

/**
 * {@code WorkspaceFilter} defined a filter for items (node or property).
 */
public interface WorkspaceFilter extends Dumpable {

    /**
     * Returns a list of path filter sets for node items.
     * @return the list of path filter sets.
     */
    @Nonnull
    List<PathFilterSet> getFilterSets();

    /**
     * Returns a list of path filter sets for property items.
     * @return the list of path filter sets.
     */
    @Nonnull
    List<PathFilterSet> getPropertyFilterSets();

    /**
     * Returns the filter set that covers the respective node path
     * @param path the path
     * @return the filter set or {@code null}
     */
    @Nullable
    PathFilterSet getCoveringFilterSet(@Nonnull String path);

    /**
     * Returns the import mode for the given node path.
     * @param path path to check
     * @return the import mode or {@link ImportMode#REPLACE} if the given path
     *         is not covered by this filter.
     */
    @Nonnull
    ImportMode getImportMode(@Nonnull String path);

    /**
     * Checks if the given node path is contained in this workspace filter.
     * It returns {@code true} if any of the filter sets contain the path
     * and it's not globally ignored.
     *
     * @param path to check
     * @return {@code true} if the given path is included in this filter.
     */
    boolean contains(@Nonnull String path);

    /**
     * Checks if the given node path is covered in this workspace filter.
     * It only returns {@code true} if at least one of the sets covers
     * the path and is not globally ignored.
     *
     * @param path the pathto check
     * @return {@code true} if the given path is covered by this filter.
     */
    boolean covers(@Nonnull String path);

    /**
     * Checks if the given node path is an ancestor of any of the filter sets.
     *
     * @param path the item to check
     * @return {@code true} if the given item is an ancestor
     */
    boolean isAncestor(@Nonnull String path);

    /**
     * Checks if the given node path is globally ignored.
     *
     * @param path the path to check.
     * @return {@code true} if the item is globally ignored.
     */
    boolean isGloballyIgnored(@Nonnull String path);

    /**
     * Returns the source xml that constructs this filter
     * It is the obligation of the caller to close the returned input stream.
     * 
     * @return the source xml
     */
    @Nonnull
    InputStream getSource();

    /**
     * Returns the source xml that constructs this filter
     * @return the source xml
     */
    @Nonnull
    String getSourceAsString();

    /**
     * Translates this workspace filter using the given path mapping.
     *
     * @param mapping the path mapping
     * @return a new workspace filter
     * @since 2.4.10
     */
    @Nonnull
    WorkspaceFilter translate(@Nullable PathMapping mapping);

    /**
     * Dumps the coverage of this filter against the given node to the listener.
     * @param rootNode root node
     * @param listener listener
     * @throws RepositoryException if an error occurs
     */
    void dumpCoverage(@Nonnull Node rootNode, @Nonnull ProgressTrackerListener listener)
            throws RepositoryException;

    /**
     * Dumps the coverage of this filter using the given session. The traversal starts
     * at the common ancestor of all filter sets. If {@code skipJcrContent} is {@code true}
     * the jcr:content nodes are excluded from traversal and reporting.
     *
     * @param session session
     * @param listener listener to report progress
     * @param skipJcrContent {@code true} to skip jcr:content nodes
     * @throws RepositoryException if an error occurs
     */
    void dumpCoverage(@Nonnull Session session, @Nonnull ProgressTrackerListener listener, boolean skipJcrContent)
            throws RepositoryException;
}
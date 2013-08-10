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

import java.io.IOException;
import java.io.PrintWriter;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

/**
 * <code>AggregateManager</code>...
 */
public interface AggregateManager extends NodeTypesCollector {
    /**
     * Releases all resources attached to this artifact manager
     * @throws RepositoryException if an error occurs.
     */
    void unmount() throws RepositoryException;

    /**
     * Returns the root aggregate of the tree
     * @return the root aggregate.
     * @throws RepositoryException if an error occurs.
     */
    Aggregate getRoot() throws RepositoryException;

    /**
     * Returns the repository address of the mountpoint of this artifacts node
     * tree.
     * @return the mountpoint
     */
    RepositoryAddress getMountpoint();

    /**
     * Returns the aggregator for the given node or <code>null</code> if none
     * found.
     * @param node for which the aggregator is to be returned
     * @param path the path of the node or <code>null</code>
     * @return the aggregator or <code>null</code>
     * @throws RepositoryException if a repository error occurs.
     */
    Aggregator getAggregator(Node node, String path) throws RepositoryException;

    /**
     * Returns the workspace filter.
     * @return the workspace filter.
     */
    WorkspaceFilter getWorkspaceFilter();

    /**
     * Checks if this tree is still mounted and if the attached session
     * is still live.
     *
     * @return <code>true</code> if still mounted
     */
    boolean isMounted();

    /**
     * Returns the user id of the session of this manager.
     * @return the user id.
     * @throws RepositoryException if an error occurs.
     */
    String getUserId() throws RepositoryException;

    /**
     * Returns the name of the workspace of this manager.
     * @return the name of the workspace.
     * @throws RepositoryException if an error occurs.
     */
    String getWorkspace() throws RepositoryException;

    /**
     * Returns the session of this manager.
     * @return the jcr session
     */
    Session getSession();

    /**
     * Returns the config
     * @return the config
     */
    VaultFsConfig getConfig();

    /**
     * Dumps the configuration to the given writer for debugging purposes.
     * @param out the writer
     * @throws IOException if an I/O error occurs
     */
    void dumpConfig(PrintWriter out) throws IOException;

    /**
     * Starts tracking aggregate lifecycles
     * @param listener optional listener
     */
    public void startTracking(ProgressTrackerListener listener);

    /**
     * Stops tracking of aggregate lifecycle
     */
    public void stopTracking();


}
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

import javax.jcr.RepositoryException;

/**
 * <code>VaultFileSystem</code>...
 */
public interface VaultFileSystem {

    /**
     * Releases all resources attached to this Vault filesystem
     * @throws RepositoryException if an error occurs.
     */
    void unmount() throws RepositoryException;

    /**
     * Checks if this tree is still mounted and if the attached session
     * is still live.
     *
     * @return <code>true</code> if still mounted
     */
    boolean isMounted();

    /**
     * Returns the root file
     * @return the root file
     */
    VaultFile getRoot();

    /**
     * Returns the attached artifacts manager.
     * @return the attached artifacts manager.
     */
    AggregateManager getAggregateManager();

    /**
     * Returns the file at the given path. If the file does not exists
     * <code>null</code> is thrown.
     *
     * @param path the path of the file
     * @return the file or <code>null</code>
     * @throws IOException if an I/O error occurs.
     * @throws RepositoryException if a repository error occurs.
     */
    VaultFile getFile(String path) throws IOException, RepositoryException;

    /**
     * Returns the file at the given path. The path can be relative and may
     * contain ".." path elements. If the file does not exists <code>null</code>
     * is returned.
     *
     * @param parent the parent file.
     * @param path the path of the file
     * @return the file or <code>null</code>
     * @throws IOException if an I/O error occurs.
     * @throws RepositoryException if a repository error occurs.
     */
    VaultFile getFile(VaultFile parent, String path)
            throws IOException, RepositoryException;

    /**
     * Starts a new transaction.
     * @return a new transaction.
     */
    VaultFsTransaction startTransaction();

    /**
     * Flushes the file cache
     * @throws RepositoryException if an error occurs
     */
    void invalidate() throws RepositoryException;

    /**
     * Returns the vault configuration that is used
     * @return the vault configuration.
     */
    VaultFsConfig getConfig();

    /**
     * Returns the current workspace filter
     * @return the workspace filter
     */
    WorkspaceFilter getWorkspaceFilter();

    
}
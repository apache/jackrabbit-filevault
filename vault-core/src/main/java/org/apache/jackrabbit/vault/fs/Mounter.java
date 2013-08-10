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

package org.apache.jackrabbit.vault.fs;

import java.io.IOException;

import javax.jcr.Credentials;
import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.jackrabbit.vault.fs.api.RepositoryAddress;
import org.apache.jackrabbit.vault.fs.api.VaultFileSystem;
import org.apache.jackrabbit.vault.fs.api.VaultFsConfig;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.fs.impl.AggregateManagerImpl;
import org.apache.jackrabbit.vault.fs.impl.VaultFileSystemImpl;

/**
 * Utility method to mount a JCR FS.
 * The filesystem is mounted relative to the given <code>mountpoint</code> and rooted at <code>rootPath</code>.
 * For example if the mountpoint is http://.../test/export and the rootPath is /foo, then the filesystem's root node
 * has a internal repository path "/foo" that corresponds to the "real" repository node at "/test/export".
 * The workspace filter will be matched against the filesystem paths (e.g. /foo).
 */
public final class Mounter {

    /**
     * Mounts a new Vault filesystem on the given repository node.
     *
     * @param config vault fs config
     * @param wspFilter the workspace filter
     * @param mountpoint the address of the mountpoint
     * @param rootPath path of root file. used for remapping
     * @param session the repository session
     * @return a Vault filesystem
     * @throws RepositoryException if an error occurs.
     * @throws IOException if an I/O error occurs.
     */
    public static VaultFileSystem mount(VaultFsConfig config,
                                      WorkspaceFilter wspFilter,
                                      RepositoryAddress mountpoint,
                                      String rootPath,
                                      Session session)
            throws RepositoryException, IOException {
        return new VaultFileSystemImpl(
                AggregateManagerImpl.mount(
                        config, wspFilter, mountpoint, session
                ).getRoot(),
                rootPath,
                true
        );
    }

    /**
     * Mounts a new Vault filesystem that is rooted at the given path using
     * the provided repository, credentials and workspace to create the
     * session.
     *
     * @param config vault fs config
     * @param wspFilter the workspace filter
     * @param rep the jcr repository
     * @param credentials the credentials
     * @param mountpoint the repository address of the mountpoint
     * @param rootPath path of root file. used for remapping
     * @return an aggregate manager
     * @throws RepositoryException if an error occurs.
     * @throws IOException if an I/O error occurs.
     */
    public static VaultFileSystem mount(VaultFsConfig config,
                                      WorkspaceFilter wspFilter,
                                      Repository rep,
                                      Credentials credentials,
                                      RepositoryAddress mountpoint,
                                      String rootPath)
    throws RepositoryException, IOException {
        return new VaultFileSystemImpl(
                AggregateManagerImpl.mount(config, wspFilter, rep, credentials,
                        mountpoint).getRoot(),
                rootPath, true);
    }


}
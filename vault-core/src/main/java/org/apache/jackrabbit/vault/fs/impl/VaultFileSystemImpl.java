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

package org.apache.jackrabbit.vault.fs.impl;

import java.io.IOException;

import javax.jcr.RepositoryException;

import org.apache.jackrabbit.vault.fs.api.Aggregate;
import org.apache.jackrabbit.vault.fs.api.AggregateManager;
import org.apache.jackrabbit.vault.fs.api.VaultFile;
import org.apache.jackrabbit.vault.fs.api.VaultFileSystem;
import org.apache.jackrabbit.vault.fs.api.VaultFsConfig;
import org.apache.jackrabbit.vault.fs.api.VaultFsTransaction;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.util.PathUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Vault filesystem provides an additional abstraction layer on top of the
 * artifacts manager tree. It is used to map the artifacts node artifacts to
 * individual java.io like files.
 *
 */
public class VaultFileSystemImpl implements VaultFileSystem {

    /**
     * default log
     */
    private static Logger log = LoggerFactory.getLogger(VaultFileSystemImpl.class);

    /**
     * The underlying artifacts manager
     */
    private AggregateManager mgr;

    /**
     * Indicates if this is our own manager and we need to release it
     */
    private boolean isOwnManager;

    /**
     * The os file root
     */
    private VaultFileImpl root;

    /**
     * the root path if mounted not a /
     */
    private final String rootPath;

    /**
     * Pattern that matches the root path
     */
    private final String rootPattern;


    public void unmount() throws RepositoryException {
        assertMounted();
        if (isOwnManager) {
            mgr.unmount();
        }
        mgr = null;
        root = null;
    }

    /**
     * Checks if this tree is still mounted and if the attached session
     * is still live.
     *
     * @throws RepositoryException if not mounted or not live.
     */
    private void assertMounted() throws RepositoryException {
        if (!isMounted()) {
            throw new RepositoryException("JcrFS is not mounted anymore.");
        }
    }

    public boolean isMounted() {
        return mgr != null && mgr.isMounted();
    }

    /**
     * Creates a new os file system that uses the given manager.
     *
     * @param rootAggregate the root artifacts node
     * @param rootPath path of root file. used for remapping
     * @param ownMgr <code>true</code> if it's own manager
     * @throws IOException if an I/O error occurs
     * @throws RepositoryException if a repository error occurs.
     */
    public VaultFileSystemImpl(Aggregate rootAggregate, String rootPath, boolean ownMgr)
            throws IOException, RepositoryException {
        if (!rootAggregate.allowsChildren()) {
            throw new IOException("Root node must allow children.");
        }
        this.mgr = rootAggregate.getManager();
        this.isOwnManager = ownMgr;

        // create root files
        VaultFileNode rootFileNode = new VaultFileNode(null, (AggregateImpl) rootAggregate);
        this.rootPath = rootPath == null || rootPath.equals("/") ? "" : rootPath;
        this.rootPattern = this.rootPath + "/";
        this.root = new VaultFileImpl(this, this.rootPath, rootFileNode);
    }

    public VaultFile getRoot() {
        return root;
    }

    public AggregateManager getAggregateManager() {
        return mgr;
    }

    public VaultFile getFile(String path)
            throws IOException, RepositoryException {
        if (path.charAt(0) != '/') {
            throw new IOException("Only absolute paths allowed");
        }
        if (rootPath.length() > 0) {
            if (!path.equals(rootPath) && !path.startsWith(rootPattern)) {
                throw new IOException("Path not under mountpoint.");
            }
            path = path.substring(rootPath.length());
        }
        return getFile(root, path);
    }

    public VaultFile getFile(VaultFile parent, String path)
            throws IOException, RepositoryException {
        if (path == null || path.equals("") || path.equals(".")) {
            return parent;
        } else if (path.equals("/")) {
            return getRoot();
        }
        String[] pathElems = PathUtil.makePath((String[])null, path);
        for (int i=0; i<pathElems.length && parent != null; i++) {
            String elem = pathElems[i];
            if (elem.equals("/")) {
                parent = getRoot();
            } else if (elem.equals("..")) {
                parent = parent.getParent();
            } else {
                parent = parent.getChild(elem);
            }
        }
        return parent;
    }

    public VaultFsTransaction startTransaction() {
        return new TransactionImpl(this);
    }

    public void invalidate() throws RepositoryException {
        // create root files
        AggregateImpl rootAggregate = (AggregateImpl) root.getAggregate();
        rootAggregate.invalidate();
        VaultFileNode rootFileNode = new VaultFileNode(null, rootAggregate);
        root = new VaultFileImpl(this, rootPath, rootFileNode);
        log.info("Filesystem invalidated.");
    }

    public VaultFsConfig getConfig() {
        return mgr.getConfig();
    }

    public WorkspaceFilter getWorkspaceFilter() {
        return mgr.getWorkspaceFilter();
    }
}
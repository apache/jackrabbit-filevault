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
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;

import javax.jcr.RepositoryException;

import org.apache.jackrabbit.vault.fs.api.AccessType;
import org.apache.jackrabbit.vault.fs.api.Aggregate;
import org.apache.jackrabbit.vault.fs.api.Artifact;
import org.apache.jackrabbit.vault.fs.api.ArtifactType;
import org.apache.jackrabbit.vault.fs.api.DumpContext;
import org.apache.jackrabbit.vault.fs.api.VaultFile;
import org.apache.jackrabbit.vault.fs.api.VaultFileSystem;
import org.apache.jackrabbit.vault.util.PlatformNameFormat;
import org.apache.jackrabbit.vault.util.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements the bridge between the repository based artifacts nodes and their
 * file system representation as a collection of artifacts.
 *
 */
public class VaultFileImpl implements VaultFile {

    /**
     * The default logger
     */
    protected static Logger log = LoggerFactory.getLogger(VaultFileImpl.class);

    /**
     * Vault filesystem of this file
     */
    private final VaultFileSystem fs;

    /**
     * the name of this entry
     */
    private final String name;

    /**
     * The artifact this represents or <code>null</code> if not attached
     */
    private Artifact artifact;

    /**
     * the jcr file node that this file belongs to
     */
    private VaultFileNode node;

    /**
     * the parent file
     */
    private VaultFileImpl parent;

    /**
     * a map of child nodes that weren't loaded yet
     */
    private LinkedHashMap<String, VaultFileNode> pendingChildNodes;

    /**
     * The map of child entries.
     */
    private LinkedHashMap<String, VaultFileImpl> children;

    /**
     * Internal constructor for the root file
     *
     * @param fs the file system
     * @param rootPath path of the root node
     * @param node the node
     * @throws RepositoryException if an error occurs
     */
    protected VaultFileImpl(VaultFileSystem fs, String rootPath, VaultFileNode node)
            throws RepositoryException {
        this.fs = fs;
        this.node = node;
        if (rootPath.equals("")) {
            this.name = rootPath;
            // bit of a hack since we know how the root artifacts look like
            for (Artifact a: node.getAggregate().getArtifacts().values()) {
                if (a.getType() == ArtifactType.DIRECTORY) {
                    this.artifact = a;
                    node.getFiles().add(this);
                } else {
                    VaultFileImpl child = new VaultFileImpl(fs, a.getPlatformPath(), node, a);
                    node.getFiles().add(child);
                    this.addChild(child);
                }
            }
        } else {
            this.name = rootPath;
            // special case when mounted deeply
            for (Artifact a: node.getAggregate().getArtifacts().values()) {
                if (a.getType() == ArtifactType.DIRECTORY) {
                    this.artifact = a;
                    node.getFiles().add(this);
                } else {
                    String p[] = Text.explode(a.getPlatformPath(), '/');
                    VaultFileImpl entry = null;
                    for (String cName: p) {
                        if (entry == null) {
                            entry = this;
                        } else {
                            entry = entry.getOrAddChild(cName);
                        }
                    }
                    if (entry != null && entry != this) {
                        entry.init(node, a);
                    }
                    // add to set of related files
                    node.getFiles().add(entry);
                }
            }

        }
        for (VaultFileNode child: node.getChildren()) {
            addPendingNode(child);
        }
    }


    /**
     * Internal constructor
     *
     * @param fs the file system
     * @param name the file entry name
     * @param node the node
     * @param artifact the underlying artifact. can be <code>null</code>
     * @throws RepositoryException if an error occurs
     */
    protected VaultFileImpl(VaultFileSystem fs, String name, VaultFileNode node,
                      Artifact artifact)
            throws RepositoryException {
        this.fs = fs;
        this.name = name;
        init(node, artifact);
    }

    /**
     * (re)initializes this file
     * @param node the artifacts node
     * @param a the artifact
     * @throws RepositoryException if an error occurs
     */
    protected void init(VaultFileNode node, Artifact a) throws RepositoryException {
        children = null;
        pendingChildNodes = null;
        this.node = node;
        this.artifact = a;
        if (node != null && a != null && a.getType() == ArtifactType.DIRECTORY) {
            for (VaultFileNode child: node.getChildren()) {
                addPendingNode(child);
            }
        }

    }

    protected void attach(VaultFileNode node, Artifact a) {
        this.node = node;
        this.artifact = a;
    }

    public String getPath() {
        if (parent == null) {
            return name.length() == 0 ? "/" : name;
        } else {
            return internalGetPath().toString();
        }
    }

    public String getRepoRelPath() {
        if (artifact == null) {
            return null;
        } else {
            String relPath = artifact.getRelativePath();
            int idx = relPath.indexOf('/');
            if (idx > 0 && idx < relPath.length() - 1) {
                return relPath.substring(idx + 1);
            }
            return "";
        }
    }

    public String getAggregatePath() {
        return node == null 
                ? parent.getAggregatePath()
                : node.getPath();
    }

    /**
     * Internal builder for the path
     * @return the string buffer.
     */
    private StringBuffer internalGetPath() {
        if (parent == null) {
            return new StringBuffer(name);
        } else {
            return parent.internalGetPath().append('/').append(name);
        }
    }

    public String getName() {
        return name;
    }

    public Artifact getArtifact() {
        return artifact;
    }

    public boolean isDirectory() {
        return artifact == null || artifact.getType() == ArtifactType.DIRECTORY;
    }

    public boolean isTransient() {
        return node == null;
    }
    
    public VaultFileImpl getParent() throws IOException, RepositoryException {
        return parent;
    }

    public Aggregate getAggregate() {
        return node == null ? null : node.getAggregate();
    }

    public Aggregate getControllingAggregate() {
        if (node == null && parent != null) {
            return parent.getControllingAggregate();
        } else {
            return node == null ? null : node.getAggregate();
        }
    }

    public VaultFileImpl getChild(String name) throws RepositoryException {
        VaultFileImpl child = children == null ? null : children.get(name);
        if (child == null) {
            // try to load child
            String repoName = PlatformNameFormat.getRepositoryName(name);
            loadPendingNode(repoName);
            child = children == null ? null : children.get(name);
            // if still not present, load all
            if (child == null) {
                loadChildren();
            }
            child = children == null ? null : children.get(name);
        }
        return child;
    }

    public Collection<? extends VaultFile> getChildren() throws RepositoryException {
        loadChildren();
        return children.values();
    }

    /**
     * Internally loads all children out of the map of pending node.
     * @throws RepositoryException if an error occurs
     */
    private void loadChildren() throws RepositoryException {
        if (children == null) {
            children = new LinkedHashMap<String, VaultFileImpl>();
        }
        if (pendingChildNodes != null) {
            Iterator<String> iter = pendingChildNodes.keySet().iterator();
            while (iter.hasNext()) {
                loadPendingNode(iter.next());
                iter = pendingChildNodes.keySet().iterator();
            }
        }
    }

    /**
     * Adds a node to the map of pending ones
     * @param n the node
     * @throws RepositoryException if an error occurs
     */
    protected void addPendingNode(VaultFileNode n) throws RepositoryException {
        // we would need to assert that (n.getParent() == this.node) but this
        // is not possible since this.node might not be loaded yet
        if (pendingChildNodes == null) {
            pendingChildNodes = new LinkedHashMap<String, VaultFileNode>();
        }
        String name = n.getName();
        pendingChildNodes.put(name, n);
        // if pending node is deep, we need to load it. otherwise it might not
        // be found afterwards
        if (name.indexOf('/') > 0) {
            loadPendingNode(name);
        }
    }

    /**
     * Loads a pending node
     * @param repoName the repository name of the node
     * @throws RepositoryException if an error occurs
     */
    private void loadPendingNode(String repoName) throws RepositoryException {
        // get node from pending ones
        VaultFileNode n = pendingChildNodes == null ? null : pendingChildNodes.remove(repoName);
        if (n == null) {
            return;
        }
        VaultFileImpl parent = this;
        // if pending node has a deep aggregate, we create intermediate
        // "directories"
        String aggName = n.getAggregate().getRelPath();
        if (aggName.indexOf('/') > 0) {
            String[] p = Text.explode(aggName, '/');
            for (int i=0; i<p.length-1; i++) {
                parent = parent.getOrAddChild(PlatformNameFormat.getPlatformName(p[i]));
            }
        }

        for (Artifact a: n.getAggregate().getArtifacts().values()) {
            String p[] = Text.explode(a.getPlatformPath(), '/');
            VaultFileImpl entry = parent;
            for (String cName: p) {
                entry = entry.getOrAddChild(cName);
            }
            entry.init(n, a);
            // add to set of related files
            n.getFiles().add(entry);
        }
    }

    /**
     * Attaches a child file
     * @param child the child
     */
    private void addChild(VaultFileImpl child) {
        child.parent = this;
        if (children == null) {
            children = new LinkedHashMap<String, VaultFileImpl>();
        }
        children.put(child.name, child);
    }

    /**
     * Returns the child of the given name or creates and adds a new one.
     * @param name the name of the file
     * @return the child
     * @throws RepositoryException if an error occurs
     */
    protected VaultFileImpl getOrAddChild(String name) throws RepositoryException {
        VaultFileImpl c = children == null ? null : children.get(name);
        if (c == null) {
            c = new VaultFileImpl(fs, name, null, null);
            addChild(c);
        }
        return c;
    }

    public Collection<? extends VaultFile> getRelated() throws RepositoryException {
        if (node == null) {
            return null;
        }
        return node.getFiles();
    }

    public boolean canRead() {
        return artifact != null
                && artifact.getPreferredAccess() != AccessType.NONE;
    }

    public long lastModified() {
        return artifact == null ? 0 : artifact.getLastModified();
    }

    public long length() {
        return artifact == null ? -1 : artifact.getContentLength();
    }

    public String getContentType() {
        return artifact == null ? null : artifact.getContentType();
    }

    public VaultFileSystem getFileSystem() {
        return fs;
    }

    public void invalidate() throws RepositoryException {
        log.info("invalidating file {}", getPath());

        // special handling for root node
        if (parent == null) {
            // bit of a hack since we know how the root artifacts look like
            node.invalidate();
            for (Artifact a: node.getAggregate().getArtifacts().values()) {
                if (a.getType() == ArtifactType.DIRECTORY) {
                    this.artifact = a;
                    node.getFiles().add(this);
                } else {
                    VaultFileImpl child = new VaultFileImpl(fs, a.getPlatformPath(), node, a);
                    node.getFiles().add(child);
                    this.addChild(child);
                }
            }
            for (VaultFileNode child: node.getChildren()) {
                addPendingNode(child);
            }
        } else {
            // get the directory artifact of this file
            for (VaultFileImpl f: node.getFiles()) {
                if (f.parent != null && f.parent.artifact.getType() == ArtifactType.DIRECTORY) {
                    f.parent.node.invalidate();
                    f.parent.init(f.parent.node, f.parent.artifact);
                    break;
                }
            }
            //node.invalidate();
            //init(node, artifact);
        }
    }

    /**
     * {@inheritDoc}
     */
    public void dump(DumpContext ctx, boolean isLast) {
        ctx.println(isLast, "Vault file");
        ctx.indent(isLast);
        ctx.printf(false, "name: %s", name);
        ctx.printf(false, "path: %s", getPath());
        ctx.printf(false, "# pending: %d", pendingChildNodes == null ? -1 : pendingChildNodes.size());
        ctx.printf(false, "# children: %d", children == null ? -1 : children.size());
        if (artifact != null) {
            artifact.dump(ctx, false);
        } else {
            ctx.println(false, "Artifact: (null)");
        }
        if (node != null) {
            node.dump(ctx, true);
        } else {
            ctx.println(true, "ArtifactsNode: (null)");
        }
        ctx.outdent();
    }


}
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

package org.apache.jackrabbit.vault.fs.impl.io;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.RepositoryException;

import org.apache.jackrabbit.vault.fs.api.Artifact;
import org.apache.jackrabbit.vault.fs.api.ArtifactType;
import org.apache.jackrabbit.vault.fs.api.ImportMode;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.fs.impl.ArtifactSetImpl;
import org.apache.jackrabbit.vault.fs.io.AccessControlHandling;
import org.apache.jackrabbit.vault.util.JcrConstants;

/**
 * Handles artifact sets with just a directory.
 *
 */
public class FolderArtifactHandler extends AbstractArtifactHandler {

    /**
     * node type to use for the folders
     */
    private String nodeType = JcrConstants.NT_FOLDER;

    /**
     * Returns the node type that is used to create folders.
     * @return the node type name.
     */
    public String getNodeType() {
        return nodeType;
    }

    /**
     * Sets the node type name that is used to create folders. Default is "nt:folder"
     * @param nodeType the node type name
     */
    public void setNodeType(String nodeType) {
        this.nodeType = nodeType;
    }

    /**
     * {@inheritDoc}
     *
     * Handles generic artifact sets
     */
    public ImportInfoImpl accept(WorkspaceFilter wspFilter, Node parent, String name,
                             ArtifactSetImpl artifacts)
            throws RepositoryException, IOException {
        Artifact dir = artifacts.getDirectory();
        if (dir == null || artifacts.size() != 1) {
            return null;
        }
        ImportInfoImpl info = new ImportInfoImpl();
        if (dir.getRelativePath().length() == 0) {
            // special check if artifact addresses itself
            return info;
        }
        if (!parent.hasNode(dir.getRelativePath())) {
            final Node node;
            if (wspFilter.contains(parent.getPath() + "/" + dir.getRelativePath())) {
                node = parent.addNode(dir.getRelativePath(), nodeType);
            } else {
                // preferably use default node type for intermediate nodes
                if (parent.getPrimaryNodeType().canAddChildNode(dir.getRelativePath())) {
                    node = parent.addNode(dir.getRelativePath());
                } else {
                    node = parent.addNode(dir.getRelativePath(), nodeType);
                }
                
            }
            info.onCreated(node.getPath());
        } else {
            // sync nodes
            Set<String> hints = new HashSet<String>();
            String rootPath = parent.getPath();
            if (!rootPath.equals("/")) {
                rootPath += "/";
            }
            for (Artifact a: artifacts.values(ArtifactType.HINT)) {
                hints.add(rootPath + a.getRelativePath());
            }

            Node node = parent.getNode(dir.getRelativePath());
            if (wspFilter.contains(node.getPath()) && !nodeType.equals(node.getPrimaryNodeType().getName())) {
                modifyPrimaryType(node, info);
            }
            NodeIterator iter = node.getNodes();
            while (iter.hasNext()) {
                Node child = iter.nextNode();
                String path = child.getPath();
                if (wspFilter.contains(path)) {
                    if (wspFilter.getImportMode(path) == ImportMode.REPLACE) {
                        if (!hints.contains(path)) {
                            // if the child is in the filter, it belongs to
                            // this aggregate and needs to be removed
                            if (getAclManagement().isACLNode(child)) {
                                if (acHandling == AccessControlHandling.OVERWRITE
                                        || acHandling == AccessControlHandling.CLEAR) {
                                    info.onDeleted(path);
                                    getAclManagement().clearACL(node);
                                }
                            } else {
                                info.onDeleted(path);
                                child.remove();
                            }
                        } else if (acHandling == AccessControlHandling.CLEAR
                                && getAclManagement().isACLNode(child)) {
                            info.onDeleted(path);
                            getAclManagement().clearACL(node);
                        }
                    }
                }
            }

        }
        return info;
    }

    /**
     * This is potentially a destructive operation as it will remove all (non-protected) properties before doing the conversion
     * @param node
     * @param info
     * @throws RepositoryException
     */
    private void modifyPrimaryType(Node node, ImportInfoImpl info) throws RepositoryException {
        // check versionable
        ensureCheckedOut(node, info);

        // remove all non-allowed properties
        PropertyIterator propertyIterator = node.getProperties();
        while (propertyIterator.hasNext()) {
            Property property = propertyIterator.nextProperty();
            if (!property.getDefinition().isProtected()) {
                property.remove();
            }
        }
        node.setPrimaryType(nodeType);
       
    }

    private void ensureCheckedOut(Node node, ImportInfoImpl info) throws RepositoryException {
        boolean isCheckedOut = !node.isNodeType(JcrConstants.MIX_VERSIONABLE) || node.isCheckedOut();
        if (!isCheckedOut) {
            info.registerToVersion(node.getPath());
            try {
                node.checkout();
            } catch (RepositoryException e) {
                info.log.warn("error while checkout node (ignored)", e);
            }
        }
    }
}
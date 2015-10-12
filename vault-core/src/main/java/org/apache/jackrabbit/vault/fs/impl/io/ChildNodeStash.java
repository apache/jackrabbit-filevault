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

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.jackrabbit.vault.fs.api.ImportInfo;
import org.apache.jackrabbit.vault.util.JcrConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class isolating the task of temporarily moving child nodes to a
 * different location in order to be able to recover (and properly merge) them
 * later on.
 */
public class ChildNodeStash {

    static final Logger log = LoggerFactory.getLogger(ChildNodeStash.class);

    private final Session session;

    private Node tmpNode;

    /**
     * List of potential roots where the transient temporary node will be created.
     * Note that the node is never persisted.
     */
    private static final String[] ROOTS = {"/", "/tmp", "/var", "/etc", "/content"};


    /**
     * Creates a new child node stash utility class
     * @param session session to operate on
     */
    public ChildNodeStash(Session session) {
        this.session = session;
    }

    /**
     * create a transient node for temporarily stash the child nodes
     * @return the temporary node
     * @throws RepositoryException if an error occurrs
     */
    private Node getOrCreateTemporaryNode() throws RepositoryException {
        if (tmpNode != null) {
            return tmpNode;
        }
        for (String rootPath: ROOTS) {
            try {
                Node root = session.getNode(rootPath);
                return tmpNode = root.addNode("tmp" + System.currentTimeMillis(), JcrConstants.NT_UNSTRUCTURED);
            } catch (RepositoryException e) {
                log.debug("unable to create temporary stash location below {}.", rootPath);
            }
        }
        throw new RepositoryException("Unable to create temporary root below.");
    }

    /**
     * Moves the nodes below the given parent path to a temporary location.
     * @param parentPath the path of the parent node.
     * @throws RepositoryException if an error occurrs
     */
    public void stashChildren(String parentPath) throws RepositoryException {
        stashChildren(session.getNode(parentPath));
    }

    /**
     * Moves the nodes below the given parent to a temporary location.
     * @param parent the parent node.
     */
    public void stashChildren(Node parent) {
        try {
            NodeIterator iter = parent.getNodes();
            while (iter.hasNext()) {
                Node child = iter.nextNode();
                Node tmp = getOrCreateTemporaryNode();
                try {
                    session.move(child.getPath(), tmp.getPath() + "/" + child.getName());
                } catch (RepositoryException e) {
                    log.error("Error while moving child node to temporary location. Child will be removed.", e);
                }
            }
        } catch (RepositoryException e) {
            log.warn("error while moving child nodes (ignored)", e);
        }
    }

    /**
     * Moves the stashed nodes back below the given parent path.
     * @param parentPath the path of the new parent node
     * @throws RepositoryException if an error occurrs
     */
    public void recoverChildren(String parentPath) throws RepositoryException {
        recoverChildren(session.getNode(parentPath), null);
    }

    /**
     * Moves the stashed nodes back below the given parent path.
     * @param parent the new parent node
     * @throws RepositoryException if an error occurrs
     */
    public void recoverChildren(Node parent, ImportInfo importInfo) throws RepositoryException {
        // move the old child nodes back
        if (tmpNode != null) {
            NodeIterator iter = tmpNode.getNodes();
            boolean hasErrors = false;
            while (iter.hasNext()) {
                Node child = iter.nextNode();
                String newPath = parent.getPath() + "/" + child.getName();
                try {
                    session.move(child.getPath(), newPath);
                } catch (RepositoryException e) {
                    log.warn("Unable to move child back to new location at {} due to: {}. Node will remain in temporary location: {}",
                            new Object[]{newPath, e.getMessage(), child.getPath()});
                    if (importInfo != null) {
                        importInfo.onError(newPath, e);
                        hasErrors = true;
                    }
                }
            }
            if (!hasErrors) {
                tmpNode.remove();
            }
        }
    }
}
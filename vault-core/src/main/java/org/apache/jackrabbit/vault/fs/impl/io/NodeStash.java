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

import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;

import org.apache.jackrabbit.vault.fs.api.ImportInfo;
import org.apache.jackrabbit.vault.fs.api.ImportMode;
import org.apache.jackrabbit.vault.util.JcrConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class isolating the task of temporarily moving child nodes and
 * properties to a different location in order to be able to recover (and
 * properly merge) them later on.
 * <p>
 * This is useful when system view XML is about to be imported, as that clears
 * everything not explicitly mentioned.
 */
public class NodeStash {

    static final Logger log = LoggerFactory.getLogger(NodeStash.class);

    private final Session session;

    private final String path;

    private Node tmpNode;

    private final Set<String> excludedNodeName = new HashSet<>();

    /**
     * List of potential roots where the transient temporary node will be created.
     * Note that the node is never persisted.
     */
    private static final String[] ROOTS = {"/tmp", "/var", "/etc", "/content", "/"};

    /** interval for logging progress */
    private static final long PROGRESS_LOG_INTERVAL = TimeUnit.MINUTES.toMillis(1);

    /** The property names of those protected properties which should be stashed (and later restored) */
    private static final List<String> PROTECTED_PROPERTIES_TO_STASH = Arrays.asList(JcrConstants.JCR_MIXINTYPES);

    private static final String PROTECTED_PROPERTIES_SUFFIX = "-stashed";

    /**
     * Creates a new stash utility class which takes care of child nodes and properties in {@code path}
     * @param session session to operate on
     */
    public NodeStash(Session session, String path) {
        this.session = session;
        this.path = path;
    }

    /**
     * create a transient node for temporarily stash the child nodes
     * @return the temporary node
     * @throws RepositoryException if an error occurrs
     */
    private Node getOrCreateTemporaryNode() throws RepositoryException {
        if (tmpNode != null) {
            return tmpNode;
        } else {
            for (String rootPath : ROOTS) {
                try {
                    Node root = session.getNode(rootPath);
                    return tmpNode = root.addNode("filevault-tmp-" + System.currentTimeMillis(), JcrConstants.NT_UNSTRUCTURED);
                } catch (RepositoryException e) {
                    log.debug("unable to create temporary stash location below {}.", rootPath);
                }
            }
            throw new RepositoryException("Unable to create temporary root (no suitable location found).");
        }
    }

    /**
     * Adds the given name to the set of excluded child node names. The nodes that are excluded are not saved
     * in the stash.
     * @param name The name of the node to exclude
     * @return "this" suitable for chaining.
     */
    public NodeStash excludeName(String name) {
        excludedNodeName.add(name);
        return this;
    }

    /**
     * Moves the child nodes and optionally properties of the path to a temporary location.
     * @return the stashed node's primary type (if it needs to be kept)
     */
    public @Nullable String stash(@Nullable ImportInfo importInfo) {
        try {
            Node parent = session.getNode(path);
            Node tmp = getOrCreateTemporaryNode();

            int childNodeCount = 0;
            int propertyCount = 0;
            long start = System.currentTimeMillis();
            long lastTimeStamp = start;
            boolean shouldWarn = false;

            NodeIterator nodeIterator = parent.getNodes();

            while (nodeIterator.hasNext()) {
                Node child = nodeIterator.nextNode();
                String name = child.getName();
                if (excludedNodeName.contains(name)) {
                    log.debug("skipping excluded child node from stash: {}", child.getPath());
                    continue;
                }
                try {
                    String path = child.getPath();
                    session.move(path, tmp.getPath() + "/" + name);
                    childNodeCount += 1;

                    long now = System.currentTimeMillis();
                    if (childNodeCount > 0 && now - PROGRESS_LOG_INTERVAL > lastTimeStamp) {
                        log.warn("Node stashing operation for node {} (last: {} into {}), still running after {}, nodes moved: {}",
                                this.path, path, tmp.getPath(), Duration.ofMillis(now - start), childNodeCount);
                        lastTimeStamp = now;
                        shouldWarn = true;
                    }

                    if (importInfo != null) {
                        importInfo.onStashed(path);
                    }
                } catch (RepositoryException e) {
                    log.error("Error while moving child node to temporary location. Child will be removed.", e);
                }
            }
            // save properties
            PropertyIterator propIterator = parent.getProperties();
            while (propIterator.hasNext()) {
                Property property = propIterator.nextProperty();
                // all unprotected and some protected properties are relevant
                String stashPropertyName;
                if (!property.getDefinition().isProtected()) {
                    stashPropertyName = property.getName();
                } else if (PROTECTED_PROPERTIES_TO_STASH.contains(property.getName())) {
                    stashPropertyName = property.getName() + PROTECTED_PROPERTIES_SUFFIX;
                } else {
                    stashPropertyName = null;
                }
                if (stashPropertyName != null) {
                    propertyCount += 1;
                    if (property.isMultiple()) {
                        tmp.setProperty(stashPropertyName, property.getValues(), property.getType());
                    } else {
                        tmp.setProperty(stashPropertyName, property.getValue(), property.getType());
                    }
                }
            }

            String primaryType = parent.getPrimaryNodeType().getName();
            String message = String.format(Locale.ENGLISH, "Stashed node %s of type %s as %s (%d child nodes, %d properties, elapsed %s).", path, primaryType,
                    tmp.getPath(), childNodeCount, propertyCount, Duration.ofMillis(System.currentTimeMillis() - start));
            if (shouldWarn) {
                log.warn(message);
            } else {
                log.debug(message);
            }

            return primaryType;
        } catch (RepositoryException e) {
            log.warn("error while moving child nodes (ignored)", e);
            return null;
        }
    }

    /**
     * Moves the stashed nodes/properties back below the original path.
     * @param importMode the import mode for the node this stash refers to
     * @param importInfo the import info to record the changes
     * @throws RepositoryException if an error occurs
     */
    public void recover(@NotNull ImportMode importMode, @Nullable ImportInfo importInfo) throws RepositoryException {
        // move the old child nodes back (independent of importMode)

        int childNodeCount = 0;
        long start = System.currentTimeMillis();
        long lastTimeStamp = start;
        boolean shouldWarn = false;

        if (tmpNode != null) {
            Node parent = session.getNode(path);
            NodeIterator iter = tmpNode.getNodes();
            boolean hasErrors = false;

            while (iter.hasNext()) {
                Node child = iter.nextNode();
                String newPath = parent.getPath() + "/" + child.getName();
                try {
                    if (session.nodeExists(newPath)) {
                        log.debug("Skipping restore from temporary location {} as node already exists at {}", child.getPath(), newPath);
                    } else {
                        String path = child.getPath();
                        session.move(path, newPath);
                        childNodeCount += 1;
                        if (importInfo != null) {
                            importInfo.onStashed(path);
                        }

                        long now = System.currentTimeMillis();
                        if (childNodeCount > 0 && now - PROGRESS_LOG_INTERVAL > lastTimeStamp) {
                            log.warn(
                                    "Node stashing recovery operation for node {} (last: {} into {}), still running after {}, nodes recovered: {}",
                                    this.path, path, newPath, Duration.ofMillis(now - start), childNodeCount);
                            lastTimeStamp = now;
                            shouldWarn = true;
                        }
                    }
                } catch (RepositoryException e) {
                    log.warn(
                            "Unable to move child back to new location at {} due to: {}. Node will remain in temporary location: {}",
                            newPath, e.getMessage(), child.getPath());
                    if (importInfo != null) {
                        importInfo.onError(newPath, e);
                        hasErrors = true;
                    }
                }
            }

            try {
                recoverProperties(importMode == ImportMode.MERGE || importMode == ImportMode.MERGE_PROPERTIES);
            } catch (RepositoryException e) {
                log.warn("Unable to restore properties at {} due to: {}. Properties will remain in temporary location: {}", path,
                        e.getMessage(), tmpNode.getPath());
                if (importInfo != null) {
                    importInfo.onError(path, e);
                    hasErrors = true;
                }
            }

            log.debug("Restored properties and child nodes of {} from {} (mode: {}).", path, tmpNode.getPath(), importMode);

            if (shouldWarn) {
                String message = String.format(Locale.ENGLISH, "Stashed node recovery for %s done (%d child nodes, elapsed %s).", path,
                        childNodeCount, Duration.ofMillis(System.currentTimeMillis() - start));
                log.warn(message);
            }

            if (!hasErrors) {
                tmpNode.remove();
            } else {
                log.debug("Temporary node {} not removed due to errors while restoring child items.", tmpNode.getPath());
            }
        }
    }

    private void recoverProperties(boolean overwriteNewOnes) throws RepositoryException {
        Node destNode = session.getNode(path);

        // restore mixins
        Property mixinProperty = tmpNode.hasProperty(JcrConstants.JCR_MIXINTYPES + PROTECTED_PROPERTIES_SUFFIX) ? tmpNode.getProperty(JcrConstants.JCR_MIXINTYPES + PROTECTED_PROPERTIES_SUFFIX) : null;
        if (mixinProperty != null) {
            for (Value value : mixinProperty.getValues()) {
                destNode.addMixin(value.getString());
            }
        }
        PropertyIterator propIterator = tmpNode.getProperties();
        while (propIterator.hasNext()) {
            Property property = propIterator.nextProperty();
            if (!property.getDefinition().isProtected() && !property.getName().endsWith(PROTECTED_PROPERTIES_SUFFIX)) {
                if (!overwriteNewOnes && destNode.hasProperty(property.getName())) {
                    log.debug("Skipping restore property {} as it has been updated", property.getPath());
                    continue;
                }
                if (property.isMultiple()) {
                    destNode.setProperty(property.getName(), property.getValues(), property.getType());
                } else {
                    destNode.setProperty(property.getName(), property.getValue(), property.getType());
                }
            } 
        }
    }
}
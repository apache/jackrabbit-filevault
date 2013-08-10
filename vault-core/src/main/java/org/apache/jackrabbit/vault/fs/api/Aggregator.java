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

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.RepositoryException;

/**
 * Defines an artifact aggregator. It provides the {@link Aggregate} with the
 * respective artifacts depending on the implementation and content.
 *
 */
public interface Aggregator extends Dumpable {

    /**
     * Creates the artifact set of the content aggregation.
     *
     * @param aggregate the aggregate from which the artifacts are created.
     * @return the artifact set of the content aggregation.
     *
     * @throws RepositoryException if an error occurs.
     */
    ArtifactSet createArtifacts(Aggregate aggregate) throws RepositoryException;

    /**
     * Checks if the given item is included in the content aggregation relative
     * to the respective aggregator root
     *
     * @param root the root of the aggregation.
     * @param node the node to check
     * @param path the path of the node or <code>null</code>
     * @return <code>true</code> if the node is included;
     *         <code>false</code> otherwise.
     *
     * @throws RepositoryException if an error occurs.
     */
    boolean includes(Node root, Node node, String path) throws RepositoryException;

    /**
     * Checks if the given property is included in the content aggregation relative
     * to the respective aggregator root. this is a speed optimized variant
     * so that property.getParent() does not need to be called.
     *
     * @param root the root of the aggregation.
     * @param parent the parent node of the property to check
     * @param property the property to check
     * @param path the path of the property or <code>null</code>
     * @return <code>true</code> if the node is included;
     *         <code>false</code> otherwise.
     *
     * @throws RepositoryException if an error occurs.
     */
    boolean includes(Node root, Node parent, Property property, String path) throws RepositoryException;

    /**
     * Checks if this aggregator can handles the given node
     *
     * @param node the node to check
     * @param path the path of the node or <code>null</code>
     * @return <code>true</code> if this aggregator will handle the node;
     *         <code>false</code> otherwise.
     * @throws RepositoryException if an error occurs.
     */
    boolean matches(Node node, String path) throws RepositoryException;

    /**
     * Returns <code>true</code> if this aggregator includes the entire node
     * sub tree into the serialization; <code>false</code> if it does not do a
     * complete serialization and allows child aggregations
     * (eg: nt:unstructured).
     *
     * @return <code>true</code> if this aggregator aggregates all children.
     */
    boolean hasFullCoverage();

    /**
     * Checks if this aggregator is the default aggregator.
     * @return <code>true</code> if this aggregator is the default aggregator.
     */
    boolean isDefault();

    /**
     * Removes the content for this aggregation. If this aggregator allows
     * child aggregations it may fail if it's not possible to remove only
     * parts of the content. If <code>recursive</code> is <code>true</code>
     * it must not fail due to that reason, though.
     * <p/>
     * The aggregator may become invalid after the removal of the content and
     * subsequent calls may throw an exception.
     *
     * @param node the node of the aggregation to remove
     * @param recursive <code>true</code> if all content is to be removed.
     * @param trySave if <code>true</code> the aggregator tries to save the
     *        modified content.
     * @return ImportInfo infos about the modification
     * @throws RepositoryException if an error occurs.
     */
    ImportInfo remove(Node node, boolean recursive, boolean trySave)
            throws RepositoryException;

}
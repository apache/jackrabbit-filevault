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

import java.util.Collection;
import java.util.List;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.RepositoryException;

/**
 * <code>Aggregate</code>...
 */
public interface Aggregate extends Dumpable {

    Node getNode() throws RepositoryException;

    boolean hasNode() throws RepositoryException;

    Aggregate getParent();

    String getPath();

    /**
     * Returns the (absolute) repository address to the node that this artifact
     * node represents.
     *
     * @return the repository address
     * @throws RepositoryException if an error occurs.
     */
    RepositoryAddress getRepositoryAddress() throws RepositoryException;

    /**
     * Checks if this artifact node possibly allows children, i.e. is a folder.
     *
     * @return <code>true</code> if this artifact node allows children;
     *         <code>false</code> otherwise.
     */
    boolean allowsChildren();

    /**
     * Returns the relative path of this aggregate in respect to it's parent
     * aggregate.
     *
     * @return the relative path
     */
    String getRelPath();

    /**
     * Returns the name of this aggregate.
     * @return the name of this aggregate.
     */
    String getName();

    /**
     * Returns the leaves of this aggregate or <code>null</code>.
     * @return the leaves
     * @throws RepositoryException if an error occurs
     */
    List<? extends Aggregate> getLeaves() throws RepositoryException;

    Aggregate getAggregate(String relPath) throws RepositoryException;

    /**
     * Returns the artifacts of this node.
     *
     * @return the artifacts
     * @throws RepositoryException if this file is not attached to the fs, yet.
     */
    ArtifactSet getArtifacts() throws RepositoryException;

    /**
     * Returns the artifact manager this node belongs to.
     * @return the Vault filesystem.
     */
    AggregateManager getManager();

    /**
     * Checks if this aggregate has an aggregator and its node exists.
     * @return <code>true</code> if this aggregate is attached
     * @throws RepositoryException if an error occurs
     */
    boolean isAttached() throws RepositoryException;

    String[] getNamespacePrefixes();

    String getNamespaceURI(String prefix) throws RepositoryException;

    /**
     * Returns the collection of binary properties in this aggregate
     * @return the binaries or <code>null</code>
     */
    Collection<Property> getBinaries();
}
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
import javax.jcr.Property;
import javax.jcr.RepositoryException;

import org.apache.jackrabbit.vault.fs.impl.AggregateImpl;

/**
 * Receives walk event from the {@link AggregateImpl#walk(AggregateWalkListener)}.
 *
 */
public interface AggregateWalkListener {

    /**
     * Invoked when a tree walk begins
     *
     * @param root the root node of the tree walk
     * @throws RepositoryException if a repository error occurs.
     */
    public void onWalkBegin(Node root) throws RepositoryException;

    /**
     * Invoked when a node is traversed
     *
     * @param node     the node that is traversed
     * @param included indicates if the node is included in the aggregate. If
     *                 <code>false</code> it's just a traversed intermediate node.
     * @param depth    the relative depth of the node in respect to the tree root node.
     * @throws RepositoryException if a repository error occurs.
     */
    public void onNodeBegin(Node node, boolean included, int depth)
            throws RepositoryException;

    /**
     * Invoked when a property is included in the aggregate.
     *
     * @param prop  the property
     * @param depth the depth relative to the tree root
     * @throws RepositoryException if a repository error occurs.
     */
    public void onProperty(Property prop, int depth) throws RepositoryException;

    /**
     * Invoked when the child nodes are to be traversed.
     *
     * @param node  the node of which the children are to be traversed.
     * @param depth the depth of that node
     * @throws RepositoryException if a repository error occurs.
     */
    public void onChildren(Node node, int depth) throws RepositoryException;

    /**
     * Invoked when a node finished traversing
     *
     * @param node     the node that is finished traversing
     * @param included indicates if the node is included in the aggregate. If
     *                 <code>false</code> it's just a traversed intermediate node.
     * @param depth    the relative depth of the node in respect to the tree root node.
     * @throws RepositoryException if a repository error occurs.
     */
    public void onNodeEnd(Node node, boolean included, int depth)
            throws RepositoryException;

    /**
     * Invoked when a traversed node is ignored due to a filter.
     *
     * @param node the node that is ignored
     * @param depth the relative depth of the node in respect to the tree root node.
     * @throws RepositoryException if a repository error occurs
     */
    public void onNodeIgnored(Node node, int depth) throws RepositoryException;

    /**
     * Invoked when a tree walk begins
     *
     * @param root the root node of the tree walk
     * @throws RepositoryException if a repository error occurs.
     */
    public void onWalkEnd(Node root) throws RepositoryException;

}
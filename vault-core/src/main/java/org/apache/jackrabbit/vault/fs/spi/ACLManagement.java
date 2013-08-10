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

package org.apache.jackrabbit.vault.fs.spi;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

/**
 * <code>AccessControlManagement</code>...
 */
public interface ACLManagement {

    /**
     * Checks if the given node type name is used for ACLs
     * @param name the node type name
     * @return <code>true</code> if used for ACLs
     */
    boolean isACLNodeType(String name);

    /**
     * Checks if the given node type name is use as access controllable
     * mixin.
     * @param name the node type name
     * @return <code>true</code> if the name is the mixin name
     */
    boolean isAccessControllableMixin(String name);


    /**
     * Checks if the given node is an ACL node.
     * @param node the node
     * @return <code>true</code> if it's an ACL node.
     * @throws RepositoryException if an error occurs
     */
    boolean isACLNode(Node node) throws RepositoryException;

    /**
     * Checks if the given node is access controllable, i.e. has the respective
     * mixin and adds it if missing.
     *
     * @param node the node to check
     * @return <code>true</code> if was made access controllable
     * @throws RepositoryException if an error occurs
     */
    boolean ensureAccessControllable(Node node) throws RepositoryException;

    /**
     * Removes all ACLs from the given node.
     *
     * @param node the node
     * @throws RepositoryException if an error occurs
     */
    void clearACL(Node node) throws RepositoryException;
}
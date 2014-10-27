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

import javax.jcr.Session;

import org.apache.jackrabbit.vault.util.DocViewNode;

/**
 * <code>UserManagement</code>...
 */
public interface UserManagement {

    /**
     * Checks if the given node type name is used for a User node.
     *
     * @param ntName the node type name
     * @return <code>true</code> if used for a User node.
     */
    boolean isAuthorizableNodeType(String ntName);

    /**
     * Returns the path of the authorizable or <code>null</code> if not exists.
     * @param name the authorizable name
     * @param session the session to access the repository
     * @return path of authorizable
     *
     * @since 2.3.26
     */
    String getAuthorizablePath(Session session, String name);

    /**
     * Returns the id of the authorizable from the specified authorizable node
     * to be imported.
     *
     * @param node the authorizable import node
     * @return The id of the authorizable to be imported.
     *
     * @since 3.1.10
     */
    String getAuthorizableId(DocViewNode node);

    /**
     * Adds the given memberships to the specified group.
     * @param session session to operate on
     * @param id id of group
     * @param membersUUID uuids of members
     * @since 2.3.28
     */
    void addMembers(Session session, String id, String[] membersUUID);
}
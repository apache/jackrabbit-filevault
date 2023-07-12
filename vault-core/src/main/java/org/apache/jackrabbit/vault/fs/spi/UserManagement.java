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
import org.apache.jackrabbit.vault.util.DocViewNode2;
import org.osgi.annotation.versioning.ProviderType;

/**
 * {@code UserManagement}...
 */
@ProviderType
public interface UserManagement {

    /**
     * Checks if the given node type name is used for a User node.
     *
     * @param ntName the node type name
     * @return {@code true} if used for a User node.
     */
    boolean isAuthorizableNodeType(String ntName);

    /**
     * Returns the path of the authorizable or {@code null} if not exists.
     * @param id the authorizable id
     * @param session the session to access the repository
     * @return path of authorizable
     *
     * @since 2.3.26
     */
    String getAuthorizablePath(Session session, String id);

    /**
     * 
     * @param session the session to access the repository
     * @param id the authorizable id
     * @return the principal name corresponding to the given authorizable id or {@code null} if the authorizable id cannot be found
     * @since 3.7.0
     */
    String getPrincipalName(Session session, String id);

    /**
     * Returns the id of the authorizable from the specified authorizable node
     * to be imported.
     *
     * @param node the authorizable import node
     * @return The id of the authorizable to be imported.
     *
     * @since 3.1.10
     * @deprecated Use {@link #getAuthorizableId(DocViewNode2)} instead
     */
    @Deprecated
    String getAuthorizableId(DocViewNode node);

    /**
     * Returns the id of the authorizable from the specified authorizable node
     * to be imported.
     *
     * @param node the authorizable import node
     * @return The id of the authorizable to be imported.
     *
     * @since 3.6.0
     */
    default String getAuthorizableId(DocViewNode2 node) {
    	throw new UnsupportedOperationException();
    }

    /**
     * Adds the given memberships to the specified group.
     * @param session session to operate on
     * @param id id of group
     * @param membersUUID uuids of members
     * @since 2.3.28
     */
    void addMembers(Session session, String id, String[] membersUUID);
}
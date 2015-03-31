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

package org.apache.jackrabbit.vault.fs.spi.impl.jcr20;

import java.util.UUID;

import javax.jcr.ItemNotFoundException;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.jackrabbit.vault.fs.spi.UserManagement;
import org.apache.jackrabbit.vault.util.DocViewNode;
import org.apache.jackrabbit.vault.util.DocViewProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <code>JackrabbitUserManagement</code>...
 */
public class JackrabbitUserManagement implements UserManagement {

    /**
     * default logger
     */
    private static final Logger log = LoggerFactory.getLogger(JackrabbitUserManagement.class);


    /**
     * {@inheritDoc}
     */
    public boolean isAuthorizableNodeType(String ntName) {
        return ntName.equals("rep:Group") || ntName.equals("rep:User");
    }

    /**
     * {@inheritDoc}
     */
    public String getAuthorizablePath(Session session, String name) {
        // currently we rely on the implementation detail to keep the API dependency to jackrabbit  < 2.3.
        try {
            UUID uuid = UUID.nameUUIDFromBytes(name.toLowerCase().getBytes("UTF-8"));
            return session.getNodeByIdentifier(uuid.toString()).getPath();
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    public String getAuthorizableId(DocViewNode node) {
        // try Oak way of storing the id first:
        DocViewProperty idProp = node.props.get("rep:authorizableId");
        if (idProp == null || idProp.isMulti) {
            // jackrabbit 2.x or Oak with migrated Jackrabbit 2.x content
            return org.apache.jackrabbit.util.Text.unescapeIllegalJcrChars(node.name);
        } else {
            // oak 1.x
            return idProp.values[0];
        }
    }

    /**
     * {@inheritDoc}
     */
    public void addMembers(Session session, String id, String[] membersUUID) {
        if (!(session instanceof JackrabbitSession)) {
            log.warn("Unable to update membership. no jackrabbit session.");
            return;
        }
        UserManager uMgr;
        try {
            uMgr = ((JackrabbitSession) session).getUserManager();
        } catch (RepositoryException e) {
            log.warn("Unable to update membership of {}. Error while retrieving user manager.", id, e);
            return;
        }
        Authorizable auth;
        try {
            auth = uMgr.getAuthorizable(id);
        } catch (RepositoryException e) {
            log.warn("Unable to update membership of {}. Error while retrieving authorizable.", id, e);
            return;
        }
        if (auth == null) {
            log.warn("Unable to update membership of {}. No such authorizable.", id);
            return;
        }
        if (!(auth instanceof Group)) {
            log.warn("Unable to update membership of {}. No a group.", id);
            return;
        }
        Group grp = (Group) auth;
        for (String uuid: membersUUID) {
            try {
                Node authNode = session.getNodeByIdentifier(uuid);
                String authPath = authNode.getPath();
                Authorizable member = uMgr.getAuthorizableByPath(authPath);
                if (member == null) {
                    log.warn("unable to add authorizable '{}' to group '{}'. Node at {} is not an authorizable.", uuid, authPath);
                } else {
                    String memberId = member.getID();
                    try {
                        if (grp.isDeclaredMember(member)) {
                            log.info("ignoring to add authorizable '{}' to group '{}'. Already member.", memberId, id);
                        } else {
                            grp.addMember(member);
                            log.info("added authorizable '{}' to group '{}'.", memberId, id);
                        }
                    } catch (RepositoryException e) {
                        log.error("Error while adding authorizable '{}' to group '{}': {}", new Object[]{memberId, id, e});
                    }
                }
            } catch (ItemNotFoundException e) {
                log.warn("unable to add authorizable '{}' to group '{}'. No such node.", uuid, id);
            } catch (RepositoryException e) {
                log.warn("unable to add authorizable '{}' to group '{}'. Internal Error: {}", new Object[]{uuid, id, e});
            }
        }
    }
}
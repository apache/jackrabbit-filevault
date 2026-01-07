/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.jackrabbit.vault.fs.spi.impl.jcr20;

import javax.jcr.ItemNotFoundException;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.jackrabbit.spi.Name;
import org.apache.jackrabbit.spi.commons.name.NameFactoryImpl;
import org.apache.jackrabbit.vault.fs.spi.UserManagement;
import org.apache.jackrabbit.vault.util.DocViewNode;
import org.apache.jackrabbit.vault.util.DocViewNode2;
import org.apache.jackrabbit.vault.util.DocViewProperty;
import org.apache.jackrabbit.vault.util.DocViewProperty2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@code JackrabbitUserManagement}...
 */
public class JackrabbitUserManagement implements UserManagement {

    // https://issues.apache.org/jira/browse/OAK-9584
    public static final Name NAME_REP_AUTHORIZABLE_ID =
            NameFactoryImpl.getInstance().create(Name.NS_REP_URI, "authorizableId");

    /**
     * default logger
     */
    private static final Logger log = LoggerFactory.getLogger(JackrabbitUserManagement.class);

    /**
     * {@inheritDoc}
     */
    public boolean isAuthorizableNodeType(String ntName) {
        return ntName.equals("rep:Group") || ntName.equals("rep:User") || ntName.equals("rep:SystemUser");
    }

    /**
     * {@inheritDoc}
     */
    public String getAuthorizablePath(Session session, String id) {
        Authorizable authorizable;
        try {
            authorizable = getAuthorizable(session, id);
            if (authorizable == null) {
                log.debug("No existing authorizable with id {} found", id);
                return null;
            }
            return authorizable.getPath();
        } catch (RepositoryException e) {
            log.warn("Unable to get authorizable path of {}: {}", id, e.getMessage(), e);
            return null;
        }
    }

    @Override
    @Deprecated
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

    @Override
    public String getAuthorizableId(DocViewNode2 node) {
        // try Oak way of storing the id first:
        DocViewProperty2 idProp = node.getProperty(NAME_REP_AUTHORIZABLE_ID).orElse(null);
        if (idProp == null || idProp.isMultiValue()) {
            // jackrabbit 2.x or Oak with migrated Jackrabbit 2.x content
            return org.apache.jackrabbit.util.Text.unescapeIllegalJcrChars(
                    node.getName().getLocalName());
        } else {
            // oak 1.x
            return idProp.getStringValue()
                    .orElseThrow(() ->
                            new IllegalStateException("No single value available for property 'rep:authorizableId'"));
        }
    }

    /**
     * {@inheritDoc}
     */
    public void addMembers(Session session, String id, String[] membersUUID) {
        Authorizable auth;
        UserManager uMgr;
        try {
            uMgr = getUserManager(session);
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
        for (String uuid : membersUUID) {
            try {
                Node authNode = session.getNodeByIdentifier(uuid);
                String authPath = authNode.getPath();
                Authorizable member = uMgr.getAuthorizableByPath(authPath);
                if (member == null) {
                    log.warn(
                            "unable to add authorizable '{}' to group '{}'. Node at {} is not an authorizable.",
                            uuid,
                            authPath);
                } else {
                    String memberId = member.getID();
                    try {
                        if (grp.isDeclaredMember(member)) {
                            log.debug("ignoring to add authorizable '{}' to group '{}'. Already member.", memberId, id);
                        } else {
                            grp.addMember(member);
                            log.debug("added authorizable '{}' to group '{}'.", memberId, id);
                        }
                    } catch (RepositoryException e) {
                        log.error(
                                "Error while adding authorizable '{}' to group '{}': {}",
                                new Object[] {memberId, id, e});
                    }
                }
            } catch (ItemNotFoundException e) {
                log.warn("unable to add authorizable '{}' to group '{}'. No such node.", uuid, id);
            } catch (RepositoryException e) {
                log.warn("unable to add authorizable '{}' to group '{}'. Internal Error: {}", new Object[] {uuid, id, e
                });
            }
        }
    }

    private UserManager getUserManager(Session session) throws RepositoryException {
        if (!(session instanceof JackrabbitSession)) {
            throw new RepositoryException("no jackrabbit session.");
        }
        return ((JackrabbitSession) session).getUserManager();
    }

    private Authorizable getAuthorizable(Session session, String id) throws RepositoryException {
        return getUserManager(session).getAuthorizable(id);
    }

    @Override
    public String getPrincipalName(Session session, String id) {
        try {
            Authorizable auth = getAuthorizable(session, id);
            if (auth != null) {
                return auth.getPrincipal().getName();
            }
        } catch (RepositoryException e) {
            log.warn("Unable to get principal name of {}. Error while retrieving user manager or authorizable.", id, e);
            return null;
        }
        return null;
    }
}

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

package org.apache.jackrabbit.vault.packaging.impl;

import java.util.Iterator;

import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.Group;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class to check if a user/session has administrative permissions (e.g. to check if a package can be installed)
 */
public class AdminPermissionChecker {

    private static final Logger log = LoggerFactory.getLogger(AdminPermissionChecker.class);
    private static final String ADMIN_USER = "admin";
    private static final String SYSTEM_USER = "system";
    private static final String ADMINISTRATORS_GROUP = "administrators";

    /**
     * Checks if the user who opened the session has administrative permissions
     *
     * @param session a JCR session
     * @return whether the passed session is an admin session
     * @throws RepositoryException
     */
    public static boolean hasAdministrativePermissions(Session session) throws RepositoryException {
        String userId = session.getUserID();
        if (ADMIN_USER.equals(userId) || SYSTEM_USER.equals(userId)) {
            return true;
        }
        if (!(session instanceof JackrabbitSession)) {
            log.warn("could not evaluate group permissions but just user name");
            return false;
        }

        JackrabbitSession jackrabbitSession = (JackrabbitSession) session;
        Authorizable authorizable = jackrabbitSession.getUserManager().getAuthorizable(userId);
        if (authorizable == null) {
            return false;
        }

        Iterator<Group> groupIterator = authorizable.memberOf();
        while (groupIterator.hasNext()) {
            if (ADMINISTRATORS_GROUP.equals(groupIterator.next().getID())) {
                return true;
            }
        }

        return false;
    }
}

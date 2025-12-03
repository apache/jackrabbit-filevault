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
package org.apache.jackrabbit.vault.packaging.impl;

import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import java.security.Principal;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class to check if a session has administrative permissions (e.g. to check if a package can be installed)
 */
public class AdminPermissionChecker {

    private static final Logger log = LoggerFactory.getLogger(AdminPermissionChecker.class);
    private static final String ADMIN_USER = "admin";
    private static final String SYSTEM_USER = "system";
    private static final String ADMINISTRATORS_GROUP = "administrators";

    public static final ComparableVersion VERSION_OAK_140 = new ComparableVersion("1.40");

    private AdminPermissionChecker() {
        // static methods only
    }

    /**
     * Checks if the user who opened the session has administrative permissions
     *
     * @param session a JCR session
     * @return whether the passed session is an admin session
     * @throws RepositoryException If an error occurrs.
     */
    public static boolean hasAdministrativePermissions(
            @NotNull Session session, String... additionalAdminAuthorizableIdsOrPrincipalNames)
            throws RepositoryException {
        List<String> additionalAdminIdsOrPrincipalNames =
                Arrays.asList(Optional.ofNullable(additionalAdminAuthorizableIdsOrPrincipalNames)
                        .orElse(new String[0]));
        final JackrabbitSession jackrabbitSession;
        if (session instanceof JackrabbitSession) {
            jackrabbitSession = (JackrabbitSession) session;
            if (isOakVersionExposingBoundPrincipals(session.getRepository())) {
                if (hasAdministrativePermissionsWithPrincipals(jackrabbitSession, additionalAdminIdsOrPrincipalNames)) {
                    return true;
                }
            }
        } else {
            jackrabbitSession = null;
        }
        // then evaluate user id
        String userId = session.getUserID();
        if (hasAdministrativePermissionsWithAuthorizableId(userId, additionalAdminIdsOrPrincipalNames)) {
            return true;
        }
        if (jackrabbitSession != null) {
            Authorizable authorizable = jackrabbitSession.getUserManager().getAuthorizable(userId);
            if (authorizable == null) {
                return false;
            }

            Iterator<Group> groupIterator = authorizable.memberOf();
            while (groupIterator.hasNext()) {
                String groupId = groupIterator.next().getID();
                if (hasAdministrativePermissionsWithAuthorizableId(groupId, additionalAdminIdsOrPrincipalNames)) {
                    return true;
                }
            }
        } else {
            log.warn("could not evaluate group permissions but just user name");
        }
        return false;
    }

    static boolean hasAdministrativePermissionsWithPrincipals(
            @NotNull Session session, List<String> additionalAdminPrincipalNames) {
        Set<Principal> boundPrincipals = (Set<Principal>) session.getAttribute("oak.bound-principals");
        if (boundPrincipals != null) {
            for (Principal principal : boundPrincipals) {
                if (additionalAdminPrincipalNames.contains(principal.getName())) {
                    return true;
                }
            }
        }
        return false;
    }

    static boolean hasAdministrativePermissionsWithAuthorizableId(
            @NotNull String authorizableId, List<String> additionalAdminIds) {
        if (ADMIN_USER.equals(authorizableId)
                || SYSTEM_USER.equals(authorizableId)
                || ADMINISTRATORS_GROUP.equals(authorizableId)) {
            return true;
        }
        if (additionalAdminIds.contains(authorizableId)) {
            return true;
        }
        return false;
    }

    /**
     * Checks if the repository is Oak 1.40 or newer. Compare with <a href="https://issues.apache.org/jira/browse/OAK-9415">OAK-9415</a>.
     * @param session
     * @return {@code true} if Oak repository >= 1.40.0 is used, otherwise {@code false}
     */
    static boolean isOakVersionExposingBoundPrincipals(@NotNull Repository repository) {
        // first check repository
        if (!"Apache Jackrabbit Oak".equals(repository.getDescriptor(Repository.REP_NAME_DESC))) {
            return false;
        }
        String version = repository.getDescriptor(Repository.REP_VERSION_DESC);
        if (version == null) {
            return false;
        }
        // parse version according to Maven standards
        return new ComparableVersion(version).compareTo(VERSION_OAK_140) >= 0;
    }
}

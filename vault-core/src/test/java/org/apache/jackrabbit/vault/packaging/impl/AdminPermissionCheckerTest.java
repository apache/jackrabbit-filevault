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

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;

import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.jackrabbit.vault.packaging.integration.IntegrationTestBase;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Testcase for {@link AdminPermissionChecker}
 */
public class AdminPermissionCheckerTest extends IntegrationTestBase {

    private static final String TEST_USER = "testUser";

    @Test
    public void testAdminUser() throws Exception {
        assertTrue("user admin should have admin permissions", AdminPermissionChecker.hasAdministrativePermissions(admin));
    }

    @After
    public void after() throws RepositoryException {
        JackrabbitSession jackrabbitSession = (JackrabbitSession) admin;
        Authorizable vip = jackrabbitSession.getUserManager().getAuthorizable(TEST_USER);
        if (vip != null) {
            vip.remove();
        }
        jackrabbitSession.save();
    }

    @Test
    public void testNotAdminUser() throws Exception {
        JackrabbitSession jackrabbitSession = (JackrabbitSession) admin;
        Authorizable vip = jackrabbitSession.getUserManager().getAuthorizable(TEST_USER);
        assertNull("test user must not exist", vip);

        jackrabbitSession.getUserManager().createUser(TEST_USER, TEST_USER);
        admin.save();

        Session session = repository.login(new SimpleCredentials(TEST_USER, TEST_USER.toCharArray()));
        try {
            assertFalse(
                    "\"" + TEST_USER + "\" is not admin/system and doesn't belong to administrators thus shouldn't have admin permissions",
                    AdminPermissionChecker.hasAdministrativePermissions(session));
        } finally {
            session.logout();
        }
    }

    @Test
    public void testAdminGroup() throws Exception {
        JackrabbitSession jackrabbitSession = (JackrabbitSession) admin;
        Authorizable admins = jackrabbitSession.getUserManager().getAuthorizable("administrators");
        if (admins == null) {
            admins = jackrabbitSession.getUserManager().createGroup("administrators");
        }
        Group adminsGroup = (Group) admins;
        User testUser = (User) jackrabbitSession.getUserManager().getAuthorizable(TEST_USER);
        if (testUser == null) {
            testUser = jackrabbitSession.getUserManager().createUser(TEST_USER, TEST_USER);
        }
        adminsGroup.addMember(testUser);
        admin.save();
        Session session = repository.login(new SimpleCredentials(TEST_USER, TEST_USER.toCharArray()));
        try {
            assertTrue(
                    "user \"" + TEST_USER + "\" has been added to administrators group thus should have admin permissions",
                    AdminPermissionChecker.hasAdministrativePermissions(session));
        } finally {
            session.logout();
        }
    }


}

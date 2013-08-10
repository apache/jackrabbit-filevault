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

import javax.jcr.SimpleCredentials;

import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.vault.packaging.integration.IntegrationTestBase;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Testcase for {@link AdminPermissionChecker}
 */
public class AdminPermissionCheckerTest extends IntegrationTestBase {

    @Test
    public void testAdminUser() throws Exception {
        assertTrue("user admin should have admin permissions", AdminPermissionChecker.hasAdministrativePermissions(admin));
    }

    @Test
    public void testNotAdminUser() throws Exception {
        JackrabbitSession jackrabbitSession = (JackrabbitSession) admin;
        Authorizable vip = jackrabbitSession.getUserManager().getAuthorizable("who");
        if (vip == null) {
            jackrabbitSession.getUserManager().createUser("who", "who");
        }
        jackrabbitSession.save();
        admin.save();
        admin.logout();
        admin = repository.login(new SimpleCredentials("who", "who".toCharArray()));
        assertFalse("\"who\" is not admin/system and doesn't belong to administrators thus shouldn't have admin permissions",
                AdminPermissionChecker.hasAdministrativePermissions(admin));
    }

    @Test
    public void testAdminGroup() throws Exception {
        JackrabbitSession jackrabbitSession = (JackrabbitSession) admin;
        Authorizable admins = jackrabbitSession.getUserManager().getAuthorizable("administrators");
        if (admins == null) {
            admins = jackrabbitSession.getUserManager().createGroup("administrators");
        }
        Group adminsGroup = (Group) admins;
        adminsGroup.addMember(jackrabbitSession.getUserManager().getAuthorizable("anonymous"));
        jackrabbitSession.save();
        admin.save();
        admin.logout();
        admin = repository.login(new SimpleCredentials("anonymous", "anonymous".toCharArray()));
        assertTrue("user \"anonymous\" has been added to administrators group thus should have admin permissions",
                AdminPermissionChecker.hasAdministrativePermissions(admin));
    }


}

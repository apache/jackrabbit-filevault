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

package org.apache.jackrabbit.vault.packaging.integration;

import java.io.IOException;

import javax.jcr.RepositoryException;

import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.jackrabbit.vault.packaging.JcrPackage;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * <code>TestEmptyPackage</code>...
 */
public class TestGroupMergePackage extends IntegrationTestBase {

    @Override
    public void tearDown() throws Exception {
        // remove test authorizables
        UserManager mgr = ((JackrabbitSession) admin).getUserManager();
        removeAuthorizable(mgr, "test-group");
        removeAuthorizable(mgr, "test-user-a");
        removeAuthorizable(mgr, "test-user-b");
        removeAuthorizable(mgr, "test-user-c");
        admin.save();
        super.tearDown();
    }

    private void removeAuthorizable(UserManager mgr, String name) throws RepositoryException {
        Authorizable a = mgr.getAuthorizable(name);
        if (a != null) {
            a.remove();
        }
    }

    /**
     * Installs a package that contains a "test-group" and a "test-user-a" as member of the group.
     */
    @Test
    public void installGroupA() throws RepositoryException, IOException, PackageException {
        UserManager mgr = ((JackrabbitSession) admin).getUserManager();
        assertNull("test-group must not exist", mgr.getAuthorizable("test-group"));
        assertNull("test-user-a must not exist", mgr.getAuthorizable("test-user-a"));

        JcrPackage pack = packMgr.upload(getStream("testpackages/group_with_a.zip"), false);
        assertNotNull(pack);
        pack.install(getDefaultOptions());

        // check if group exists
        Group grp = (Group) mgr.getAuthorizable("test-group");
        assertNotNull("test-group must exist", grp);
        User userA = (User) mgr.getAuthorizable("test-user-a");
        assertNotNull("test-user-a must exist", userA);
        assertTrue("test-user-a is member of test-group", grp.isMember(userA));
    }

    /**
     * Installs 2 packages with "test-group" that contain test-user-a and test-user-b,test-user-c respectively.
     * since the import mode is merge, the memberships should be merged.
     */
    @Test
    public void installGroupABC() throws RepositoryException, IOException, PackageException {
        // ensure that test users don't exist yet (proper setup)
        UserManager mgr = ((JackrabbitSession) admin).getUserManager();
        assertNull("test-group must not exist", mgr.getAuthorizable("test-group"));
        assertNull("test-user-a must not exist", mgr.getAuthorizable("test-user-a"));
        assertNull("test-user-b must not exist", mgr.getAuthorizable("test-user-b"));
        assertNull("test-user-c must not exist", mgr.getAuthorizable("test-user-c"));

        JcrPackage pack = packMgr.upload(getStream("testpackages/group_with_a.zip"), false);
        assertNotNull(pack);
        pack.install(getDefaultOptions());

        pack = packMgr.upload(getStream("testpackages/group_with_bc.zip"), false);
        assertNotNull(pack);
        pack.install(getDefaultOptions());
        assertABC(mgr);
    }

    /**
     * Installs 2 packages with "test-group" that contain test-user-a and test-user-b,test-user-c respectively.
     * since the import mode is merge, the memberships should be merged. this variant uses a renamed authorizable node name
     */
    @Test
    public void installGroupABC_renamed() throws RepositoryException, IOException, PackageException {
        // ensure that test users don't exist yet (proper setup)
        UserManager mgr = ((JackrabbitSession) admin).getUserManager();
        assertNull("test-group must not exist", mgr.getAuthorizable("test-group"));
        assertNull("test-user-a must not exist", mgr.getAuthorizable("test-user-a"));
        assertNull("test-user-b must not exist", mgr.getAuthorizable("test-user-b"));
        assertNull("test-user-c must not exist", mgr.getAuthorizable("test-user-c"));

        JcrPackage pack = packMgr.upload(getStream("testpackages/group_with_bc.zip"), false);
        assertNotNull(pack);
        pack.install(getDefaultOptions());

        pack = packMgr.upload(getStream("testpackages/group_with_a_moved.zip"), false);
        assertNotNull(pack);
        pack.install(getDefaultOptions());
        assertABC(mgr);
    }


    private void assertABC(UserManager mgr) throws RepositoryException {
        // check if group exists
        Group grp = (Group) mgr.getAuthorizable("test-group");
        assertNotNull("test-group must exist", grp);
        User userA = (User) mgr.getAuthorizable("test-user-a");
        User userB = (User) mgr.getAuthorizable("test-user-b");
        User userC = (User) mgr.getAuthorizable("test-user-c");
        assertNotNull("test-user-a must exist", userA);
        assertNotNull("test-user-b must exist", userB);
        assertNotNull("test-user-c must exist", userC);

        assertTrue("test-user-a is member of test-group", grp.isMember(userA));
        assertTrue("test-user-b is member of test-group", grp.isMember(userB));
        assertTrue("test-user-c is member of test-group", grp.isMember(userC));
    }

}
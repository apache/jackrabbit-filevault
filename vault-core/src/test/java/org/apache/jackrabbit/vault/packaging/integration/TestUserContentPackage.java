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
import javax.jcr.nodetype.NodeType;

import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.jackrabbit.core.security.principal.PrincipalImpl;
import org.apache.jackrabbit.vault.fs.api.ImportMode;
import org.apache.jackrabbit.vault.fs.io.ImportOptions;
import org.apache.jackrabbit.vault.packaging.JcrPackage;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.apache.jackrabbit.vault.util.Text;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;

/**
 * <code>TestEmptyPackage</code>...
 */
public class TestUserContentPackage extends IntegrationTestBase {

    private static final String PARENT_PATH_TEST_USER_A = "/home/users/test";
    private static final String ID_TEST_USER_A = "test-user-a";
    private static final String NAME_USER_PROPERTY = "userProperty";
    private static final String NAME_PROFILE_FULLNAME = "profile/fullname";
    private static final String NAME_PROFILE_PROPERTY = "profile/profileProperty";
    private static final String NAME_PROFILE_NODE = "profile";
    private static final String NAME_PROFILE_PICTURE_NODE = "profile/picture.txt";
    private static final String NAME_PROFILE_PRIVATE_NODE = "profile_private";

    @Override
    public void tearDown() throws Exception {
        // remove test authorizables
        UserManager mgr = ((JackrabbitSession) admin).getUserManager();
        removeAuthorizable(mgr, ID_TEST_USER_A);
        admin.save();
        super.tearDown();
    }

    private void removeAuthorizable(UserManager mgr, String name) throws RepositoryException {
        Authorizable a = mgr.getAuthorizable(name);
        if (a != null) {
            a.remove();
        }
    }

    @Test
    public void installUserA() throws RepositoryException, IOException, PackageException {
        installUserA(null, false, true);
    }

    @Test
    public void installUserA_PkgPath() throws RepositoryException, IOException, PackageException {
        installUserA(null, true, true);
    }

    @Test
    public void installUserA_Merge() throws RepositoryException, IOException, PackageException {
        installUserA(ImportMode.MERGE, false, false);
    }

    @Test
    public void installUserA_Merge_PkgPath() throws RepositoryException, IOException, PackageException {
        installUserA(ImportMode.MERGE, true, true);
    }

    @Test
    public void installUserA_Update() throws RepositoryException, IOException, PackageException {
        installUserA(ImportMode.UPDATE, false, false);
    }

    @Test
    public void installUserA_Update_PkgPath() throws RepositoryException, IOException, PackageException {
        installUserA(ImportMode.UPDATE, true, true);
    }

    @Test
    public void installUserA_Replace() throws RepositoryException, IOException, PackageException {
        installUserA(ImportMode.REPLACE, false, true);
    }

    @Test
    public void installUserA_Replace_PkgPath() throws RepositoryException, IOException, PackageException {
        installUserA(ImportMode.REPLACE, true, true);
    }

    @Test
    public void installUserA_Profile() throws RepositoryException, IOException, PackageException {
        // install default user at package path
        User userA = installUserA(ImportMode.REPLACE, true, true);
        String authPath = userA.getPath();

        assertPropertyMissing(authPath + "/" + NAME_PROFILE_PROPERTY);

        // install updated profile
        JcrPackage pack = packMgr.upload(getStream("testpackages/test_user_a_profile.zip"), false);
        assertNotNull(pack);
        pack.install(getDefaultOptions());

        assertProperty(authPath + "/" + NAME_PROFILE_FULLNAME, "Test User");
        assertProperty(authPath + "/" + NAME_PROFILE_PROPERTY, "a");
    }

    @Test
    public void installUserA_Profile_Moved() throws RepositoryException, IOException, PackageException {
        // install default user at package path
        User userA = installUserA(ImportMode.UPDATE, false, false);
        String authPath = userA.getPath();

        assertPropertyMissing(authPath + "/" + NAME_PROFILE_PROPERTY);

        // install updated profile
        JcrPackage pack = packMgr.upload(getStream("testpackages/test_user_a_profile.zip"), false);
        assertNotNull(pack);
        pack.install(getDefaultOptions());

        assertProperty(authPath + "/" + NAME_PROFILE_FULLNAME, "Test User");
        assertProperty(authPath + "/" + NAME_PROFILE_PROPERTY, "a");
    }

    @Test
    public void installUserA_Profile_Picture() throws RepositoryException, IOException, PackageException {
        // install default user at package path
        User userA = installUserA(ImportMode.REPLACE, true, true);
        String authPath = userA.getPath();

        assertPropertyMissing(authPath + "/" + NAME_PROFILE_PROPERTY);

        // install updated profile
        JcrPackage pack = packMgr.upload(getStream("testpackages/test_user_a_profile_picture.zip"), false);
        assertNotNull(pack);
        pack.install(getDefaultOptions());

        assertProperty(authPath + "/" + NAME_PROFILE_FULLNAME, "Test User");
        assertProperty(authPath + "/" + NAME_PROFILE_PROPERTY, "a");
        assertNodeExists(authPath + "/" + NAME_PROFILE_PICTURE_NODE);
    }

    @Test
    public void installUserA_Profile_Picture_Moved() throws RepositoryException, IOException, PackageException {
        // install default user at package path
        User userA = installUserA(ImportMode.UPDATE, false, false);
        String authPath = userA.getPath();

        assertPropertyMissing(authPath + "/" + NAME_PROFILE_PROPERTY);

        // install updated profile
        JcrPackage pack = packMgr.upload(getStream("testpackages/test_user_a_profile_picture.zip"), false);
        assertNotNull(pack);
        pack.install(getDefaultOptions());

        assertProperty(authPath + "/" + NAME_PROFILE_FULLNAME, "Test User");
        assertProperty(authPath + "/" + NAME_PROFILE_PROPERTY, "a");
        assertNodeExists(authPath + "/" + NAME_PROFILE_PICTURE_NODE);
    }

    private User installUserA(ImportMode mode, boolean usePkgPath, boolean expectPkgPath) throws RepositoryException, IOException, PackageException {
        UserManager mgr = ((JackrabbitSession) admin).getUserManager();
        assertNull("test-user-a must not exist", mgr.getAuthorizable(ID_TEST_USER_A));

        User u;
        if (usePkgPath) {
            u = mgr.createUser(ID_TEST_USER_A, "nonce", new PrincipalImpl(ID_TEST_USER_A), PARENT_PATH_TEST_USER_A);
        } else {
            u = mgr.createUser(ID_TEST_USER_A, "nonce");
        }
        final String authPath = u.getPath();
        if (usePkgPath) {
            assertEquals("authorizable path must be correct", PARENT_PATH_TEST_USER_A, Text.getRelativeParent(authPath, 1));
        } else {
            assertNotSame("authorizable path must be different than the one in the package", PARENT_PATH_TEST_USER_A, Text.getRelativeParent(authPath, 1));
        }

        // create test property and node
        u.setProperty(NAME_USER_PROPERTY, admin.getValueFactory().createValue("initial"));
        admin.getNode(u.getPath()).addNode(NAME_PROFILE_PRIVATE_NODE, NodeType.NT_UNSTRUCTURED);

        admin.save();

        JcrPackage pack = packMgr.upload(getStream("testpackages/test_user_a.zip"), false);
        assertNotNull(pack);
        ImportOptions opts = getDefaultOptions();
        if (mode != null) {
            opts.setImportMode(mode);
        }
        pack.install(opts);

        // check if user exists
        User userA = (User) mgr.getAuthorizable(ID_TEST_USER_A);
        assertNotNull("test-user-a must exist", userA);

        // check path
        if (expectPkgPath) {
            assertEquals("authorizable path must be correct", PARENT_PATH_TEST_USER_A, Text.getRelativeParent(userA.getPath(), 1));
        } else {
            assertEquals("authorizable path must be correct", authPath, userA.getPath());
        }

        // check import mode dependent stuff
        if (mode == null || mode == ImportMode.REPLACE) {
            assertProperty(userA.getPath() + "/" + NAME_USER_PROPERTY, "a");
            assertProperty(userA.getPath() + "/" + NAME_PROFILE_FULLNAME, "Test User");
            assertNodeExists(userA.getPath() + "/" + NAME_PROFILE_NODE);
            assertNodeMissing(userA.getPath() + "/" + NAME_PROFILE_PRIVATE_NODE);
        } else if (mode == ImportMode.UPDATE) {
            assertProperty(userA.getPath() + "/" + NAME_USER_PROPERTY, "a");
            assertProperty(userA.getPath() + "/" + NAME_PROFILE_FULLNAME, "Test User");
            assertNodeExists(userA.getPath() + "/" + NAME_PROFILE_NODE);
            assertNodeExists(userA.getPath() + "/" + NAME_PROFILE_PRIVATE_NODE);
        } else if (mode == ImportMode.MERGE) {
            assertProperty(userA.getPath() + "/" + NAME_USER_PROPERTY, "initial");
            assertProperty(userA.getPath() + "/" + NAME_PROFILE_FULLNAME, "Test User");
            assertNodeExists(userA.getPath() + "/" + NAME_PROFILE_NODE);
            assertNodeExists(userA.getPath() + "/" + NAME_PROFILE_PRIVATE_NODE);
        }

        return userA;
    }

}
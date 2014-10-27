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
import org.apache.jackrabbit.core.security.principal.PrincipalImpl;
import org.apache.jackrabbit.oak.security.user.RandomAuthorizableNodeName;
import org.apache.jackrabbit.oak.spi.security.user.AuthorizableNodeName;
import org.apache.jackrabbit.oak.spi.security.user.UserConfiguration;
import org.apache.jackrabbit.oak.spi.security.user.UserConstants;
import org.apache.jackrabbit.vault.fs.api.ImportMode;
import org.apache.jackrabbit.vault.fs.io.ImportOptions;
import org.apache.jackrabbit.vault.packaging.JcrPackage;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * <code>TestEmptyPackage</code>...
 */
public class TestUserContentPackage extends IntegrationTestBase {

    private static final String PARENT_PATH_TEST_USER_A = "/home/users/test";
    private static final String PATH_TEST_USER_A = "/home/users/test/test-user-a";
    private static final String ID_TEST_USER_A = "test-user-a";
    private static final String NAME_USER_PROPERTY = "userProperty";

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
        UserManager mgr = ((JackrabbitSession) admin).getUserManager();
        assertNull("test-user-a must not exist", mgr.getAuthorizable("test-user-a"));

        JcrPackage pack = packMgr.upload(getStream("testpackages/test_user_a.zip"), false);
        assertNotNull(pack);
        pack.install(getDefaultOptions());

        // check if user exists
        User userA = (User) mgr.getAuthorizable(ID_TEST_USER_A);
        assertNotNull("test-user-a must exist", userA);

        // default installation must preserve auth-path in package
        assertEquals("authorizable path must be correct", PATH_TEST_USER_A, userA.getPath());

        // user must contain new property
        assertProperty(userA.getPath() + "/" + NAME_USER_PROPERTY, "a");
    }

    @Test
    public void installUserA_Merge_SamePath() throws RepositoryException, IOException, PackageException {
        UserManager mgr = ((JackrabbitSession) admin).getUserManager();
        assertNull("test-user-a must not exist", mgr.getAuthorizable(ID_TEST_USER_A));

        User u = mgr.createUser(ID_TEST_USER_A, "nonce", new PrincipalImpl(ID_TEST_USER_A), PARENT_PATH_TEST_USER_A);
        String authPath = u.getPath();
        assertEquals("authorizable path must be correct", PATH_TEST_USER_A, authPath);

        // create test property
        u.setProperty(NAME_USER_PROPERTY, admin.getValueFactory().createValue("initial"));
        admin.save();

        JcrPackage pack = packMgr.upload(getStream("testpackages/test_user_a.zip"), false);
        assertNotNull(pack);
        ImportOptions opts = getDefaultOptions();
        opts.setImportMode(ImportMode.MERGE);
        pack.install(opts);

        // check if user exists
        User userA = (User) mgr.getAuthorizable("test-user-a");
        assertNotNull("test-user-a must exist", userA);

        // merge installation must preserve auth-path
        assertEquals("authorizable path must be correct", authPath, userA.getPath());

        // merged user must contain initial property
        assertProperty(authPath + "/" + NAME_USER_PROPERTY, "initial");
    }

    @Test
    public void installUserA_Update_SamePath() throws RepositoryException, IOException, PackageException {
        UserManager mgr = ((JackrabbitSession) admin).getUserManager();
        assertNull("test-user-a must not exist", mgr.getAuthorizable(ID_TEST_USER_A));

        User u = mgr.createUser(ID_TEST_USER_A, "nonce", new PrincipalImpl(ID_TEST_USER_A), PARENT_PATH_TEST_USER_A);
        String authPath = u.getPath();
        assertEquals("authorizable path must be correct", PATH_TEST_USER_A, authPath);

        // create test property
        u.setProperty(NAME_USER_PROPERTY, admin.getValueFactory().createValue("initial"));
        admin.save();

        JcrPackage pack = packMgr.upload(getStream("testpackages/test_user_a.zip"), false);
        assertNotNull(pack);
        ImportOptions opts = getDefaultOptions();
        opts.setImportMode(ImportMode.UPDATE);
        pack.install(opts);

        // check if user exists
        User userA = (User) mgr.getAuthorizable("test-user-a");
        assertNotNull("test-user-a must exist", userA);

        // merge installation must preserve auth-path
        assertEquals("authorizable path must be correct", authPath, userA.getPath());

        // merged user must contain new property
        assertProperty(authPath + "/"+ NAME_USER_PROPERTY, "a");
    }

    @Test
    public void installUserA_Merge_DifferentPath() throws RepositoryException, IOException, PackageException {
        UserManager mgr = ((JackrabbitSession) admin).getUserManager();
        assertNull("test-user-a must not exist", mgr.getAuthorizable(ID_TEST_USER_A));

        User u = mgr.createUser(ID_TEST_USER_A, "nonce");
        String authPath = u.getPath();
        assertNotSame("authorizable path must be different than the one in the package", PATH_TEST_USER_A, authPath);

        // create test property
        u.setProperty(NAME_USER_PROPERTY, admin.getValueFactory().createValue("initial"));
        admin.save();

        JcrPackage pack = packMgr.upload(getStream("testpackages/test_user_a.zip"), false);
        assertNotNull(pack);
        ImportOptions opts = getDefaultOptions();
        opts.setImportMode(ImportMode.MERGE);
        pack.install(opts);

        // check if user exists
        User userA = (User) mgr.getAuthorizable(ID_TEST_USER_A);
        assertNotNull("test-user-a must exist", userA);

        // merge installation must preserve auth-path
        assertEquals("authorizable path must be correct", authPath, userA.getPath());

        // merged user must contain new property
        assertProperty(authPath + "/" + NAME_USER_PROPERTY, "initial");
    }

    @Test
    public void installUserA_Update_DifferentPath() throws RepositoryException, IOException, PackageException {
        UserManager mgr = ((JackrabbitSession) admin).getUserManager();
        assertNull("test-user-a must not exist", mgr.getAuthorizable(ID_TEST_USER_A));

        User u = mgr.createUser(ID_TEST_USER_A, "nonce");
        String authPath = u.getPath();
        assertNotSame("authorizable path must be different than the one in the package", PATH_TEST_USER_A, authPath);

        // create test property
        u.setProperty(NAME_USER_PROPERTY, admin.getValueFactory().createValue("initial"));
        admin.save();

        JcrPackage pack = packMgr.upload(getStream("testpackages/test_user_a.zip"), false);
        assertNotNull(pack);
        ImportOptions opts = getDefaultOptions();
        opts.setImportMode(ImportMode.UPDATE);
        pack.install(opts);

        // check if user exists
        User userA = (User) mgr.getAuthorizable(ID_TEST_USER_A);
        assertNotNull("test-user-a must exist", userA);

        // update installation must use package path
        assertEquals("authorizable path must be correct", PATH_TEST_USER_A, userA.getPath());

        // merged user must contain new property
        assertProperty(userA.getPath() + "/" + NAME_USER_PROPERTY, "a");

    }

}
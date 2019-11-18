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

import java.io.File;
import java.io.IOException;
import java.util.Properties;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.SimpleCredentials;
import javax.jcr.nodetype.NodeType;

import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.jackrabbit.core.security.principal.PrincipalImpl;
import org.apache.jackrabbit.vault.fs.api.ImportMode;
import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.config.DefaultMetaInf;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.fs.io.AccessControlHandling;
import org.apache.jackrabbit.vault.fs.io.ImportOptions;
import org.apache.jackrabbit.vault.packaging.ExportOptions;
import org.apache.jackrabbit.vault.packaging.JcrPackage;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.apache.jackrabbit.vault.util.Text;
import org.junit.Assume;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * {@code TestEmptyPackage}...
 */
public class TestUserContentPackage extends IntegrationTestBase {

    private static final String PARENT_PATH_TEST_USER_A = "/home/users/test";
    private static final String ID_TEST_USER_A = "test-user-a";
    private static final String ID_TEST_PASSWORD = "nonce";
    private static final String ID_TEST_GROUP_A = "test-group-a";
    private static final String NAME_USER_PROPERTY = "userProperty";
    private static final String NAME_PROFILE_FULLNAME = "profile/fullname";
    private static final String NAME_PROFILE_PROPERTY = "profile/profileProperty";
    private static final String NAME_PROFILE_NODE = "profile";
    private static final String NAME_PROFILE_PICTURE_NODE = "profile/picture.txt";
    private static final String NAME_PROFILE_PRIVATE_NODE = "profile_private";

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
        JcrPackage pack = packMgr.upload(getStream("/test-packages/test_user_a_profile.zip"), false);
        assertNotNull(pack);
        pack.install(getDefaultOptions());

        assertProperty(authPath + "/" + NAME_PROFILE_FULLNAME, "Test User");
        assertProperty(authPath + "/" + NAME_PROFILE_PROPERTY, "a");
    }

    @Test
    public void installUserA_Profile_NonExistingUser() throws RepositoryException, IOException, PackageException {
        UserManager mgr = ((JackrabbitSession) admin).getUserManager();
        assertNull("test-user-a must not exist", mgr.getAuthorizable(ID_TEST_USER_A));

        // install profile
        JcrPackage pack = packMgr.upload(getStream("/test-packages/test_user_a_profile.zip"), false);
        assertNotNull(pack);
        pack.install(getDefaultOptions());

        Authorizable user = mgr.getAuthorizable(ID_TEST_USER_A);
        assertNotNull("test-user-a must exist", user);

        // profile must exist
        assertProperty(user.getPath() + "/" + NAME_PROFILE_PROPERTY, "a");
    }

    @Test
    public void installUserA_Profile_Moved() throws RepositoryException, IOException, PackageException {
        // install default user at package path
        User userA = installUserA(ImportMode.UPDATE, false, false);
        String authPath = userA.getPath();

        assertPropertyMissing(authPath + "/" + NAME_PROFILE_PROPERTY);

        // install updated profile
        JcrPackage pack = packMgr.upload(getStream("/test-packages/test_user_a_profile.zip"), false);
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
        JcrPackage pack = packMgr.upload(getStream("/test-packages/test_user_a_profile_picture.zip"), false);
        assertNotNull(pack);
        pack.install(getDefaultOptions());

        assertProperty(authPath + "/" + NAME_PROFILE_FULLNAME, "Test User");
        assertProperty(authPath + "/" + NAME_PROFILE_PROPERTY, "a");
        assertNodeExists(authPath + "/" + NAME_PROFILE_PICTURE_NODE);
    }

    @Test
    public void installUserA_Profile_Picture_NonExistingUser() throws RepositoryException, IOException, PackageException {
        UserManager mgr = ((JackrabbitSession) admin).getUserManager();
        assertNull("test-user-a must not exist", mgr.getAuthorizable(ID_TEST_USER_A));

        // install updated profile
        JcrPackage pack = packMgr.upload(getStream("/test-packages/test_user_a_profile_picture.zip"), false);
        assertNotNull(pack);
        pack.install(getDefaultOptions());

        Authorizable user = mgr.getAuthorizable(ID_TEST_USER_A);
        assertNotNull("test-user-a must exist", user);

        // image profile must exist
        assertNodeExists(user.getPath() + "/" + NAME_PROFILE_PICTURE_NODE);
    }

    @Test
    public void installUserA_Profile_Picture_Moved() throws RepositoryException, IOException, PackageException {
        // install default user at package path
        User userA = installUserA(ImportMode.UPDATE, false, false);
        String authPath = userA.getPath();

        assertPropertyMissing(authPath + "/" + NAME_PROFILE_PROPERTY);

        // install updated profile
        JcrPackage pack = packMgr.upload(getStream("/test-packages/test_user_a_profile_picture.zip"), false);
        assertNotNull(pack);
        pack.install(getDefaultOptions());

        assertProperty(authPath + "/" + NAME_PROFILE_FULLNAME, "Test User");
        assertProperty(authPath + "/" + NAME_PROFILE_PROPERTY, "a");
        assertNodeExists(authPath + "/" + NAME_PROFILE_PICTURE_NODE);
    }

    @Test
    public void installUserA_Policy_Moved() throws RepositoryException, IOException, PackageException {
        UserManager mgr = ((JackrabbitSession) admin).getUserManager();
        assertNull("test-user-a must not exist", mgr.getAuthorizable(ID_TEST_USER_A));

        User u = mgr.createUser(ID_TEST_USER_A, "nonce");
        String authPath = u.getPath();
        assertNotSame("authorizable path must be different than the one in the package", PARENT_PATH_TEST_USER_A, Text.getRelativeParent(authPath, 1));

        // assert that user does not have an ACL setup
        assertPermissionMissing(authPath, true, new String[]{"jcr:all"}, "everyone", null);

        JcrPackage pack = packMgr.upload(getStream("/test-packages/test_user_a_policy.zip"), false);
        assertNotNull(pack);
        ImportOptions opts = getDefaultOptions();
        opts.setImportMode(ImportMode.MERGE);
        opts.setAccessControlHandling(AccessControlHandling.MERGE_PRESERVE);
        pack.install(opts);

        // check if user exists
        User userA = (User) mgr.getAuthorizable(ID_TEST_USER_A);
        assertNotNull("test-user-a must exist", userA);
        authPath = u.getPath();

        // assert that user has an ACL setup
        assertPermission(authPath, true, new String[]{"jcr:all"}, "everyone", null);
    }

    /**
     * Tests if a package that contains 2 sibling user aggregates don't produce a concurrent modification
     * exception, if the users are remapped. JCRVLT-76
     */
    @Test
    public void install_two_moved_users() throws RepositoryException, IOException, PackageException {
        JcrPackage pack = packMgr.upload(getStream("/test-packages/test_two_moved_users.zip"), false);
        assertNotNull(pack);
        ImportOptions opts = getDefaultOptions();
        opts.setImportMode(ImportMode.MERGE);
        opts.setAccessControlHandling(AccessControlHandling.MERGE_PRESERVE);
        pack.install(opts);
    }



    @Test
    public void install_mv_property() throws RepositoryException, IOException, PackageException {
        UserManager mgr = ((JackrabbitSession) admin).getUserManager();
        User u = mgr.createUser(ID_TEST_USER_A, ID_TEST_PASSWORD);
        Node node = admin.getNode(u.getPath());

        node.setProperty("mv", new String[]{"mv1", "mv2"});
        Property property = node.getProperty("mv");
        assertTrue(property.isMultiple());
        admin.save();

        File tmpFile = createPackage("test", "test", u.getPath());
        try {
            u.remove();
            u = (User) mgr.getAuthorizable(ID_TEST_USER_A);
            assertNull(u);

            try (JcrPackage pack = packMgr.upload(tmpFile, true, true, null)) {
                assertNotNull(pack);
                ImportOptions opts = getDefaultOptions();
                pack.install(opts);

                u = (User) mgr.getAuthorizable(ID_TEST_USER_A);
                assertNotNull(u);

                node = admin.getNode(u.getPath());
                property = node.getProperty("mv");
                assertTrue(property.isMultiple());
            }
        } finally {
            tmpFile.delete();
        }
    }

    @Test
    public void install_single_mv_property() throws RepositoryException, IOException, PackageException {
        UserManager mgr = ((JackrabbitSession) admin).getUserManager();
        User u = mgr.createUser(ID_TEST_USER_A, ID_TEST_PASSWORD);
        Node node = admin.getNode(u.getPath());

        node.setProperty("mv", new String[]{"mv1"});
        Property property = node.getProperty("mv");
        assertTrue(property.isMultiple());
        admin.save();

        File tmpFile = createPackage("test", "test", u.getPath());
        try {
            u.remove();
            u = (User)  mgr.getAuthorizable(ID_TEST_USER_A);
            assertNull(u);

            try (JcrPackage pack = packMgr.upload(tmpFile, true, true, null);) {
                assertNotNull(pack);
                ImportOptions opts = getDefaultOptions();
                pack.install(opts);

                u = (User) mgr.getAuthorizable(ID_TEST_USER_A);
                assertNotNull(u);

                node = admin.getNode(u.getPath());
                property = node.getProperty("mv");
                assertTrue(property.isMultiple());
            }
        } finally {
            tmpFile.delete();
        }
    }

    /**
     * Tests if installing a package using {@link ImportMode#UPDATE} with a user that already exists in the repository
     * succeeds, even if it has a rep:cache node and is on a different location (JCRVLT-128).
     */
    @Test
    public void install_moved_user_with_rep_cache_update() throws RepositoryException, IOException, PackageException {
        Assume.assumeTrue(isOak());
        install_moved_user_with_rep_cache(ImportMode.UPDATE);
    }

    /**
     * Tests if installing a package using {@link ImportMode#REPLACE} with a user that already exists in the repository
     * succeeds, even if it has a rep:cache node and is on a different location (JCRVLT-128).
     */
    @Test
    public void install_moved_user_with_rep_cache_replace() throws RepositoryException, IOException, PackageException {
        Assume.assumeTrue(isOak());
        install_moved_user_with_rep_cache(ImportMode.REPLACE);
    }

    /**
     * Tests if installing a package using {@link ImportMode#MERGE} with a user that already exists in the repository
     * succeeds, even if it has a rep:cache node and is on a different location (JCRVLT-128).
     */
    @Test
    public void install_moved_user_with_rep_cache_merge() throws RepositoryException, IOException, PackageException {
        Assume.assumeTrue(isOak());
        install_moved_user_with_rep_cache(ImportMode.MERGE);
    }

    /**
     * Tests if installing a package using {@link ImportMode#UPDATE} with a package that contains a rep:cache node
     * and another childnode works (JCRVLT-137).
     */
    @Test
    public void install_user_with_rep_cache_update() throws RepositoryException, IOException, PackageException {
        Assume.assumeTrue(isOak());
        install_user_with_rep_cache(ImportMode.UPDATE);
    }

    /**
     * Tests if installing a package using {@link ImportMode#REPLACE} with a package that contains a rep:cache node
     * and another childnode works (JCRVLT-137).
     */
    @Test
    public void install_user_with_rep_cache_replace() throws RepositoryException, IOException, PackageException {
        Assume.assumeTrue(isOak());
        install_user_with_rep_cache(ImportMode.REPLACE);
    }

    /**
     * Tests if installing a package using {@link ImportMode#MERGE} with a package that contains a rep:cache node
     * and another childnode works (JCRVLT-137).
     */
    @Test
    public void install_user_with_rep_cache_merge() throws RepositoryException, IOException, PackageException {
        Assume.assumeTrue(isOak());
        install_user_with_rep_cache(ImportMode.MERGE);
    }

    private void install_user_with_rep_cache(ImportMode mode) throws RepositoryException, IOException, PackageException {
        UserManager mgr = ((JackrabbitSession) admin).getUserManager();
        assertNull("test-user-a must not exist", mgr.getAuthorizable(ID_TEST_USER_A));

        // install user package
        JcrPackage pack = packMgr.upload(getStream("/test-packages/test_user_with_rep_cache.zip"), false);
        assertNotNull(pack);
        ImportOptions opts = getDefaultOptions();
        opts.setImportMode(mode);
        pack.install(opts);

        // check if user exists
        User userA = (User) mgr.getAuthorizable(ID_TEST_USER_A);
        assertNotNull("test-user-a must exist", userA);
    }

    private void install_moved_user_with_rep_cache(ImportMode mode) throws RepositoryException, IOException, PackageException {
        UserManager mgr = ((JackrabbitSession) admin).getUserManager();
        User u = mgr.createUser(ID_TEST_USER_A, ID_TEST_PASSWORD);
        String newPath = u.getPath() + "_moved";
        admin.move(u.getPath(), newPath);
        admin.save();

        Group g = mgr.createGroup(ID_TEST_GROUP_A);
        g.addMember(u);
        admin.save();

        // login to the repository to generate some rep:cache nodes
        repository.login(new SimpleCredentials(ID_TEST_USER_A, ID_TEST_PASSWORD.toCharArray())).logout();
        admin.refresh(false);

        // ensure that there is a rep:cache node
        assertNodeExists(newPath + "/rep:cache");

        // install user package
        JcrPackage pack = packMgr.upload(getStream("/test-packages/test_user_a.zip"), false);
        assertNotNull(pack);
        ImportOptions opts = getDefaultOptions();
        opts.setImportMode(mode);
        pack.install(opts);

        // check if user exists
        User userA = (User) mgr.getAuthorizable(ID_TEST_USER_A);
        assertNotNull("test-user-a must exist", userA);
    }

    private User installUserA(ImportMode mode, boolean usePkgPath, boolean expectPkgPath) throws RepositoryException, IOException, PackageException {
        UserManager mgr = ((JackrabbitSession) admin).getUserManager();
        assertNull("test-user-a must not exist", mgr.getAuthorizable(ID_TEST_USER_A));

        User u;
        if (usePkgPath) {
            u = mgr.createUser(ID_TEST_USER_A, ID_TEST_PASSWORD, new PrincipalImpl(ID_TEST_USER_A), PARENT_PATH_TEST_USER_A);
        } else {
            u = mgr.createUser(ID_TEST_USER_A, ID_TEST_PASSWORD);
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

        JcrPackage pack = packMgr.upload(getStream("/test-packages/test_user_a.zip"), false);
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

    private File createPackage(String group, String name, String... paths) throws RepositoryException, IOException, PackageException {
        ExportOptions opts = new ExportOptions();
        DefaultMetaInf inf = new DefaultMetaInf();
        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        for (String path : paths) {
            filter.add(new PathFilterSet(path));
        }

        inf.setFilter(filter);
        Properties props = new Properties();
        props.setProperty(VaultPackage.NAME_GROUP, group);
        props.setProperty(VaultPackage.NAME_NAME, name);
        inf.setProperties(props);

        opts.setMetaInf(inf);

        File tmpFile = File.createTempFile("vaulttest", ".zip");
        VaultPackage pkg = packMgr.assemble(admin, opts, tmpFile);

        pkg.close();

        return tmpFile;
    }

}
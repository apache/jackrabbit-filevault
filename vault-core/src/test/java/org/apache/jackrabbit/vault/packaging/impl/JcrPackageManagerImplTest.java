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

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import javax.jcr.AccessDeniedException;
import javax.jcr.GuestCredentials;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.security.AccessControlEntry;
import javax.jcr.security.AccessControlManager;

import org.apache.jackrabbit.api.security.JackrabbitAccessControlList;
import org.apache.jackrabbit.commons.jackrabbit.authorization.AccessControlUtils;
import org.apache.jackrabbit.vault.packaging.integration.IntegrationTestBase;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test case for {@link JcrPackageManagerImpl}
 */
public class JcrPackageManagerImplTest extends IntegrationTestBase {

    private List<String> visitedPaths = new LinkedList<String>();

    @Test
    public void testMkdDirWithUnauthorizedSession() throws Exception {
        Session session = mock(Session.class);
        when(session.nodeExists(anyString())).thenReturn(false);
        when(session.getWorkspace()).thenReturn(admin.getWorkspace());
        JcrPackageManagerImpl jcrPackageManager = new JcrPackageManagerImpl(session);
        String path = "/etc/packages";
        try {
            jcrPackageManager.mkdir(path, true);
            fail("this should have thrown RepositoryException as the session always tells nodes don't exist");
        }
        catch (RepositoryException e) {
            // everything it's ok
        }

    }

    @Test
    public void testMkDirWithAnonymousSession() throws Exception {
        Session session = repository.login(new GuestCredentials());
        JcrPackageManagerImpl jcrPackageManager = new JcrPackageManagerImpl(session);
        jcrPackageManager.mkdir("/something/that/is/not/going/to/be/found/anywhere/in/this/repository/even/if/searching/in/very/long/paths/like/this", false);
        jcrPackageManager.mkdir("/something/that/is/not/going/to/be/found/anywhere/in/this/repository/even/if/searching/in/very/long/paths/like/this", false);
        jcrPackageManager.mkdir("/something/that/is/not/going/to/be/found/anywhere/in/this/repository/even/if/searching/in/very/long/paths/like/this", false);
    }

    @Test
    public void mkdDirStressTest() throws Exception {
        JcrPackageManagerImpl jcrPackageManager = new JcrPackageManagerImpl(admin);
        String path = admin.getRootNode().getPath();
        while (path != null) {
            jcrPackageManager.mkdir(path, true);
            jcrPackageManager.mkdir(path, false);
            path = getNextPath(path);
        }
    }

    @Test
    public void testGetPackageRootNoCreate() throws Exception {
        JcrPackageManagerImpl jcrPackageManager = new JcrPackageManagerImpl(admin);

        assertNull(jcrPackageManager.getPackageRoot(true));
    }

    @Test
    public void testGetPackageRootWithCreate() throws Exception {
        JcrPackageManagerImpl jcrPackageManager = new JcrPackageManagerImpl(admin);

        Node packageNode = jcrPackageManager.getPackageRoot(false);
        assertEquals("/etc/packages", packageNode.getPath());
    }

    @Test
    public void testGetPackageRootTwice() throws Exception {
        JcrPackageManagerImpl jcrPackageManager = new JcrPackageManagerImpl(admin);
        Node packageNode = jcrPackageManager.getPackageRoot(false);
        assertSame(packageNode, jcrPackageManager.getPackageRoot());
    }

    @Test
    public void testGetPackageRootWithAdminPendingChanges() throws Exception {
        admin.getRootNode().addNode("testNode");

        JcrPackageManagerImpl jcrPackageManager = new JcrPackageManagerImpl(admin);
        try {
            jcrPackageManager.getPackageRoot(false);
            fail("transient modifications must fail the package root creation.");
        } catch (RepositoryException e) {
            // success
        }
    }

    @Test
    public void testGetPackageRootNoRootAccess() throws Exception {
        Node packageRoot = new JcrPackageManagerImpl(admin).getPackageRoot();

        // TODO: maybe rather change the setup of the test-base to not assume that everyone has full read-access
        AccessControlManager acMgr = admin.getAccessControlManager();
        JackrabbitAccessControlList acl = AccessControlUtils.getAccessControlList(acMgr, "/");
        acMgr.removePolicy(acl.getPath(), acl);

        AccessControlUtils.getAccessControlList(acMgr, "/etc/packages");
        AccessControlUtils.allow(packageRoot, org.apache.jackrabbit.oak.spi.security.principal.EveryonePrincipal.NAME, javax.jcr.security.Privilege.JCR_READ);

        admin.save();

        Session anonymous = repository.login(new GuestCredentials());
        try {
            assertFalse(anonymous.nodeExists("/"));
            assertFalse(anonymous.nodeExists("/etc"));
            assertTrue(anonymous.nodeExists("/etc/packages"));

            JcrPackageManagerImpl jcrPackageManager = new JcrPackageManagerImpl(anonymous);
            jcrPackageManager.getPackageRoot(false);
        } finally {
            anonymous.logout();
        }
    }

    @Test
    public void testGetPackageRootNoCreateAccess() throws Exception {
        // TODO: maybe rather change the setup of the test-base to not assume that everyone has full read-access
        AccessControlManager acMgr = admin.getAccessControlManager();
        JackrabbitAccessControlList acl = AccessControlUtils.getAccessControlList(acMgr, "/");
        for (AccessControlEntry ace : acl.getAccessControlEntries()) {
            acl.removeAccessControlEntry(ace);
        }
        acl.addEntry(AccessControlUtils.getEveryonePrincipal(admin),
                AccessControlUtils.privilegesFromNames(admin, javax.jcr.security.Privilege.JCR_READ),
                true,
                Collections.singletonMap("rep:glob", admin.getValueFactory().createValue("etc/*")));
        admin.save();

        Session anonymous = repository.login(new GuestCredentials());
        try {
            JcrPackageManagerImpl jcrPackageManager = new JcrPackageManagerImpl(anonymous);
            assertNull(jcrPackageManager.getPackageRoot(true));

            try {
                jcrPackageManager.getPackageRoot(false);
                fail();
            } catch (AccessDeniedException e) {
                // success
            }
        }  finally {
            anonymous.logout();
        }
    }

    private String getNextPath(String path) throws RepositoryException {
        Node currentNode = admin.getNode(path);
        if (currentNode.hasNodes()) {
            NodeIterator nodes = currentNode.getNodes();
            while (nodes.hasNext()) {
                Node node = nodes.nextNode();
                if ("jcr:system".equals(node.getName())) {
                    continue;
                }
                String nodePath = node.getPath();
                if (visitedPaths.contains(nodePath)) {
                    continue;
                } else {
                    visitedPaths.add(nodePath);
                }
                return nodePath;
            }
            return getParentPath(path);
        } else {
            return getParentPath(path);
        }
    }

    private String getParentPath(String path) throws RepositoryException {
        Node currentNode = admin.getNode(path);
        if (currentNode.getPath().equals(admin.getRootNode().getPath())) {
            return null;
        } else {
            Node parent = currentNode.getParent();
            if (parent != null) {
                return parent.getPath();
            } else {
                return null;
            }
        }
    }
}

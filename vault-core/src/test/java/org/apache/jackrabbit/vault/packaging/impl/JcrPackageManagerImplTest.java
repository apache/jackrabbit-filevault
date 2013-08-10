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

import java.util.LinkedList;
import java.util.List;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;

import org.apache.jackrabbit.vault.packaging.integration.IntegrationTestBase;
import org.junit.Test;

import static org.junit.Assert.fail;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Testcase for {@link JcrPackageManagerImpl}
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
            fail("this should've thrown RepositoryException as the session always tells nodes don't exist");
        }
        catch (RepositoryException e) {
            // everything it's ok
        }

    }

    @Test
    public void testMkDirWithAnonymousSession() throws Exception {
        Session session = repository.login(new SimpleCredentials("anonymous", "anonymous".toCharArray()));
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

    private String getNextPath(String path) throws RepositoryException {
        Node currentNode = admin.getNode(path);
        if (currentNode.hasNodes()) {
            NodeIterator nodes = currentNode.getNodes();
            while (nodes.hasNext()) {
                Node node = nodes.nextNode();
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

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

import static org.apache.jackrabbit.vault.fs.io.AccessControlHandling.IGNORE;
import static org.apache.jackrabbit.vault.fs.io.AccessControlHandling.MERGE;
import static org.apache.jackrabbit.vault.fs.io.AccessControlHandling.OVERWRITE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;

import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.jackrabbit.vault.fs.io.AccessControlHandling;
import org.apache.jackrabbit.vault.fs.io.ImportOptions;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.junit.BeforeClass;
import org.junit.Test;

public final class CugHandlingIT extends IntegrationTestBase {

    private static final String TEST_ROOT = "/testroot";

    /**
     * contains cugPolicy with rep:principalNames=[principal-1,principal-2]
     */
    private static final String CUG_PACKAGE_1 = "/test-packages/cug-test-1.zip";

    /**
     * contains cugPolicy with rep:principalNames=[principal-2,principal-3]
     */
    private static final String CUG_PACKAGE_2 = "/test-packages/cug-test-2.zip";

    
    @BeforeClass
    public static void initRepository() throws RepositoryException, IOException {
        assumeTrue(isOak());
        initRepository(useFileStore(), false, TEST_ROOT);
    }

    /**
     * When cugHandling is set to IGNORE, rep:cugPolicy node should not be created.
     */
    @Test
    public void testCugIgnore() throws Exception {
       try (VaultPackage vp1 = extractVaultPackage(CUG_PACKAGE_1, IGNORE)) {
           Node testRoot = admin.getNode(TEST_ROOT);
           assertNodeExists(testRoot, "node_with_cug");
           Node nodeWithCug = testRoot.getNode("node_with_cug");
           assertProperty(nodeWithCug, "jcr:mixinTypes", asSet("rep:CugMixin"));
           assertNodeMissing(nodeWithCug, "rep:cugPolicy");
       }
    }

    /**
     * When cugHandling is set to MERGE, existing principals should be combined with installed principals
     */
    @Test
    public void testCugMerge() throws Exception {
        try (VaultPackage vp2 = extractVaultPackage(CUG_PACKAGE_2, OVERWRITE);
                VaultPackage vp1 = extractVaultPackage(CUG_PACKAGE_1, MERGE)) {
            Node testRoot = admin.getNode(TEST_ROOT);
            assertNodeExists(testRoot, "node_with_cug");
            Node nodeWithCug = testRoot.getNode("node_with_cug");
            assertProperty(nodeWithCug, "jcr:mixinTypes", asSet("rep:CugMixin"));
            assertNodeExists(nodeWithCug, "rep:cugPolicy");
            Node cugNode = nodeWithCug.getNode("rep:cugPolicy");
            assertProperty(cugNode, "jcr:primaryType", "rep:CugPolicy");
            assertProperty(cugNode, "rep:principalNames", asSet("principal-1", "principal-2", "principal-3"));
        }
    }

    /**
     * When cugHandling is set to MERGE_PRESERVE, existing principals should be combined with installed principals
     * same behavior as with MERGE
     */
    @Test
    public void testCugMergePreserve() throws Exception {
        try (VaultPackage vp2 = extractVaultPackage(CUG_PACKAGE_2, OVERWRITE);
                VaultPackage vp1 = extractVaultPackage(CUG_PACKAGE_1, AccessControlHandling.MERGE_PRESERVE)) {
            Node testRoot = admin.getNode(TEST_ROOT);
            assertNodeExists(testRoot, "node_with_cug");
            Node nodeWithCug = testRoot.getNode("node_with_cug");
            assertProperty(nodeWithCug, "jcr:mixinTypes", asSet("rep:CugMixin"));
            assertNodeExists(nodeWithCug, "rep:cugPolicy");
            Node cugNode = nodeWithCug.getNode("rep:cugPolicy");
            assertProperty(cugNode, "jcr:primaryType", "rep:CugPolicy");
            assertProperty(cugNode, "rep:principalNames", asSet("principal-1", "principal-2", "principal-3"));
        }
    }

    /**
     * When cugHandling is set to OVERWRITE installed principals should completely owerwrite existing ones.
     */
    @Test
    public void testCugOverwrite() throws Exception {
        try (VaultPackage vp1 = extractVaultPackage(CUG_PACKAGE_1, OVERWRITE);
                VaultPackage vp2 = extractVaultPackage(CUG_PACKAGE_2, OVERWRITE)) {
            Node testRoot = admin.getNode(TEST_ROOT);
            assertNodeExists(testRoot, "node_with_cug");
            Node nodeWithCug = testRoot.getNode("node_with_cug");
            assertProperty(nodeWithCug, "jcr:mixinTypes", asSet("rep:CugMixin"));
            assertNodeExists(nodeWithCug, "rep:cugPolicy");
            Node cugNode = nodeWithCug.getNode("rep:cugPolicy");
            assertProperty(cugNode, "jcr:primaryType", "rep:CugPolicy");
            assertProperty(cugNode, "rep:principalNames", asSet("principal-2", "principal-3"));
        }
    }

    /**
     * When cugHandling is not set (or set to <code>null</code>), cugHandling should be governed by aclHandling
     */
    @Test
    public void testCugSameAsAclByDefault() throws Exception {
        ImportOptions opts = new ImportOptions();
        opts.setAccessControlHandling(MERGE);
        try (VaultPackage vp1 = extractVaultPackage(CUG_PACKAGE_1, OVERWRITE);
                VaultPackage vp2 = extractVaultPackage(CUG_PACKAGE_2, opts)) {
            Node testRoot = admin.getNode(TEST_ROOT);
            assertNodeExists(testRoot, "node_with_cug");
            Node nodeWithCug = testRoot.getNode("node_with_cug");
            assertProperty(nodeWithCug, "jcr:mixinTypes", asSet("rep:CugMixin"));
            assertNodeExists(nodeWithCug, "rep:cugPolicy");
            Node cugNode = nodeWithCug.getNode("rep:cugPolicy");
            assertProperty(cugNode, "jcr:primaryType", "rep:CugPolicy");
            assertProperty(cugNode, "rep:principalNames", asSet("principal-1", "principal-2", "principal-3"));
        }
    }

    //*********************************************
    // Custom assertions
    //*********************************************

    private static void assertHasProperty(Node node, String propName) throws RepositoryException {
        if (!node.hasProperty(propName)) {
            fail("Node [" + node.getPath() + "] doesn't have property [" + propName + "]");
        }
    }

    private static void assertProperty(Node node, String propName, String value) throws RepositoryException {
        assertHasProperty(node, propName);
        assertEquals(node.getPath() + "/" + propName + " should contain " + value, value, node.getProperty(propName).getString());
    }

    public static void assertProperty(Node node, String name, Set<String> values) throws RepositoryException {
        Set<String> strings = new HashSet<String>();
        for (Value v: node.getProperty(name).getValues()) {
            strings.add(v.getString());
        }
        assertEquals(node.getPath() + "/" + name + " should contain " + values, values, strings);
    }

    public static void assertNodeExists(Node parent, String relPath) throws RepositoryException {
        assertTrue(parent.getPath() + "/" + relPath + " should exist", parent.hasNode(relPath));
    }

    public static void assertNodeMissing(Node parent, String relPath) throws RepositoryException {
        assertFalse(parent.getPath() + "/" + relPath + " should not exist", parent.hasNode(relPath));
    }

    private static Set<String> asSet(String ... values) {
        return new TreeSet<>(Arrays.asList(values));
    }

    private static void createUsers(Session session) throws RepositoryException {
        UserManager userManager = ((JackrabbitSession) session).getUserManager();
        userManager.createUser("principal-1", "pwd-1");
        userManager.createUser("principal-2", "pwd-2");
        userManager.createUser("principal-3", "pwd-3");
    }


}

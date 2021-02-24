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

import org.apache.jackrabbit.api.JackrabbitRepository;
import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.jackrabbit.oak.jcr.Jcr;
import org.apache.jackrabbit.oak.query.QueryEngineSettings;
import org.apache.jackrabbit.oak.security.internal.SecurityProviderBuilder;
import org.apache.jackrabbit.oak.security.internal.SecurityProviderHelper;
import org.apache.jackrabbit.oak.spi.security.ConfigurationParameters;
import org.apache.jackrabbit.oak.spi.security.SecurityProvider;
import org.apache.jackrabbit.oak.spi.security.authorization.AuthorizationConfiguration;
import org.apache.jackrabbit.oak.spi.security.authorization.cug.impl.CugConfiguration;
import org.apache.jackrabbit.oak.spi.security.user.UserConstants;
import org.apache.jackrabbit.vault.fs.io.AccessControlHandling;
import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.fs.io.FileArchive;
import org.apache.jackrabbit.vault.fs.io.ImportOptions;
import org.apache.jackrabbit.vault.fs.io.ZipArchive;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.apache.jackrabbit.vault.packaging.impl.ZipVaultPackage;

import javax.jcr.Node;
import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;
import javax.jcr.Value;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.apache.jackrabbit.vault.fs.io.AccessControlHandling.IGNORE;
import static org.apache.jackrabbit.vault.fs.io.AccessControlHandling.MERGE;
import static org.apache.jackrabbit.vault.fs.io.AccessControlHandling.OVERWRITE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** This is more an IT but isn't derived from IntegrationTestBase, therefore doesn't need parametrization */
public final class CugHandlingTest {

    private static final String TEST_ROOT = "/testroot";

    /**
     * contains cugPolicy with rep:principalNames=[principal-1,principal-2]
     */
    private static final String CUG_PACKAGE_1 = "/test-packages/cug-test-1.zip";

    /**
     * contains cugPolicy with rep:principalNames=[principal-2,principal-3]
     */
    private static final String CUG_PACKAGE_2 = "/test-packages/cug-test-2.zip";

    private Repository repository;

    private Session adminSession;

    /**
     * When cugHandling is set to IGNORE, rep:cugPolicy node should not be created.
     */
    @Test
    public void testCugIgnore() throws Exception {
       extractVaultPackage(CUG_PACKAGE_1, IGNORE);
       Node testRoot = adminSession.getNode(TEST_ROOT);
       assertNodeExists(testRoot, "node_with_cug");
       Node nodeWithCug = testRoot.getNode("node_with_cug");
       assertProperty(nodeWithCug, "jcr:mixinTypes", asSet("rep:CugMixin"));
       assertNodeMissing(nodeWithCug, "rep:cugPolicy");
    }

    /**
     * When cugHandling is set to MERGE, existing principals should be combined with installed principals
     */
    @Test
    public void testCugMerge() throws Exception {
        extractVaultPackage(CUG_PACKAGE_2, OVERWRITE);
        extractVaultPackage(CUG_PACKAGE_1, MERGE);
        Node testRoot = adminSession.getNode(TEST_ROOT);
        assertNodeExists(testRoot, "node_with_cug");
        Node nodeWithCug = testRoot.getNode("node_with_cug");
        assertProperty(nodeWithCug, "jcr:mixinTypes", asSet("rep:CugMixin"));
        assertNodeExists(nodeWithCug, "rep:cugPolicy");
        Node cugNode = nodeWithCug.getNode("rep:cugPolicy");
        assertProperty(cugNode, "jcr:primaryType", "rep:CugPolicy");
        assertProperty(cugNode,"rep:principalNames", asSet("principal-1", "principal-2", "principal-3"));
    }

    /**
     * When cugHandling is set to MERGE_PRESERVE, existing principals should be combined with installed principals
     * same behavior as with MERGE
     */
    @Test
    public void testCugMergePreserve() throws Exception {
        extractVaultPackage(CUG_PACKAGE_2, OVERWRITE);
        extractVaultPackage(CUG_PACKAGE_1, AccessControlHandling.MERGE_PRESERVE);
        Node testRoot = adminSession.getNode(TEST_ROOT);
        assertNodeExists(testRoot, "node_with_cug");
        Node nodeWithCug = testRoot.getNode("node_with_cug");
        assertProperty(nodeWithCug, "jcr:mixinTypes", asSet("rep:CugMixin"));
        assertNodeExists(nodeWithCug, "rep:cugPolicy");
        Node cugNode = nodeWithCug.getNode("rep:cugPolicy");
        assertProperty(cugNode, "jcr:primaryType", "rep:CugPolicy");
        assertProperty(cugNode,"rep:principalNames", asSet("principal-1", "principal-2", "principal-3"));
    }

    /**
     * When cugHandling is set to OVERWRITE installed principals should completely owerwrite existing ones.
     */
    @Test
    public void testCugOverwrite() throws Exception {
        extractVaultPackage(CUG_PACKAGE_1, OVERWRITE);
        extractVaultPackage(CUG_PACKAGE_2, OVERWRITE);
        Node testRoot = adminSession.getNode(TEST_ROOT);
        assertNodeExists(testRoot, "node_with_cug");
        Node nodeWithCug = testRoot.getNode("node_with_cug");
        assertProperty(nodeWithCug, "jcr:mixinTypes", asSet("rep:CugMixin"));
        assertNodeExists(nodeWithCug, "rep:cugPolicy");
        Node cugNode = nodeWithCug.getNode("rep:cugPolicy");
        assertProperty(cugNode, "jcr:primaryType", "rep:CugPolicy");
        assertProperty(cugNode,"rep:principalNames", asSet("principal-2", "principal-3"));
    }

    /**
     * When cugHandling is not set (or set to <code>null</code>), cugHandling should be governed by aclHandling
     */
    @Test
    public void testCugSameAsAclByDefault() throws Exception {
        extractVaultPackage(CUG_PACKAGE_1, OVERWRITE);
        ImportOptions opts = new ImportOptions();
        opts.setAccessControlHandling(MERGE);
        extractVaultPackage(CUG_PACKAGE_2, opts);
        Node testRoot = adminSession.getNode(TEST_ROOT);
        assertNodeExists(testRoot, "node_with_cug");
        Node nodeWithCug = testRoot.getNode("node_with_cug");
        assertProperty(nodeWithCug, "jcr:mixinTypes", asSet("rep:CugMixin"));
        assertNodeExists(nodeWithCug, "rep:cugPolicy");
        Node cugNode = nodeWithCug.getNode("rep:cugPolicy");
        assertProperty(cugNode, "jcr:primaryType", "rep:CugPolicy");
        assertProperty(cugNode,"rep:principalNames", asSet("principal-1", "principal-2", "principal-3"));
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
        Set<String> strings = new HashSet();
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
    
    //*********************************************
    // Helpers
    //*********************************************

    private Archive getFileArchive(String name) {
        final URL packageURL = getClass().getResource(name);
        final String filename = packageURL.getFile();
        final File file = new File(filename);
        if (file.isDirectory()) {
            return new FileArchive(file);
        } else {
            return new ZipArchive(file);
        }
    }

    private void extractVaultPackage(String name, AccessControlHandling cugHandling) throws PackageException, RepositoryException, IOException {
        ImportOptions opts = new ImportOptions();
        opts.setCugHandling(cugHandling);
        extractVaultPackage(name, opts);
    }

    private void extractVaultPackage(String name, ImportOptions opts) throws PackageException, RepositoryException, IOException {
        VaultPackage pack = new ZipVaultPackage(getFileArchive(name), true);
        pack.extract(adminSession, opts);
    }

    private static Set<String> asSet(String ... values) {
        return new TreeSet<>(Arrays.asList(values));
    }

    //*********************************************
    // setUp/tearDown
    //*********************************************

    @Before
    public void setUp() throws Exception {
        repository = createRepository();
        adminSession = repository.login(new SimpleCredentials(UserConstants.DEFAULT_ADMIN_ID, UserConstants.DEFAULT_ADMIN_ID.toCharArray()));
        createUsers(adminSession);
        adminSession.save();
    }

    @After
    public void tearDown() throws Exception {
        try {
            adminSession.refresh(false);
            if (adminSession.nodeExists(TEST_ROOT)) {
                adminSession.getNode(TEST_ROOT).remove();
            }
            adminSession.save();
        } finally {
            adminSession.logout();
            if (repository instanceof JackrabbitRepository) {
                ((JackrabbitRepository) repository).shutdown();
            }
            repository = null;
        }
    }

    private static SecurityProvider createSecirityProvider() {
        ConfigurationParameters params = ConfigurationParameters.of(
                "cugSupportedPaths", TEST_ROOT,
                "cugEnabled", true
        );
        CugConfiguration cugConfiguration = new CugConfiguration();
        cugConfiguration.setParameters(params);
        SecurityProvider result = SecurityProviderBuilder.newBuilder()
                                                         .with(ConfigurationParameters.of(params))
                                                         .build();
        SecurityProviderHelper.updateConfig(result, cugConfiguration, AuthorizationConfiguration.class);
        return result;
    }

    private static void createUsers(Session session) throws RepositoryException {
        UserManager userManager = ((JackrabbitSession) session).getUserManager();
        userManager.createUser("principal-1", "pwd-1");
        userManager.createUser("principal-2", "pwd-2");
        userManager.createUser("principal-3", "pwd-3");
    }

    private static Repository createRepository() {
        SecurityProvider securityProvider = createSecirityProvider();
        QueryEngineSettings queryEngineSettings = new QueryEngineSettings();
        queryEngineSettings.setFailTraversal(true);
        Jcr jcr = new Jcr();
        jcr.with(securityProvider);
        jcr.with(queryEngineSettings);
        return jcr.createRepository();
    }

}

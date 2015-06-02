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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;
import javax.jcr.Value;
import javax.jcr.security.AccessControlEntry;
import javax.jcr.security.AccessControlPolicy;
import javax.jcr.security.AccessControlPolicyIterator;
import javax.jcr.security.Privilege;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.jackrabbit.api.security.JackrabbitAccessControlEntry;
import org.apache.jackrabbit.api.security.JackrabbitAccessControlList;
import org.apache.jackrabbit.commons.jackrabbit.authorization.AccessControlUtils;
import org.apache.jackrabbit.core.RepositoryImpl;
import org.apache.jackrabbit.core.config.RepositoryConfig;
import org.apache.jackrabbit.core.security.principal.EveryonePrincipal;
import org.apache.jackrabbit.core.security.principal.PrincipalImpl;
import org.apache.jackrabbit.oak.jcr.Jcr;
import org.apache.jackrabbit.oak.security.SecurityProviderImpl;
import org.apache.jackrabbit.oak.security.user.RandomAuthorizableNodeName;
import org.apache.jackrabbit.oak.spi.security.ConfigurationParameters;
import org.apache.jackrabbit.oak.spi.security.authorization.AuthorizationConfiguration;
import org.apache.jackrabbit.oak.spi.security.privilege.PrivilegeConstants;
import org.apache.jackrabbit.oak.spi.security.user.AuthorizableNodeName;
import org.apache.jackrabbit.oak.spi.security.user.UserConfiguration;
import org.apache.jackrabbit.oak.spi.security.user.UserConstants;
import org.apache.jackrabbit.oak.spi.security.user.action.AccessControlAction;
import org.apache.jackrabbit.oak.spi.xml.ImportBehavior;
import org.apache.jackrabbit.oak.spi.xml.ProtectedItemImporter;
import org.apache.jackrabbit.vault.fs.api.ProgressTrackerListener;
import org.apache.jackrabbit.vault.fs.io.ImportOptions;
import org.apache.jackrabbit.vault.packaging.JcrPackageManager;
import org.apache.jackrabbit.vault.packaging.impl.JcrPackageManagerImpl;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * <code>IntegrationTestBase</code>...
 */
public class IntegrationTestBase  {

    /**
     * default logger
     */
    private static final Logger log = LoggerFactory.getLogger(IntegrationTestBase.class);

    private static final String REPO_HOME = "target/repository";

    protected static Repository repository;

    protected Session admin;

    protected JcrPackageManager packMgr;

    @BeforeClass
    public static void initRepository() throws RepositoryException {
        if (isOak()) {
            Properties userProps = new Properties();
            AuthorizableNodeName nameGenerator = new RandomAuthorizableNodeName();

            userProps.put(UserConstants.PARAM_USER_PATH, "/home/users");
            userProps.put(UserConstants.PARAM_GROUP_PATH, "/home/groups");
            userProps.put(AccessControlAction.USER_PRIVILEGE_NAMES, new String[] {PrivilegeConstants.JCR_ALL});
            userProps.put(AccessControlAction.GROUP_PRIVILEGE_NAMES, new String[] {PrivilegeConstants.JCR_READ});
            userProps.put(ProtectedItemImporter.PARAM_IMPORT_BEHAVIOR, ImportBehavior.NAME_BESTEFFORT);
            userProps.put(UserConstants.PARAM_AUTHORIZABLE_NODE_NAME, nameGenerator);
            Properties authzProps = new Properties();
            authzProps.put(ProtectedItemImporter.PARAM_IMPORT_BEHAVIOR, ImportBehavior.NAME_BESTEFFORT);
            Properties securityProps = new Properties();
            securityProps.put(UserConfiguration.NAME, ConfigurationParameters.of(userProps));
            securityProps.put(AuthorizationConfiguration.NAME, ConfigurationParameters.of(authzProps));
            repository = new Jcr()
                    .with(new SecurityProviderImpl(ConfigurationParameters.of(securityProps)))
                    .createRepository();

            // setup default read ACL for everyone
            Session admin = repository.login(new SimpleCredentials("admin", "admin".toCharArray()));
            AccessControlUtils.addAccessControlEntry(admin, "/", EveryonePrincipal.getInstance(), new String[]{"jcr:read"}, true);
            admin.save();
            admin.logout();
        } else {
            InputStream in = IntegrationTestBase.class.getResourceAsStream("repository.xml");
            RepositoryConfig cfg = RepositoryConfig.create(in, REPO_HOME);
            repository = RepositoryImpl.create(cfg);
        }
        log.info("repository created: {} {}",
                repository.getDescriptor(Repository.REP_NAME_DESC),
                repository.getDescriptor(Repository.REP_VERSION_DESC));
    }

    @AfterClass
    public static void shutdownRepository() {
        if (repository instanceof RepositoryImpl) {
            ((RepositoryImpl) repository).shutdown();
        }
        repository = null;
    }

    @Before
    public void setUp() throws Exception {
        admin = repository.login(new SimpleCredentials("admin", "admin".toCharArray()));

        // ensure not packages or tmp
        clean("/etc");
        clean("/tmp");
        clean("/testroot");

        packMgr = new JcrPackageManagerImpl(admin);
    }

    public static boolean isOak() {
        return Boolean.getBoolean("oak");
    }

    public void clean(String path) {
        try {
            admin.getNode(path).remove();
            admin.save();
        } catch (RepositoryException e) {
            // ignore
        }
    }
    @After
    public void tearDown() throws Exception {
        packMgr = null;
        if (admin != null) {
            admin.logout();
            admin = null;
        }
    }

    public InputStream getStream(String name) {
        InputStream in;
        if (name.startsWith("/")) {
           in = getClass().getClassLoader().getResourceAsStream(name);
        } else {
            in = getClass().getResourceAsStream(name);
        }
        return in;
    }

    public File getTempFile(String name) throws IOException {
        InputStream in = getStream(name);

        File tmpFile = File.createTempFile("vaultpack", ".zip");
        FileOutputStream out = FileUtils.openOutputStream(tmpFile);
        IOUtils.copy(in, out);
        in.close();
        out.close();
        return tmpFile;
    }


    public ImportOptions getDefaultOptions() {
        ImportOptions opts = new ImportOptions();
        opts.setListener(new ProgressTrackerListener() {
            public void onMessage(Mode mode, String action, String path) {
                log.info("{} {}", action, path);
            }

            public void onError(Mode mode, String path, Exception e) {
                log.info("E {} {}", path, e.toString());
            }
        });
        return opts;
    }

    public void assertNodeExists(String path) throws RepositoryException {
        assertTrue(path + " should exist", admin.nodeExists(path));
    }

    public void assertNodeMissing(String path) throws RepositoryException {
        assertFalse(path + " should not exist", admin.nodeExists(path));
    }

    public void assertProperty(String path, String value) throws RepositoryException {
        assertEquals(path + " should contain " + value, value, admin.getProperty(path).getString());
    }

    public void assertProperty(String path, String[] values) throws RepositoryException {
        ArrayList<String> strings = new ArrayList<String>();
        for (Value v: admin.getProperty(path).getValues()) {
            strings.add(v.getString());
        }
        assertArrayEquals(path + " should contain " + values, values, strings.toArray(new String[strings.size()]));
    }

    public void assertPropertyMissing(String path) throws RepositoryException {
        assertFalse(path + " should not exist", admin.propertyExists(path));
    }

    public void createNodes(Node parent, int maxDepth, int nodesPerFolder) throws RepositoryException {
        for (int i=0; i<nodesPerFolder; i++) {
            Node n = parent.addNode("n" + i, "nt:folder");
            if (maxDepth > 0) {
                createNodes(n, maxDepth - 1, nodesPerFolder);
            }
        }
    }

    public int countNodes(Node parent) throws RepositoryException {
        int total = 1;
        NodeIterator iter = parent.getNodes();
        while (iter.hasNext()) {
            total += countNodes(iter.nextNode());
        }
        return total;
    }

    public void assertPermissionMissing(String path, boolean allow, String[] privs, String name, String globRest)
            throws RepositoryException {
        Map<String, String[]> restrictions = new HashMap<String, String[]>();
        if (globRest != null) {
            restrictions.put("rep:glob", new String[]{globRest});
        }
        if (hasPermission(path, allow, privs, name, restrictions) >= 0) {
            fail("Expected permission should not exist on path " + path + ". permissions: " + dumpPermissions(path));
        }
    }

    public void assertPermission(String path, boolean allow, String[] privs, String name, String globRest)
            throws RepositoryException {
        Map<String, String[]> restrictions = new HashMap<String, String[]>();
        if (globRest != null) {
            restrictions.put("rep:glob", new String[]{globRest});
        }
        if (hasPermission(path, allow, privs, name, restrictions) < 0) {
            fail("Expected permission missing on path " + path + ". permissions: " + dumpPermissions(path));
        }
    }

    public String dumpPermissions(String path) throws RepositoryException {
        StringBuilder ret = new StringBuilder();
        AccessControlPolicy[] ap = admin.getAccessControlManager().getPolicies(path);
        for (AccessControlPolicy p: ap) {
            if (p instanceof JackrabbitAccessControlList) {
                JackrabbitAccessControlList acl = (JackrabbitAccessControlList) p;
                for (AccessControlEntry ac: acl.getAccessControlEntries()) {
                    if (ac instanceof JackrabbitAccessControlEntry) {
                        JackrabbitAccessControlEntry ace = (JackrabbitAccessControlEntry) ac;
                        ret.append(ace.isAllow() ? "\n- allow " : "deny ");
                        ret.append(ace.getPrincipal().getName());
                        char delim = '[';
                        for (Privilege priv: ace.getPrivileges()) {
                            ret.append(delim).append(priv.getName());
                            delim=',';
                        }
                        ret.append(']');
                        for (String restName: ace.getRestrictionNames()) {
                            Value[] values;
                            if ("rep:glob".equals(restName)) {
                                values = new Value[]{ace.getRestriction(restName)};
                            } else {
                                values = ace.getRestrictions(restName);
                            }
                            for (Value value : values) {
                                ret.append(" rest=").append(value.getString());
                            }
                        }
                    }
                }
            }
        }
        return ret.toString();
    }

    public int hasPermission(String path, boolean allow, String[] privs, String name, Map<String, String[]> restrictions)
            throws RepositoryException {
        AccessControlPolicy[] ap = admin.getAccessControlManager().getPolicies(path);
        int idx = 0;
        for (AccessControlPolicy p: ap) {
            if (p instanceof JackrabbitAccessControlList) {
                JackrabbitAccessControlList acl = (JackrabbitAccessControlList) p;
                for (AccessControlEntry ac: acl.getAccessControlEntries()) {
                    if (ac instanceof JackrabbitAccessControlEntry) {
                        idx++;
                        JackrabbitAccessControlEntry ace = (JackrabbitAccessControlEntry) ac;
                        if (ace.isAllow() != allow) {
                            continue;
                        }
                        if (!ace.getPrincipal().getName().equals(name)) {
                            continue;
                        }
                        Set<String> expectedPrivs = new HashSet<String>(Arrays.asList(privs));
                        for (Privilege priv: ace.getPrivileges()) {
                            if (!expectedPrivs.remove(priv.getName())) {
                                expectedPrivs.add("dummy");
                                break;
                            }
                        }
                        if (!expectedPrivs.isEmpty()) {
                            continue;
                        }
                        Map<String, String[]> rests = new HashMap<String, String[]>(restrictions);
                        boolean restrictionExpected = true;
                        for (String restName: ace.getRestrictionNames()) {
                            String[] expected = rests.remove(restName);
                            if (expected == null) {
                                continue;
                            }
                            Value[] values;
                            if ("rep:glob".equals(restName)) {
                                values = new Value[]{ace.getRestriction(restName)};
                            } else {
                                values = ace.getRestrictions(restName);
                            }
                            String[] actual = new String[values.length];
                            for (int i=0; i<actual.length; i++) {
                                actual[i] = values[i].getString();
                            }
                            Arrays.sort(expected);
                            Arrays.sort(actual);
                            if (!Arrays.equals(expected, actual)) {
                                restrictionExpected = false;
                                break;
                            }
                        }
                        if (!restrictionExpected || !rests.isEmpty()) {
                            continue;
                        }
                        return idx-1;
                    }
                }
            }
        }
        return -1;
    }

    public void removeRepoACL() throws RepositoryException {
        AccessControlPolicy[] ap = admin.getAccessControlManager().getPolicies(null);
        for (AccessControlPolicy p: ap) {
            if (p instanceof JackrabbitAccessControlList) {
                JackrabbitAccessControlList acl = (JackrabbitAccessControlList) p;
                for (AccessControlEntry ac: acl.getAccessControlEntries()) {
                    if (ac instanceof JackrabbitAccessControlEntry) {
                        acl.removeAccessControlEntry(ac);
                    }
                }
            }
        }
        admin.save();
    }

    public void addACL(String path, boolean allow, String[] privs, String principal) throws RepositoryException {
        JackrabbitAccessControlList acl = null;
        for (AccessControlPolicy p: admin.getAccessControlManager().getPolicies(path)) {
            if (p instanceof JackrabbitAccessControlList) {
                acl = (JackrabbitAccessControlList) p;
                break;
            }
        }
        if (acl == null) {
            AccessControlPolicyIterator iter =  admin.getAccessControlManager().getApplicablePolicies(path);
            while (iter.hasNext()) {
                AccessControlPolicy p = iter.nextAccessControlPolicy();
                if (p instanceof JackrabbitAccessControlList) {
                    acl = (JackrabbitAccessControlList) p;
                    break;
                }
            }
        }
        assertNotNull(acl);

        Privilege[] ps = new Privilege[privs.length];
        for (int i=0; i<privs.length; i++) {
            ps[i] = admin.getAccessControlManager().privilegeFromName(privs[i]);
        }
        acl.addEntry(new PrincipalImpl(principal), ps, allow);
        admin.getAccessControlManager().setPolicy(path, acl);
        admin.save();
    }
}
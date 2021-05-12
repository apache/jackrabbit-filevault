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
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.jar.JarFile;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.PathNotFoundException;
import javax.jcr.Property;
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
import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.JackrabbitAccessControlEntry;
import org.apache.jackrabbit.api.security.JackrabbitAccessControlList;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.jackrabbit.commons.jackrabbit.authorization.AccessControlUtils;
import org.apache.jackrabbit.core.RepositoryImpl;
import org.apache.jackrabbit.core.config.RepositoryConfig;
import org.apache.jackrabbit.core.data.FileDataStore;
import org.apache.jackrabbit.core.security.principal.EveryonePrincipal;
import org.apache.jackrabbit.core.security.principal.PrincipalImpl;
import org.apache.jackrabbit.oak.jcr.Jcr;
import org.apache.jackrabbit.oak.plugins.blob.datastore.DataStoreBlobStore;
import org.apache.jackrabbit.oak.plugins.tree.impl.RootProviderService;
import org.apache.jackrabbit.oak.plugins.tree.impl.TreeProviderService;
import org.apache.jackrabbit.oak.security.internal.SecurityProviderBuilder;
import org.apache.jackrabbit.oak.security.user.RandomAuthorizableNodeName;
import org.apache.jackrabbit.oak.segment.SegmentNodeStore;
import org.apache.jackrabbit.oak.segment.SegmentNodeStoreBuilders;
import org.apache.jackrabbit.oak.segment.file.FileStore;
import org.apache.jackrabbit.oak.segment.file.FileStoreBuilder;
import org.apache.jackrabbit.oak.segment.file.InvalidFileStoreVersionException;
import org.apache.jackrabbit.oak.spi.blob.BlobStore;
import org.apache.jackrabbit.oak.spi.security.ConfigurationParameters;
import org.apache.jackrabbit.oak.spi.security.SecurityProvider;
import org.apache.jackrabbit.oak.spi.security.authorization.AuthorizationConfiguration;
import org.apache.jackrabbit.oak.spi.security.privilege.PrivilegeConstants;
import org.apache.jackrabbit.oak.spi.security.user.AuthorizableNodeName;
import org.apache.jackrabbit.oak.spi.security.user.UserConfiguration;
import org.apache.jackrabbit.oak.spi.security.user.UserConstants;
import org.apache.jackrabbit.oak.spi.security.user.action.AccessControlAction;
import org.apache.jackrabbit.oak.spi.xml.ImportBehavior;
import org.apache.jackrabbit.oak.spi.xml.ProtectedItemImporter;
import org.apache.jackrabbit.vault.fs.api.ProgressTrackerListener;
import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.fs.io.FileArchive;
import org.apache.jackrabbit.vault.fs.io.ImportOptions;
import org.apache.jackrabbit.vault.fs.io.ZipArchive;
import org.apache.jackrabbit.vault.fs.io.ZipStreamArchive;
import org.apache.jackrabbit.vault.packaging.JcrPackage;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.apache.jackrabbit.vault.packaging.events.impl.PackageEventDispatcherImpl;
import org.apache.jackrabbit.vault.packaging.impl.ActivityLog;
import org.apache.jackrabbit.vault.packaging.impl.JcrPackageManagerImpl;
import org.apache.jackrabbit.vault.packaging.impl.ZipVaultPackage;
import org.apache.jackrabbit.vault.packaging.registry.impl.JcrPackageRegistry;
import org.apache.jackrabbit.util.Text;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * {@code IntegrationTestBase}...
 */
public class IntegrationTestBase  {

    /**
     * default logger
     */
    private static final Logger log = LoggerFactory.getLogger(IntegrationTestBase.class);

    private static final String REPO_HOME = "target/repository";
    private static final File DIR_REPO_HOME = new File(REPO_HOME + System.getProperty("repoIndex", "0"));
    private static final File DIR_DATA_STORE = new File(DIR_REPO_HOME, "datastore");
    private static final File DIR_BLOB_STORE = new File(DIR_REPO_HOME, "blobstore");

    public static final PackageId TMP_PACKAGE_ID = new PackageId("my_packages", "tmp", "");

    public static final PackageId TMP_SNAPSHOT_PACKAGE_ID = PackageId.fromString("my_packages/.snapshot:tmp");

    public static final PackageId TEST_PACKAGE_A_10_ID = new PackageId("my_packages", "test_a", "1.0");

    public static final PackageId TEST_PACKAGE_B_10_ID = new PackageId("my_packages", "test_b", "1.0");

    public static final PackageId TEST_PACKAGE_C_10_ID = new PackageId("my_packages", "test_c", "1.0");

    public static final PackageId PACKAGE_ID_SUB_A = PackageId.fromString("my_packages:sub_a");

    public static final PackageId PACKAGE_ID_SUB_B = PackageId.fromString("my_packages:sub_b");

    /**
     * Test package A-1.0. Depends on B and C-1.X
     */
    public static String TEST_PACKAGE_A_10 = "/test-packages/test_a-1.0.zip";

    /**
     * Test package B-1.0. Depends on C
     */
    public static String TEST_PACKAGE_B_10 = "/test-packages/test_b-1.0.zip";

    /**
     * Test package C-1.0
     */
    public static String TEST_PACKAGE_C_10 = "/test-packages/test_c-1.0.zip";


    @ClassRule
    public static TemporaryFolder tempFolder = new TemporaryFolder();

    private static FileStore fileStore = null;

    protected static Repository repository;

    protected Session admin;

    protected JcrPackageManagerImpl packMgr;

    protected Set<String> preTestAuthorizables;

    @BeforeClass
    public static void initRepository() throws RepositoryException, IOException, InvalidFileStoreVersionException {
        if (isOak()) {
            Jcr jcr;
            if (useFileStore()) {
                BlobStore blobStore = createBlobStore();
                DIR_DATA_STORE.mkdirs();
                fileStore = FileStoreBuilder.fileStoreBuilder(DIR_DATA_STORE)
                        .withBlobStore(blobStore)
                        .build();
                SegmentNodeStore nodeStore = SegmentNodeStoreBuilders.builder(fileStore).build();
                jcr = new Jcr(nodeStore);
            } else {
                jcr = new Jcr();
            }

            repository = jcr
                    .with(createSecurityProvider())
                    .withAtomicCounter()
                    .createRepository();

            // setup default read ACL for everyone
            Session admin = repository.login(new SimpleCredentials("admin", "admin".toCharArray()));
            AccessControlUtils.addAccessControlEntry(admin, "/", EveryonePrincipal.getInstance(), new String[]{"jcr:read"}, true);
            admin.save();
            admin.logout();
        } else {
            try (InputStream in = IntegrationTestBase.class.getResourceAsStream("repository.xml")) {
                RepositoryConfig cfg = RepositoryConfig.create(in, DIR_REPO_HOME.getPath());
                repository = RepositoryImpl.create(cfg);
            }
        }
        log.info("repository created: {} {}",
                repository.getDescriptor(Repository.REP_NAME_DESC),
                repository.getDescriptor(Repository.REP_VERSION_DESC));
    }

    public static ConfigurationParameters getSecurityConfigurationParameters() {
        Properties userProps = new Properties();
        AuthorizableNodeName nameGenerator = new RandomAuthorizableNodeName();

        userProps.put(UserConstants.PARAM_USER_PATH, "/home/users");
        userProps.put(UserConstants.PARAM_GROUP_PATH, "/home/groups");
        userProps.put(AccessControlAction.USER_PRIVILEGE_NAMES, new String[] {PrivilegeConstants.JCR_ALL});
        userProps.put(AccessControlAction.GROUP_PRIVILEGE_NAMES, new String[] {PrivilegeConstants.JCR_READ});
        userProps.put(ProtectedItemImporter.PARAM_IMPORT_BEHAVIOR, ImportBehavior.NAME_BESTEFFORT);
        userProps.put(UserConstants.PARAM_AUTHORIZABLE_NODE_NAME, nameGenerator);
        userProps.put("cacheExpiration", 3600*1000);
        Properties authzProps = new Properties();
        authzProps.put(ProtectedItemImporter.PARAM_IMPORT_BEHAVIOR, ImportBehavior.NAME_BESTEFFORT);
        return ConfigurationParameters.of(
                UserConfiguration.NAME, ConfigurationParameters.of(userProps),
                AuthorizationConfiguration.NAME, ConfigurationParameters.of(authzProps));
    }

    public static SecurityProvider createSecurityProvider() {
        SecurityProvider securityProvider = SecurityProviderBuilder.newBuilder()
                .with(getSecurityConfigurationParameters())
                .withRootProvider(new RootProviderService())
                .withTreeProvider(new TreeProviderService()).build();
        return securityProvider;
    }

    public static boolean useFileStore() {
        return Boolean.getBoolean("fds");
    }

    private static BlobStore createBlobStore() throws IOException {
        DIR_BLOB_STORE.mkdirs();
        FileDataStore fds = new FileDataStore();
        fds.setMinRecordLength(4092);
        fds.init(DIR_BLOB_STORE.getAbsolutePath());
        return new DataStoreBlobStore(fds);
    }

    @AfterClass
    public static void shutdownRepository() throws IOException {
        if (repository instanceof RepositoryImpl) {
            ((RepositoryImpl) repository).shutdown();
        } else if (repository instanceof org.apache.jackrabbit.oak.jcr.repository.RepositoryImpl) {
            ((org.apache.jackrabbit.oak.jcr.repository.RepositoryImpl) repository).shutdown();
        }
        repository = null;

        if (fileStore != null) {
            fileStore.close();
            fileStore = null;
        }

        FileUtils.deleteDirectory(DIR_REPO_HOME);
    }

    @Before
    public void setUp() throws Exception {
        admin = repository.login(new SimpleCredentials("admin", "admin".toCharArray()));

        // ensure not packages or tmp
        clean("/etc");
        clean("/var");
        clean("/tmp");
        clean("/testroot");

        packMgr = new JcrPackageManagerImpl(admin, new String[0]);

        PackageEventDispatcherImpl dispatcher = new PackageEventDispatcherImpl();
        dispatcher.bindPackageEventListener(new ActivityLog(), Collections.singletonMap("component.id", (Object) "1234"));
        packMgr.setDispatcher(dispatcher);

        preTestAuthorizables = getAllAuthorizableIds();
    }

    @After
    public void tearDown() throws Exception {
        // remove test authorizables
        admin.refresh(false);
        UserManager mgr = ((JackrabbitSession) admin).getUserManager();
        for (String id: getAllAuthorizableIds()) {
            if (!preTestAuthorizables.remove(id)) {
                removeAuthorizable(mgr, id);
            }
        }
        admin.save();

        packMgr = null;
        if (admin != null) {
            admin.logout();
            admin = null;
        }
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

    public final Set<String> getAllAuthorizableIds() throws RepositoryException {
        Set<String> ret = new HashSet<String>();
        UserManager mgr = ((JackrabbitSession) admin).getUserManager();
        Iterator<Authorizable> auths = mgr.findAuthorizables("rep:principalName", null);
        while (auths.hasNext()) {
            ret.add(auths.next().getID());
        }
        return ret;
    }

    public final void removeAuthorizable(UserManager mgr, String name) throws RepositoryException {
        Authorizable a = mgr.getAuthorizable(name);
        if (a != null) {
            a.remove();
        }
    }

    public InputStream getStream(String name) {
        InputStream in;
        in = getClass().getResourceAsStream(name);
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

    public Archive getFileArchive(String name) {
        final URL packageURL = getClass().getResource(name);
        final String filename = packageURL.getFile();
        final File file = new File(filename);
        if (file.isDirectory()) {
            return new FileArchive(file);
        } else {
            return new ZipArchive(file);
        }
    }

    public Archive getStreamArchive(String name, int size) throws IOException {
        final URL packageURL = getClass().getResource(name);
        final String filename = packageURL.getFile();
        final File file = new File(filename);
        if (file.isDirectory()) {
            throw new IllegalArgumentException("Can't create stream archive from directory");
        } else {
            return new ZipStreamArchive(FileUtils.openInputStream(file), size);
        }
    }

    /**
     * Returns a ZipVaultPackage from either a directory (exploded package) or a package zip file
     * @param name
     * @return VaultPackage
     * @throws IOException
     */
    public VaultPackage loadVaultPackage(String name) throws IOException {
        return new ZipVaultPackage(getFileArchive(name), true);
    }

    /**
     * Returns an ZipVaultPackage which has been extracted in the repository.
     * 
     * @param name either the name of a zip file or the name of a directory which contains an exploded package
     * @return an extracted vault package
     * @throws IOException
     * @throws PackageException
     * @throws RepositoryException
     */
    public VaultPackage extractVaultPackage(String name) throws IOException, PackageException, RepositoryException {
        return extractVaultPackage(name, null);
    }

    /**
     * Returns an ZipVaultPackage which has been extracted in the repository.
     * 
     * @param name either the name of a zip file or the name of a directory which contains an exploded package
     * @return an extracted vault package
     * @throws IOException
     * @throws PackageException
     * @throws RepositoryException
     */
    public VaultPackage extractVaultPackageStrict(String name) throws IOException, PackageException, RepositoryException {
        ImportOptions  opts = getDefaultOptions();
        opts.setStrict(true);
        return extractVaultPackage(name, opts);
    }

    public VaultPackage extractVaultPackage(String name, ImportOptions opts) throws IOException, PackageException, RepositoryException {
        if (opts == null) {
            opts = getDefaultOptions();
        }
        VaultPackage pack = loadVaultPackage(name);
        pack.extract(admin, opts);
        return pack;
    }

    /**
     * Uploads, closes, and reopens the package
     */
    public JcrPackage uploadPackage(String name) throws RepositoryException, IOException {
        JcrPackage jcrPackage = packMgr.upload(getStream(name), false);
        assertNotNull(jcrPackage);
        PackageId pid = jcrPackage.getDefinition().getId();
        jcrPackage.close();
        jcrPackage = packMgr.open(pid);
        assertNotNull(jcrPackage);
        return jcrPackage;
    }

    public JcrPackage installPackage(String name) throws IOException, RepositoryException, PackageException {
        return installPackage(name, null);
    }

    public JcrPackage installPackage(String name, ImportOptions opts) throws IOException, RepositoryException, PackageException {
        if (opts == null) {
            opts = getDefaultOptions();
        }
        JcrPackage pack = packMgr.upload(getStream(name), false);
        assertNotNull(pack);
        pack.install(opts);
        return pack;
    }

    public ImportOptions getDefaultOptions() {
        ImportOptions opts = new ImportOptions();
        opts.setListener(getLoggingProgressTrackerListener());
        return opts;
    }

    public ProgressTrackerListener getLoggingProgressTrackerListener() {
        return new ProgressTrackerListener() {
            public void onMessage(Mode mode, String action, String path) {
                log.info("{} {}", action, path);
            }

            public void onError(Mode mode, String path, Exception e) {
                log.info("E {} {}", path, e.toString());
            }
        };
    }

    /**
     * Returns the installation path of the package including the ".zip" extension.
     * @param id the package id
     * @return the path
     */
    public String getInstallationPath(PackageId id) {
        // make sure we use the one from the test parameter
        return ((JcrPackageRegistry)packMgr.getRegistry()).getInstallationPath(id) + ".zip";
    }

    public void assertPackageNodeExists(PackageId id) throws RepositoryException {
        assertNodeExists(getInstallationPath(id));
    }

    public void assertPackageNodeMissing(PackageId id) throws RepositoryException {
        assertNodeMissing(getInstallationPath(id));
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

    public void assertPropertyExists(String path) throws RepositoryException {
        assertTrue(path + " should exist", admin.propertyExists(path));
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

    public void assertPropertyMissingOrEmpty(String path) throws RepositoryException {
        if (!admin.propertyExists(path)) {
            return;
        }
        Property p = admin.getProperty(path);
        if (p.isMultiple()) {
            assertTrue(path + " should not exist or be empty", p.getValues().length == 0);
        } else {
            assertTrue(path + " should not exist or be empty", p.getString().length() == 0);
        }
    }

    public void assertNodeHasPrimaryType(String path, String primaryType) throws PathNotFoundException, RepositoryException {
        Node node = admin.getNode(path);
        assertNotNull("Node at '" + path + "' must exist", node);
        assertEquals("Node at '" + path + "' does not have the expected node type", primaryType, node.getPrimaryNodeType().getName());
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

    public static class TrackingListener implements ProgressTrackerListener {

        private final ProgressTrackerListener delegate;

        private final Map<String, String> actions = new HashMap<String, String>();

        public TrackingListener(ProgressTrackerListener delegate) {
            this.delegate = delegate;
        }

        public Map<String, String> getActions() {
            return actions;
        }

        @Override
        public void onMessage(Mode mode, String action, String path) {
            if (delegate != null) {
                delegate.onMessage(mode, action, path);
            }
            actions.put(path, action);
        }

        @Override
        public void onError(Mode mode, String path, Exception e) {
            if (delegate != null) {
                delegate.onError(mode, path, e);
            }
            actions.put(path, "E");
        }
    }

    void verifyManifest(File testPackageFile, Set<String> ignoredEntries, String expected) throws IOException {
        try (JarFile jar = new JarFile(testPackageFile)) {
            List<String> entries = new ArrayList<String>();
            for (Map.Entry<Object, Object> e: jar.getManifest().getMainAttributes().entrySet()) {
                String key = e.getKey().toString();
                if (ignoredEntries.contains(key)) {
                    continue;
                }
                entries.add(e.getKey() + ":" + e.getValue());
            }
            Collections.sort(entries);
            String result = Text.implode(entries.toArray(new String[entries.size()]),"\n");
            assertEquals("Manifest", expected, result);
        }
    }

}
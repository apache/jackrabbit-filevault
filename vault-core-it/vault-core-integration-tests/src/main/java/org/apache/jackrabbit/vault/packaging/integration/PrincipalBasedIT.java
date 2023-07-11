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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import java.io.IOException;
import java.security.Principal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.jcr.ValueFactory;
import javax.jcr.security.AccessControlEntry;
import javax.jcr.security.AccessControlPolicy;
import javax.jcr.security.Privilege;

import org.apache.jackrabbit.JcrConstants;
import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.JackrabbitAccessControlManager;
import org.apache.jackrabbit.api.security.authorization.PrincipalAccessControlList;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.jackrabbit.commons.jackrabbit.authorization.AccessControlUtils;
import org.apache.jackrabbit.oak.spi.security.authorization.accesscontrol.AccessControlConstants;
import org.apache.jackrabbit.oak.spi.security.privilege.PrivilegeConstants;
import org.apache.jackrabbit.vault.fs.api.ImportMode;
import org.apache.jackrabbit.vault.fs.io.AccessControlHandling;
import org.apache.jackrabbit.vault.fs.io.ImportOptions;
import org.apache.sling.testing.mock.osgi.junit.OsgiContext;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

public class PrincipalBasedIT extends IntegrationTestBase {

    private static final String EFFECTIVE_PATH = "/testroot/secured";
    private static final String SYSTEM_USER_ID = "testSystemUser";
    @ClassRule
    public static final OsgiContext context = new OsgiContext();

    private UserManager userManager;
    private User testUser;

    private JackrabbitAccessControlManager acMgr;
    private AccessControlEntry[] existingEntries;
    private AccessControlEntry[] packageEntries;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();

        userManager = ((JackrabbitSession) admin).getUserManager();
        String serviceUserPath = repositoryProvider.getServiceUserPath();
        testUser = userManager.createSystemUser(SYSTEM_USER_ID, serviceUserPath);
        admin.save();

        ValueFactory vf = admin.getValueFactory();

        acMgr = (JackrabbitAccessControlManager) admin.getAccessControlManager();
        for (AccessControlPolicy policy : acMgr.getApplicablePolicies(testUser.getPrincipal())) {
            if (policy instanceof PrincipalAccessControlList) {
                PrincipalAccessControlList pacl = (PrincipalAccessControlList) policy;
                Map<String, Value[]> mvRestrictions = ImmutableMap.of(AccessControlConstants.REP_ITEM_NAMES, new Value[]{vf.createValue(JcrConstants.JCR_CONTENT, PropertyType.NAME)});
                pacl.addEntry(EFFECTIVE_PATH, AccessControlUtils.privilegesFromNames(acMgr, Privilege.JCR_READ), ImmutableMap.<String, Value>of(), mvRestrictions);
                pacl.addEntry(null, AccessControlUtils.privilegesFromNames(acMgr, PrivilegeConstants.JCR_NAMESPACE_MANAGEMENT));
                existingEntries = pacl.getAccessControlEntries();
                acMgr.setPolicy(pacl.getPath(), pacl);
                break;
            }
        }
        admin.save();

        User testUser2 = userManager.createSystemUser(SYSTEM_USER_ID+"_2", serviceUserPath);
        for (AccessControlPolicy policy : acMgr.getApplicablePolicies(testUser2.getPrincipal())) {
            if (policy instanceof PrincipalAccessControlList) {
                PrincipalAccessControlList pacl = (PrincipalAccessControlList) policy;

                pacl.addEntry(EFFECTIVE_PATH, AccessControlUtils.privilegesFromNames(acMgr, Privilege.JCR_WRITE), ImmutableMap.of("rep:glob", vf.createValue("*")), ImmutableMap.<String, Value[]>of());
                pacl.addEntry(EFFECTIVE_PATH, AccessControlUtils.privilegesFromNames(acMgr, Privilege.JCR_LOCK_MANAGEMENT), ImmutableMap.of("rep:glob", vf.createValue("*/foo")), ImmutableMap.of("rep:itemNames", new Value[] {vf.createValue("jcr:content", PropertyType.NAME), vf.createValue("jcr:data", PropertyType.NAME)}));
                pacl.addEntry("/content", AccessControlUtils.privilegesFromNames(acMgr, Privilege.JCR_READ), ImmutableMap.<String, Value>of(), ImmutableMap.<String, Value[]>of());
                pacl.addEntry(null, AccessControlUtils.privilegesFromNames(acMgr, PrivilegeConstants.JCR_WORKSPACE_MANAGEMENT, PrivilegeConstants.JCR_NAMESPACE_MANAGEMENT));
                packageEntries = pacl.getAccessControlEntries();
                break;
            }
        }
        admin.refresh(false);
    }

    @Override
    @After
    public void tearDown() throws Exception {
        try {
            User u = userManager.getAuthorizable(SYSTEM_USER_ID, User.class);
            if (u != null) {
                u.remove();
                admin.save();
            }
        } finally {
            super.tearDown();
            shutdownRepository();
            initRepository();
        }
    }

    @BeforeClass
    public static void initRepository() throws RepositoryException, IOException {
        assumeTrue(isOak());
        initRepository(false, true);
    }

    private void assertPolicy(@NotNull Principal principal, @NotNull AccessControlEntry... expectedEntries) throws RepositoryException {
        for (AccessControlPolicy policy : acMgr.getPolicies(principal)) {
            // disregard the order
            if (policy instanceof PrincipalAccessControlList) {
                PrincipalAccessControlList pacl = (PrincipalAccessControlList) policy;
                AccessControlEntry[] aces = pacl.getAccessControlEntries();
                MatcherAssert.assertThat(Arrays.asList(aces), Matchers.containsInAnyOrder(Arrays.stream(aces)
                        .map(e -> new PrincipalBasedStashingIT.PrincipalAccessControlEntryMatcher(e, pacl))
                        .collect(Collectors.toList())));
                return;
            }
        }
        fail("expected PrincipalAccessControlList for principal " + principal.getName());
    }

    @Test
    public void testHandlingIgnoreModeUpdate() throws Exception {
        ImportOptions opts = getDefaultOptions();
        opts.setAccessControlHandling(AccessControlHandling.IGNORE);
        opts.setImportMode(ImportMode.UPDATE);

        extractVaultPackage("/test-packages/principalbased.zip", opts);
        assertPolicy(testUser.getPrincipal(), existingEntries);
    }

    @Test
    public void testHandlingIgnoreModeMerge() throws Exception {
        ImportOptions opts = getDefaultOptions();
        opts.setAccessControlHandling(AccessControlHandling.IGNORE);
        opts.setImportMode(ImportMode.MERGE);

        extractVaultPackage("/test-packages/principalbased.zip", opts);
        assertPolicy(testUser.getPrincipal(), existingEntries);
    }

    @Test
    public void testHandlingIgnoreModeReplace() throws Exception {
        ImportOptions opts = getDefaultOptions();
        opts.setAccessControlHandling(AccessControlHandling.IGNORE);
        opts.setImportMode(ImportMode.REPLACE);

        extractVaultPackage("/test-packages/principalbased.zip", opts);
        // user may have been moved due to 'replace' mode -> need to retrieve again
        Principal p = userManager.getAuthorizable(SYSTEM_USER_ID).getPrincipal();
        assertEquals(0, acMgr.getPolicies(p).length);
    }

    @Test
    public void testHandlingOverwriteModeUpdate() throws Exception {
        ImportOptions opts = getDefaultOptions();
        opts.setAccessControlHandling(AccessControlHandling.OVERWRITE);
        opts.setImportMode(ImportMode.UPDATE);

        extractVaultPackage("/test-packages/principalbased.zip");
        assertPolicy(testUser.getPrincipal(), packageEntries);
    }

    @Test
    public void testHandlingOverwriteModeMerge() throws Exception {
        ImportOptions opts = getDefaultOptions();
        opts.setAccessControlHandling(AccessControlHandling.OVERWRITE);
        opts.setImportMode(ImportMode.MERGE);

        extractVaultPackage("/test-packages/principalbased.zip");
        assertPolicy(testUser.getPrincipal(), packageEntries);
    }

    @Test
    public void testHandlingOverwriteModeReplace() throws Exception {
        ImportOptions opts = getDefaultOptions();
        opts.setAccessControlHandling(AccessControlHandling.OVERWRITE);
        opts.setImportMode(ImportMode.REPLACE);

        extractVaultPackage("/test-packages/principalbased.zip");
        assertPolicy(testUser.getPrincipal(), packageEntries);
    }

    @Test
    public void testHandlingMergeModeUpdate() throws Exception {
        ImportOptions opts = getDefaultOptions();
        opts.setAccessControlHandling(AccessControlHandling.MERGE);
        opts.setImportMode(ImportMode.UPDATE);

        extractVaultPackage("/test-packages/principalbased.zip", opts);

        List<AccessControlEntry> expected = Lists.newArrayList(existingEntries);
        expected.addAll(ImmutableList.copyOf(packageEntries));
        assertPolicy(testUser.getPrincipal(), expected.toArray(new AccessControlEntry[0]));
    }

    @Test
    public void testHandlingMergeModeMerge() throws Exception {
        ImportOptions opts = getDefaultOptions();
        opts.setAccessControlHandling(AccessControlHandling.MERGE);
        opts.setImportMode(ImportMode.MERGE);

        extractVaultPackage("/test-packages/principalbased.zip", opts);

        List<AccessControlEntry> expected = Lists.newArrayList(existingEntries);
        expected.addAll(ImmutableList.copyOf(packageEntries));
        assertPolicy(testUser.getPrincipal(), expected.toArray(new AccessControlEntry[0]));
    }

    @Test
    public void testHandlingMergeModeReplace() throws Exception {
        ImportOptions opts = getDefaultOptions();
        opts.setAccessControlHandling(AccessControlHandling.MERGE);
        opts.setImportMode(ImportMode.REPLACE);

        extractVaultPackage("/test-packages/principalbased.zip", opts);

        // user may have been moved due to 'replace' mode -> need to retrieve again
        assertPolicy(userManager.getAuthorizable(SYSTEM_USER_ID).getPrincipal(), packageEntries);
    }

    @Test
    public void testHandlingMergePreserveModeUpdate() throws Exception {
        ImportOptions opts = getDefaultOptions();
        opts.setAccessControlHandling(AccessControlHandling.MERGE_PRESERVE);
        opts.setImportMode(ImportMode.UPDATE);

        extractVaultPackage("/test-packages/principalbased.zip", opts);
        assertPolicy(testUser.getPrincipal(), existingEntries);
    }

    @Test
    public void testHandlingMergePreserveModeMerge() throws Exception {
        ImportOptions opts = getDefaultOptions();
        opts.setAccessControlHandling(AccessControlHandling.MERGE_PRESERVE);
        opts.setImportMode(ImportMode.MERGE);

        extractVaultPackage("/test-packages/principalbased.zip", opts);
        assertPolicy(testUser.getPrincipal(), existingEntries);
    }

    @Test
    public void testHandlingMergePreserveModeReplace() throws Exception {
        ImportOptions opts = getDefaultOptions();
        opts.setAccessControlHandling(AccessControlHandling.MERGE_PRESERVE);
        opts.setImportMode(ImportMode.REPLACE);

        extractVaultPackage("/test-packages/principalbased.zip", opts);
        // user may have been moved due to 'replace' mode -> need to retrieve again
        Principal p = userManager.getAuthorizable(SYSTEM_USER_ID).getPrincipal();
        assertEquals(0, acMgr.getPolicies(p).length);
    }
}
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

import java.security.Principal;
import java.util.List;
import javax.jcr.Node;
import javax.jcr.ValueFactory;
import javax.jcr.security.AccessControlEntry;
import javax.jcr.security.AccessControlManager;
import javax.jcr.security.Privilege;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.JackrabbitAccessControlList;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.jackrabbit.commons.jackrabbit.authorization.AccessControlUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Test if order of ACE is preserved upon import
 */
public class AceOrderIT extends IntegrationTestBase {

    private final static String NAME_TEST_USER = "testuser";

    private UserManager uMgr;
    private AccessControlManager acMgr;

    private List<AccessControlEntry> expectedEntries;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        uMgr = ((JackrabbitSession) admin).getUserManager();
        User testuser = uMgr.createUser(NAME_TEST_USER, null);
        admin.save();

        acMgr = admin.getAccessControlManager();

        Node tmp = admin.getRootNode().addNode("testroot").addNode("secured");
        JackrabbitAccessControlList list = AccessControlUtils.getAccessControlList(acMgr, tmp.getPath());
        Privilege[] writePrivilege = AccessControlUtils.privilegesFromNames(acMgr, Privilege.JCR_WRITE);
        ValueFactory vf = admin.getValueFactory();
        Principal everyone = ((JackrabbitSession) admin).getPrincipalManager().getEveryone();
        list.addEntry(everyone, writePrivilege, true, ImmutableMap.of("rep:glob", vf.createValue("/foo")));
        list.addEntry(testuser.getPrincipal(), writePrivilege, false, ImmutableMap.of("rep:glob", vf.createValue("/foo")));
        list.addEntry(everyone, writePrivilege, true, ImmutableMap.of("rep:glob", vf.createValue("/bar")));
        acMgr.setPolicy(tmp.getPath(), list);

        expectedEntries = ImmutableList.copyOf(list.getAccessControlEntries());

        admin.refresh(false);
    }

    @Override
    public void tearDown() throws Exception {
        try {
            if (admin.nodeExists("/testroot")) {
                admin.getNode("/testroot").remove();
                admin.save();
            }
            Authorizable testUser = uMgr.getAuthorizable(NAME_TEST_USER);
            if (testUser != null) {
                testUser.remove();
                admin.save();
            }
        } finally {
            super.tearDown();
        }
    }

    private void assertACEs(@NotNull String path) throws Exception {
        JackrabbitAccessControlList list = AccessControlUtils.getAccessControlList(acMgr, path);
        AccessControlEntry[] entries = list.getAccessControlEntries();

        assertEquals(expectedEntries, ImmutableList.copyOf(entries));
    }

    @Test
    public void testHandlingOverwrite() throws Exception {
        assertNodeMissing("/testroot/secured");

        extractVaultPackage("/test-packages/ace_order_overwrite.zip");

        // test if nodes and ACLs of first package exist
        assertNodeExists("/testroot/secured");
        assertACEs("/testroot/secured");
    }
}

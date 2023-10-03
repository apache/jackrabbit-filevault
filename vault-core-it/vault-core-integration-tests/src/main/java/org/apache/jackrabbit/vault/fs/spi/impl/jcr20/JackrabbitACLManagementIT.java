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

package org.apache.jackrabbit.vault.fs.spi.impl.jcr20;

import org.apache.jackrabbit.vault.packaging.integration.IntegrationTestBase;
import org.junit.Test;

import javax.jcr.GuestCredentials;
import javax.jcr.Node;
import javax.jcr.Session;
import javax.jcr.security.AccessControlPolicy;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Testcase for {@link JackrabbitACLManagement}
 */
public class JackrabbitACLManagementIT extends IntegrationTestBase {

    @Test
    public void testReadOnlyPermissionsOverHome() throws Exception {
        Session session = repository.login(new GuestCredentials());
        final String userId = session.getUserID();
        JackrabbitACLManagement service = new JackrabbitACLManagement();
        JackrabbitUserManagement userManagement = new JackrabbitUserManagement();
        final String userPath = userManagement.getAuthorizablePath(session, userId);
        assertNodeExists(userPath);
        final Node userNode = session.getNode(userPath);
        final Map<String, List<? extends AccessControlPolicy>> policies = service.getPrincipalAcls(userNode);
        assertNotNull(policies);
        assertTrue(policies.isEmpty());
    }
}

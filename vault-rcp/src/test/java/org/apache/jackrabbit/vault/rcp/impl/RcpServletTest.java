/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.jackrabbit.vault.rcp.impl;

import javax.jcr.Session;
import javax.servlet.http.HttpServletResponse;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.ResourceResolver;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Tests that the RCP servlet enforces administrative permissions: it is bound to a path
 * ({@code sling.servlet.paths}) and therefore not subject to JCR resource ACLs, while its
 * commands can expose task configuration and ship stored source credentials to
 * caller-chosen hosts.
 */
public class RcpServletTest {

    private final RcpServlet servlet = new RcpServlet();

    private static Session sessionWithUserId(String userId) {
        Session session = Mockito.mock(Session.class);
        Mockito.when(session.getUserID()).thenReturn(userId);
        return session;
    }

    @Test
    public void testDoPostWithoutAdminSessionIsRejected() throws Exception {
        SlingHttpServletRequest request = Mockito.mock(SlingHttpServletRequest.class);
        SlingHttpServletResponse response = Mockito.mock(SlingHttpServletResponse.class);
        ResourceResolver resolver = Mockito.mock(ResourceResolver.class);
        Session nonAdminSession = sessionWithUserId("anonymous");
        Mockito.when(request.getResourceResolver()).thenReturn(resolver);
        Mockito.when(resolver.adaptTo(Session.class)).thenReturn(nonAdminSession);

        servlet.doPost(request, response);

        Mockito.verify(response).sendError(Mockito.eq(HttpServletResponse.SC_FORBIDDEN), Mockito.anyString());
        Mockito.verifyNoMoreInteractions(response);
    }

    @Test
    public void testDoGetWithoutSessionIsRejected() throws Exception {
        SlingHttpServletRequest request = Mockito.mock(SlingHttpServletRequest.class);
        SlingHttpServletResponse response = Mockito.mock(SlingHttpServletResponse.class);
        ResourceResolver resolver = Mockito.mock(ResourceResolver.class);
        Mockito.when(request.getResourceResolver()).thenReturn(resolver);
        Mockito.when(resolver.adaptTo(Session.class)).thenReturn(null);

        servlet.doGet(request, response);

        Mockito.verify(response).sendError(Mockito.eq(HttpServletResponse.SC_FORBIDDEN), Mockito.anyString());
        Mockito.verifyNoMoreInteractions(response);
    }
}

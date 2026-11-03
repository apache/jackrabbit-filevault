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

import javax.jcr.SimpleCredentials;

import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.jackrabbit.spi2dav.ConnectionOptions;
import org.apache.jackrabbit.vault.fs.api.RepositoryAddress;
import org.apache.jackrabbit.vault.rcp.RcpTask;
import org.apache.jackrabbit.vault.rcp.RcpTaskManager;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.Version;

@RunWith(MockitoJUnitRunner.class)
public class RcpServletTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<Map<String, Object>>() {};

    @Mock
    private RcpTaskManager taskManager;

    @Mock
    private BundleContext bundleContext;

    @Mock
    private Bundle bundle;

    private RcpServlet servlet;

    @Before
    public void setUp() throws Exception {
        Mockito.when(bundleContext.getBundle()).thenReturn(bundle);
        servlet = new RcpServlet(bundleContext, taskManager);
    }

    @Test
    public void testWriteInfoJson() throws Exception {
        Mockito.when(bundle.getSymbolicName()).thenReturn("org.apache.jackrabbit.vault.rcp");
        Mockito.when(bundle.getVersion()).thenReturn(new Version("1.2.3"));
        Dictionary<String, String> headers = new Hashtable<>();
        headers.put(Constants.BUNDLE_VENDOR, "The Apache Software Foundation");
        Mockito.when(bundle.getHeaders()).thenReturn(headers);

        StringWriter jsonWriter = new StringWriter();
        servlet.writeInfoJson(jsonWriter);
        Map<String, Object> parsed = OBJECT_MAPPER.readValue(jsonWriter.toString(), MAP_TYPE);

        Assert.assertEquals("org.apache.jackrabbit.vault.rcp", parsed.get(Constants.BUNDLE_SYMBOLICNAME));
        Assert.assertEquals("1.2.3", parsed.get(Constants.BUNDLE_VERSION));
        Assert.assertEquals("The Apache Software Foundation", parsed.get(Constants.BUNDLE_VENDOR));
    }

    @Test
    public void testWriteTaskJsonForSingleTask() throws Exception {
        RcpTask task = createTask("task-1");
        Mockito.when(taskManager.getTask("task-1")).thenReturn(task);

        StringWriter jsonWriter = new StringWriter();
        servlet.writeTaskJson(jsonWriter, "/task-1");
        Map<String, Object> parsed = OBJECT_MAPPER.readValue(jsonWriter.toString(), MAP_TYPE);

        Assert.assertEquals("task-1", parsed.get(RcpServlet.PARAM_ID));
        Assert.assertEquals(task.getSource().toString(), parsed.get(RcpServlet.PARAM_SRC));
        Assert.assertEquals("/var/target", parsed.get(RcpServlet.PARAM_DST));
        Assert.assertFalse(parsed.containsKey("tasks"));
        Mockito.verify(taskManager).getTask("task-1");
    }

    @Test
    public void testWriteTaskJsonForUnknownTaskReturnsEmptyObject() throws Exception {
        Mockito.when(taskManager.getTask("missing")).thenReturn(null);

        StringWriter jsonWriter = new StringWriter();
        servlet.writeTaskJson(jsonWriter, "/missing");
        Map<String, Object> parsed = OBJECT_MAPPER.readValue(jsonWriter.toString(), MAP_TYPE);

        Assert.assertEquals(Collections.emptyMap(), parsed);
    }

    @Test
    public void testWriteTaskJsonForAllTasks() throws Exception {
        Map<String, RcpTask> tasks = new LinkedHashMap<>();
        tasks.put("task-1", createTask("task-1"));
        tasks.put("task-2", createTask("task-2"));
        Mockito.when(taskManager.getTasks()).thenReturn(tasks);

        StringWriter jsonWriter = new StringWriter();
        servlet.writeTaskJson(jsonWriter, null);
        Map<String, Object> parsed = OBJECT_MAPPER.readValue(jsonWriter.toString(), MAP_TYPE);

        Object taskListObj = parsed.get("tasks");
        Assert.assertNotNull(taskListObj);
        Assert.assertTrue(taskListObj instanceof Iterable);

        int count = 0;
        Map<String, Boolean> seenIds = new HashMap<>();
        for (Object taskObj : (Iterable<?>) taskListObj) {
            Assert.assertTrue(taskObj instanceof Map);
            @SuppressWarnings("unchecked")
            Map<String, Object> taskMap = (Map<String, Object>) taskObj;
            seenIds.put((String) taskMap.get(RcpServlet.PARAM_ID), true);
            count++;
        }

        Assert.assertEquals(2, count);
        Assert.assertTrue(seenIds.containsKey("task-1"));
        Assert.assertTrue(seenIds.containsKey("task-2"));
        Mockito.verify(taskManager).getTasks();
    }

    private static RcpTask createTask(String id) throws Exception {
        ConnectionOptions connectionOptions = ConnectionOptions.builder()
                .useSystemProperties(true)
                .allowSelfSignedCertificates(true)
                .disableHostnameVerification(false)
                .connectionTimeoutMs(10)
                .requestTimeoutMs(20)
                .socketTimeoutMs(30)
                .build();
        RcpTaskImpl task = new RcpTaskImpl(
                RcpServletTest.class.getClassLoader(),
                new RepositoryAddress("http://localhost:4502"),
                connectionOptions,
                new SimpleCredentials("admin", "admin".toCharArray()),
                "/var/target",
                id,
                Arrays.asList("/content/exclude(/.*)?"),
                true);
        task.getRcp().setBatchSize(42);
        task.getRcp().setUpdate(true);
        task.getRcp().setOnlyNewer(true);
        task.getRcp().setNoOrdering(false);
        task.getRcp().setThrottle(7);
        return task;
    }
}

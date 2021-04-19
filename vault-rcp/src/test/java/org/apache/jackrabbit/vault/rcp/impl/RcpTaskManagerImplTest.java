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
package org.apache.jackrabbit.vault.rcp.impl;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Dictionary;

import javax.jcr.RepositoryException;
import javax.jcr.SimpleCredentials;

import org.apache.jackrabbit.spi2dav.ConnectionOptions;
import org.apache.jackrabbit.vault.fs.api.RepositoryAddress;
import org.apache.jackrabbit.vault.fs.config.ConfigurationException;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.rcp.RcpTask;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.hamcrest.collection.IsIterableContainingInOrder;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;
import org.osgi.framework.BundleContext;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;


@RunWith(MockitoJUnitRunner.class)
public class RcpTaskManagerImplTest {

    @Mock
    BundleContext mockBundleContext;

    @Mock
    ConfigurationAdmin mockConfigurationAdmin;

    @Mock
    Configuration mockConfiguration;

    Dictionary<String, Object> configProperties;

    RcpTaskManagerImpl taskManager;

    @Rule
    public TemporaryFolder folder= new TemporaryFolder();

    @Before
    public void before() throws IOException {
        Mockito.when(mockBundleContext.getDataFile(Mockito.anyString())).then(new Answer<File>() {
            @Override
            public File answer(InvocationOnMock invocation) throws Throwable {
                String name = invocation.getArgument(0, String.class);
                return new File(folder.getRoot(),name);
            }
        });
        configProperties = null;
        Mockito.when(mockConfigurationAdmin.getConfiguration(Mockito.anyString())).thenReturn(mockConfiguration);
        Mockito.doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                configProperties = invocation.getArgument(0, Dictionary.class);
                return null;
            }
            
        }).when(mockConfiguration).updateIfDifferent(Mockito.any());
        taskManager = new RcpTaskManagerImpl(mockBundleContext, mockConfigurationAdmin, Collections.emptyMap());
    }

    @Test 
    public void testEditTask() throws IOException, ConfigurationException, URISyntaxException {
        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        try (InputStream input = this.getClass().getResourceAsStream("/filter.xml")) {
            filter.load(input);
        }
        ConnectionOptions.Builder connectionOptionsBuilder = ConnectionOptions.builder();
        connectionOptionsBuilder.proxyHost("proxyHost");
        RcpTaskImpl taskOld = (RcpTaskImpl)taskManager.addTask(new RepositoryAddress("http://localhost:4502"), connectionOptionsBuilder.build(), new SimpleCredentials("testUser", "pw".toCharArray()), "/target/path", "2", Arrays.asList("exclude1", "exclude2"), false);
        RcpTaskImpl taskNew = (RcpTaskImpl)taskManager.editTask(taskOld.getId(), null, null, null, null, null, null, null);
        MatcherAssert.assertThat(taskNew, Matchers.equalTo(taskOld));
        
        RepositoryAddress newSource = new RepositoryAddress("http://localhost:4503");
        taskNew = (RcpTaskImpl)taskManager.editTask(taskOld.getId(), newSource, null, null, null, null, null, null);
        MatcherAssert.assertThat(taskNew, Matchers.not(Matchers.equalTo(taskOld)));
        Assert.assertEquals(newSource, taskNew.getSource());
    }

    @Test
    public void testSerializeDeserialize() throws IOException, ConfigurationException, URISyntaxException, RepositoryException {
        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        try (InputStream input = this.getClass().getResourceAsStream("/filter.xml")) {
            filter.load(input);
        }
        ConnectionOptions.Builder connectionOptionsBuilder = ConnectionOptions.builder();
        connectionOptionsBuilder.allowSelfSignedCertificates(true);
        connectionOptionsBuilder.disableHostnameVerification(false);
        connectionOptionsBuilder.socketTimeoutMs(100);
        RcpTask task1 = taskManager.addTask(new RepositoryAddress("http://localhost:4502"), connectionOptionsBuilder.build(), new SimpleCredentials("testUser", "pw".toCharArray()), "/target/path", "2", Arrays.asList("exclude1", "exclude2"), false);
        task1.getRcp().setBatchSize(200);
        task1.getRcp().setUpdate(true);
        task1.getRcp().setThrottle(30);
        task1.getRcp().setOnlyNewer(true);
        taskManager.addTask(new RepositoryAddress("http://localhost:8080"), connectionOptionsBuilder.build(), new SimpleCredentials("testUser3", "pw3".toCharArray()), "/target/path5", "3", filter, true);
        taskManager.deactivate();
        Assert.assertNotNull("The tasks should have been persisted here but are not!", configProperties);
        // convert to Map
        RcpTaskManagerImpl taskManager2 = new RcpTaskManagerImpl(mockBundleContext, mockConfigurationAdmin, RcpTaskManagerImpl.createMapFromDictionary(configProperties));
        // how to get list ordered by id?
        MatcherAssert.assertThat(taskManager2.tasks.values(), IsIterableContainingInOrder.contains(taskManager.tasks.values().toArray()));
    }
}

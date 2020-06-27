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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javax.jcr.ItemNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.SimpleCredentials;

import org.apache.jackrabbit.vault.fs.api.RepositoryAddress;
import org.apache.jackrabbit.vault.fs.config.ConfigurationException;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.sling.commons.classloader.DynamicClassLoaderManager;
import org.apache.sling.jcr.api.SlingRepository;
import org.junit.Assert;
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
import org.osgi.util.converter.Converter;
import org.osgi.util.converter.Converters;


@RunWith(MockitoJUnitRunner.class)
public class RcpTaskManagerImplTest {

    @Mock
    BundleContext mockBundleContext;

    @Mock
    DynamicClassLoaderManager mockClassLoaderManager;

    @Mock
    ClassLoader mockClassLoader;
    
    @Mock
    SlingRepository mockRepository;
    
    @Rule
    public TemporaryFolder folder= new TemporaryFolder();
    
    @Test
    public void testSerializeDeserialize() throws IOException, ConfigurationException, URISyntaxException, RepositoryException {
        Mockito.when(mockBundleContext.getDataFile(Mockito.anyString())).then(new Answer<File>() {
            @Override
            public File answer(InvocationOnMock invocation) throws Throwable {
                String name = invocation.getArgument(0, String.class);
                return new File(folder.getRoot(),name);
            }
        });
        Mockito.when(mockClassLoaderManager.getDynamicClassLoader()).thenReturn(mockClassLoader);
        Converter c = Converters.standardConverter();
        Map<String, String> configMap = new HashMap<>();
        RcpTaskManagerImpl.Configuration config = c.convert(configMap).to(RcpTaskManagerImpl.Configuration.class);
        File repoFile = folder.newFile();
        
        RcpTaskManagerImpl taskManager = new RcpTaskManagerImpl(mockBundleContext, config, mockClassLoaderManager, mockRepository) {
            @Override
            InputStream getInputStream() throws RepositoryException {
                throw new ItemNotFoundException();
            }

            @Override
            OutputStream getOutputStream() throws IOException {
                return new FileOutputStream(repoFile);
            }
        };
        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        try (InputStream input = this.getClass().getResourceAsStream("/filter.xml")) {
            filter.load(input);
        }
        taskManager.addTask(new RepositoryAddress("http://localhost:4502"), new SimpleCredentials("testUser", "pw".toCharArray()), "/target/path", "2", Arrays.asList("exclude1", "exclude2"), false);
        taskManager.addTask(new RepositoryAddress("http://localhost:8080"), new SimpleCredentials("testUser3", "pw3".toCharArray()), "/target/path5", "3", filter, true);
        taskManager.deactivate();
        RcpTaskManagerImpl taskManager2 = new RcpTaskManagerImpl(mockBundleContext, config, mockClassLoaderManager, mockRepository) {
            @Override
            InputStream getInputStream() throws IOException {
                return new FileInputStream(repoFile);
            }

            @Override
            OutputStream getOutputStream() throws IOException {
                return new FileOutputStream(repoFile);
            }
        };
        Assert.assertEquals(taskManager.tasks, taskManager2.tasks);
    }
}

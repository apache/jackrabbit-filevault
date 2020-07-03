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
import java.util.Collection;
import java.util.Collections;
import java.util.Dictionary;
import java.util.stream.Collectors;

import javax.jcr.RepositoryException;
import javax.jcr.SimpleCredentials;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.jackrabbit.vault.fs.api.RepositoryAddress;
import org.apache.jackrabbit.vault.fs.config.ConfigurationException;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.sling.commons.classloader.DynamicClassLoaderManager;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.hamcrest.collection.IsIterableContainingInOrder;
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
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;


@RunWith(MockitoJUnitRunner.class)
public class RcpTaskManagerImplTest {

    @Mock
    BundleContext mockBundleContext;

    @Mock
    DynamicClassLoaderManager mockClassLoaderManager;

    @Mock
    ClassLoader mockClassLoader;

    @Mock
    ConfigurationAdmin mockConfigurationAdmin;

    @Mock
    Configuration mockConfiguration;

    Dictionary<String, Object> configProperties;

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
        configProperties = null;
        Mockito.when(mockConfigurationAdmin.getConfiguration(Mockito.anyString())).thenReturn(mockConfiguration);
        Mockito.doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                configProperties = invocation.getArgument(0, Dictionary.class);
                return null;
            }
            
        }).when(mockConfiguration).updateIfDifferent(Mockito.any());
        RcpTaskManagerImpl taskManager = new RcpTaskManagerImpl(mockBundleContext, mockClassLoaderManager, mockConfigurationAdmin, Collections.emptyMap());
        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        try (InputStream input = this.getClass().getResourceAsStream("/filter.xml")) {
            filter.load(input);
        }
        taskManager.addTask(new RepositoryAddress("http://localhost:4502"), new SimpleCredentials("testUser", "pw".toCharArray()), "/target/path", "2", Arrays.asList("exclude1", "exclude2"), false);
        taskManager.addTask(new RepositoryAddress("http://localhost:8080"), new SimpleCredentials("testUser3", "pw3".toCharArray()), "/target/path5", "3", filter, true);
        taskManager.deactivate();
        Assert.assertNotNull("The tasks should have been persisted here but are not!", configProperties);
        // convert to Map
        RcpTaskManagerImpl taskManager2 = new RcpTaskManagerImpl(mockBundleContext, mockClassLoaderManager, mockConfigurationAdmin, RcpTaskManagerImpl.createMapFromDictionary(configProperties));
        // how to get list ordered by id?
        Assert.assertThat(taskManager.tasks.values(), new TaskCollectionMatcher(taskManager2.tasks.values()));
    }
    
    private final static class TaskCollectionMatcher extends IsIterableContainingInOrder<RcpTaskImpl> {

        public TaskCollectionMatcher(Collection<RcpTaskImpl> tasks) {
            super(tasks.stream().map(TaskMatcher::new).collect(Collectors.toList()));
        }
    }
    
    private final static class TaskMatcher extends TypeSafeMatcher<RcpTaskImpl> {
        private final RcpTaskImpl task;
        
        public TaskMatcher(RcpTaskImpl task) {
            this.task = task;
        }

        @Override
        public void describeTo(Description description) {
            description.appendValue(taskToString(task));
        }

        @Override
        protected void describeMismatchSafely(RcpTaskImpl item, Description mismatchDescription) {
            mismatchDescription.appendText("was ").appendValue(taskToString(item));
        }
        
        private static String taskToString(RcpTaskImpl task) {
            ReflectionToStringBuilder builder = new ReflectionToStringBuilder(task, ToStringStyle.SHORT_PREFIX_STYLE);
            return builder.toString();
        }

        @Override
        protected boolean matchesSafely(RcpTaskImpl otherTask) {
            if (!EqualsBuilder.reflectionEquals(task.getSrcCreds(), otherTask.getSrcCreds())) {
                return false;
            }
            if (!task.getSource().equals(otherTask.getSource())) {
                return false;
            }
            if (!task.getDestination().equals(otherTask.getDestination())) {
                return false;
            }
            if (!(task.getExcludes() == null ? otherTask.getExcludes() == null : task.getExcludes().equals(otherTask.getExcludes()))) {
                return false;
            }
            if (task.isRecursive() != otherTask.isRecursive()) {
                return false;
            }
            if (!EqualsBuilder.reflectionEquals(task.filter, otherTask.filter, "source")) {
                return false;
            }
            return true;
        }
        
    }
}

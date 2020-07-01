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
import java.util.Collections;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.SortedMap;
import java.util.TreeMap;

import javax.jcr.Credentials;
import javax.jcr.RepositoryException;
import javax.jcr.SimpleCredentials;

import org.apache.jackrabbit.vault.fs.api.RepositoryAddress;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.fs.config.ConfigurationException;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.rcp.RcpTask;
import org.apache.jackrabbit.vault.rcp.RcpTaskManager;
import org.apache.sling.commons.classloader.DynamicClassLoaderManager;
import org.osgi.framework.BundleContext;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.propertytypes.ServiceVendor;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

/** {@code RcpTaskManager}... */
@Component
@ServiceVendor("The Apache Software Foundation")
@Designate(ocd = RcpTaskManagerImpl.ComponentPropertyType.class)
public class RcpTaskManagerImpl implements RcpTaskManager {

    @ObjectClassDefinition(name = "Apache Jackrabbit FileVault RCP Task Manager", description = "Manages tasks for RCP (remote copy)")
    public static @interface ComponentPropertyType {
        @AttributeDefinition(name = "Serialized Tasks", description = "The JSON serialization of all tasks. Credentials are not stored in here, but rather in the bundle context data file.")
        String serialized_tasks_json() default "";
    }

    private static final String TASKS_DATA_FILE_NAME = "tasks";
    private static final String PROP_TASKS_SERIALIZATION = "serialized.tasks.json";
    private static final String PID = RcpTaskManagerImpl.class.getName();

    /** default logger */
    private static final Logger log = LoggerFactory.getLogger(RcpTaskManagerImpl.class);

    private final DynamicClassLoaderManager dynLoaderMgr;

    SortedMap<String, RcpTaskImpl> tasks;

    private final File dataFile;

    private final ObjectMapper mapper = new ObjectMapper();
    
    private final Configuration configuration;


    @Activate
    public RcpTaskManagerImpl(BundleContext bundleContext, @Reference DynamicClassLoaderManager dynLoaderMgr,
            @Reference ConfigurationAdmin configurationAdmin) {
        mapper.configure(MapperFeature.PROPAGATE_TRANSIENT_MARKER, true);
        mapper.addMixIn(RepositoryAddress.class, RepositoryAddressMixin.class);
        SimpleModule module = new SimpleModule();
        module.addSerializer(DefaultWorkspaceFilter.class, new DefaultWorkspaceFilterSerializer());
        module.addDeserializer(WorkspaceFilter.class, new WorkspaceFilterDeserializer());
        mapper.registerModule(module);
        mapper.addMixIn(SimpleCredentials.class, SimpleCredentialsMixin.class);

        this.dynLoaderMgr = dynLoaderMgr;
        this.dataFile = bundleContext.getDataFile(TASKS_DATA_FILE_NAME);
        Configuration configuration;
        try {
            configuration = configurationAdmin.getConfiguration(PID);
            tasks = loadTasks(configuration.getProperties(), dataFile);
        } catch (IOException e) {
            log.error("Could not restore previous tasks", e);
            configuration = null;
        }
        this.configuration = configuration;
    }

    @Deactivate
    void deactivate() throws IOException, RepositoryException {
        log.info("RcpTaskManager deactivated. Stopping running tasks...");
        for (RcpTask task : tasks.values()) {
            task.stop();
        }
        log.info("RcpTaskManager deactivated. Stopping running tasks...done.");
    }
    
    private void persistTasks() {
        Dictionary<String, Object> configProperties = new Hashtable<>();
        try {
            persistTasks(configProperties, dataFile);
            configuration.update(configProperties);
            log.info("Persisted RCP tasks in OSGi configuration");
        } catch (RepositoryException | IOException e) {
            throw new IllegalStateException("Could not persist tasks", e);
        }
    }

    private SortedMap<String, RcpTaskImpl> loadTasks(Dictionary<String, Object> configProperties, File dataFile) throws IOException {
        if (configProperties == null) {
            log.info("No previously persisted tasks found in OSGi configuation");
            return new TreeMap<>();
        }
        String serializedTasks = (String) configProperties.get(PROP_TASKS_SERIALIZATION);
        if (serializedTasks == null) {
            log.info("No previously persisted tasks found in OSGi configuation");
            return new TreeMap<>();
        }
        SortedMap<String, RcpTaskImpl> tasks = mapper.readValue(serializedTasks, new TypeReference<SortedMap<String, RcpTaskImpl>>() {});
        // additionally load credentials data from bundle context
        if (dataFile != null && dataFile.exists()) {
            loadTasksCredentials(tasks, dataFile);
        } else {
            log.info("No previously persisted task credentials found at '{}'", dataFile);
        }
        return tasks;
    }

    private void loadTasksCredentials(Map<String, RcpTaskImpl> tasks, File dataFile) throws IOException {
        Properties props = new Properties();
        try (FileInputStream inputStream = new FileInputStream(dataFile)) {
            props.load(inputStream);
        }
        for (RcpTaskImpl task : tasks.values()) {
            String serializedCredentials = props.getProperty(task.getId());
            if (serializedCredentials != null) {
                Credentials credentials = mapper.readValue(serializedCredentials, SimpleCredentials.class);
                task.setSrcCreds(credentials);
            }
        }
    }

    private void persistTasks(Dictionary<String, Object> configProperties, File dataFile) throws RepositoryException, JsonGenerationException, JsonMappingException, IOException {
        String serializedTasks = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(tasks);
        configProperties.put(PROP_TASKS_SERIALIZATION, serializedTasks);

        // additionally persist the sensitive data in a data file
        if (dataFile != null) {
            persistTasksCredentials(dataFile);
        }
        log.info("Persisted sensitive part of RCP tasks in '{}'", dataFile);
    }

    private void persistTasksCredentials(File dataFile) throws IOException {
        // persist credentials of tasks to data file
        Properties props = new Properties();
        for (RcpTaskImpl task : tasks.values()) {
            // include type information
            String value = mapper.writeValueAsString(task.getSrcCreds());
            props.setProperty(task.getId(), value);
        }
        try (FileOutputStream output = new FileOutputStream(dataFile)) {
            props.store(output, "Credentials used for Apache Jackrabbit FileVault RCP");
        }
    }

    public RcpTask getTask(String taskId) {
        return tasks.get(taskId);
    }

    public Map<String, RcpTask> getTasks() {
        return Collections.unmodifiableMap(tasks);
    }

    @Override
    public RcpTask addTask(RepositoryAddress src, Credentials srcCreds, String dst, String id, List<String> excludes, boolean recursive)
            throws ConfigurationException {
        if (id != null && id.length() > 0 && tasks.containsKey(id)) {
            throw new IllegalArgumentException("Task with id " + id + " already exists.");
        }
        RcpTaskImpl task = new RcpTaskImpl(getDynamicClassLoader(), src, srcCreds, dst, id, excludes, recursive);
        tasks.put(task.getId(), task);
        persistTasks();
        return task;
    }

    @Override
    public RcpTask addTask(RepositoryAddress src, Credentials srcCreds, String dst, String id, WorkspaceFilter srcFilter,
            boolean recursive) {
        if (id != null && id.length() > 0 && tasks.containsKey(id)) {
            throw new IllegalArgumentException("Task with id " + id + " already exists.");
        }
        RcpTaskImpl task = new RcpTaskImpl(getDynamicClassLoader(), src, srcCreds, dst, id, srcFilter, recursive);
        tasks.put(task.getId(), task);
        persistTasks();
        return task;
    }

    public boolean removeTask(String taskId) {
        RcpTask rcpTask = tasks.remove(taskId);
        if (rcpTask != null) {
            rcpTask.stop();
            return true;
        }
        return false;
    }

    protected ClassLoader getDynamicClassLoader() {
        return dynLoaderMgr.getDynamicClassLoader();
    }
}
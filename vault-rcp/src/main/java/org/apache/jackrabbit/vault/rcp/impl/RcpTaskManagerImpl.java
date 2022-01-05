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
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.jcr.Credentials;
import javax.jcr.RepositoryException;
import javax.jcr.SimpleCredentials;

import org.apache.jackrabbit.spi2dav.ConnectionOptions;
import org.apache.jackrabbit.vault.fs.api.RepositoryAddress;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.fs.config.ConfigurationException;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.rcp.RcpTask;
import org.apache.jackrabbit.vault.rcp.RcpTaskManager;
import org.apache.jackrabbit.vault.util.RepositoryCopier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.framework.BundleContext;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
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

    SortedMap<String, RcpTaskImpl> tasks;

    private File dataFile;

    private final ObjectMapper mapper = new ObjectMapper();
    
    private Configuration configuration;
    
    @Reference
    ConfigurationAdmin configurationAdmin;

    /** the serialized tasks which have been processed (for detecting relevant updates) */
    private String serializedTasks;
    
    
    
    @Activate
    void activate(BundleContext bundleContext, Map <String, Object> newConfigProperties) throws IOException {
        mapper.configure(MapperFeature.PROPAGATE_TRANSIENT_MARKER, true);
        mapper.addMixIn(RepositoryAddress.class, RepositoryAddressMixin.class);
        SimpleModule module = new SimpleModule();
        module.addSerializer(DefaultWorkspaceFilter.class, new DefaultWorkspaceFilterSerializer());
        module.addDeserializer(WorkspaceFilter.class, new WorkspaceFilterDeserializer());
        mapper.registerModule(module);
        mapper.addMixIn(SimpleCredentials.class, SimpleCredentialsMixin.class);
        mapper.addMixIn(ConnectionOptions.class, ConnectionOptionsMixin.class);
        mapper.addMixIn(RepositoryCopier.class, RepositoryCopierMixin.class);
        this.dataFile = bundleContext.getDataFile(TASKS_DATA_FILE_NAME);
        this.configuration = configurationAdmin.getConfiguration(PID);
        try {
            tasks = loadTasks((String)newConfigProperties.get(PROP_TASKS_SERIALIZATION), dataFile);
        } catch (IOException e) {
            log.error("Could not restore previous tasks", e);
            tasks = new TreeMap<>();
        }
    }

    // default constructor, used by DS 1.3
    public RcpTaskManagerImpl() {
        
    }

    // alternative constructor, currently only used for testing
    public RcpTaskManagerImpl(BundleContext bundleContext, ConfigurationAdmin configurationAdmin, Map <String, Object> newConfigProperties) throws IOException {
        this.configurationAdmin = configurationAdmin;
        activate(bundleContext, newConfigProperties);
    }

    @Deactivate
    void deactivate() throws IOException, RepositoryException {
        log.info("RcpTaskManager deactivated. Stopping running tasks...");
        for (RcpTask task : tasks.values()) {
            task.stop();
        }
        // necessary again, because tasks are not fully immutable (i.e. may be modified after addTask or editTask has been called)
        persistTasks();
        log.info("RcpTaskManager deactivated. Stopping running tasks...done.");
    }

    @Modified
    void modified(Map <String, Object> newConfigProperties) throws IOException {
        this.configuration = configurationAdmin.getConfiguration(PID);
        // might be triggered internally or externally
        // only external events are relevant
        if (serializedTasks == null || !serializedTasks.equals(newConfigProperties.get(PROP_TASKS_SERIALIZATION))) {
            log.info("Detected external properties change");
            tasks = loadTasks((String) newConfigProperties.get(PROP_TASKS_SERIALIZATION), null);
        }
    }

    static Map<String, Object> createMapFromDictionary(Dictionary<String, Object> dictionary) {
        // filter out irrelevant properties
        List<String> keys = Collections.list(dictionary.keys());
        return keys.stream().collect(Collectors.toMap(Function.identity(), dictionary::get));
    }

    private SortedMap<String, RcpTaskImpl> loadTasks(String serializedTasks, File dataFile) throws IOException {
        if (serializedTasks != null && serializedTasks.isEmpty()) {
            log.info("No previously persisted tasks found in OSGi configuation");
            return new TreeMap<>();
        }
        if (serializedTasks == null) {
            log.info("No previously persisted tasks found in OSGi configuation");
            return new TreeMap<>();
        }
        SortedMap<String, RcpTaskImpl> tasks = mapper.readValue(serializedTasks, new TypeReference<SortedMap<String, RcpTaskImpl>>() {});
        validateTasks(tasks);
        // additionally load credentials data from bundle context
        if (dataFile != null && dataFile.exists()) {
            loadTasksCredentials(tasks, dataFile);
        } else {
            log.info("No previously persisted task credentials found at '{}'", dataFile);
        }
        this.serializedTasks = serializedTasks;
        return tasks;
    }

    void validateTasks(SortedMap<String, RcpTaskImpl> tasks) {
        for (Map.Entry<String, RcpTaskImpl> entry : tasks.entrySet()) {
            // make sure id of map entry is task id
            if (!entry.getKey().equals(entry.getValue().getId())) {
                throw new IllegalArgumentException("Id of entry " + entry.getKey() + " does not match its task id " + entry.getValue().getId());
            }
            // set classloader to use for retrieving the RepositoryImpl
            entry.getValue().setClassLoader(getClassLoaderForRepositoryFactory());
        }
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
                task.setSourceCredentials(credentials);
            }
        }
    }

    private void persistTasks() {
        Dictionary<String, Object> configProperties = new Hashtable<>();
        try {
            persistTasks(configProperties, dataFile);
            configuration.updateIfDifferent(configProperties);
            log.info("Persisted RCP tasks in OSGi configuration");
        } catch (RepositoryException | IOException e) {
            throw new IllegalStateException("Could not persist tasks", e);
        }
    }

    private void persistTasks(Dictionary<String, Object> configProperties, File dataFile) throws RepositoryException, JsonGenerationException, JsonMappingException, IOException {
        serializedTasks = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(tasks);
        configProperties.put(PROP_TASKS_SERIALIZATION, serializedTasks);

        // additionally persist the sensitive data in a data file
        if (dataFile != null) {
            persistTasksCredentials(dataFile);
        }
        log.info("Persisted sensitive part of RCP tasks in '{}'", dataFile);
    }

    private void persistTasksCredentials() {
        try {
            persistTasksCredentials(dataFile);
        } catch (IOException e) {
            throw new IllegalStateException("Could not persist tasks credentials", e);
        }
    }

    private void persistTasksCredentials(File dataFile) throws IOException {
        // persist credentials of tasks to data file
        Properties props = new Properties();
        for (RcpTaskImpl task : tasks.values()) {
            // include type information
            String value = mapper.writeValueAsString(task.getSourceCredentials());
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
    public RcpTask addTask(RepositoryAddress src, ConnectionOptions connectionOptions, Credentials srcCreds, String dst, String id, List<String> excludes, @Nullable Boolean recursive)
            throws ConfigurationException {
        if (id != null && id.length() > 0 && tasks.containsKey(id)) {
            throw new IllegalArgumentException("Task with id " + id + " already exists.");
        }
        RcpTaskImpl task = new RcpTaskImpl(getClassLoaderForRepositoryFactory(), src, connectionOptions, srcCreds, dst, id, excludes, recursive);
        tasks.put(task.getId(), task);
        persistTasks();
        return task;
    }

    @Override
    public RcpTask addTask(RepositoryAddress src, ConnectionOptions connectionOptions, Credentials srcCreds, String dst, String id, WorkspaceFilter srcFilter,
            @Nullable Boolean recursive) {
        if (id != null && id.length() > 0 && tasks.containsKey(id)) {
            throw new IllegalArgumentException("Task with id " + id + " already exists.");
        }
        RcpTaskImpl task = new RcpTaskImpl(getClassLoaderForRepositoryFactory(), src, connectionOptions, srcCreds, dst, id, srcFilter, recursive);
        tasks.put(task.getId(), task);
        persistTasks();
        return task;
    }

    @Override
    public RcpTask editTask(@NotNull String taskId, @Nullable RepositoryAddress src, @Nullable ConnectionOptions connectionOptions, @Nullable Credentials srcCreds, @Nullable String dst, @Nullable List<String> excludes,
            @Nullable WorkspaceFilter srcFilter, @Nullable Boolean recursive) throws ConfigurationException {
        RcpTaskImpl oldTask = tasks.get(taskId);
        if (oldTask == null) {
            throw new IllegalArgumentException("No such task with id='" + taskId + "'");
        }
        RcpTaskImpl newTask = new RcpTaskImpl(oldTask, src, connectionOptions, srcCreds, dst, excludes, srcFilter, recursive);
        tasks.put(taskId, newTask);
        persistTasks();
        return newTask;
    }

    @Override
    public boolean removeTask(String taskId) {
        RcpTask rcpTask = tasks.remove(taskId);
        if (rcpTask != null) {
            rcpTask.stop();
            persistTasks();
            return true;
        }
        return false;
    }

    @Override
    public void setSourceCredentials(String taskId, Credentials srcCreds) {
        RcpTaskImpl task = tasks.get(taskId);
        if (task == null) {
            throw new IllegalArgumentException("No such task with id='" + taskId + "'");
        }
        task.setSourceCredentials(srcCreds);
        persistTasksCredentials();
    }

    protected ClassLoader getClassLoaderForRepositoryFactory() {
        // everything is embedded in the current bundle, therefore just take the bundle classloader
        return this.getClass().getClassLoader();
    }
}
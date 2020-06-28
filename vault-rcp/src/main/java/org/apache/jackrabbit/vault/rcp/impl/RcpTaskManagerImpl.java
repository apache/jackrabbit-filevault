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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.jcr.Credentials;
import javax.jcr.ItemNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.SimpleCredentials;

import org.apache.jackrabbit.vault.fs.api.RepositoryAddress;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.fs.config.ConfigurationException;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.rcp.RcpTask;
import org.apache.jackrabbit.vault.rcp.RcpTaskManager;
import org.apache.sling.api.resource.observation.ResourceChange;
import org.apache.sling.api.resource.observation.ResourceChangeListener;
import org.apache.sling.commons.classloader.DynamicClassLoaderManager;
import org.apache.sling.jcr.api.SlingRepository;
import org.osgi.framework.BundleContext;
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
@Component(property = { ResourceChangeListener.CHANGES+"=REMOVED", 
        ResourceChangeListener.CHANGES+"=ADDED",
        ResourceChangeListener.CHANGES+"=CHANGED"}
)
@ServiceVendor("The Apache Software Foundation")
@Designate(ocd = RcpTaskManagerImpl.Configuration.class)
public class RcpTaskManagerImpl implements RcpTaskManager, ResourceChangeListener {

    @ObjectClassDefinition(name = "Apache Jackrabbit FileVault RCP Task Manager", description = "Manages tasks for RCP (remote copy)")
    public static @interface Configuration {
        @AttributeDefinition(name = "Configuration Node Path", description = "The absolute node path where to store the tasks in the repository. Credentials are not stored in that node, but rather in the bundle context data file.")
        String resource_paths() default "/conf/rcptaskmanagerimpl/tasks";
    }

    private static final String TASKS_DATA_FILE_NAME = "tasks";
    private static final String MIMETYPE_JSON = "application/json";

    /** default logger */
    private static final Logger log = LoggerFactory.getLogger(RcpTaskManagerImpl.class);

    private final DynamicClassLoaderManager dynLoaderMgr;

    Map<String, RcpTaskImpl> tasks;

    private final File dataFile;

    private final ObjectMapper mapper = new ObjectMapper();

    private final Configuration configuration;

    private final SlingRepository repository;

    @Activate
    public RcpTaskManagerImpl(BundleContext bundleContext, Configuration configuration, @Reference DynamicClassLoaderManager dynLoaderMgr,
            @Reference SlingRepository repository) {
        mapper.configure(MapperFeature.PROPAGATE_TRANSIENT_MARKER, true);
        mapper.addMixIn(RepositoryAddress.class, RepositoryAddressMixin.class);
        SimpleModule module = new SimpleModule();
        module.addSerializer(DefaultWorkspaceFilter.class, new DefaultWorkspaceFilterSerializer());
        module.addDeserializer(WorkspaceFilter.class, new WorkspaceFilterDeserializer());
        mapper.registerModule(module);
        mapper.addMixIn(SimpleCredentials.class, SimpleCredentialsMixin.class);

        this.configuration = configuration;
        this.dynLoaderMgr = dynLoaderMgr;
        this.repository = repository;
        this.dataFile = bundleContext.getDataFile(TASKS_DATA_FILE_NAME);
        try {
            tasks = loadTasks(dataFile);
            return;
        } catch (ItemNotFoundException e) {
            log.info("No previously persisted tasks found at '{}'", configuration.resource_paths());
            log.debug("No previously persisted tasks found!", e);
        } catch (IOException | RepositoryException e) {
            log.error("Could not restore previous tasks", e);
        }
        tasks = new LinkedHashMap<>();
    }

    @Deactivate
    void deactivate() throws IOException, RepositoryException {
        log.info("RcpTaskManager deactivated. Stopping running tasks...");
        for (RcpTask task : tasks.values()) {
            task.stop();
        }
        log.info("RcpTaskManager deactivated. Stopping running tasks...done.");
        persistTasks(dataFile);
    }

    InputStream getInputStream() throws RepositoryException, IOException {
        return new FileNodeBackedInputStream(repository, configuration.resource_paths());
    }

    private Map<String, RcpTaskImpl> loadTasks(File dataFile) throws IOException, RepositoryException {
        try (InputStream input = getInputStream()) {
            tasks = mapper.readValue(input, new TypeReference<Map<String, RcpTaskImpl>>() {
            });
        }
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

    OutputStream getOutputStream() throws RepositoryException, IOException {
        return new FileNodeBackedOutputStream(repository, configuration.resource_paths(), MIMETYPE_JSON);
    }

    private void persistTasks(File dataFile) throws RepositoryException, JsonGenerationException, JsonMappingException, IOException {
        try (OutputStream out = getOutputStream()) {
            mapper.writerWithDefaultPrettyPrinter().writeValue(out, tasks);
        }
        log.info("Persisted RCP tasks in '{}'", configuration.resource_paths());

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

    @Override
    public void onChange(List<ResourceChange> changes) {
        try {
            loadTasks(dataFile);
        } catch (IOException | RepositoryException e) {
            log.error("Could not load tasks from persisted data", e);
        }
    }
}
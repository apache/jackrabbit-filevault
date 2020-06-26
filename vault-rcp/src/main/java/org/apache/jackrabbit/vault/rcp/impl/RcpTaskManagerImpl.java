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
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.jcr.Credentials;

import org.apache.jackrabbit.vault.fs.api.RepositoryAddress;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.fs.config.ConfigurationException;
import org.apache.jackrabbit.vault.rcp.RcpTask;
import org.apache.jackrabbit.vault.rcp.RcpTaskManager;
import org.apache.sling.commons.classloader.DynamicClassLoaderManager;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** {@code RcpTaskManager}... */
@Component(immediate = true, property = { "service.vendor=The Apache Software Foundation" })
public class RcpTaskManagerImpl implements RcpTaskManager {

    private static final String TASKS_DATA_FILE_NAME = "tasks";
    /** default logger */
    private static final Logger log = LoggerFactory.getLogger(RcpTaskManagerImpl.class);

    private final DynamicClassLoaderManager dynLoaderMgr;

    Map<String, RcpTaskImpl> tasks;
    
    private final File dataFile;

    @Activate
    public RcpTaskManagerImpl(BundleContext bundleContext, @Reference DynamicClassLoaderManager dynLoaderMgr) {
        this.dynLoaderMgr = dynLoaderMgr;
        // load tasks from data file
        dataFile = bundleContext.getDataFile(TASKS_DATA_FILE_NAME);
        if (dataFile == null) {
            log.warn("No filesystem support of OSGi provided, persisting/loading tasks not possible!");
            return;
        }
        try (FileInputStream fis = new FileInputStream (dataFile);
            ObjectInputStream ois = new ObjectInputStream (fis)) {
            tasks = Map.class.cast(ois.readObject());
            for (RcpTaskImpl task : tasks.values()) {
                task.setClassLoader(getDynamicClassLoader());
            }
            log.info("Loaded RCP tasks from '{}'", dataFile);
        } catch (ClassNotFoundException | IOException e) {
            log.error("Could not restore previous tasks", e);
            tasks = new LinkedHashMap<>();
        }
    }

    @Deactivate
    void deactivate() throws IOException {
        log.info("RcpTaskManager deactivated. Stopping running tasks...");
        for (RcpTask task : tasks.values()) {
            task.stop();
        }
        log.info("RcpTaskManager deactivated. Stopping running tasks...done.");
        if (dataFile != null) {
            try (FileOutputStream fos = new FileOutputStream (dataFile);
                 ObjectOutputStream oos = new ObjectOutputStream (fos)) {
                   oos.writeObject(tasks);
            }
            log.info("Persisted RCP tasks in '{}'", dataFile);
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
}
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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.jcr.Credentials;

import org.apache.jackrabbit.vault.fs.api.RepositoryAddress;
import org.apache.jackrabbit.vault.rcp.RcpTask;
import org.apache.jackrabbit.vault.rcp.RcpTaskManager;
import org.apache.sling.commons.classloader.DynamicClassLoaderManager;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@code RcpTaskManager}...
 */
@Component(
        immediate = true,
        property = {"service.vendor=The Apache Software Foundation"}
)
public class RcpTaskManagerImpl implements RcpTaskManager {

    /**
     * default logger
     */
    private static final Logger log = LoggerFactory.getLogger(RcpTaskManagerImpl.class);

    @Reference
    private DynamicClassLoaderManager dynLoaderMgr;

    private final Map<String, RcpTaskImpl> tasks = new LinkedHashMap<>();

    @Deactivate
    private void deactivate() {
        log.info("RcpTaskManager deactivated. Stopping running tasks...");
        for (String id : tasks.keySet()) {
            removeTask(id);
        }
        log.info("RcpTaskManager deactivated. Stopping running tasks...done.");
    }

    public RcpTask getTask(String taskId) {
        return tasks.get(taskId);
    }

    public Map<String, RcpTask> getTasks() {
        return Collections.unmodifiableMap(tasks);
    }

    public RcpTask addTask(RepositoryAddress src, Credentials srcCreds, String dst, String id) {
        if (id != null && id.length() > 0 && tasks.containsKey(id)) {
            throw new IllegalArgumentException("Task with id " + id + " already exists.");
        }
        RcpTaskImpl task = new RcpTaskImpl(this, src, srcCreds, dst, id);
        tasks.put(task.getId(), task);
        return task;
    }

    public boolean removeTask(String taskId)  {
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
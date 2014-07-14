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

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.jackrabbit.vault.fs.api.RepositoryAddress;
import org.apache.sling.commons.classloader.DynamicClassLoaderManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <code>RcpTaskManager</code>...
 */
@Component
@Service(value = RcpTaskManager.class)
public class RcpTaskManagerImpl implements RcpTaskManager {

    /**
     * default logger
     */
    private static final Logger log = LoggerFactory.getLogger(RcpTaskManagerImpl.class);

    @Reference
    private DynamicClassLoaderManager dynLoaderMgr;

    private final Map<String, RcpTask> tasks = new LinkedHashMap<String, RcpTask>();

    @Deactivate
    private void deactivate() {
        log.info("RcpTaskManager deactivated. Stopping running tasks...");
        while (!tasks.isEmpty()) {
            tasks.values().iterator().next().remove();
        }
        log.info("RcpTaskManager deactivated. Stopping running tasks...done.");
    }

    public RcpTask getTask(String taskId) {
        return tasks.get(taskId);
    }

    public Map<String, RcpTask> getTasks() {
        return Collections.unmodifiableMap(tasks);
    }

    public RcpTask addTask(RepositoryAddress src, String dst, String id) {
        if (id != null && id.length() > 0 && tasks.containsKey(id)) {
            throw new IllegalArgumentException("Task with id " + id + " already exists.");
        }
        RcpTask task = new RcpTask(this, src, dst, id);
        tasks.put(task.getId(), task);
        return task;
    }

    protected void remove(RcpTask task)  {
        tasks.remove(task.getId());
    }

    protected ClassLoader getDynamicClassLoader() {
        return dynLoaderMgr.getDynamicClassLoader();
    }
}
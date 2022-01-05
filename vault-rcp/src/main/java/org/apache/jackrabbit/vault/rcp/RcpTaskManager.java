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
package org.apache.jackrabbit.vault.rcp;

import java.util.List;
import java.util.Map;

import javax.jcr.Credentials;

import org.apache.jackrabbit.spi2dav.ConnectionOptions;
import org.apache.jackrabbit.vault.fs.api.RepositoryAddress;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.fs.config.ConfigurationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@code RcpTaskManager}...
 */
public interface RcpTaskManager {

    RcpTask getTask(String taskId);

    Map<String, RcpTask> getTasks();

    boolean removeTask(String taskId);

    RcpTask addTask(RepositoryAddress src, ConnectionOptions connectionOptions, Credentials srcCreds, String dst, String id, WorkspaceFilter srcFilter, @Nullable Boolean recursive);

    RcpTask addTask(RepositoryAddress src, ConnectionOptions connectionOptions, Credentials srcCreds, String dst, String id, List<String> excludes, @Nullable Boolean recursive)
            throws ConfigurationException;

    void setSourceCredentials(@NotNull String taskId, Credentials srcCreds);

    RcpTask editTask(@NotNull String taskId, @Nullable RepositoryAddress src, @Nullable ConnectionOptions connectionOptions, @Nullable Credentials srcCreds, @Nullable String dst,
            @Nullable List<String> excludes, @Nullable WorkspaceFilter srcFilter, @Nullable Boolean recursive)
            throws ConfigurationException;

}
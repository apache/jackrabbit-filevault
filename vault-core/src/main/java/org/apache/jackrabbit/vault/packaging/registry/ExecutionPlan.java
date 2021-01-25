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
package org.apache.jackrabbit.vault.packaging.registry;

import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.osgi.annotation.versioning.ProviderType;

/**
 * Holds a list of tasks that perform package installation related operations.
 */
@ProviderType
public interface ExecutionPlan {

    /**
     * An id of the execution plan.
     * @return the id.
     */
    @NotNull
    String getId();

    /**
     * Retrieves the list of all tasks
     * @return the tasks.
     */
    @NotNull
    List<PackageTask> getTasks();

    /**
     * Checks if this plan has finished.
     * @return {@code true} if executed.
     */
    boolean isExecuted();

    /**
     * Checks if execution of any of the tasks in the plan resulted in an error.
     * Only returns a valid result in case {@link #isExecuted()} returns {@code true}.
     * The concrete errors must be looked up from {@link #getTasks()}.
     * @return {@code true} if it has errors.
     */
    boolean hasErrors();
}

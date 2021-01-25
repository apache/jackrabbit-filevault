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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;

import javax.jcr.Session;

import org.apache.jackrabbit.vault.fs.api.ProgressTrackerListener;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.jetbrains.annotations.NotNull;
import org.osgi.annotation.versioning.ProviderType;

/**
 * Helps to construct an execution plan that can be serialized and given to the packaging backend for execution.
 */
@ProviderType
public interface ExecutionPlanBuilder {

    /**
     * Loads the tasks from a serialized plan and replaces the plans already in this builder.
     * @param in input stream to the data.
     * @return this
     * @throws IOException if an I/O error occurrs.
     */
    @NotNull
    ExecutionPlanBuilder load(@NotNull InputStream in) throws IOException;

    /**
     * Serializes the tasks of this plan.
     * @param out the output stream
     * @return this
     * @throws IOException if an I/O error occurrs.
     * @throws PackageException if this builder does not have valid tasks.
     */
    @NotNull
    ExecutionPlanBuilder save(@NotNull OutputStream out) throws IOException, PackageException;

    /**
     * Adds a new task to this builder.
     * @return an package task builder that helps to assemble the task.
     */
    @NotNull
    PackageTaskBuilder addTask();

    /**
     * Validates this plan.
     * @return this
     * @throws IOException if an I/O error occurrs.
     * @throws PackageException if the plan is not valid.
     */
    @NotNull
    ExecutionPlanBuilder validate() throws IOException, PackageException;

    /**
     * Sets the JCR session for this execution plan.
     * @param session the session
     * @return this
     */
    @NotNull
    ExecutionPlanBuilder with(@NotNull Session session);

    /**
     * Sets the progress tracker listener for this plan.
     * @param listener the listener
     * @return this
     */
    @NotNull
    ExecutionPlanBuilder with(@NotNull ProgressTrackerListener listener);

    
    /**
     * Sets packages handled externally ahead of execution for prevalidation of plan
     * @param externalPackages Set of package ids handled by other builder
     * @return this
     */
    @NotNull
    ExecutionPlanBuilder with(@NotNull Set<PackageId> externalPackages);

    /**
     * Triggers validation and returns {@link PackageId}s of all packages to be installed
     * by this builder
     * @return Set of packages to be installed by this builder.
     * @throws IOException if an I/O error occurs.
     * @throws PackageException if the plan is not valid.
     */
    @NotNull
    Set<PackageId> preview() throws IOException, PackageException;
    
    /**
     * Builds an executes the plan synchronously. Does not throw an exception in case one or 
     * multiple task executions fail, therefore the caller should check {@link ExecutionPlan#hasErrors()} 
     * after calling this method.
     * 
     * @return the execution plan.
     * @throws IOException if an I/O error occurs.
     * @throws PackageException if the plan contains an unsupported task.
     */
    @NotNull
    ExecutionPlan execute() throws IOException, PackageException;
}

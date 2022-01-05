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

import org.apache.jackrabbit.vault.packaging.PackageId;
import org.jetbrains.annotations.NotNull;
import org.osgi.annotation.versioning.ProviderType;

/**
 * {@code ExecutionTaskBuilder}...
 */
@ProviderType
public interface PackageTaskBuilder {

    /**
     * Sets the package id of this task.
     * @param id the package id
     * @return this.
     */
    @NotNull
    PackageTaskBuilder with(@NotNull PackageId id);

    /**
     * Sets the type of this task
     * @param type the type
     * @return the parent execution plan builder.
     */
    @NotNull
    ExecutionPlanBuilder with(@NotNull PackageTask.Type type);
    
    /**
     * Set the optional options for the package task
     * @param options the options
     * @return this.
     */
    @NotNull
    PackageTaskBuilder withOptions(@NotNull PackageTaskOptions options);

}

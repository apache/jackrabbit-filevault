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
import org.jetbrains.annotations.Nullable;
import org.osgi.annotation.versioning.ProviderType;

/**
 * Describes a package operation task.
 */
@ProviderType
public interface PackageTask {

    /**
     * Task type
     */
    enum Type {

        /**
         * Package installation
         */
        INSTALL,

        /**
         * Package uninstallation
         */
        UNINSTALL,

        /**
         * Package removal
         */
        REMOVE,

        /**
         * Package extraction
         */
        EXTRACT
    }

    /**
     * Task state
     */
    enum State {
        /**
         * task is new
         */
        NEW,
        /**
         * Task is running
         */
        RUNNING,
        /**
         * Task has completed
         */
        COMPLETED,
        /**
         * Task has errors
         */
        ERROR
    }

    /**
     * Returns the package id of this task.
     * @return the package id.
     */
    @NotNull
    PackageId getPackageId();

    /**
     * Returns the task type.
     * @return the task type.
     */
    @NotNull
    Type getType();

    /**
     * Returns the task optional options.
     * @return the task options (may be null).
     */
    @Nullable
    PackageTaskOptions getOptions();

    /**
     * Returns the task state
     * @return the task state
     */
    @NotNull
    State getState();

    /**
     * Returns the error if there was one.
     * @return the error
     */
    @Nullable
    Throwable getError();
}

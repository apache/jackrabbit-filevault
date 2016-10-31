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

package org.apache.jackrabbit.vault.packaging;

/**
 * Defines how package dependencies influence package installation and un-installation.
 */
public enum DependencyHandling {

    /**
     * No dependency checks are enforced
     */
    IGNORE,

    /**
     * Dependency checks are performed but not enforced. If a dependency is present but not installed, it will be
     * installed prior to installing the referencing issue. However the installation will proceed, even if the dependency is missing.
     *
     * Un-installation will automatically uninstall referencing packages.
     */
    BEST_EFFORT,

    /**
     * Dependency checks are performed but not enforced. If a dependency is present but not installed, it will be
     * installed prior to installing the referencing issue. If a dependency is not present, installation fails.
     *
     * Un-installation will automatically uninstall referencing packages.
     */
    REQUIRED,

    /**
     * Full dependency checks are enforced. Packages with missing or uninstalled dependencies are not installed and
     * packages that are dependencies of other packages cannot be un-installed.
     */
    STRICT

}

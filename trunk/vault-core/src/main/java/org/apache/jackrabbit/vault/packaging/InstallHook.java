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
 * An install hook is used to do some pre and post work during a package
 * install. the hooks need to have at least one class that implements this
 * interface. the class is identified by the normal "Main-Class" manifest
 * property and needs to be instantiatable. The instantiated hook class is
 * used for the entire life-cycle of the installation process until the
 * {@link InstallContext.Phase#END} phase.
 *
 * The hook jars need to be placed in the "META-INF/vault/hooks" directory
 * and are executed in alphabetical sequence for each installation phase.
 * A hook can throw a {@link PackageException} to abort the current phase,
 * but this has currently only an effect in the
 * {@link InstallContext.Phase#PREPARE} phase. If a hook fails, the current
 * phase is aborted and all hooks (also the failing one) are called again with
 * the respective "fail" phase.
 */
public interface InstallHook {

    /**
     * Executes hook specific code. This is called for each installation
     * phase.
     *
     * @param context the installation context
     * @throws PackageException if the hook desires to abort the current phase.
     */
    void execute(InstallContext context) throws PackageException;
}
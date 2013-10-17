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

import java.io.IOException;

import org.apache.jackrabbit.vault.fs.api.VaultInputSource;
import org.apache.jackrabbit.vault.fs.io.Archive;

/**
 * The install hook processor is used for handle the install hooks, from registration to execution.
 */
public interface InstallHookProcessor {

    /**
     * Register all hooks found in the given archive.
     * @param archive the archive.
     * @param classLoader the class loader
     * @throws PackageException if an error occurs.
     */
    void registerHooks(Archive archive, ClassLoader classLoader) throws PackageException;

    /**
     * Register the hook provided by the given input source.
     * @param input a vault input source containing the jar file of the install hook
     * @param classLoader the class loader
     * @throws IOException if an I/O error occurs
     * @throws PackageException if an error occurs.
     */
    void registerHook(VaultInputSource input, ClassLoader classLoader) throws IOException, PackageException;

    /**
     * Checks if this process has any hooks registered.
     * @return <code>true</code> if there are hooks registered.
     */
    boolean hasHooks();

    /**
     * Executes the registered hooks with the current {@link InstallContext.Phase}.
     * @param context the context
     * @return <code>true</code> if successful.
     */
    boolean execute(InstallContext context);
}
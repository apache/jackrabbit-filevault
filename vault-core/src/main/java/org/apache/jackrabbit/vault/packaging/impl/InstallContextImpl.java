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

package org.apache.jackrabbit.vault.packaging.impl;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.jackrabbit.vault.fs.io.ImportOptions;
import org.apache.jackrabbit.vault.fs.io.Importer;
import org.apache.jackrabbit.vault.packaging.InstallContext;
import org.apache.jackrabbit.vault.packaging.InstallHookProcessor;
import org.apache.jackrabbit.vault.packaging.VaultPackage;

/**
 * <code>InstallContextImpl</code>...
 */
public class InstallContextImpl implements InstallContext {

    private final Session session;

    private final Node importRoot;

    private final VaultPackage pack;

    private Phase phase = Phase.PREPARE;

    private final Importer importer;

    private final InstallHookProcessor hooks;

    public InstallContextImpl(Node importRoot, VaultPackage pack,
                              Importer importer, InstallHookProcessor hooks)
            throws RepositoryException {
        this.session = importRoot.getSession();
        this.importRoot = importRoot;
        this.pack = pack;
        this.importer = importer;
        this.hooks = hooks;
    }

    public Session getSession() {
        return session;
    }

    public VaultPackage getPackage() {
        return pack;
    }

    public ImportOptions getOptions() {
        return importer.getOptions();
    }

    public Phase getPhase() {
        return phase;
    }

    protected void setPhase(Phase phase) {
        this.phase = phase;
    }

    protected Importer getImporter() {
        return importer;
    }

    protected InstallHookProcessor getHooks() {
        return hooks;
    }

    protected Node getImportRoot() {
        return importRoot;
    }
}
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
package org.apache.jackrabbit.vault.packaging.registry.impl;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Calendar;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.jcr.RepositoryException;

import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.packaging.Dependency;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.PackageProperties;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.apache.jackrabbit.vault.packaging.registry.RegisteredPackage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registry package based on the {@link FSPackageRegistry}.
 */
public class FSRegisteredPackage implements RegisteredPackage {

    /**
     * default logger
     */
    private static final Logger log = LoggerFactory.getLogger(FSPackageRegistry.class);


    private FSPackageRegistry registry;

    private VaultPackage vltPkg = null;
        
    private PackageId id;
    private Path filepath;
    private PackageProperties packageProperties;
    private Dependency[] dependencies;
    private WorkspaceFilter filter;

    public FSRegisteredPackage(FSPackageRegistry registry, FSInstallState installState) throws IOException, RepositoryException {
        this.id = installState.getPackageId();
        this.filepath = installState.getFilePath();
        this.dependencies = installState.getDependencies().toArray(new Dependency[installState.getDependencies().size()]);
        this.filter = installState.getFilter();
        this.packageProperties = new FsPackageProperties(installState);
        this.registry = registry;
    }

    @Nonnull
    @Override
    public PackageId getId() {
        return this.id;
    }

    @Nonnull
    @Override
    public VaultPackage getPackage() throws IOException {
        if (this.vltPkg == null) {
            this.vltPkg = registry.open(filepath.toFile());
        }
        return this.vltPkg;
    }

    @Override
    public boolean isInstalled() {
            try {
                return registry.isInstalled(getId());
            } catch (IOException e) {
                log.error("Packagestate couldn't be read for package {}", getId().toString(), e);
                return false;
            }
    }

    @Override
    public long getSize() throws IOException {
        return getPackage().getSize();
    }

    @CheckForNull
    @Override
    public Calendar getInstallationTime() {
        Calendar cal = Calendar.getInstance();
        Long installTime;
        try {
            installTime = registry.getInstallState(getId()).getInstallationTime();
            if (installTime == null) {
                cal = null;
            } else{
                cal.setTimeInMillis(installTime);
            }
        } catch (IOException e) {
            log.error("Could not read package state for package {}.", getId(), e);
            cal = null;
        }
        return cal;
    }

    @Override
    public void close() {
        if (vltPkg != null) {
            vltPkg.close();
            vltPkg = null;
        }
    }

    @Override
    public int compareTo(RegisteredPackage o) {
        return getId().compareTo(o.getId());
    }

    @Override
    public Dependency[] getDependencies() {
        return this.dependencies;
    }

    @Override
    public WorkspaceFilter getWorkspaceFilter() {
        return this.filter;
    }

    @Override
    public PackageProperties getPackageProperties() throws IOException {
        return this.packageProperties;
    }
}
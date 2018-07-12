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

import java.io.File;
import java.io.IOException;
import java.util.Calendar;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.jcr.RepositoryException;

import org.apache.jackrabbit.vault.packaging.JcrPackage;
import org.apache.jackrabbit.vault.packaging.JcrPackageDefinition;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.apache.jackrabbit.vault.packaging.registry.RegisteredPackage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@code JcrRegisteredPackage}...
 */
public class FSRegisteredPackage implements RegisteredPackage {

    /**
     * default logger
     */
    private static final Logger log = LoggerFactory.getLogger(FSPackageRegistry.class);

    private VaultPackage vltPkg;
    private FSPackageRegistry registry;

    public FSRegisteredPackage(FSPackageRegistry registry, VaultPackage vltPkg) throws IOException, RepositoryException {
        this.vltPkg = vltPkg;
        this.registry = registry;
    }

    @Nonnull
    @Override
    public PackageId getId() {
        return vltPkg.getId();
    }

    @Nonnull
    @Override
    public VaultPackage getPackage() throws IOException {
        return vltPkg;
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
    public long getSize() {
        return vltPkg.getSize();
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
        vltPkg.close();
        vltPkg = null;
    }

    @Override
    public int compareTo(RegisteredPackage o) {
        return getId().compareTo(o.getId());
    }
}
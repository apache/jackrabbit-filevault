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
import java.util.Calendar;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.jcr.RepositoryException;

import org.apache.jackrabbit.vault.packaging.JcrPackage;
import org.apache.jackrabbit.vault.packaging.JcrPackageDefinition;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.apache.jackrabbit.vault.packaging.registry.RegisteredPackage;

/**
 * {@code JcrRegisteredPackage}...
 */
public class JcrRegisteredPackage implements RegisteredPackage {

    private JcrPackage pkg;

    private VaultPackage vltPkg;

    public JcrRegisteredPackage(JcrPackage pkg) throws IOException, RepositoryException {
        this.pkg = pkg;
        this.vltPkg = pkg.getPackage();
    }

    public JcrPackage getJcrPackage() {
        return pkg;
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
            return pkg.isInstalled();
        } catch (RepositoryException e) {
            return false;
        }
    }

    @Override
    public long getSize() {
        return pkg.getSize();
    }

    @CheckForNull
    @Override
    public Calendar getInstallationTime() {
        try {
            JcrPackageDefinition def = pkg.getDefinition();
            return def == null ? null : def.getLastUnpacked();
        } catch (RepositoryException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void close() {
        vltPkg.close();
        vltPkg = null;
        pkg.close();
        pkg = null;
    }

    @Override
    public int compareTo(RegisteredPackage o) {
        return getId().compareTo(o.getId());
    }
}
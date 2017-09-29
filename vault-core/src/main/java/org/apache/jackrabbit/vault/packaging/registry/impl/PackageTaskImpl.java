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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.jcr.RepositoryException;

import org.apache.jackrabbit.vault.fs.io.ImportOptions;
import org.apache.jackrabbit.vault.packaging.DependencyHandling;
import org.apache.jackrabbit.vault.packaging.JcrPackage;
import org.apache.jackrabbit.vault.packaging.NoSuchPackageException;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.registry.PackageTask;
import org.apache.jackrabbit.vault.packaging.registry.RegisteredPackage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@code PackageTaskImpl}...
 */
public class PackageTaskImpl implements PackageTask {

    /**
     * default logger
     */
    private static final Logger log = LoggerFactory.getLogger(PackageTaskImpl.class);

    final static PackageTaskImpl MARKER = new PackageTaskImpl(new PackageId("", "" ,""), Type.INSTALL);

    private final PackageId id;

    private final Type type;

    private State state = State.NEW;

    private Throwable error;

    PackageTaskImpl(@Nonnull PackageId id, @Nonnull Type type) {
        this.id = id;
        this.type = type;
    }

    @Nonnull
    @Override
    public PackageId getPackageId() {
        return id;
    }

    @Nonnull
    @Override
    public Type getType() {
        return type;
    }

    @Nonnull
    @Override
    public State getState() {
        return state;
    }

    @Nullable
    @Override
    public Throwable getError() {
        return error;
    }

    @Override
    public String toString() {
        return "PackageTaskImpl{" + "id=" + id +
                ", type=" + type +
                ", state=" + state +
                '}';
    }

    void execute(ExecutionPlanImpl executionPlan) {
        if (state != State.NEW) {
            return;
        }
        state = State.RUNNING;
        try {
            switch (type) {
                case INSTALL:
                    doInstall(executionPlan, false);
                    break;
                case UNINSTALL:
                    doUninstall(executionPlan);
                    break;
                case REMOVE:
                    doRemove(executionPlan);
                    break;
                case EXTRACT:
                    doInstall(executionPlan, true);
                    break;
            }
            state = State.COMPLETED;
        } catch (Exception e) {
            log.info("error during package task {} on {}: {}", type, id, e.toString());
            error  = e;
            state = State.ERROR;
        }
    }

    /**
     * Performs the removal.
     * @param plan the execution plan
     * @throws IOException if an I/O error occurs
     * @throws PackageException if a package error occurs
     */
    private void doRemove(ExecutionPlanImpl plan) throws IOException, PackageException {
        try (RegisteredPackage pkg = plan.getRegistry().open(id)) {
            if (pkg == null) {
                throw new NoSuchPackageException("No such package: " + id);
            }
            if (pkg.isInstalled()) {
                throw new PackageException("refusing to remove installed package: " + id);
            }
            plan.getRegistry().remove(id);
        }
    }

    /**
     * Performs the uninstallation.
     * @param plan the execution plan
     * @throws IOException if an I/O error occurs
     * @throws PackageException if a package error occurs
     */
    private void doUninstall(ExecutionPlanImpl plan) throws IOException, PackageException {
        ImportOptions opts = new ImportOptions();
        opts.setListener(plan.getListener());
        // execution plan resolution already has resolved all dependencies, so there is no need to use best effort here.
        opts.setDependencyHandling(DependencyHandling.STRICT);

        try (RegisteredPackage pkg = plan.getRegistry().open(id)) {
            if (pkg == null) {
                throw new NoSuchPackageException("No such package: " + id);
            }
            if (!(pkg instanceof JcrRegisteredPackage)) {
                throw new PackageException("non jcr packages not supported yet");
            }
            try (JcrPackage jcrPkg = ((JcrRegisteredPackage) pkg).getJcrPackage()){
                jcrPkg.uninstall(opts);
            } catch (RepositoryException e) {
                throw new IOException(e);
            }
        }
    }

    /**
     * Performs the installation.
     * @param plan the execution plan
     * @throws IOException if an I/O error occurs
     * @throws PackageException if a package error occurs
     */
    private void doInstall(ExecutionPlanImpl plan, boolean extract) throws IOException, PackageException {
        ImportOptions opts = new ImportOptions();
        opts.setListener(plan.getListener());
        // execution plan resolution already has resolved all dependencies, so there is no need to use best effort here.
        opts.setDependencyHandling(DependencyHandling.STRICT);

        try (RegisteredPackage pkg = plan.getRegistry().open(id)) {
            if (pkg == null) {
                throw new NoSuchPackageException("No such package: " + id);
            }
            if (!(pkg instanceof JcrRegisteredPackage)) {
                throw new PackageException("non jcr packages not supported yet");
            }
            try (JcrPackage jcrPkg = ((JcrRegisteredPackage) pkg).getJcrPackage()){
                if (extract) {
                    jcrPkg.extract(opts);
                } else {
                    jcrPkg.install(opts);
                }
            } catch (RepositoryException e) {
                throw new IOException(e);
            }
        }
    }


}
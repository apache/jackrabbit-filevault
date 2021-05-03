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
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import javax.jcr.Session;

import org.apache.jackrabbit.vault.fs.io.ImportOptions;
import org.apache.jackrabbit.vault.packaging.Dependency;
import org.apache.jackrabbit.vault.packaging.NoSuchPackageException;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.apache.jackrabbit.vault.packaging.PackageExistsException;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.registry.DependencyReport;
import org.apache.jackrabbit.vault.packaging.registry.ExecutionPlanBuilder;
import org.apache.jackrabbit.vault.packaging.registry.PackageRegistry;
import org.apache.jackrabbit.vault.packaging.registry.RegisteredPackage;
import org.jetbrains.annotations.NotNull;

/**
 * Abstraction for shared methods of PackageRegistry &amp; InternalPackageRegistry implementations
 */
public abstract class AbstractPackageRegistry implements PackageRegistry, InternalPackageRegistry {

    public static final class SecurityConfig {
        private final String[] authIdsForHookExecution;
        private final String[] authIdsForRootInstallation;

        public SecurityConfig(String[] authIdsForHooks, String[] authIdsForRoots) {
            this.authIdsForHookExecution = authIdsForHooks;
            this.authIdsForRootInstallation = authIdsForRoots;
        }

        public String[] getAuthIdsForHookExecution() {
            return authIdsForHookExecution;
        }

        public String[] getAuthIdsForRootInstallation() {
            return authIdsForRootInstallation;
        }
    }
    /**
     * default root path for packages
     */
    public static final String DEFAULT_PACKAGE_ROOT_PATH = "/etc/packages";

    /**
     * Archive root path for packages
     */
    public final static String ARCHIVE_PACKAGE_ROOT_PATH = "/jcr_root" + DEFAULT_PACKAGE_ROOT_PATH;
    
    /**
     * default root path prefix for packages
     */
    public static final String DEFAULT_PACKAGE_ROOT_PATH_PREFIX = DEFAULT_PACKAGE_ROOT_PATH + "/";

    protected @NotNull SecurityConfig securityConfig;

    /** whether package imports should be strict by default (can be overwritten by {@link ImportOptions#setStrict(boolean)})
     * 
     */
    private final boolean isStrictByDefault;

    public AbstractPackageRegistry(SecurityConfig securityConfig, boolean isStrictByDefault) {
        if (securityConfig != null) {
            this.securityConfig = securityConfig;
        } else {
            this.securityConfig = new SecurityConfig(null, null);
        }
        this.isStrictByDefault = isStrictByDefault;
    }

    public boolean isStrictByDefault() {
        return isStrictByDefault;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public abstract void installPackage(Session session, RegisteredPackage pkg, ImportOptions opts, boolean extract)
            throws IOException, PackageException;

    /**
     * {@inheritDoc}
     */
    @Override
    public abstract void uninstallPackage(Session session, RegisteredPackage pkg, ImportOptions opts)
            throws IOException, PackageException;

    /**
     * {@inheritDoc}
     */
    @Override
    public abstract boolean contains(PackageId id) throws IOException;

    /**
     * {@inheritDoc}
     */
    @Override
    public abstract Set<PackageId> packages() throws IOException;

    /**
     * {@inheritDoc}
     */
    @Override
    public abstract RegisteredPackage open(PackageId id) throws IOException;

    /**
     * {@inheritDoc}
     */
    @Override
    public abstract PackageId register(InputStream in, boolean replace) throws IOException, PackageExistsException;

    /**
     * {@inheritDoc}
     */
    @Override
    public abstract PackageId register(File file, boolean replace) throws IOException, PackageExistsException;

    /**
     * {@inheritDoc}
     */
    @Override
    public abstract PackageId registerExternal(File file, boolean replace) throws IOException, PackageExistsException;

    /**
     * {@inheritDoc}
     */
    @Override
    public abstract void remove(PackageId id) throws IOException, NoSuchPackageException;

    /**
     * {@inheritDoc}
     */
    @NotNull
    @Override
    public DependencyReport analyzeDependencies(@NotNull PackageId id, boolean onlyInstalled) throws IOException, NoSuchPackageException {
        List<Dependency> unresolved = new LinkedList<Dependency>();
        List<PackageId> resolved = new LinkedList<PackageId>();
        try (RegisteredPackage pkg = open(id)) {
            if (pkg == null) {
                throw new NoSuchPackageException().setId(id);
            }
            //noinspection resource
            for (Dependency dep : pkg.getPackage().getDependencies()) {
                PackageId resolvedId = resolve(dep, onlyInstalled);
                if (resolvedId == null) {
                    unresolved.add(dep);
                } else {
                    resolved.add(resolvedId);
                }
            }
        }

        return new DependencyReportImpl(id, unresolved.toArray(new Dependency[unresolved.size()]),
                resolved.toArray(new PackageId[resolved.size()])
        );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public abstract PackageId resolve(Dependency dependency, boolean onlyInstalled) throws IOException;

    /**
     * {@inheritDoc}
     */
    @NotNull
    @Override
    public PackageId[] usage(PackageId id) throws IOException {
        TreeSet<PackageId> usages = new TreeSet<>();
        for (PackageId pid : packages()) {
            try (RegisteredPackage pkg = open(pid)) {
                if (pkg == null || !pkg.isInstalled()) {
                    continue;
                }
                // noinspection resource
                for (Dependency dep : pkg.getPackage().getDependencies()) {
                    if (dep.matches(id)) {
                        usages.add(pid);
                        break;
                    }
                }
            }
        }
        return usages.toArray(new PackageId[usages.size()]);
    }

    /**
     * {@inheritDoc}
     */
    @NotNull
    @Override
    public ExecutionPlanBuilder createExecutionPlan() {
        return new ExecutionPlanBuilderImpl(this);
    }

    /**
     * Returns the relative path of this package. please note that since 2.3 this also
     * includes the version, but never the extension (.zip).
     *
     * @param id the package id
     * @return the relative path of this package
     * @since 2.2
     */
    public static String getRelativeInstallationPath(PackageId id) {
        StringBuilder b = new StringBuilder();
        if (id.getGroup().length() > 0) {
            b.append(id.getGroup());
            b.append("/");
        }
        b.append(id.getName());
        String v = id.getVersion().toString();
        if (v.length() > 0) {
            b.append("-").append(v);
        }
        return b.toString();
    }

    /**
     * Creates a random package id for packages that lack one.
     * 
     * @return a random package id.
     */
    protected static PackageId createRandomPid() {
        return new PackageId("temporary", "pack_" + UUID.randomUUID().toString(), (String) null);
    }

    public @NotNull SecurityConfig getSecurityConfig() {
        return securityConfig;
    }


}

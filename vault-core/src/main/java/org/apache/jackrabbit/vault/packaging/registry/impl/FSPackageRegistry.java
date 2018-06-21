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
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.jcr.RepositoryException;
import javax.jcr.Session;import org.apache.jackrabbit.vault.fs.config.MetaInf;
import org.apache.jackrabbit.vault.fs.io.ImportOptions;
import org.apache.jackrabbit.vault.fs.io.MemoryArchive;
import org.apache.jackrabbit.vault.packaging.Dependency;
import org.apache.jackrabbit.vault.packaging.JcrPackage;
import org.apache.jackrabbit.vault.packaging.NoSuchPackageException;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.apache.jackrabbit.vault.packaging.PackageExistsException;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.apache.jackrabbit.vault.packaging.events.PackageEvent;
import org.apache.jackrabbit.vault.packaging.events.impl.PackageEventDispatcher;
import org.apache.jackrabbit.vault.packaging.impl.PackagePropertiesImpl;
import org.apache.jackrabbit.vault.packaging.impl.ZipVaultPackage;
import org.apache.jackrabbit.vault.packaging.registry.DependencyReport;
import org.apache.jackrabbit.vault.packaging.registry.ExecutionPlanBuilder;
import org.apache.jackrabbit.vault.packaging.registry.PackageRegistry;
import org.apache.jackrabbit.vault.packaging.registry.RegisteredPackage;
import org.apache.jackrabbit.vault.util.InputStreamPump;
import org.apache.jackrabbit.vault.util.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@code JcrPackagePersistence}...
 */
public class FSPackageRegistry implements PackageRegistry, InternalPackageRegistry {

    /**
     * default logger
     */
    private static final Logger log = LoggerFactory.getLogger(FSPackageRegistry.class);


    /**
     * default root path for packages
     */
    public static final String DEFAULT_PACKAGE_ROOT_PATH = "/etc/packages";

    /**
     * default root path prefix for packages
     */
    public static final String DEFAULT_PACKAGE_ROOT_PATH_PREFIX = DEFAULT_PACKAGE_ROOT_PATH + "/";

    /**
     * suggested folder types
     */
    private final static String[] FOLDER_TYPES = { "sling:Folder", "nt:folder", "nt:unstructured", null };

    /**
     * internal session
     */
    private final Session session;

    @Nullable
    private PackageEventDispatcher dispatcher;

    /**
     * Creates a new JcrPackageRegistry based on the given session.
     * 
     * @param session
     *            the JCR session that is used to access the repository.
     * @param roots
     *            the root paths to store the packages.
     */
    public FSPackageRegistry() {
        this.session = null;
    }

    /**
     * Creates a new JcrPackageRegistry based on the given session.
     * 
     * @param session
     *            the JCR session that is used to access the repository.
     * @param roots
     *            the root paths to store the packages.
     */
    public FSPackageRegistry(@Nonnull Session session, @Nullable String... roots) {
        this.session = session;
    }

    /**
     * Sets the event dispatcher
     * 
     * @param dispatcher
     *            the dispatcher.
     */
    public void setDispatcher(@Nullable PackageEventDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    /**
     * Dispatches a package event using the configured dispatcher.
     * 
     * @param type
     *            event type
     * @param id
     *            package id
     * @param related
     *            related packages
     */
    public void dispatch(@Nonnull PackageEvent.Type type, @Nonnull PackageId id, @Nullable PackageId[] related) {
        if (dispatcher == null) {
            return;
        }
        dispatcher.dispatch(type, id, related);
    }

    @Nullable
    @Override
    public RegisteredPackage open(@Nonnull PackageId id) throws IOException {
        try {
            File pkg = getPackageFile(id);
            return pkg == null ? null : new FSRegisteredPackage(open(pkg));
        } catch (RepositoryException e) {
            throw new IOException(e);
        }
    }

    @Override
    public boolean contains(@Nonnull PackageId id) throws IOException {
            return getPackageFile(id) != null;
    }

    @Nullable
    private File getPackageFile(@Nonnull PackageId id) {
        String path = getInstallationPath(id);

        String[] exts = new String[] { "", ".zip", ".jar" };
        for (String ext : exts) {
            return new File(path + ext);
        }

        return null;
    }

    /**
     * {@inheritDoc}
     */
    public VaultPackage open(File pkg) throws RepositoryException {
        try {
            return new ZipVaultPackage(pkg, false);
        } catch (IOException e) {
            log.error("Cloud not open file {} as ZipVaultPackage.", pkg.getAbsolutePath(), e);
            return null;
        }

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PackageId resolve(Dependency dependency, boolean onlyInstalled) throws IOException {
        PackageId bestId = null;
        for (PackageId id : packages()) {
            if (isInstalled(id)) {
                if (dependency.matches(id)) {
                    if (bestId == null || id.getVersion().compareTo(bestId.getVersion()) > 0) {
                        bestId = id;
                    }
                }
            }
        }
        return bestId;
    }

    private boolean isInstalled(PackageId id) {
        // TODO @suess - how to capture installation state
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Nonnull
    @Override
    public PackageId[] usage(PackageId id) throws IOException {
        TreeSet<PackageId> usages = new TreeSet<PackageId>();
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

    @Nonnull
    @Override
    public DependencyReport analyzeDependencies(@Nonnull PackageId id, boolean onlyInstalled)
            throws IOException, NoSuchPackageException {
        List<Dependency> unresolved = new LinkedList<Dependency>();
        List<PackageId> resolved = new LinkedList<PackageId>();
        try (RegisteredPackage pkg = open(id)) {
            if (pkg == null) {
                throw new NoSuchPackageException().setId(id);
            }
            // noinspection resource
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
                resolved.toArray(new PackageId[resolved.size()]));
    }

    @Nonnull
    @Override
    public PackageId register(@Nonnull InputStream in, boolean replace) throws IOException, PackageExistsException {
        ZipVaultPackage pkg = upload(in, replace);
        return pkg.getId();
    }

    public ZipVaultPackage upload(InputStream in, boolean replace)
            throws IOException, PackageExistsException {

        MemoryArchive archive = new MemoryArchive(true);
        long startTime = System.nanoTime();
        InputStreamPump pump = new InputStreamPump(in, archive);

        // this will cause the input stream to be consumed and the memory
        // archive being initialized.
        try {
            archive.run(in);
        } catch (Exception e) {
            String msg = "Stream could be read successfully.";
            log.error(msg);
            throw new IOException(msg, e);
        }

        if (archive.getJcrRoot() == null) {
            String msg = "Stream is not a content package. Missing 'jcr_root'.";
            log.error(msg);
            throw new IOException(msg);
        }

        final MetaInf inf = archive.getMetaInf();
        PackagePropertiesImpl props = new PackagePropertiesImpl() {
            @Override
            protected Properties getPropertiesMap() {
                return inf.getProperties();
            }
        };
        PackageId pid = props.getId();

        // invalidate pid if path is unknown
        if (pid == null) {
            pid = createRandomPid();
        }
        if (!pid.isValid()) {
            throw new IllegalArgumentException("Unable to create package. Illegal package name.");
        }

        File oldPkgFile = getPackageFile(pid);
        String path = oldPkgFile.exists() ? oldPkgFile.getAbsolutePath() : getInstallationPath(pid);
        String name = Text.getName(path);

        // TODO: @suess : capture installation state

        if (oldPkgFile.exists()) {
            if (replace) {
                oldPkgFile.delete();
            } else {
                throw new PackageExistsException("Package already exists: " + pid).setId(pid);
            }
        }

        ZipVaultPackage pkg = new ZipVaultPackage(archive, true);
        dispatch(PackageEvent.Type.UPLOAD, pid, null);
        return pkg;

    }

    @Nonnull
    @Override
    public PackageId register(@Nonnull File file, boolean replace) throws IOException, PackageExistsException {
        ZipVaultPackage pack = new ZipVaultPackage(file, false, true);
        // TODO: @suess - save in cache directory
        return pack.getId();
    }

    @Nonnull
    @Override
    public PackageId registerExternal(@Nonnull File file, boolean replace) throws IOException, PackageExistsException {
        ZipVaultPackage pack = new ZipVaultPackage(file, false, true);
        // TODO: @suess - capture reference in central location
        return pack.getId();
    }

    @SuppressWarnings("resource")
    @Override
    public void remove(@Nonnull PackageId id) throws IOException, NoSuchPackageException {
        FSRegisteredPackage pkg = (FSRegisteredPackage) open(id);
        if (pkg == null) {
            throw new NoSuchPackageException().setId(id);
        }
        pkg.getPackage().getFile().delete();
        // TODO @suess - remove install state?

        dispatch(PackageEvent.Type.REMOVE, id, null);
    }

    @Nonnull
    @Override
    public Set<PackageId> packages() throws IOException {
        try {
            Set<PackageId> packages = listPackages();
            return packages;
        } catch (RepositoryException e) {
            throw new IOException(e);
        }
    }

    Set<PackageId> listPackages() throws RepositoryException {

        // TODO @suess return packages from fs location / mapped packages -
        // can't go through JcrPackageImpl as being JCR dependant but has to
        // load metadata otherways (e.g. extract from file initially)

        // try (JcrPackageImpl pack = new JcrPackageImpl(this, child)) {
        // if (pack.isValid()) {
        // // skip packages with illegal names
        // JcrPackageDefinition jDef = pack.getDefinition();
        // if (jDef == null || !jDef.getId().isValid()) {
        // continue;
        // }
        // packages.add(jDef.getId());
        // }
        // }
        // }
        return Collections.emptySet();
    }

    /**
     * Returns the path of this package.this also includes the version, but
     * never the extension (.zip).
     *
     * @param id
     *            the package id
     * @return the path of this package
     */
    public String getInstallationPath(PackageId id) {

        String installPath;
        installPath = getMappedPackage(id);
        if (installPath == null) {
            StringBuilder b = new StringBuilder("/");
            if (id.getGroup().length() > 0) {
                b.append(id.getGroup());
                b.append("/");
            }
            b.append(id.getName());
            String v = id.getVersion().toString();
            if (v.length() > 0) {
                b.append("-").append(v);
            }
            installPath = b.toString();
        }

        return installPath;
    }

    private String getMappedPackage(PackageId id) {
        // TODO: @suess lookup if installpath is mapped
        return null;
    }

    /**
     * Creates a random package id for packages that lack one.
     * 
     * @return a random package id.
     */
    private static PackageId createRandomPid() {
        return new PackageId("temporary", "pack_" + UUID.randomUUID().toString(), (String) null);
    }

    @Nonnull
    @Override
    public ExecutionPlanBuilder createExecutionPlan() {
        return new ExecutionPlanBuilderImpl(this);
    }

    @Override
    public void installPackage(RegisteredPackage pkg, ImportOptions opts, boolean extract) throws IOException, PackageException {

        if (session == null){
            log.error("Installation of packages only supported when session is set.");
        }
        // TODO: @suess - replace by logic to trigger install from FS registry
        VaultPackage vltPkg = pkg.getPackage();
        if (vltPkg instanceof ZipVaultPackage) {
            if (!extract) {
                // TODO: @suess - snapshot handling in FS 
            }
            try {
                ((ZipVaultPackage)vltPkg).extract(session, opts);
            } catch (RepositoryException e) {
                throw new IOException(e);
            }
            // TODO: @suess - capture installation state for registry handling
        }
    }
    
    @Override
    public void uninstallPackage(RegisteredPackage pkg, ImportOptions opts) throws IOException, PackageException {
     // TODO: @suess - replace by logic to trigger uninstall from FS registry (identify & restore snapshot) 
//        try (JcrPackage jcrPkg = ((JcrRegisteredPackage) pkg).getJcrPackage()){
//          jcrPkg.uninstall(opts);
//      } catch (RepositoryException e) {
//          throw new IOException(e);
//      }  
    }
}
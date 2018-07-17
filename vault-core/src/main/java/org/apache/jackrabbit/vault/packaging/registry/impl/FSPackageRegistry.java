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
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.commons.io.FileUtils;
import org.apache.jackrabbit.vault.fs.config.MetaInf;
import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.fs.io.ImportOptions;
import org.apache.jackrabbit.vault.fs.io.MemoryArchive;
import org.apache.jackrabbit.vault.packaging.Dependency;
import org.apache.jackrabbit.vault.packaging.DependencyException;
import org.apache.jackrabbit.vault.packaging.NoSuchPackageException;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.apache.jackrabbit.vault.packaging.PackageExistsException;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.apache.jackrabbit.vault.packaging.events.PackageEvent;
import org.apache.jackrabbit.vault.packaging.events.PackageEvent.Type;
import org.apache.jackrabbit.vault.packaging.events.impl.PackageEventDispatcher;
import org.apache.jackrabbit.vault.packaging.impl.PackagePropertiesImpl;
import org.apache.jackrabbit.vault.packaging.impl.ZipVaultPackage;
import org.apache.jackrabbit.vault.packaging.registry.DependencyReport;
import org.apache.jackrabbit.vault.packaging.registry.PackageRegistry;
import org.apache.jackrabbit.vault.packaging.registry.RegisteredPackage;
import org.apache.jackrabbit.vault.util.InputStreamPump;
import org.apache.jackrabbit.vault.util.PlatformNameFormat;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * FileSystem based registry not depending on a JCR Session. All metadata is stored in Filesystem and can be prepared and used without a running JCR repository
 * Only methods to install or uninstall packages require an active {@code Session} object of running jcr instance to perform the actual installation tasks
 */
@Component(
        service = PackageRegistry.class,
        configurationPolicy = ConfigurationPolicy.REQUIRE,
        property = {"service.vendor=The Apache Software Foundation"}
)
@Designate(ocd = FSPackageRegistry.Config.class)
public class FSPackageRegistry extends AbstractPackageRegistry {

    private static final String REPOSITORY_HOME = "repository.home";

    /**
     * default logger
     */
    private static final Logger log = LoggerFactory.getLogger(FSPackageRegistry.class);

    /**
     * Suffixes for metadata files
     */
    private final String[] META_SUFFIXES = {"xml"};

    private Map<PackageId, FSInstallState> stateCache = new ConcurrentHashMap<>();

    private boolean packagesInitializied = false;

    @Reference
    private PackageEventDispatcher dispatcher;

    private File homeDir;

    private File getHomeDir() {
        return homeDir;
    }

    /**
     * Creates a new FSPackageRegistry based on the given home directory.
     *
     * @param homeDir the directory in which packages and their metadata is stored
     * @throws IOException If an I/O error occurs.
     */
    public FSPackageRegistry(@Nonnull File homeDir) throws IOException {
        this.homeDir = homeDir;
    }

    /**
     * Deafult constructor for OSGi initialization (homeDir defined via activator)
     */
    public FSPackageRegistry() {
    }


    @ObjectClassDefinition(
            name = "Apache Jackrabbit FS Package Registry Service"
    )
    @interface Config {

        @AttributeDefinition
        String homePath() default "packageregistry";
    }

    @Activate
    private void activate(BundleContext context, Config config) {
        String repoHome = context.getProperty(REPOSITORY_HOME);
        this.homeDir = new File(repoHome + "/" + config.homePath());
        if (!homeDir.exists()) {
            homeDir.mkdirs();
        }
        log.info("Jackrabbit Filevault FS Package Registry initialized with home location {}", this.homeDir.getPath());
    }

    /**
     * Sets the event dispatcher
     *
     * @param dispatcher the dispatcher.
     */
    public void setDispatcher(@Nullable PackageEventDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    /**
     * Dispatches a package event using the configured dispatcher.
     *
     * @param type event type
     * @param id package id
     * @param related related packages
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
            return pkg != null && pkg.exists() ? new FSRegisteredPackage(this, open(pkg)) : null;
        } catch (RepositoryException e) {
            throw new IOException(e);
        }
    }

    @Override
    public boolean contains(@Nonnull PackageId id) throws IOException {
        return stateCache.containsKey(id);
    }

    @Nullable
    private File getPackageFile(@Nonnull PackageId id) {
        try {
            FSInstallState state = getInstallState(id);
            if (FSPackageStatus.NOTREGISTERED.equals(state.getStatus())) {
                return buildPackageFile(id);
            } else {
                return new File(state.getFilePath());
            }
        } catch (IOException e) {
            log.error("Couldn't get install state of packageId {}", id, e);
        }
        return null;
    }

    private File buildPackageFile(@Nonnull PackageId id) {
        String path = getInstallationPath(id);
        return new File(getHomeDir(), path + ".zip");
    }

    /**
     * Returns the meta data file of the package with the given Id.
     *
     * @param id The package Id.
     * @return the meta data file.
     */
    @Nonnull
    private File getPackageMetaDataFile(@Nonnull PackageId id) {
        final String path = getInstallationPath(id);
        return new File(getHomeDir(), path + ".xml");
    }

    /**
     * {@inheritDoc}
     */
    public VaultPackage open(File pkg) throws RepositoryException {
        try {
            return new ZipVaultPackage(pkg, false, true);
        } catch (IOException e) {
            log.error("Cloud not open file {} as ZipVaultPackage.", pkg.getPath(), e);
            return null;
        }

    }

    /**
     * {@inheritDoc}
     */
    @Nonnull
    @Override
    public DependencyReport analyzeDependencies(@Nonnull PackageId id, boolean onlyInstalled) throws IOException, NoSuchPackageException {
        List<Dependency> unresolved = new LinkedList<>();
        List<PackageId> resolved = new LinkedList<>();
        FSInstallState state = getInstallState(id);
        if (FSPackageStatus.NOTREGISTERED.equals(state.getStatus())) {
            throw new NoSuchPackageException().setId(id);
        }

        // Make sure that also dependencies of contained packages are considered as packages will be installed in a joined sequence.
        Set<Dependency> allDependencies = new HashSet<>();
        allDependencies.addAll(state.getDependencies());
        for (PackageId subId : state.getSubPackages()) {
            FSInstallState subState = getInstallState(subId);
            allDependencies.addAll(subState.getDependencies());
        }

        for (Dependency dep : allDependencies) {
            PackageId resolvedId = resolve(dep, onlyInstalled);
            if (resolvedId == null) {
                unresolved.add(dep);
            } else {
                resolved.add(resolvedId);
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
    public PackageId resolve(Dependency dependency, boolean onlyInstalled) throws IOException {
        PackageId bestId = null;
        for (PackageId id : packages()) {
            if (!onlyInstalled || isInstalled(id)) {
                if (dependency.matches(id)) {
                    if (bestId == null || id.getVersion().compareTo(bestId.getVersion()) > 0) {
                        bestId = id;
                    }
                }
            }
        }
        return bestId;
    }

    /**
     * Returns {@code true} when state {@link FSPackageStatus#EXTRACTED} is recorded for given {@code PackageId}
     *
     * @param id PackageId of the package to test.
     * @return {@code true} if package is in state {@link FSPackageStatus#EXTRACTED}
     *
     * @throws IOException If an I/O error occurs.
     */
    boolean isInstalled(PackageId id) throws IOException {
        FSPackageStatus status = getInstallState(id).getStatus();
        return FSPackageStatus.EXTRACTED.equals(status);
    }

    /**
     * {@inheritDoc}
     */
    @Nonnull
    @Override
    public PackageId register(@Nonnull InputStream in, boolean replace) throws IOException, PackageExistsException {
        ZipVaultPackage pkg = upload(in, replace);

        Set<PackageId> subpackages = registerSubPackages(pkg, replace);
        File pkgFile = buildPackageFile(pkg.getId());
        Collection<Dependency> dependencies = Arrays.asList(pkg.getDependencies());
        setInstallState(pkg.getId(), FSPackageStatus.REGISTERED, pkgFile.getPath(), false, new HashSet<>(dependencies), subpackages, null);
        return pkg.getId();
    }

    /**
     * Registers subpackages in registry
     *
     * @param pkg The package to regist
     * @param replace {@code true} to replace
     * @return {@code Set} of {@code PackageId}s registered from a given {@code VaultPackage}
     *
     * @throws IOException
     * @throws PackageExistsException
     */
    private Set<PackageId> registerSubPackages(VaultPackage pkg, boolean replace)
            throws IOException, PackageExistsException {
        Set<PackageId> subpackages = new HashSet<>();
        try {
            Archive.Entry packagesRoot = pkg.getArchive().getJcrRoot().getChild("etc").getChild("packages");

            registerSubPackages(pkg.getArchive(), packagesRoot, DEFAULT_PACKAGE_ROOT_PATH, replace, subpackages);
        } catch (NullPointerException e) {
            // nothing to do - expected if subpath isn't available
        }
        return subpackages;
    }

    /**
     * Parses given {@link Archive.Entry} for .jar & .zip binaries and tries to register given subpackage.
     *
     * @param archive
     * @param directory
     * @param parentPath
     * @param replace
     * @param subpackages
     * @throws IOException
     * @throws PackageExistsException
     */
    private void registerSubPackages(Archive archive, Archive.Entry directory, String parentPath, boolean replace, Set<PackageId> subpackages)
            throws IOException, PackageExistsException {
        Collection<? extends Archive.Entry> files = directory.getChildren();

        for (Archive.Entry file : files) {
            String fileName = file.getName();
            String repoName = PlatformNameFormat.getRepositoryName(fileName);
            String repoPath = parentPath + "/" + repoName;
            if (file.isDirectory()) {
                registerSubPackages(archive, file, repoPath, replace, subpackages);
            } else {
                if (repoPath.startsWith(DEFAULT_PACKAGE_ROOT_PATH_PREFIX) && (repoPath.endsWith(".jar") || repoPath.endsWith(".zip"))) {
                    try (InputStream in = archive.openInputStream(file)) {
                        if (in == null) {
                            throw new IOException("Unable to open archive input stream of " + file);
                        }
                        subpackages.add(register(in, replace));
                    } catch (PackageExistsException e) {
                        log.info("Subpackage already registered, skipping subpackage extraction.");
                    }
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public ZipVaultPackage upload(InputStream in, boolean replace)
            throws IOException, PackageExistsException {

        MemoryArchive archive = new MemoryArchive(false);
        File tempFile = File.createTempFile("upload", ".zip");

        try (InputStreamPump pump = new InputStreamPump(in, archive)) {
            // this will cause the input stream to be consumed and the memory
            // archive being initialized.
            try {

                FileUtils.copyInputStreamToFile(pump, tempFile);
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
                throw new IllegalArgumentException("Unable to create package. No package pid set.");
            }
            if (!pid.isValid()) {
                throw new IllegalArgumentException("Unable to create package. Illegal package name.");
            }

            File oldPkgFile = getPackageFile(pid);
            FSInstallState state = getInstallState(pid);

            if (oldPkgFile != null && oldPkgFile.exists()) {
                if (replace && !state.isExternal()) {
                    oldPkgFile.delete();
                } else {
                    throw new PackageExistsException("Package already exists: " + pid).setId(pid);
                }
            }

            ZipVaultPackage pkg = new ZipVaultPackage(archive, true);
            registerSubPackages(pkg, replace);
            File pkgFile = buildPackageFile(pid);
            FileUtils.moveFile(tempFile, pkgFile);
            dispatch(Type.UPLOAD, pid, null);
            return pkg;
        }

    }

    /**
     * {@inheritDoc}
     */
    @Nonnull
    @Override
    public PackageId register(@Nonnull File file, boolean replace) throws IOException, PackageExistsException {
        ZipVaultPackage pack = new ZipVaultPackage(file, false, true);
        try {
            File pkgFile = buildPackageFile(pack.getId());
            if (pkgFile.exists()) {
                if (replace) {
                    pkgFile.delete();
                } else {
                    throw new PackageExistsException("Package already exists: " + pack.getId()).setId(pack.getId());
                }
            }
            Set<PackageId> subpackages = registerSubPackages(pack, replace);
            FileUtils.copyFile(file, pkgFile);
            Collection<Dependency> dependencies = Arrays.asList(pack.getDependencies());
            setInstallState(pack.getId(), FSPackageStatus.REGISTERED, pkgFile.getPath(), false, new HashSet<>(dependencies), subpackages, null);
            return pack.getId();
        } finally {
            if (!pack.isClosed()) {
                pack.close();
            }
        }
    }

    @Nonnull
    @Override
    public PackageId registerExternal(@Nonnull File file, boolean replace) throws IOException, PackageExistsException {
        ZipVaultPackage pack = new ZipVaultPackage(file, false, true);
        try {

            FSInstallState state = getInstallState(pack.getId());
            if (!FSPackageStatus.NOTREGISTERED.equals(state.getStatus())) {
                if (replace) {
                    try {
                        remove(pack.getId());
                    } catch (NoSuchPackageException e) {
                        log.error("Status isn't NOTREGISTERD but no metafile exists to remove", e);
                    }
                } else {
                    throw new PackageExistsException("Package already exists: " + pack.getId()).setId(pack.getId());
                }
            }
            Set<PackageId> subpackages = registerSubPackages(pack, replace);
            Collection<Dependency> dependencies = Arrays.asList(pack.getDependencies());
            setInstallState(pack.getId(), FSPackageStatus.REGISTERED, file.getPath(), true, new HashSet<>(dependencies), subpackages, null);
            return pack.getId();
        } finally {
            if (!pack.isClosed()) {
                pack.close();
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void remove(@Nonnull PackageId id) throws IOException, NoSuchPackageException {
        FSInstallState state = getInstallState(id);
        File metaData = getPackageMetaDataFile(id);

        if (!metaData.exists()) {
            throw new NoSuchPackageException().setId(id);
        }
        metaData.delete();

        if (!state.isExternal()) {
            getPackageFile(id).delete();
        }
        updateInstallState(id, FSPackageStatus.NOTREGISTERED);
        dispatch(PackageEvent.Type.REMOVE, id, null);
    }

    /**
     * {@inheritDoc}
     */
    @Nonnull
    @Override
    public Set<PackageId> packages() throws IOException {
        return packagesInitializied ? stateCache.keySet() : loadPackageCache();
    }

    /**
     * Loads all state from files persisted in configured homeDir, adds to cache and returns all cached {@code PackageId}s.
     *
     * @return {@code Set} of all cached {@code PackageId}s
     *
     * @throws IOException If an I/O error occurs
     */
    private Set<PackageId> loadPackageCache() throws IOException {
        Map<PackageId, FSInstallState> cacheEntries = new HashMap<>();

        Collection<File> files = FileUtils.listFiles(getHomeDir(), META_SUFFIXES, true);
        for (File file : files) {
            FSInstallState state = FSInstallState.fromFile(file);
            if (state != null) {
                PackageId id = state.getPackageId();
                if (id != null) {
                    cacheEntries.put(id, state);
                }
            }
        }
        stateCache.putAll(cacheEntries);
        packagesInitializied = true;
        return cacheEntries.keySet();
    }

    /**
     * Returns the path of this package.this also includes the version, but
     * never the extension (.zip).
     *
     * @param id the package id
     * @return the path of this package
     */
    public String getInstallationPath(PackageId id) {
        return getRelativeInstallationPath(id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void installPackage(@Nonnull Session session, @Nonnull RegisteredPackage pkg, @Nonnull ImportOptions opts,
                               boolean extract) throws IOException, PackageException {

        // For now FS based persistence only supports extraction but no reversible installation
        if (!extract) {
            String msg = "Only extraction supported by FS based registry";
            log.error(msg);
            throw new PackageException(msg);
        }
        try (VaultPackage vltPkg = pkg.getPackage()) {
            if (vltPkg instanceof ZipVaultPackage) {
                FSInstallState state = getInstallState(vltPkg.getId());
                //Calculate if subpackage dependencies would be satisfied

                Set<PackageId> sortedSubPackages = new TreeSet<>(new Comparator<PackageId>() {
                    @Override
                    public int compare(PackageId id1, PackageId id2) {
                        FSInstallState state1;
                        try {
                            state1 = getInstallState(id1);
                            Set<Dependency> deps = state1.getDependencies();
                            return deps.contains(id2) ? 1 : -1;
                        } catch (IOException e) {
                            log.error("Could read state for package {}", id1);
                            return 0;
                        }
                    }
                });
                sortedSubPackages.addAll(state.getSubPackages());
                if (!sortedSubPackages.isEmpty()) {
                    for (PackageId subPackId : sortedSubPackages) {
                        DependencyReport report = analyzeDependencies(subPackId, false);
                        if (report.getUnresolvedDependencies().length > 0) {
                            String msg = String.format("Refusing to install subpackage %s. required dependencies missing: %s - intercepting installation of package %s",
                                    subPackId, Arrays.toString(report.getUnresolvedDependencies()), vltPkg.getId());
                            log.error(msg);
                            throw new DependencyException(msg);
                        }
                        RegisteredPackage regPkg = open(subPackId);
                        if (regPkg == null) {
                            throw new IOException("Internal error while reading sub-package: " + subPackId);
                        }
                        installPackage(session, regPkg, opts, true);
                    }
                    dispatch(Type.EXTRACT_SUB_PACKAGES, pkg.getId(), sortedSubPackages.toArray(new PackageId[sortedSubPackages.size()]));
                }

                vltPkg.extract(session, opts);

                dispatch(PackageEvent.Type.EXTRACT, pkg.getId(), null);
                updateInstallState(vltPkg.getId(), FSPackageStatus.EXTRACTED);
            }
        } catch (RepositoryException e) {
            throw new IOException(e);
        }
    }

    /**
     * Uninstallation not supported for FS based PackageRegistry
     */
    @Override
    public void uninstallPackage(@Nonnull Session session, @Nonnull RegisteredPackage pkg, @Nonnull ImportOptions opts) throws IOException, PackageException {
        String msg = "Uninstallation not supported by FS based registry";
        log.error(msg);
        throw new PackageException(msg);
    }

    /**
     * Shortcut to just change the status of a package - implicitly sets the installtime when switching to EXTRACTED
     *
     * @param pid PackageId of the package to update
     * @param targetStatus Status to update
     * @throws IOException If an I/O error occurs.
     */
    private void updateInstallState(PackageId pid, FSPackageStatus targetStatus) throws IOException {
        FSInstallState state = getInstallState(pid);
        Long installTime = state.getInstallationTime();
        if (FSPackageStatus.EXTRACTED.equals(targetStatus)) {
            installTime = Calendar.getInstance().getTimeInMillis();
        }
        setInstallState(pid, targetStatus, state.getFilePath(), state.isExternal(), state.getDependencies(), state.getSubPackages(), installTime);
    }

    /**
     * Persists the installState to a metadatafile and adds current state to cache
     *
     * @param pid
     * @param targetStatus
     * @param filePath
     * @param external
     * @param dependencies
     * @param subPackages
     * @param installTimeStamp
     * @throws IOException
     */
    private void setInstallState(@Nonnull PackageId pid, @Nonnull FSPackageStatus targetStatus, @Nonnull String filePath, @Nonnull boolean external, @Nullable Set<Dependency> dependencies, @Nullable Set<PackageId> subPackages, @Nullable Long installTimeStamp) throws IOException {
        File metaData = getPackageMetaDataFile(pid);

        if (targetStatus.equals(FSPackageStatus.NOTREGISTERED)) {
            metaData.delete();
            stateCache.remove(pid);
        } else {
            FSInstallState state = new FSInstallState(pid, targetStatus, filePath, external, dependencies, subPackages, installTimeStamp);
            state.save(metaData);
            stateCache.put(pid, state);
        }
    }

    /**
     * Retrieves {@code InstallState} from cache, falls back to reading from metafile and returns state for {@code FSPackageStatus.NOTREGISTERED} in case not found.
     *
     * @param pid the PackageId of the package to retrieve the install state from.
     * @return {@code InstallState} found for given {@code PackageId} or a fresh one with status {@code FSPackageStatus.NOTREGISTERED}
     *
     * @throws IOException if an I/O error occurs.
     */
    @Nonnull
    public FSInstallState getInstallState(PackageId pid) throws IOException {
        if (stateCache.containsKey(pid)) {
            return stateCache.get(pid);
        } else {
            File metaFile = getPackageMetaDataFile(pid);
            FSInstallState state = FSInstallState.fromFile(metaFile);
            if (state != null) {
                //theoretical file - should only be feasible when manipulating on filesystem, writing metafile automatically updates cache
                stateCache.put(pid, state);
            }
            return state != null ? state : new FSInstallState(pid, FSPackageStatus.NOTREGISTERED, null, false, null, null, null);
        }
    }


}

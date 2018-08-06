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
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.commons.io.FileUtils;
import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.config.MetaInf;
import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.fs.io.ImportOptions;
import org.apache.jackrabbit.vault.fs.io.MemoryArchive;
import org.apache.jackrabbit.vault.packaging.Dependency;
import org.apache.jackrabbit.vault.packaging.NoSuchPackageException;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.apache.jackrabbit.vault.packaging.PackageExistsException;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.SubPackageHandling;
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
import org.apache.jackrabbit.vault.util.Text;
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

    /**
     * Contains a map of all filesystem paths to package IDs
     */
    private Map<Path, PackageId> pathIdMapping = new ConcurrentHashMap<>();


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
        loadPackageCache();
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
    private void activate(BundleContext context, Config config) throws IOException {
        String repoHome = context.getProperty(REPOSITORY_HOME);
        if (repoHome == null) {
            this.homeDir = context.getDataFile(config.homePath());
        } else {
            this.homeDir = new File(repoHome + "/" + config.homePath());
            if (!homeDir.exists()) {
                homeDir.mkdirs();
            }
        }
        loadPackageCache();
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
            FSInstallState state = getInstallState(id);
            return FSPackageStatus.NOTREGISTERED != state.getStatus() ? new FSRegisteredPackage(this, state) : null;
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
            if (FSPackageStatus.NOTREGISTERED == state.getStatus()) {
                return buildPackageFile(id);
            } else {
                return state.getFilePath().toFile();
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
        if (FSPackageStatus.NOTREGISTERED == state.getStatus()) {
            throw new NoSuchPackageException().setId(id);
        }

        // Make sure that also dependencies of contained packages are considered as packages will be installed in a joined sequence.
        Set<Dependency> allDependencies = new HashSet<>();
        allDependencies.addAll(state.getDependencies());
        for (PackageId subId : state.getSubPackages().keySet()) {
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
        return FSPackageStatus.EXTRACTED == status;
    }

    /**
     * {@inheritDoc}
     */
    @Nonnull
    @Override
    public PackageId register(@Nonnull InputStream in, boolean replace) throws IOException, PackageExistsException {
      return register(in, replace, null);
    }
    
    /**
     * {@inheritDoc}
     */
    @Nonnull
    private PackageId register(@Nonnull InputStream in, boolean replace, Dependency autoDependency) throws IOException, PackageExistsException {
        ZipVaultPackage pkg = upload(in, replace);

        Map<PackageId, SubPackageHandling.Option> subpackages = registerSubPackages(pkg, replace);
        File pkgFile = buildPackageFile(pkg.getId());
        HashSet<Dependency> dependencies = new HashSet<>();
        dependencies.addAll(Arrays.asList(pkg.getDependencies()));
        if (autoDependency != null) {
            dependencies.add(autoDependency);
        }
        setInstallState(pkg.getId(), FSPackageStatus.REGISTERED, pkgFile.toPath(), false, dependencies, subpackages,
                null);
        return pkg.getId();
    }

    /**
     * Registers subpackages in registry
     *
     * @param pkg The package to regist
     * @param replace {@code true} to replace
     * @return {@code Map} of {@code PackageId}s along with the corresponding {@code SubPackageHandling.Option} registered from a given {@code VaultPackage}
     *
     * @throws IOException
     * @throws PackageExistsException
     */
    private Map<PackageId, SubPackageHandling.Option> registerSubPackages(VaultPackage pkg, boolean replace)
            throws IOException, PackageExistsException {
        Map<PackageId, SubPackageHandling.Option> subpackages = new HashMap<>();

        Archive.Entry packagesRoot = pkg.getArchive().getEntry(ARCHIVE_PACKAGE_ROOT_PATH);
        if (packagesRoot != null) { 
            // As for JcrPackageImpl subpackages need to get an implicit autoDependency to the parent in case they have own content
            boolean hasOwnContent = false;
            for (PathFilterSet root : pkg.getArchive().getMetaInf().getFilter().getFilterSets()) {
                // todo: find better way to detect subpackages
                if (!Text.isDescendantOrEqual(DEFAULT_PACKAGE_ROOT_PATH, root.getRoot())) {
                    log.debug(
                            "Package {}: contains content outside /etc/packages. Sub packages will have a dependency to it",
                            pkg.getId());
                    hasOwnContent = true;
                }
            }
            Dependency autoDependency = hasOwnContent ? new Dependency(pkg.getId()) : null;
            registerSubPackages(pkg, packagesRoot, DEFAULT_PACKAGE_ROOT_PATH, replace, subpackages, autoDependency);
            dispatch(Type.EXTRACT_SUB_PACKAGES, pkg.getId(), subpackages.keySet().toArray(new PackageId[subpackages.size()]));
        }
        return subpackages;
    }

    /**
     * Parses given {@link Archive.Entry} for .jar & .zip binaries and tries to register given subpackage.
     *
     * @param vltPkg
     * @param directory
     * @param parentPath
     * @param replace
     * @param subpackages
     * @throws IOException
     * @throws PackageExistsException
     */
    private void registerSubPackages(VaultPackage vltPkg, Archive.Entry directory, String parentPath, boolean replace, Map<PackageId, SubPackageHandling.Option> subpackages, Dependency autoDependency)
            throws IOException, PackageExistsException {
        Collection<? extends Archive.Entry> files = directory.getChildren();

        for (Archive.Entry file : files) {
            String fileName = file.getName();
            String repoName = PlatformNameFormat.getRepositoryName(fileName);
            String repoPath = parentPath + "/" + repoName;
            if (file.isDirectory()) {
                registerSubPackages(vltPkg, file, repoPath, replace, subpackages, autoDependency);
            } else {
                if (repoPath.startsWith(DEFAULT_PACKAGE_ROOT_PATH_PREFIX) && (repoPath.endsWith(".jar") || repoPath.endsWith(".zip"))) {
                    try (InputStream in = vltPkg.getArchive().openInputStream(file)) {
                        if (in == null) {
                            throw new IOException("Unable to open archive input stream of " + file);
                        }
                        PackageId id = register(in, replace);
                        SubPackageHandling.Option option = vltPkg.getSubPackageHandling().getOption(id);
                        subpackages.put(id, option);
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
            Map<PackageId, SubPackageHandling.Option> subpackages = registerSubPackages(pack, replace);
            FileUtils.copyFile(file, pkgFile);
            Collection<Dependency> dependencies = Arrays.asList(pack.getDependencies());
            setInstallState(pack.getId(), FSPackageStatus.REGISTERED, pkgFile.toPath(), false, new HashSet<>(dependencies), subpackages, null);
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
        if (!replace && pathIdMapping.containsKey(file.getPath())) {
            throw new PackageExistsException("Package already exists: " + pathIdMapping.get(file.getPath()));
        }
        ZipVaultPackage pack = new ZipVaultPackage(file, false, true);
        try {

            FSInstallState state = getInstallState(pack.getId());
            if (!(FSPackageStatus.NOTREGISTERED == state.getStatus())) {
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
            Map<PackageId, SubPackageHandling.Option> subpackages = registerSubPackages(pack, replace);
            Collection<Dependency> dependencies = Arrays.asList(pack.getDependencies());
            setInstallState(pack.getId(), FSPackageStatus.REGISTERED, file.toPath(), true, new HashSet<>(dependencies), subpackages, null);
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
        Map<Path, PackageId> idMapping = new HashMap<>();


        Collection<File> files = FileUtils.listFiles(getHomeDir(), META_SUFFIXES, true);
        for (File file : files) {
            FSInstallState state = FSInstallState.fromFile(file);
            if (state != null) {
                PackageId id = state.getPackageId();
                if (id != null) {
                    cacheEntries.put(id, state);
                    idMapping.put(state.getFilePath(), id);
                    
                }
            }
        }
        stateCache.putAll(cacheEntries);
        pathIdMapping.putAll(idMapping);
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
            vltPkg.extract(session, opts);
            dispatch(PackageEvent.Type.EXTRACT, pkg.getId(), null);
            updateInstallState(vltPkg.getId(), FSPackageStatus.EXTRACTED);

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
        if (FSPackageStatus.EXTRACTED == targetStatus) {
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
    private void setInstallState(@Nonnull PackageId pid, @Nonnull FSPackageStatus targetStatus, @Nonnull Path filePath, @Nonnull boolean external, @Nullable Set<Dependency> dependencies, @Nullable Map<PackageId, SubPackageHandling.Option> subPackages, @Nullable Long installTimeStamp) throws IOException {
        File metaData = getPackageMetaDataFile(pid);

        if (targetStatus == FSPackageStatus.NOTREGISTERED) {
            pathIdMapping.remove(stateCache.get(pid).getFilePath());
            metaData.delete();
            stateCache.remove(pid);
        } else {
            FSInstallState state = new FSInstallState(pid, targetStatus)
                    .withFilePath(filePath)
                    .withDependencies(dependencies)
                    .withSubPackages(subPackages)
                    .withInstallTime(installTimeStamp)
                    .withExternal(external);
            state.save(metaData);
            stateCache.put(pid, state);
            pathIdMapping.put(state.getFilePath(), pid);
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
                pathIdMapping.put(state.getFilePath(), pid);
            }
            return state != null ? state : new FSInstallState(pid, FSPackageStatus.NOTREGISTERED);
        }
    }


}

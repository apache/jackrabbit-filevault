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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.jackrabbit.util.Text;
import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.fs.config.MetaInf;
import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.fs.io.ImportOptions;
import org.apache.jackrabbit.vault.fs.io.MemoryArchive;
import org.apache.jackrabbit.vault.packaging.Dependency;
import org.apache.jackrabbit.vault.packaging.NoSuchPackageException;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.apache.jackrabbit.vault.packaging.PackageExistsException;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.ScopedWorkspaceFilter;
import org.apache.jackrabbit.vault.packaging.SubPackageHandling;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.apache.jackrabbit.vault.packaging.events.PackageEvent;
import org.apache.jackrabbit.vault.packaging.events.PackageEvent.Type;
import org.apache.jackrabbit.vault.packaging.events.impl.PackageEventDispatcher;
import org.apache.jackrabbit.vault.packaging.impl.HollowVaultPackage;
import org.apache.jackrabbit.vault.packaging.impl.ZipVaultPackage;
import org.apache.jackrabbit.vault.packaging.registry.DependencyReport;
import org.apache.jackrabbit.vault.packaging.registry.PackageRegistry;
import org.apache.jackrabbit.vault.packaging.registry.RegisteredPackage;
import org.apache.jackrabbit.vault.util.InputStreamPump;
import org.apache.jackrabbit.vault.util.PlatformNameFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.osgi.service.metatype.annotations.Option;
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

    protected static final String REPOSITORY_HOME = "repository.home";

    /**
     * default logger
     */
    private static final Logger log = LoggerFactory.getLogger(FSPackageRegistry.class);

    private FSInstallStateCache stateCache;


    @Reference
    private PackageEventDispatcher dispatcher;

    private InstallationScope scope = InstallationScope.UNSCOPED;

    /**
     * Creates a new FSPackageRegistry based on the given home directory.
     *
     * @param homeDir the directory in which packages and their metadata is stored
     * @throws IOException If an I/O error occurs.
     * @deprecated Use {@link #FSPackageRegistry(File, InstallationScope, SecurityConfig, boolean)} instead
     */
    @Deprecated
    public FSPackageRegistry(@NotNull File homeDir) throws IOException {
        this(homeDir, InstallationScope.UNSCOPED);
    }

    /**
     * Creates a new FSPackageRegistry based on the given home directory.
     *
     * @param homeDir the directory in which packages and their metadata is stored
     * @param scope to set a corresponding workspacefilter
     * @throws IOException If an I/O error occurs.
     * @deprecated Use {@link #FSPackageRegistry(File, InstallationScope, SecurityConfig, boolean)} instead
     */
    @Deprecated
    public FSPackageRegistry(@NotNull File homeDir, InstallationScope scope) throws IOException {
       this(homeDir, scope, null);
    }

    /**
     * 
     * @param homeDir
     * @param scope
     * @param securityConfig
     * @throws IOException
     * @deprecated Use {@link #FSPackageRegistry(File, InstallationScope, SecurityConfig, boolean)} instead
     */
    @Deprecated
    public FSPackageRegistry(@NotNull File homeDir, InstallationScope scope, @Nullable AbstractPackageRegistry.SecurityConfig securityConfig) throws IOException {
        this(homeDir, scope, securityConfig, false);
    }

    public FSPackageRegistry(@NotNull File homeDir, InstallationScope scope, @Nullable AbstractPackageRegistry.SecurityConfig securityConfig, boolean isStrict) throws IOException {
        super(securityConfig, isStrict);
        log.info("Jackrabbit Filevault FS Package Registry initialized with home location {}", homeDir.getPath());
        this.scope = scope;
        this.stateCache = new FSInstallStateCache(homeDir.toPath());
    }

    /**
     * Default constructor for OSGi initialization (homeDir defined via activator)
     * @throws IOException 
     */
    public FSPackageRegistry() throws IOException {
        super(null, false); // set security config delayed (i.e. only after activate())
    }

    @Activate
    public void activate(BundleContext context, Config config) throws IOException {
        File homeDir = context.getProperty(REPOSITORY_HOME) != null ? ( 
                new File(config.homePath()).isAbsolute() ? new File(config.homePath()) : new File(context.getProperty(REPOSITORY_HOME) + "/" + config.homePath())) : 
                context.getDataFile(config.homePath());
        if (!homeDir.exists()) {
            homeDir.mkdirs();
        }
        log.info("Jackrabbit Filevault FS Package Registry initialized with home location {}", homeDir.getPath());
        this.scope = InstallationScope.valueOf(config.scope());
        this.securityConfig = new AbstractPackageRegistry.SecurityConfig(config.authIdsForHookExecution(), config.authIdsForRootInstallation());
        this.stateCache = new FSInstallStateCache(homeDir.toPath());
    }

    @ObjectClassDefinition(
            name = "Apache Jackrabbit FS Package Registry Service"
    )
    @interface Config {

        @AttributeDefinition
        String homePath() default "packageregistry";
        
        @AttributeDefinition(name = "Installation Scope",
                description = "Allows to limit the installation scope of this Apache Jackrabbit FS Package Registry Service. "
                        + "Packages installed from this registry may be unscoped (unfiltered), "
                        + "application scoped (only content for /apps & /libs) "
                        + "or content scoped (all content except for /libs & /apps)",
                options = {
                    @Option(label = "Unscoped", value = "UNSCOPED"),
                    @Option(label = "Application Scoped", value = "APPLICATION_SCOPED"),
                    @Option(label = "Content Scoped", value = "CONTENT_SCOPED")
        })
        String scope() default "UNSCOPED";
        
        @AttributeDefinition(description = "The authorizable ids which are allowed to execute hooks (in addition to 'admin', 'administrators' and 'system'")
        String[] authIdsForHookExecution();
        
        @AttributeDefinition(description = "The authorizable ids which are allowed to install packages with the 'requireRoot' flag (in addition to 'admin', 'administrators' and 'system'")
        String[] authIdsForRootInstallation();
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
    public void dispatch(@NotNull PackageEvent.Type type, @NotNull PackageId id, @Nullable PackageId[] related) {
        if (dispatcher == null) {
            return;
        }
        dispatcher.dispatch(type, id, related);
    }

    @Nullable
    @Override
    public RegisteredPackage open(@NotNull PackageId id) throws IOException {
        FSInstallState state = getInstallState(id);
        return state != null ? new FSRegisteredPackage(this, state) : null;
    }

    @Override
    public boolean contains(@NotNull PackageId id) throws IOException {
        return getInstallState(id) != null; // don't use hasKey as otherwise there is no fallback for lazily loading metadata files
    }

    @Nullable 
    FSInstallState getInstallState(@NotNull PackageId id) throws IOException {
        try {
            return stateCache.get(id);
        } catch (FSInstallStateCache.UncheckedIOException e) {
            throw e.getIOException();
        }
    }

    /**
     * 
     * @param id
     * @return the file pointing to an existing or new package with the given id
     * @throws IOException
     */
    @NotNull
    private Path getPackageFile(@NotNull PackageId id) throws IOException {
        FSInstallState state = getInstallState(id);
        if (state == null) {
            return stateCache.getPackageFile(id);
        } else {
            return state.getFilePath();
        }
    }

    /**
     * Opens the package of a file with the given Id.
     * @param id The Id of package file.
     * @return the package
     * @throws IOException if an I/O error occurs.
     */
    @NotNull
    protected VaultPackage openPackageFile(@NotNull PackageId id) throws IOException, NoSuchPackageException {
        Path pkg = getPackageFile(id);

        if (Files.exists(pkg) && Files.size(pkg) > 0) {
            return new ZipVaultPackage(pkg.toFile(), false, true);
        } else {
            FSInstallState state = getInstallState(id);
            if (state == null) {
                throw new NoSuchPackageException().setId(id);
            }
            return new HollowVaultPackage(state.getProperties());
        }
    }

    /**
     * {@inheritDoc}
     */
    @NotNull
    @Override
    public DependencyReport analyzeDependencies(@NotNull PackageId id, boolean onlyInstalled) throws IOException, NoSuchPackageException {
        List<Dependency> unresolved = new LinkedList<>();
        List<PackageId> resolved = new LinkedList<>();
        FSInstallState state = stateCache.get(id);
        if (state == null) {
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
        FSInstallState state = getInstallState(id);
        if (state != null) {
            return FSPackageStatus.EXTRACTED == state.getStatus();
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @NotNull
    @Override
    public PackageId register(@NotNull InputStream in, boolean replace) throws IOException, PackageExistsException {
      return register(in, replace, null);
    }

    @NotNull
    private PackageId register(@NotNull InputStream in, boolean replace, Dependency autoDependency) throws IOException, PackageExistsException {
        ZipVaultPackage pkg = upload(in, replace);

        Map<PackageId, SubPackageHandling.Option> subpackages = registerSubPackages(pkg, replace);
        Path pkgFile = getPackageFile(pkg.getId());
        HashSet<Dependency> dependencies = new HashSet<>();
        dependencies.addAll(Arrays.asList(pkg.getDependencies()));
        if (autoDependency != null) {
            dependencies.add(autoDependency);
        }
        FSInstallState state = new FSInstallState(pkg.getId(), FSPackageStatus.REGISTERED, pkgFile)
                .withDependencies(dependencies)
                .withSubPackages(subpackages)
                .withFilter(pkg.getArchive().getMetaInf().getFilter())
                .withSize(pkg.getSize())
                .withProperties(pkg.getArchive().getMetaInf().getProperties())
                .withExternal(false);
        stateCache.put(pkg.getId(), state);
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

    protected ZipVaultPackage upload(InputStream in, boolean replace)
            throws IOException, PackageExistsException {

        Path tempFile = Files.createTempFile("upload", ".zip");
        MemoryArchive archive = new MemoryArchive(false);

        try (InputStreamPump pump = new InputStreamPump(in, archive)) {
            // this will cause the input stream to be consumed and the memory
            // archive being initialized.
            try {
                Files.copy(pump, tempFile, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                String msg = "Stream could not be read successfully.";
                throw new IOException(msg, e);
            }
        }
        if (archive.getJcrRoot() == null) {
            String msg = "Stream is not a content package. Missing 'jcr_root'.";
            throw new IOException(msg);
        }

        final MetaInf inf = archive.getMetaInf();
        PackageId pid = inf.getPackageProperties().getId();

        // invalidate pid if path is unknown
        if (pid == null) {
            throw new IllegalArgumentException("Unable to create package. No package pid set.");
        }
        if (!pid.isValid()) {
            throw new IllegalArgumentException("Unable to create package. Illegal package name.");
        }

        Path pkgFile = getPackageFile(pid);
        FSInstallState state = getInstallState(pid);

        if (Files.exists(pkgFile)) {
            if (replace && !state.isExternal()) {
                Files.delete(pkgFile);
            } else {
                throw new PackageExistsException("Package already exists: " + pid).setId(pid);
            }
        } else {
            Files.createDirectories(pkgFile.getParent());
        }

        ZipVaultPackage pkg = new ZipVaultPackage(archive, true);
        registerSubPackages(pkg, replace);
        Files.move(tempFile, pkgFile);
        dispatch(Type.UPLOAD, pid, null);
        return pkg;

    }

    /**
     * {@inheritDoc}
     */
    @NotNull
    @Override
    public PackageId register(@NotNull File file, boolean replace) throws IOException, PackageExistsException {
        return doRegister(file, replace, false);
    }

    @NotNull
    @Override
    public PackageId registerExternal(@NotNull File file, boolean replace) throws IOException, PackageExistsException {
        return doRegister(file, replace, true);
    }

    @NotNull
    private PackageId doRegister(@NotNull File file, boolean replace, boolean external) throws IOException, PackageExistsException {
        // detect collisions without parsing the package to speed things up
        PackageId oldPackageId = stateCache.getIdForFile(file.toPath());
        if (!replace && oldPackageId != null) {
            throw new PackageExistsException("Package already exists: " + oldPackageId).setId(oldPackageId);
        }
        try (ZipVaultPackage pack = new ZipVaultPackage(file, false, true)) {
            FSInstallState state = getInstallState(pack.getId());
            if (state != null) {
                if (replace) {
                    try {
                        remove(pack.getId());
                    } catch (NoSuchPackageException e) {
                        log.error("No metafile exists to remove", e);
                    }
                } else {
                    throw new PackageExistsException("Package already exists: " + pack.getId()).setId(pack.getId());
                }
            }
            final Path newPackageFile;
            if (!external) {
                // copy to registry path
                newPackageFile = getPackageFile(pack.getId());
                Files.createDirectories(newPackageFile.getParent());
                Files.copy(file.toPath(), newPackageFile);
            } else {
                newPackageFile = file.toPath();
            }
            Map<PackageId, SubPackageHandling.Option> subpackages = registerSubPackages(pack, replace);
            Set<Dependency> dependencies = new HashSet<>(Arrays.asList(pack.getDependencies()));
            FSInstallState targetState = new FSInstallState(pack.getId(), FSPackageStatus.REGISTERED, newPackageFile)
                    .withDependencies(dependencies)
                    .withSubPackages(subpackages)
                    .withFilter(pack.getArchive().getMetaInf().getFilter())
                    .withSize(pack.getSize())
                    .withProperties(pack.getArchive().getMetaInf().getProperties())
                    .withExternal(external);
            stateCache.put(pack.getId(), targetState);
            return pack.getId();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void remove(@NotNull PackageId id) throws IOException, NoSuchPackageException {
        FSInstallState state = stateCache.remove(id);
        if (state == null) {
            throw new NoSuchPackageException().setId(id);
        }
        if (!state.isExternal()) {
            Files.delete(state.getFilePath());
        }
        dispatch(PackageEvent.Type.REMOVE, id, null);
    }

    /**
     * {@inheritDoc}
     */
    @NotNull
    @Override
    public Set<PackageId> packages() throws IOException {
        return stateCache.keySet();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void installPackage(@NotNull Session session, @NotNull RegisteredPackage pkg, @NotNull ImportOptions opts,
                               boolean extract) throws IOException, PackageException {

        // For now FS based persistence only supports extraction but no reversible installation
        if (!extract) {
            String msg = "Only extraction supported by FS based registry";
            log.error(msg);
            throw new PackageException(msg);
        }
        try (VaultPackage vltPkg = pkg.getPackage()) {
            WorkspaceFilter filter = getInstallState(vltPkg.getId()).getFilter();
            switch(scope) {
                case APPLICATION_SCOPED:
                   if (filter instanceof DefaultWorkspaceFilter) {
                       opts.setFilter(ScopedWorkspaceFilter.createApplicationScoped((DefaultWorkspaceFilter)filter));
                   } else {
                       String msg = "Scoped only supports WorkspaceFilters extending DefaultWorkspaceFilter";
                       log.error(msg);
                       throw new PackageException(msg);
                   }
                   break;
                case CONTENT_SCOPED:
                    if (filter instanceof DefaultWorkspaceFilter) {
                        opts.setFilter(ScopedWorkspaceFilter.createContentScoped((DefaultWorkspaceFilter)filter));
                    } else {
                        String msg = "Scoped only supports WorkspaceFilters extending DefaultWorkspaceFilter";
                        log.error(msg);
                        throw new PackageException(msg);
                    }
                    break;
                default:
                    // no need to set filter in other cases
                
            }
            ((ZipVaultPackage)vltPkg).extract(session, opts, getSecurityConfig(), isStrictByDefault());
            dispatch(PackageEvent.Type.EXTRACT, pkg.getId(), null);
            stateCache.updatePackageStatus(vltPkg.getId(), FSPackageStatus.EXTRACTED);

        } catch (RepositoryException e) {
            throw new IOException(e);
        }
    }

    /**
     * Uninstallation not supported for FS based PackageRegistry
     */
    @Override
    public void uninstallPackage(@NotNull Session session, @NotNull RegisteredPackage pkg, @NotNull ImportOptions opts) throws IOException, PackageException {
        String msg = "Uninstallation not supported by FS based registry";
        log.error(msg);
        throw new PackageException(msg);
    }

}

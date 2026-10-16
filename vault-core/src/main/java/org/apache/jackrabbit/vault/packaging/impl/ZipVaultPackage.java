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

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.regex.PatternSyntaxException;

import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.jackrabbit.vault.fs.config.MetaInf;
import org.apache.jackrabbit.vault.fs.io.AccessControlHandling;
import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.fs.io.ImportOptions;
import org.apache.jackrabbit.vault.fs.io.Importer;
import org.apache.jackrabbit.vault.fs.io.ZipArchive;
import org.apache.jackrabbit.vault.packaging.InstallContext;
import org.apache.jackrabbit.vault.packaging.InstallHookProcessor;
import org.apache.jackrabbit.vault.packaging.InstallHookProcessorFactory;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.apache.jackrabbit.vault.packaging.PackageProperties;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.apache.jackrabbit.vault.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements a vault package that is a zipped representation of a file vault
 * export.
 */
public class ZipVaultPackage extends PackagePropertiesImpl implements VaultPackage {

    private static final Logger log = LoggerFactory.getLogger(ZipVaultPackage.class);

    private Archive archive;

    public ZipVaultPackage(File file, boolean isTmpFile) throws IOException {
        this(file, isTmpFile, false);
    }

    public ZipVaultPackage(File file, boolean isTmpFile, boolean strict)
            throws IOException {
        this(new ZipArchive(file, isTmpFile), strict);
    }

    public ZipVaultPackage(Archive archive, boolean strict)
            throws IOException {
        this.archive = archive;
        if (strict) {
            try {
                archive.open(true);
            } catch (IOException e) {
                log.error("Error while loading package {}.", archive);
                throw e;
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public void close() {
        if (archive != null) {
            archive.close();
            archive = null;
        }
    }

    /**
     * {@inheritDoc}
     */
    public Archive getArchive() {
        if (archive == null) {
            log.error("Package already closed: {}", getId());
            throw new IllegalStateException("Package already closed: " + getId());
        }
        try {
            archive.open(false);
        } catch (IOException e) {
            log.error("Archive not valid.", e);
            throw new IllegalStateException("Archive not valid.", e);
        }
        return archive;
    }

    /**
     * {@inheritDoc}
     */
    public boolean isValid() {
        try {
            return getMetaInf().getFilter() != null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * {@inheritDoc}
     */
    public boolean isClosed() {
        return archive == null;
    }

    /**
     * Returns the file this package is based on.
     * @return the file of this package or {@code null}.
     */
    public File getFile() {
        return (archive instanceof ZipArchive) ? ((ZipArchive) archive).getFile() : null;
    }

    /**
     * {@inheritDoc}
     */
    public MetaInf getMetaInf() {
        try {
            return getArchive().getMetaInf();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * {@inheritDoc}
     */
    public long getSize() {
        return (archive instanceof ZipArchive) ? ((ZipArchive) archive).getFileSize() : -1;
    }

    /**
     * {@inheritDoc}
     */
    public void extract(Session session, ImportOptions opts) throws RepositoryException, PackageException {
        extract(prepareExtract(session, opts), null);
    }

    /**
     * {@inheritDoc}
     */
    public PackageProperties getProperties() {
        return this;
    }

    /**
     * Prepares extraction.
     *
     * @param session repository session
     * @param opts import options
     *
     * @throws javax.jcr.RepositoryException if a repository error during installation occurs.
     * @throws org.apache.jackrabbit.vault.packaging.PackageException if an error during packaging occurs
     * @throws IllegalStateException if the package is not valid.
     * @return installation context
     */
    protected InstallContextImpl prepareExtract(Session session, ImportOptions opts) throws RepositoryException, PackageException {
        if (!isValid()) {
            throw new IllegalStateException("Package not valid.");
        }
        // try to find any hooks
        InstallHookProcessor hooks = opts instanceof InstallHookProcessorFactory ?
                ((InstallHookProcessorFactory) opts).createInstallHookProcessor()
                : new InstallHookProcessorImpl();
        if (!opts.isDryRun()) {
            // Check archive metadata before hook registration can load package-supplied code.
            if (requiresRoot() || hasHookEntries(archive)) {
                checkAdministrativePermissions(session);
            }
            hooks.registerHooks(archive, opts.getHookClassLoader());
        }

        // Custom processors may register hooks that are not declared in the archive.
        if (requiresRoot() || hooks.hasHooks()) {
            checkAdministrativePermissions(session);
        }

        Importer importer = new Importer(opts);
        AccessControlHandling ac = getACHandling();
        if (opts.getAccessControlHandling() == null) {
            opts.setAccessControlHandling(ac);
        }
        String cndPattern = getProperty(NAME_CND_PATTERN);
        if (cndPattern != null) {
            try {
                opts.setCndPattern(cndPattern);
            } catch (PatternSyntaxException e) {
                throw new PackageException("Specified CND pattern not valid.", e);
            }
        }

        return new InstallContextImpl(session, "/", this, importer, hooks);
    }

    /**
     * Detects whether the given archive declares install hooks, by pure archive listing:
     * an entry below {@code META-INF/vault/hooks} ending with {@code .jar}, or a package
     * property starting with {@code installhook.}. Deliberately loads no class and creates
     * no class loader. The package properties have already been parsed when the archive was
     * opened, so this only consults cached metadata and triggers no additional MetaInf
     * processing.
     *
     * @param archive the archive to check
     * @return {@code true} if the archive declares at least one install hook
     * @throws PackageException if an I/O error occurs while listing the archive
     */
    static boolean hasHookEntries(Archive archive) throws PackageException {
        try {
            Archive.Entry metaInf = archive.getRoot().getChild(Constants.META_INF);
            Archive.Entry vaultDir = metaInf == null ? null : metaInf.getChild(Constants.VAULT_DIR);
            Archive.Entry hooksDir = vaultDir == null ? null : vaultDir.getChild(Constants.HOOKS_DIR);
            if (hooksDir != null) {
                for (Archive.Entry entry : hooksDir.getChildren()) {
                    // only respect .jar files (same rule as InstallHookProcessorImpl.registerHooks)
                    if (entry.getName().endsWith(".jar")) {
                        return true;
                    }
                }
            }
            Properties props = archive.getMetaInf().getProperties();
            if (props != null) {
                Enumeration<?> names = props.propertyNames();
                while (names.hasMoreElements()) {
                    if (names.nextElement().toString().startsWith(VaultPackage.PREFIX_INSTALL_HOOK)) {
                        return true;
                    }
                }
            }
            return false;
        } catch (IOException e) {
            throw new PackageException("I/O Error while detecting install hooks", e);
        }
    }

    private static void checkAdministrativePermissions(Session session)
            throws PackageException, RepositoryException {
        if (!AdminPermissionChecker.hasAdministrativePermissions(session)) {
            log.error("Package extraction requires admin session.");
            throw new PackageException("Package extraction requires admin session (userid not allowed).");
        }
    }

    /**
     * Same as above but the given subPackages argument receives a list of
     * potential sub packages.
     *
     * @param ctx install context
     * @param subPackages receives the list of potential sub packages
     *
     * @throws javax.jcr.RepositoryException if a repository error during installation occurs.
     * @throws org.apache.jackrabbit.vault.packaging.PackageException if an error during packaging occurs
     * @throws IllegalStateException if the package is not valid.
     */
    protected void extract(InstallContextImpl ctx,
                           List<String> subPackages)
            throws RepositoryException, PackageException {
        log.debug("Extracting {}", getId());
        InstallHookProcessor hooks = ctx.getHooks();
        Importer importer = ctx.getImporter();
        try {
            if (!hooks.execute(ctx)) {
                ctx.setPhase(InstallContext.Phase.PREPARE_FAILED);
                hooks.execute(ctx);
                throw new PackageException("Error while executing an install hook during prepare phase.");
            }
            try {
                importer.run(archive, ctx.getSession(), ctx.getImportRootPath());
            } catch (Exception e) {
                log.error("Error during install.", e);
                ctx.setPhase(InstallContext.Phase.INSTALL_FAILED);
                hooks.execute(ctx);
                throw new PackageException(e);
            }
            ctx.setPhase(InstallContext.Phase.INSTALLED);
            if (!hooks.execute(ctx)) {
                ctx.setPhase(InstallContext.Phase.INSTALL_FAILED);
                hooks.execute(ctx);
                throw new PackageException("Error while executing an install hook during installed phase.");
            }
            if (importer.hasErrors() && ctx.getOptions().isStrict()) {
                ctx.setPhase(InstallContext.Phase.INSTALL_FAILED);
                hooks.execute(ctx);
                throw new PackageException("Errors during import.");
            }
        } finally {
            ctx.setPhase(InstallContext.Phase.END);
            hooks.execute(ctx);
        }
        if (subPackages != null) {
            subPackages.addAll(importer.getSubPackages());
        }
        log.debug("Extracting {} completed.", getId());
    }

    @Override
    protected Properties getPropertiesMap() {
        return getMetaInf().getProperties();
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            close();
        } catch (Throwable e) {
            // ignore
        }
        super.finalize();
    }
}
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements a vault package that is a zipped representation of a file vault
 * export.
 */
public class ZipVaultPackage extends PackagePropertiesImpl implements VaultPackage {

    private static final Logger log = LoggerFactory.getLogger(ZipVaultPackage.class);

    public static final String UNKNOWN_PATH = "/etc/packages/unknown";

    private Archive archive;

    protected ZipVaultPackage(File file, boolean isTmpFile) throws IOException {
        this(file, isTmpFile, false);
    }

    protected ZipVaultPackage(File file, boolean isTmpFile, boolean strict)
            throws IOException {
        this(new ZipArchive(file, isTmpFile), strict);
    }

    protected ZipVaultPackage(Archive archive, boolean strict)
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
     * @return the file of this package or <code>null</code>.
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
            hooks.registerHooks(archive, opts.getHookClassLoader());
        }

        if (requiresRoot() || hooks.hasHooks()) {
            if (!AdminPermissionChecker.hasAdministrativePermissions(session)) {
                log.error("Package extraction requires admin session.");
                throw new PackageException("Package extraction requires admin session (userid not allowed).");
            }
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

        return new InstallContextImpl(session.getRootNode(), this, importer, hooks);
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
        log.info("Extracting {}", getId());
        InstallHookProcessor hooks = ctx.getHooks();
        Importer importer = ctx.getImporter();
        try {
            if (!hooks.execute(ctx)) {
                ctx.setPhase(InstallContext.Phase.PREPARE_FAILED);
                hooks.execute(ctx);
                throw new PackageException("Import aborted during prepare phase.");
            }
            try {
                importer.run(archive, ctx.getImportRoot());
            } catch (Exception e) {
                log.error("Error during install.", e);
                ctx.setPhase(InstallContext.Phase.INSTALL_FAILED);
                hooks.execute(ctx);
                throw new PackageException(e);
            }
            ctx.setPhase(InstallContext.Phase.INSTALLED);
            hooks.execute(ctx);
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
        log.info("Extracting {} completed.", getId());
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
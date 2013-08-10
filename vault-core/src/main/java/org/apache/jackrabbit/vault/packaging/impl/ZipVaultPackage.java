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
import java.util.Calendar;
import java.util.List;
import java.util.Properties;
import java.util.regex.PatternSyntaxException;

import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.commons.io.FileUtils;
import org.apache.jackrabbit.util.ISO8601;
import org.apache.jackrabbit.vault.fs.config.MetaInf;
import org.apache.jackrabbit.vault.fs.io.AccessControlHandling;
import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.fs.io.ImportOptions;
import org.apache.jackrabbit.vault.fs.io.Importer;
import org.apache.jackrabbit.vault.fs.io.ZipArchive;
import org.apache.jackrabbit.vault.packaging.Dependency;
import org.apache.jackrabbit.vault.packaging.InstallContext;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements a vault package that is a zipped representation of a file vault
 * export.
 */
public class ZipVaultPackage implements VaultPackage {

    private static final Logger log = LoggerFactory.getLogger(ZipVaultPackage.class);

    public static final String UNKNOWN_PATH = "/etc/packages/unknown";

    private File file;

    private Archive archive;

    private boolean isTmpFile;

    private PackageId id;

    protected ZipVaultPackage(File file, boolean isTmpFile) throws IOException {
        this(file, isTmpFile, false);
    }

    protected ZipVaultPackage(File file, boolean isTmpFile, boolean strict)
            throws IOException {
        this.file = file;
        this.isTmpFile = isTmpFile;
        if (strict) {
            try {
                archive = new ZipArchive(file);
                archive.open(strict);
            } catch (IOException e) {
                log.error("Error while loading package {}.", file.getPath());
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
        if (file != null && isTmpFile) {
            FileUtils.deleteQuietly(file);
        }
        file = null;
    }

    /**
     * {@inheritDoc}
     */
    public Archive getArchive() {
        if (archive == null) {
            if (file == null) {
                log.error("Package already closed: " + id);
                throw new IllegalStateException("Package already closed: " + id);
            }
            archive = new ZipArchive(file);
            try {
                archive.open(false);
            } catch (IOException e) {
                log.error("Archive not valid.", e);
                throw new IllegalStateException("Archive not valid for file " + file, e);
            }
        }
        return archive;
    }

    public PackageId getId() {
        if (id == null) {
            String version = getProperty(NAME_VERSION);
            if (version == null) {
                log.warn("Package does not specify a version. setting to ''");
                 version = "";
            }
            String group = getProperty(NAME_GROUP);
            String name = getProperty(NAME_NAME);
            if (group != null && name != null) {
                id = new PackageId(group, name, version);
            } else {
                // check for legacy packages that only contains a 'path' property
                String path = getProperty("path");
                if (path == null || path.length() == 0) {
                    log.warn("Package does not specify a path. setting to 'unknown'");
                    path = UNKNOWN_PATH;
                }
                id = new PackageId(path, version);
            }
        }
        return id;
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
        return file == null;
    }

    /**
     * Returns the file this package is based on.
     * @return the file of this package or <code>null</code>.
     */
    public File getFile() {
        return file;
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
        return file == null
                ? -1
                : file.length();
    }

    /**
     * {@inheritDoc}
     */
    public Calendar getLastModified() {
        return getDateProperty(NAME_LAST_MODIFIED);
    }

    /**
     * {@inheritDoc}
     */
    public String getLastModifiedBy() {
        return getProperty(NAME_LAST_MODIFIED_BY);
    }

    /**
     * {@inheritDoc}
     */
    public Calendar getCreated() {
        return getDateProperty(NAME_CREATED);
    }

    /**
     * {@inheritDoc}
     */
    public String getCreatedBy() {
        return getProperty(NAME_CREATED_BY);
    }

    /**
     * {@inheritDoc}
     */
    public Calendar getLastWrapped() {
        return getDateProperty(NAME_LAST_WRAPPED);
    }

    /**
     * {@inheritDoc}
     */
    public String getLastWrappedBy() {
        return getProperty(NAME_LAST_WRAPPED_BY);
    }

    /**
     * {@inheritDoc}
     */
    public String getDescription() {
        return getProperty(NAME_DESCRIPTION);
    }

    /**
     * {@inheritDoc}
     */
    public AccessControlHandling getACHandling() {
        String ac = getProperty(NAME_AC_HANDLING);
        if (ac == null) {
            return AccessControlHandling.IGNORE;
        } else {
            try {
                return AccessControlHandling.valueOf(ac.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("invalid access control handling configured: {}", ac);
                return AccessControlHandling.IGNORE;
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public boolean requiresRoot() {
        return "true".equals(getProperty(NAME_REQUIRES_ROOT));
    }

    /**
     * {@inheritDoc}
     */
    public Dependency[] getDependencies() {
        String deps = getProperty(NAME_DEPENDENCIES);
        if (deps == null) {
            return Dependency.EMPTY;
        } else {
            return Dependency.parse(deps);
        }
    }

    /**
     * {@inheritDoc}
     */
    public void extract(Session session, ImportOptions opts) throws RepositoryException, PackageException {
        extract(prepareExtract(session, opts), null);
    }

    /**
     * Prepares extraction.
     *
     * @param session repository session
     * @param opts import options
     *
     * @throws RepositoryException if a repository error during installation occurs.
     * @throws PackageException if an error during packaging occurs
     * @throws IllegalStateException if the package is not valid.
     * @return installation context
     */
    protected InstallContextImpl prepareExtract(Session session, ImportOptions opts) throws RepositoryException, PackageException {
        if (!isValid()) {
            throw new IllegalStateException("Package not valid.");
        }
        // try to find any hooks
        InstallHookProcessor hooks = new InstallHookProcessor();
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
     * @throws RepositoryException if a repository error during installation occurs.
     * @throws PackageException if an error during packaging occurs
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

    private Calendar getDateProperty(String name) {
        try {
            String p = getProperty(name);
            return p == null
                    ? null
                    : ISO8601.parse(p);
        } catch (Exception e) {
            log.error("Error while converting date property", e);
            return null;
        }
    }

    private String getProperty(String name) {
        try {
            Properties props = getMetaInf().getProperties();
            return props == null ? null : props.getProperty(name);
        } catch (Exception e) {
            return null;
        }
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
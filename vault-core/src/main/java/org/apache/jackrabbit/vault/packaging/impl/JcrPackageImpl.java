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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.jcr.Binary;
import javax.jcr.NamespaceException;
import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.jcr.nodetype.NodeTypeManager;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.jackrabbit.spi.Name;
import org.apache.jackrabbit.spi.commons.conversion.DefaultNamePathResolver;
import org.apache.jackrabbit.vault.fs.api.ImportMode;
import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.api.ProgressTrackerListener;
import org.apache.jackrabbit.vault.fs.api.VaultInputSource;
import org.apache.jackrabbit.vault.fs.io.AccessControlHandling;
import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.fs.io.ImportOptions;
import org.apache.jackrabbit.vault.fs.io.MemoryArchive;
import org.apache.jackrabbit.vault.fs.io.ZipArchive;
import org.apache.jackrabbit.vault.fs.spi.NodeTypeSet;
import org.apache.jackrabbit.vault.packaging.CyclicDependencyException;
import org.apache.jackrabbit.vault.packaging.Dependency;
import org.apache.jackrabbit.vault.packaging.DependencyException;
import org.apache.jackrabbit.vault.packaging.DependencyHandling;
import org.apache.jackrabbit.vault.packaging.DependencyUtil;
import org.apache.jackrabbit.vault.packaging.ExportOptions;
import org.apache.jackrabbit.vault.packaging.JcrPackage;
import org.apache.jackrabbit.vault.packaging.JcrPackageDefinition;
import org.apache.jackrabbit.vault.packaging.JcrPackageManager;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.SubPackageHandling;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.apache.jackrabbit.vault.packaging.Version;
import org.apache.jackrabbit.vault.packaging.events.PackageEvent;
import org.apache.jackrabbit.vault.util.JcrConstants;
import org.apache.jackrabbit.vault.util.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements a JcrPackage
 */
public class JcrPackageImpl implements JcrPackage {

    /**
     * max allowed package size for using a memory archive
     */
    public static final long MAX_MEMORY_ARCHIVE_SIZE = 1024*1024;

    /**
     * default logger
     */
    private static final Logger log = LoggerFactory.getLogger(JcrPackageImpl.class);

    /**
     * our package manager
     */
    private final JcrPackageManagerImpl mgr;

    /**
     * underlying node
     */
    @Nullable
    private Node node;

    /**
     * underlying package
     */
    @Nullable
    private ZipVaultPackage pack;

    /**
     * underlying definition
     */
    @Nullable
    private JcrPackageDefinitionImpl def;

    public JcrPackageImpl(@Nonnull JcrPackageManagerImpl mgr, @Nullable Node node) throws RepositoryException {
        this.mgr = mgr;
        this.node = node;
    }

    protected JcrPackageImpl(@Nonnull JcrPackageManagerImpl mgr, @Nullable Node node, @Nullable ZipVaultPackage pack) throws RepositoryException {
        this.mgr = mgr;
        this.node = node;
        this.pack = pack;
    }

    /**
     * {@inheritDoc}
     */
    public JcrPackageDefinition getDefinition() throws RepositoryException {
        if (def == null) {
            if (isValid()) {
                Node defNode = getDefNode();
                def = defNode == null
                        ? null
                        : new JcrPackageDefinitionImpl(defNode);
            }
        }
        return def;
    }

    /**
     * {@inheritDoc}
     */
    public int compareTo(JcrPackage o) {
        try {
            JcrPackageDefinition d1 = getDefinition();
            JcrPackageDefinition d2 = o.getDefinition();
            return d1.getId().compareTo(d2.getId());
        } catch (Exception e) {
            log.error("error during compare: {}", e.toString());
            return 0;
        }
    }

    /**
     * {@inheritDoc}
     */
    public boolean isValid() {
        try {
            if (node != null) {
                if (node.isNodeType(JcrConstants.NT_HIERARCHYNODE) && node.hasNode(JcrConstants.JCR_CONTENT)) {
                    if (node.getNode(JcrConstants.JCR_CONTENT).isNodeType(NT_VLT_PACKAGE)) {
                        return true;
                    }
                }
            }
        } catch (RepositoryException e) {
            log.warn("Error during evaluation of isValid()", e);
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    public boolean isInstalled() throws RepositoryException {
        JcrPackageDefinition def = getDefinition();
        return def != null && def.getLastUnpacked() != null;
    }

    /**
     * {@inheritDoc}
     */
    public Node getNode() {
        return node;
    }

    /**
     * {@inheritDoc}
     */
    public boolean isSealed() {
        try {
            if (!isValid()) {
                return false;
            }
            if (getSize() == 0) {
                return false;
            }
            final JcrPackageDefinition def = getDefinition();
            return def == null || !def.isModified();
        } catch (RepositoryException e) {
            log.warn("Error during isSealed()", e);
            return false;
        }

    }

    /**
     * {@inheritDoc}
     */
    public boolean verifyId(boolean autoFix, boolean autoSave) throws RepositoryException {
        // check if package id is correct
        JcrPackageDefinition jDef = getDefinition();
        if (jDef == null) {
            return true;
        }
        if (node == null) {
            return false;
        }
        PackageId id = jDef.getId();
        PackageId cId = new PackageId(node.getPath());
        // compare installation paths since non-conform version numbers might
        // lead to different pids (bug #35564)
        if (id.getInstallationPath().equals(cId.getInstallationPath())) {
            if (autoFix && id.isFromPath()) {
                // if definition has no id set, fix anyways
                jDef.setId(cId, autoSave);
            }
            return true;
        }
        if (autoFix) {
            log.warn("Fixing non-matching id from {} to {}.", id, cId);
            jDef.setId(cId, autoSave);
        }
        return false;
    }


    /**
     * Tries to unwrap the definition of this package.
     * @throws IOException if an I/O error occurs or if the underlying file is not a package
     * @throws RepositoryException if a repository error occurs
     */
    public void tryUnwrap() throws IOException, RepositoryException {
        if (isValid()) {
            return;
        }
        if (node == null) {
            return;
        }
        VaultPackage pack = getPackage();
        Node content = getContent();
        if (content == null) {
            return;
        }
        boolean ok = false;
        try {
            content.addMixin(NT_VLT_PACKAGE);
            Node defNode = content.addNode(NN_VLT_DEFINITION);
            JcrPackageDefinition def = new JcrPackageDefinitionImpl(defNode);
            def.unwrap(pack, true, false);
            node.getSession().save();
            ok = true;
        } finally {
            if (!ok) {
                try {
                    node.getSession().refresh(false);
                } catch (RepositoryException e) {
                    // ignore
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public VaultPackage getPackage() throws RepositoryException, IOException {
        return getPackage(false);
    }

    /**
     * Creates a new package by creating the appropriate archive.
     *
     * This is basically a workaround to ensure that 'rewrap' has a zip file to work on.
     * Ideally rewrap should not realy on the archive format.
     *
     * @param forceFileArchive if {@code true} a file archive is enforced
     * @return the package
     *
     * @throws RepositoryException If a repository error occurrs.
     * @throws IOException if an i/o error occurrs.
     */
    @Nonnull
    protected VaultPackage getPackage(boolean forceFileArchive) throws RepositoryException, IOException {
        if (forceFileArchive && pack != null && !(pack.getArchive() instanceof ZipArchive)) {
            pack.close();
            pack = null;
        }

        if (pack == null) {
            long size = -1;
            try {
                size = getData().getLength();
            } catch (RepositoryException e) {
                // ignore
            }
            if (!forceFileArchive && size >= 0 && size < MAX_MEMORY_ARCHIVE_SIZE) {
                MemoryArchive archive = new MemoryArchive(false);
                InputStream in = getData().getStream();
                try {
                    archive.run(in);
                } catch (Exception e) {
                    throw new IOException("Error while reading stream", e);
                } finally {
                    in.close();
                }
                pack = new ZipVaultPackage(archive, true);
            } else {
                File tmpFile = File.createTempFile("vaultpack", ".zip");
                FileOutputStream out = FileUtils.openOutputStream(tmpFile);
                Binary bin = getData().getBinary();
                InputStream in = null;
                try {
                    in = bin.getStream();
                    IOUtils.copy(in, out);
                } finally {
                    IOUtils.closeQuietly(in);
                    IOUtils.closeQuietly(out);
                    bin.dispose();
                }
                pack = new ZipVaultPackage(tmpFile, true);
            }
        }
        return pack;
    }

    /**
     * {@inheritDoc}
     */
    public void extract(ImportOptions opts) throws RepositoryException, PackageException, IOException {
        extract(opts, false, false);

    }

    /**
     * {@inheritDoc}
     */
    public void install(ImportOptions opts) throws RepositoryException, PackageException, IOException {
        extract(opts, true, false);
    }


    private void extract(ImportOptions options, boolean createSnapshot, boolean replaceSnapshot)
            throws RepositoryException, PackageException, IOException {
        extract(new HashSet<PackageId>(), options, createSnapshot, replaceSnapshot);
    }

    /**
     * internally extracts the package.
     *
     * @param processed the set of processed dependencies
     * @param options the import options
     * @param createSnapshot {@code true} if a snapshot should be created
     * @param replaceSnapshot {@code true} if a snapshot should be replaced
     * @throws RepositoryException if a repository error occurs
     * @throws PackageException if a package error occurs
     * @throws IOException if an I/O error occurs
     */
    private void extract(Set<PackageId> processed, ImportOptions options, boolean createSnapshot, boolean replaceSnapshot)
            throws RepositoryException, PackageException, IOException {
        getPackage();
        if (def != null) {
            processed.add(def.getId());
        }

        if (options.getDependencyHandling() != null && options.getDependencyHandling() != DependencyHandling.IGNORE) {
            installDependencies(processed, options, createSnapshot, replaceSnapshot);
        }

        // get a copy of the import options (bug 35164)
        ImportOptions opts = options.copy();
        // check for disable intermediate saves (GRANITE-1047)
        if (this.getDefinition().getBoolean(JcrPackageDefinition.PN_DISABLE_INTERMEDIATE_SAVE) ) {
            // MAX_VALUE disables saving completely, therefore we have to use a lower value!
            opts.setAutoSaveThreshold(Integer.MAX_VALUE - 1);
        }
        InstallContextImpl ctx = pack.prepareExtract(node.getSession(), opts);
        JcrPackage snap = null;
        if (!opts.isDryRun() && createSnapshot) {
            ExportOptions eOpts = new ExportOptions();
            eOpts.setListener(opts.getListener());
            snap = snapshot(eOpts, replaceSnapshot, opts.getAccessControlHandling());
        }
        List<String> subPackages = new ArrayList<String>();
        pack.extract(ctx, subPackages);
        getDefinition();
        if (def != null && !opts.isDryRun()) {
            def.touchLastUnpacked(null, true);
        }

        // process sub packages
        Session s = node.getSession();
        List<JcrPackageImpl> subPacks = new LinkedList<JcrPackageImpl>();
        for (String path: subPackages) {
            if (s.nodeExists(path)) {
                JcrPackageImpl p = new JcrPackageImpl(mgr, s.getNode(path));
                if (!p.isValid()) {
                    // check if package was included as pure .zip or .jar
                    try {
                        p.tryUnwrap();
                    } catch (Exception e) {
                        log.info("Sub package {} not valid: " + e, path);
                    }
                }
                if (p.isValid()) {
                    // ensure that sub package is marked as not-installed. it might contain wrong data in vlt:definition (JCRVLT-114)
                    JcrPackageDefinitionImpl def = (JcrPackageDefinitionImpl) p.getDefinition();
                    def.clearLastUnpacked(false);

                    // add dependency to the parent package if required
                    Dependency[] oldDeps = def.getDependencies();
                    Dependency[] newDeps = DependencyUtil.addExact(oldDeps, pack.getId());
                    if (oldDeps != newDeps) {
                        def.setDependencies(newDeps, false);
                    }

                    PackageId pId = def.getId();
                    String pName = pId.getName();
                    Version pVersion = pId.getVersion();

                    // get the list of packages available in the same group
                    JcrPackageManager pkgMgr = new JcrPackageManagerImpl(s);                    
                    List<JcrPackage> listPackages = pkgMgr.listPackages(pId.getGroup(), true);

                    // keep some status variable if a more recent is found in the next loop
                    PackageId newerPackageId = null;

                    // loop in the list of packages returned previously by package manager
                    for (JcrPackage listedPackage: listPackages) {
                        JcrPackageDefinition listedPackageDef = listedPackage.getDefinition();
                        if (listedPackageDef == null) {
                            continue;
                        }
                        PackageId listedPackageId = listedPackageDef.getId();
                        if (listedPackageId.equals(pId)) {
                            continue;
                        }
                        // check that the listed package is actually from same name (so normally only version would differ)
                        // if that package is valid, installed, and the version is more recent than the one in our sub package
                        // then we can stop the loop here
                        if (pName.equals(listedPackageId.getName()) && listedPackage.isValid() && listedPackage.isInstalled()
                                && listedPackageId.getVersion().compareTo(pVersion) > 0) {
                            newerPackageId = listedPackageId;
                            break;
                        }
                    }
                    // if a more recent version of that subpackage was found we don't need to add it to the list of sub packages to eventually extract later on.
                    if (newerPackageId != null) {
                        log.debug("Skipping installation if subpackage '{}' due to newer installed version: '{}'", pId, newerPackageId);
                    } else {
                        subPacks.add(p);
                    }
                }
            }
        }

        // don't extract sub packages if not recursive
        if (!opts.isNonRecursive() && !subPacks.isEmpty()) {
            try {
                DependencyUtil.sortPackages(subPacks);
            } catch (CyclicDependencyException e) {
                if (opts.isStrict()) {
                    throw e;
                }
            }
            List<String> subIds = new LinkedList<String>();
            SubPackageHandling sb = pack.getSubPackageHandling();
            for (JcrPackageImpl p: subPacks) {
                boolean skip = false;
                PackageId id = p.getDefinition().getId();
                SubPackageHandling.Option option = sb.getOption(id);
                String msg;
                if (option == SubPackageHandling.Option.ADD || option == SubPackageHandling.Option.IGNORE) {
                    msg = "skipping installation of subpackage " + id + " due to option " + option;
                    skip = true;
                } else if (option == SubPackageHandling.Option.INSTALL) {
                    msg = "Starting installation of subpackage " + id;
                } else {
                    msg = "Starting extraction of subpackage " + id;
                }
                if (options.isDryRun()) {
                    msg = "Dry run: " + msg;
                }
                if (options.getListener() != null) {
                    options.getListener().onMessage(ProgressTrackerListener.Mode.TEXT, msg, "");
                } else {
                    log.debug(msg);
                }
                if (!skip) {
                    if (createSnapshot && option == SubPackageHandling.Option.INSTALL) {
                        p.extract(options, true, true);
                        subIds.add(id.toString());
                    } else {
                        p.extract(options, false, true);
                    }
                }
                p.close();
            }
            // register sub packages in snapshot for uninstall
            if (snap != null) {
                snap.getDefinition().getNode().setProperty(JcrPackageDefinition.PN_SUB_PACKAGES, subIds.toArray(new String[subIds.size()]));
                s.save();
            }
        }

        if (createSnapshot) {
            mgr.dispatch(PackageEvent.Type.INSTALL, def.getId(), null);
        } else {
            mgr.dispatch(PackageEvent.Type.EXTRACT, def.getId(), null);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Nonnull
    @Override
    public PackageId[] extractSubpackages(@Nonnull ImportOptions opts)
            throws RepositoryException, PackageException, IOException {
        Set<PackageId> processed = new HashSet<PackageId>();
        extractSubpackages(opts, processed);
        PackageId[] ret = processed.toArray(new PackageId[processed.size()]);
        Arrays.sort(ret);
        mgr.dispatch(PackageEvent.Type.EXTRACT_SUB_PACKAGES, getDefinition().getId(), ret);
        return ret;
    }

    private void extractSubpackages(@Nonnull ImportOptions opts, @Nonnull Set<PackageId> processed)
            throws RepositoryException, PackageException, IOException {
        final VaultPackage pack = getPackage();
        final PackageId pId = pack.getId();
        Archive a = pack.getArchive();
        Archive.Entry packages = a.getEntry("/jcr_root/etc/packages");
        if (packages == null) {
            return;
        }
        List<Archive.Entry> entries = new LinkedList<Archive.Entry>();
        findSubPackageEntries(entries, packages);
        if (entries.isEmpty()) {
            log.debug("Package {} contains no sub-packages.", pId);
            return;
        }

        // check if filter has root outside /etc/packages
        boolean hasOwnContent = false;
        for (PathFilterSet root: a.getMetaInf().getFilter().getFilterSets()) {
            if (!Text.isDescendantOrEqual("/etc/packages", root.getRoot())) {
                log.debug("Package {}: contains content outside /etc/packages. Sub packages will have a dependency to it", pId);
                hasOwnContent = true;
                break;
            }
        }
        // check if package has nodetype no installed in the repository
        if (!hasOwnContent) {
            DefaultNamePathResolver npResolver = new DefaultNamePathResolver(getNode().getSession());
            NodeTypeManager ntMgr = getNode().getSession().getWorkspace().getNodeTypeManager();
            loop0: for (NodeTypeSet cnd: a.getMetaInf().getNodeTypes()) {
                for (Name name: cnd.getNodeTypes().keySet()) {
                    String jcrName;
                    try {
                        jcrName = npResolver.getJCRName(name);
                    } catch (NamespaceException e) {
                        // in case the uri is not registered. we also break here
                        log.debug("Package {}: contains namespace not installed in the repository: {}. Sub packages will have a dependency to it", pId, name.getNamespaceURI());
                        hasOwnContent = true;
                        break loop0;
                    }
                    if (!ntMgr.hasNodeType(jcrName)) {
                        log.debug("Package {}: contains nodetype not installed in the repository: {}. Sub packages will have a dependency to it", pId, jcrName);
                        hasOwnContent = true;
                        break loop0;
                    }
                }
            }
        }

        // process the discovered sub-packages
        for (Archive.Entry e: entries) {
            VaultInputSource in = a.getInputSource(e);
            InputStream ins = null;
            try {
                ins = in.getByteStream();
                JcrPackageImpl subPackage;
                try {
                    subPackage = (JcrPackageImpl) mgr.upload(ins, true, true);
                } catch (RepositoryException e1) {
                    log.error("Package {}: Error while extracting subpackage {}: {}", pId, in.getSystemId());
                    continue;
                }

                if (hasOwnContent) {
                    // add dependency to this package
                    Dependency[] oldDeps = subPackage.getDefinition().getDependencies();
                    Dependency[] newDeps = DependencyUtil.addExact(oldDeps, pId);
                    if (oldDeps != newDeps) {
                        subPackage.getDefinition().setDependencies(newDeps, true);
                    }
                } else {
                    // add parent dependencies to this package
                    Dependency[] oldDeps = subPackage.getDefinition().getDependencies();
                    Dependency[] newDeps = oldDeps;
                    for (Dependency d: getDefinition().getDependencies()) {
                        newDeps = DependencyUtil.add(newDeps, d);
                    }
                    if (oldDeps != newDeps) {
                        subPackage.getDefinition().setDependencies(newDeps, true);
                    }
                }

                PackageId id = subPackage.getDefinition().getId();
                processed.add(id);
                log.debug("Package {}: Extracted sub-package: {}", pId, id);

                if (!opts.isNonRecursive()) {
                    subPackage.extractSubpackages(opts, processed);
                }
            } finally {
                if (ins != null) {
                    ins.close();
                }
            }
        }

        // if no content, mark as installed
        if (!entries.isEmpty() && !hasOwnContent) {
            log.debug("Package {}: is pure container package. marking as installed.", pId);
            getDefinition();
            if (def != null && !opts.isDryRun()) {
                def.touchLastUnpacked(null, true);
            }
        }
    }

    private void findSubPackageEntries(@Nonnull List<Archive.Entry> entries, @Nonnull Archive.Entry folder) {
        for (Archive.Entry e: folder.getChildren()) {
            final String name = e.getName();
            if (e.isDirectory()) {
                if (!".snapshot".equals(name)) {
                    findSubPackageEntries(entries, e);
                }
            } else {
                // only process files with .zip extension
                if (name.endsWith(".zip")) {
                    entries.add(e);
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Dependency[] getUnresolvedDependencies() throws RepositoryException {
        JcrPackageDefinition def = getDefinition();
        if (def == null) {
            return Dependency.EMPTY;
        }
        List<Dependency> unresolved = new LinkedList<Dependency>();
        for (Dependency dep: def.getDependencies()) {
            if (mgr.resolve(dep, true) == null) {
                unresolved.add(dep);
            }
        }
        return unresolved.toArray(new Dependency[unresolved.size()]);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PackageId[] getResolvedDependencies() throws RepositoryException {
        JcrPackageDefinition def = getDefinition();
        if (def == null) {
            return PackageId.EMPTY;
        }
        List<PackageId> resolved = new LinkedList<PackageId>();
        for (Dependency dep: def.getDependencies()) {
            PackageId id = mgr.resolve(dep, true);
            if (id != null) {
                resolved.add(id);
            }
        }
        return resolved.toArray(new PackageId[resolved.size()]);
    }

    /**
     * Checks if all the package dependencies are resolved
     * @param opts install options
     */
    private void installDependencies(Set<PackageId> processed, ImportOptions opts, boolean createSnapshot, boolean replaceSnapshot)
            throws PackageException, RepositoryException, IOException {
        if (def == null) {
            return;
        }
        List<Dependency> unresolved = new LinkedList<Dependency>();
        List<JcrPackageImpl> uninstalled = new LinkedList<JcrPackageImpl>();
        for (Dependency dep: def.getDependencies()) {
            // resolve to installed and uninstalled packages
            PackageId id = mgr.resolve(dep, false);
            if (id == null) {
                unresolved.add(dep);
            } else {
                JcrPackageImpl pack = (JcrPackageImpl) mgr.open(id);
                if (!pack.isInstalled()) {
                    unresolved.add(dep);
                    uninstalled.add(pack);
                }
            }
        }
        // if non unresolved, then we're good
        if (unresolved.size() == 0) {
            return;
        }
        // if the package is not installed at all, abort for required and strict handling
        if ((opts.getDependencyHandling() == DependencyHandling.STRICT && unresolved.size() > 0)
                || (opts.getDependencyHandling() == DependencyHandling.REQUIRED && unresolved.size() > uninstalled.size())) {
            String msg = String.format("Refusing to install package %s. required dependencies missing: %s", def.getId(), unresolved);
            log.error(msg);
            throw new DependencyException(msg);
        }

        for (JcrPackageImpl pack: uninstalled) {
            if (pack.isInstalled()) {
                continue;
            }
            PackageId packageId = pack.getDefinition().getId();
            if (processed.contains(packageId)) {
                if (opts.getDependencyHandling() == DependencyHandling.BEST_EFFORT) {
                    continue;
                }
                String msg = String.format("Unable to install package %s. dependency has as cycling reference to %s", def.getId(), packageId);
                log.error(msg);
                throw new CyclicDependencyException(msg);
            }
            pack.extract(processed, opts, createSnapshot, replaceSnapshot);
        }
    }

    /**
     * Checks if all no other package depend on us.
     * @param processed set of already uninstalled packages.
     * @param opts install options
     */
    private void uninstallUsages(Set<PackageId> processed, ImportOptions opts)
            throws PackageException, RepositoryException, IOException {
        if (def == null) {
            return;
        }
        PackageId[] usage = mgr.usage(getDefinition().getId());
        if (usage.length > 0 && opts.getDependencyHandling() == DependencyHandling.STRICT) {
            String msg = String.format("Refusing to uninstall package %s. it is still used by: %s", def.getId(), Arrays.toString(usage));
            log.error(msg);
            throw new DependencyException(msg);
        }
        for (PackageId id: usage) {
            JcrPackageImpl pack = (JcrPackageImpl) mgr.open(id);
            if (pack == null || !pack.isInstalled()) {
                continue;
            }
            PackageId packageId = pack.getDefinition().getId();
            if (processed.contains(packageId)) {
                // ignore cyclic...
                continue;
            }
            pack.uninstall(processed, opts);
        }
    }

    /**
     * {@inheritDoc}
     */
    public JcrPackage snapshot(ExportOptions opts, boolean replace)
            throws RepositoryException, PackageException, IOException {
        return snapshot(opts, replace, null);
    }

    /**
     * Internally creates the snapshot
     * @param opts exports options when building the snapshot
     * @param replace if {@code true} existing snapshot will be replaced
     * @param acHandling user acHandling to use when snapshot is installed, i.e. package is uninstalled
     * @return the package of the snapshot or {@code null}
     * @throws RepositoryException if an error occurrs.
     * @throws PackageException if an error occurrs.
     * @throws IOException if an error occurrs.
     */
    @CheckForNull
    private JcrPackage snapshot(@Nonnull ExportOptions opts, boolean replace, @Nullable AccessControlHandling acHandling)
            throws RepositoryException, PackageException, IOException {
        if (node == null) {
            return null;
        }
        PackageId id = getSnapshotId();
        Node packNode = getPackageNode(id);
        if (packNode != null) {
            if (!replace) {
                log.debug("Refusing to recreate snapshot {}, already exists.", id);
                return null;
            } else {
                packNode.remove();
                node.getSession().save();
            }
        }
        log.debug("Creating snapshot for {}.", id);
        JcrPackageManagerImpl packMgr = new JcrPackageManagerImpl(node.getSession());
        String path = id.getInstallationPath();
        String parentPath = Text.getRelativeParent(path, 1);
        Node folder = packMgr.mkdir(parentPath, true);
        JcrPackage snap = mgr.createNew(folder, id, null, true);
        JcrPackageDefinitionImpl snapDef = (JcrPackageDefinitionImpl) snap.getDefinition();
        JcrPackageDefinitionImpl myDef = (JcrPackageDefinitionImpl) getDefinition();
        snapDef.setId(id, false);
        snapDef.setFilter(myDef.getMetaInf().getFilter(), false);
        snapDef.set(JcrPackageDefinition.PN_DESCRIPTION, "Snapshot of package " + myDef.getId().toString(), false);
        if (acHandling == null) {
            snapDef.set(JcrPackageDefinition.PN_AC_HANDLING, myDef.get(JcrPackageDefinition.PN_AC_HANDLING), false);
        } else {
            snapDef.set(JcrPackageDefinition.PN_AC_HANDLING, acHandling.name(), false);
        }
        if (opts.getListener() != null) {
            opts.getListener().onMessage(ProgressTrackerListener.Mode.TEXT, "Creating snapshot for package " + myDef.getId(), "");
        }
        packMgr.assemble(snap.getNode(), snapDef, opts.getListener());
        log.debug("Creating snapshot for {} completed.", id);
        mgr.dispatch(PackageEvent.Type.SNAPSHOT, id, null);
        return snap;
    }

    /**
     * Returns the package node of the given package id.
     * @param id the package id
     * @return the package node
     * @throws RepositoryException if an error occurs
     */
    @CheckForNull
    private Node getPackageNode(PackageId id) throws RepositoryException {
        if (node == null) {
            return null;
        }
        if (node.getSession().nodeExists(id.getInstallationPath())) {
            return node.getSession().getNode(id.getInstallationPath());
        } else if (node.getSession().nodeExists(id.getInstallationPath() + ".zip")) {
            return node.getSession().getNode(id.getInstallationPath() + ".zip");
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    public JcrPackage getSnapshot() throws RepositoryException {
        PackageId id = getSnapshotId();
        Node packNode = getPackageNode(id);
        if (packNode != null) {
            JcrPackageImpl snap = new JcrPackageImpl(mgr, packNode);
            if (snap.isValid()) {
                return snap;
            }
        }
        return null;
    }

    /**
     * Returns the snapshot id of this package.
     * @return the snapshot package id
     * @throws RepositoryException if an error occurs
     */
    private PackageId getSnapshotId() throws RepositoryException {
        PackageId id = getDefinition().getId();
        String group = id.getGroup();
        if (group.length() == 0) {
            group = ".snapshot";
        } else {
            group += "/.snapshot";
        }
        return new PackageId(
                group,
                id.getName(),
                id.getVersion());
    }


    /**
     * {@inheritDoc}
     */
    public void uninstall(ImportOptions opts) throws RepositoryException, PackageException, IOException {
        uninstall(new HashSet<PackageId>(), opts);
    }

    /**
     * {@inheritDoc}
     */
    private void uninstall(Set<PackageId> processed, ImportOptions opts) throws RepositoryException, PackageException, IOException {
        if (def != null) {
            processed.add(def.getId());
        }
        if (opts.getDependencyHandling() != null && opts.getDependencyHandling() != DependencyHandling.IGNORE) {
            uninstallUsages(processed, opts);
        }

        JcrPackage snap = getSnapshot();
        if (snap == null) {
            if (opts.isStrict()) {
                throw new PackageException("Unable to uninstall package. No snapshot present.");
            }
            log.warn("Unable to revert package content {}. Snapshot missing.", getDefinition().getId());
            if (opts.getListener() != null) {
                opts.getListener().onMessage(ProgressTrackerListener.Mode.TEXT, "Unable to revert package content. Snapshot missing.", "");
            }

        } else {
            Session s = getNode().getSession();
            // check for recursive uninstall
            if (!opts.isNonRecursive()) {
                Node defNode = snap.getDefNode();
                LinkedList<PackageId> subPackages = new LinkedList<PackageId>();
                if (defNode.hasProperty(JcrPackageDefinition.PN_SUB_PACKAGES)) {
                    Value[] subIds = defNode.getProperty(JcrPackageDefinition.PN_SUB_PACKAGES).getValues();
                    for (Value v : subIds) {
                        // reverse installation order
                        subPackages.addLast(PackageId.fromString(v.getString()));
                    }
                }
                if (subPackages.size() > 0) {
                    JcrPackageManagerImpl packMgr = new JcrPackageManagerImpl(s);
                    for (PackageId id : subPackages) {
                        JcrPackage pack = packMgr.open(id);
                        if (pack != null) {
                            if (pack.getSnapshot() == null) {
                                log.warn("Unable to uninstall sub package {}. Snapshot missing.", id);
                            } else {
                                pack.uninstall(opts);
                            }
                        }
                    }

                }
            }

            if (opts.getListener() != null) {
                opts.getListener().onMessage(ProgressTrackerListener.Mode.TEXT, "Uninstalling package from snapshot " + snap.getDefinition().getId(), "");
            }
            // override import mode
            opts.setImportMode(ImportMode.REPLACE);
            snap.extract(opts);
            snap.getNode().remove();
            s.save();
        }

        // revert installed flags on this package
        JcrPackageDefinitionImpl def = (JcrPackageDefinitionImpl) getDefinition();
        def.clearLastUnpacked(true);

        mgr.dispatch(PackageEvent.Type.UNINSTALL, def.getId(), null);

    }

    /**
     * {@inheritDoc}
     */
    public long getSize() {
        try {
            assertValid();
            return getData().getLength();
        } catch (RepositoryException e) {
            log.error("Error during getSize()", e);
            return -1;
        }
    }

    /**
     * {@inheritDoc}
     */
    public void close() {
        node = null;
        if (pack != null) {
            pack.close();
            pack = null;
        }
    }

    /**
     * Returns the jcr:content node
     * @return the jcr:content node
     * @throws RepositoryException if an error occurrs
     */
    @CheckForNull
    private Node getContent() throws RepositoryException {
        return node == null ? null : node.getNode(JcrConstants.JCR_CONTENT);
    }

    /**
     * {@inheritDoc}
     */
    @CheckForNull
    public Property getData() throws RepositoryException {
        Node content = getContent();
        return content == null ? null : content.getProperty(JcrConstants.JCR_DATA);
    }

    /**
     * {@inheritDoc}
     */
    @CheckForNull
    public Node getDefNode() throws RepositoryException {
        Node content = getContent();
        return content != null && content.hasNode(NN_VLT_DEFINITION)
                ? content.getNode(NN_VLT_DEFINITION)
                : null;
    }

    /**
     * Ensures that the package is valid.
     * @throws RepositoryException if an error occurs
     */
    private void assertValid() throws RepositoryException {
        if (!isValid()) {
            throw new IllegalArgumentException("not a valid package.");
        }
    }
}
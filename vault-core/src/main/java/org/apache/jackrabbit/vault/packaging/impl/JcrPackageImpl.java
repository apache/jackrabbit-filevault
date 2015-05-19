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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;

import javax.jcr.Binary;
import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.jackrabbit.vault.fs.api.ImportMode;
import org.apache.jackrabbit.vault.fs.api.ProgressTrackerListener;
import org.apache.jackrabbit.vault.fs.io.AccessControlHandling;
import org.apache.jackrabbit.vault.fs.io.ImportOptions;
import org.apache.jackrabbit.vault.fs.io.MemoryArchive;
import org.apache.jackrabbit.vault.fs.io.ZipArchive;
import org.apache.jackrabbit.vault.packaging.CyclicDependencyException;
import org.apache.jackrabbit.vault.packaging.DependencyUtil;
import org.apache.jackrabbit.vault.packaging.ExportOptions;
import org.apache.jackrabbit.vault.packaging.JcrPackage;
import org.apache.jackrabbit.vault.packaging.JcrPackageDefinition;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.SubPackageHandling;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.apache.jackrabbit.vault.util.JcrConstants;
import org.apache.jackrabbit.vault.util.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements a JcrPackage
 */
public class JcrPackageImpl implements JcrPackage {

    /**
     * default logger
     */
    private static final Logger log = LoggerFactory.getLogger(JcrPackageImpl.class);

    /**
     * underlying node
     */
    private Node node;

    /**
     * underlying package
     */
    private ZipVaultPackage pack;

    /**
     * underlying definition
     */
    private JcrPackageDefinitionImpl def;

    public JcrPackageImpl(Node node) throws RepositoryException {
        this.node = node;
    }

    protected JcrPackageImpl(Node node, ZipVaultPackage pack) throws RepositoryException {
        this.node = node;
        this.pack = pack;
    }

    protected JcrPackageImpl(Node node, ZipVaultPackage pack, JcrPackageDefinitionImpl def)
            throws RepositoryException {
        this.node = node;
        this.pack = pack;
        this.def = def;
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
            if (getDefinition() == null) {
                return true;
            }
            return !def.isModified();
        } catch (RepositoryException e) {
            log.warn("Error during isSealed()", e);
            return false;
        }

    }

    /**
     * Creates a new jcr vault package.
     *
     * @param parent the parent node
     * @param pid the package id of the new package.
     * @param pack the underlying zip package or null.
     * @param autoSave if <code>true</code> the changes are persisted immediately
     * @return the created jcr vault package.
     * @throws RepositoryException if an repository error occurs
     * @throws IOException if an I/O error occurs
     *
     * @since 2.3.0
     */
    public static JcrPackage createNew(Node parent, PackageId pid, VaultPackage pack, boolean autoSave)
            throws RepositoryException, IOException {
        Node node = parent.addNode(Text.getName(pid.getInstallationPath() + ".zip"), JcrConstants.NT_FILE);
        Node content = node.addNode(JcrConstants.JCR_CONTENT, JcrConstants.NT_RESOURCE);
        content.addMixin(NT_VLT_PACKAGE);
        Node defNode = content.addNode(NN_VLT_DEFINITION);
        JcrPackageDefinition def = new JcrPackageDefinitionImpl(defNode);
        def.set(JcrPackageDefinition.PN_NAME, pid.getName(), false);
        def.set(JcrPackageDefinition.PN_GROUP, pid.getGroup(), false);
        def.set(JcrPackageDefinition.PN_VERSION, pid.getVersionString(), false);
        def.touch(null, false);
        content.setProperty(JcrConstants.JCR_LASTMODIFIED, Calendar.getInstance());
        content.setProperty(JcrConstants.JCR_MIMETYPE, MIME_TYPE);
        InputStream in = new ByteArrayInputStream(new byte[0]);
        try {
            if (pack != null && pack.getFile() != null) {
                in = FileUtils.openInputStream(pack.getFile());
            }
            // stay jcr 1.0 compatible
            //noinspection deprecation
            content.setProperty(JcrConstants.JCR_DATA, in);
            if (pack != null) {
                def.unwrap(pack, true, false);
            }
            if (autoSave) {
                parent.save();
            }
        } finally {
            IOUtils.closeQuietly(in);
        }
        return new JcrPackageImpl(node, (ZipVaultPackage) pack);
    }
    /**
     * Creates a new jcr vault package.
     *
     * @param parent the parent node
     * @param pid the package id of the new package.
     * @param bin the binary containing the zip
     * @param archive the archive with the meta data
     * @return the created jcr vault package.
     * @throws RepositoryException if an repository error occurs
     * @throws IOException if an I/O error occurs
     *
     * @since 3.1
     */
    public static JcrPackage createNew(Node parent, PackageId pid, Binary bin, MemoryArchive archive)
            throws RepositoryException, IOException {
        Node node = parent.addNode(Text.getName(pid.getInstallationPath() + ".zip"), JcrConstants.NT_FILE);
        Node content = node.addNode(JcrConstants.JCR_CONTENT, JcrConstants.NT_RESOURCE);
        content.addMixin(NT_VLT_PACKAGE);
        Node defNode = content.addNode(NN_VLT_DEFINITION);
        JcrPackageDefinitionImpl def = new JcrPackageDefinitionImpl(defNode);
        def.set(JcrPackageDefinition.PN_NAME, pid.getName(), false);
        def.set(JcrPackageDefinition.PN_GROUP, pid.getGroup(), false);
        def.set(JcrPackageDefinition.PN_VERSION, pid.getVersionString(), false);
        def.touch(null, false);
        content.setProperty(JcrConstants.JCR_LASTMODIFIED, Calendar.getInstance());
        content.setProperty(JcrConstants.JCR_MIMETYPE, MIME_TYPE);
        content.setProperty(JcrConstants.JCR_DATA, bin);
        def.unwrap(archive, false);
        return new JcrPackageImpl(node);
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
        VaultPackage pack = getPackage();
        Node content = getContent();
        boolean ok = false;
        try {
            content.addMixin(NT_VLT_PACKAGE);
            Node defNode = content.addNode(NN_VLT_DEFINITION);
            JcrPackageDefinition def = new JcrPackageDefinitionImpl(defNode);
            def.unwrap(pack, true, false);
            node.save();
            ok = true;
        } finally {
            if (!ok) {
                try {
                    node.refresh(false);
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
     * @throws RepositoryException
     * @throws IOException
     */
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
            if (!forceFileArchive && size >= 0 && size < 1024*1024) {
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

    /**
     * internally extracts the package.
     *
     * @param options the import options
     * @param createSnapshot <code>true</code> if a snapshot should be created
     * @param replaceSnapshot <code>true</code> if a snapshot should be replaced
     * @throws RepositoryException if a repository error occurs
     * @throws PackageException if a package error occurs
     * @throws IOException if an I/O error occurs
     */
    private void extract(ImportOptions options, boolean createSnapshot, boolean replaceSnapshot)
            throws RepositoryException, PackageException, IOException {
        getPackage();
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
                JcrPackageImpl p = new JcrPackageImpl(s.getNode(path));
                if (!p.isValid()) {
                    // check if package was included as pure .zip or .jar
                    try {
                        p.tryUnwrap();
                    } catch (Exception e) {
                        log.info("Sub package {} not valid: " + e, path);
                    }
                }
                if (p.isValid()) {
                    subPacks.add(p);
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
                PackageId id = p.getPackage().getId();
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
                    log.info(msg);
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
                snap.getDefinition().getNode().save();
            }
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
     * @param replace if <code>true</code> existing snapshot will be replaced
     * @param acHandling user acHandling to use when snapshot is installed, i.e. package is uninstalled
     * @return the package of the snapshot or <code>null</code>
     * @throws RepositoryException if an error occurrs.
     * @throws PackageException if an error occurrs.
     * @throws IOException if an error occurrs.
     */
    private JcrPackage snapshot(ExportOptions opts, boolean replace, AccessControlHandling acHandling)
            throws RepositoryException, PackageException, IOException {
        PackageId id = getSnapshotId();
        Node packNode = getPackageNode(id);
        if (packNode != null) {
            if (!replace) {
                log.warn("Refusing to recreate snapshot {}, already exists.", id);
                return null;
            } else {
                packNode.remove();
                node.getSession().save();
            }
        }
        log.info("Creating snapshot for {}.", id);
        JcrPackageManagerImpl packMgr = new JcrPackageManagerImpl(node.getSession());
        String path = id.getInstallationPath();
        String parentPath = Text.getRelativeParent(path, 1);
        Node folder = packMgr.mkdir(parentPath, true);
        JcrPackage snap = JcrPackageImpl.createNew(folder, id, null, true);
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
        log.info("Creating snapshot for {} completed.", id);
        return snap;
    }

    /**
     * Returns the package node of the given package id.
     * @param id the package id
     * @return the package node
     * @throws RepositoryException if an error occurs
     */
    private Node getPackageNode(PackageId id) throws RepositoryException {
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
            JcrPackageImpl snap = new JcrPackageImpl(packNode);
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
        JcrPackage snap = getSnapshot();
        if (snap == null) {
            throw new PackageException("Unable to uninstall package. No snapshot present.");
        }
        if (opts.getListener() != null) {
            opts.getListener().onMessage(ProgressTrackerListener.Mode.TEXT, "Uninstalling package from snapshot " + snap.getDefinition().getId(), "");
        }
        Session s = getNode().getSession();
        // check for recursive uninstall
        if (!opts.isNonRecursive()) {
            Node defNode = snap.getDefNode();
            LinkedList<PackageId> subPackages = new LinkedList<PackageId>();
            if (defNode.hasProperty(JcrPackageDefinition.PN_SUB_PACKAGES)) {
                Value[] subIds = defNode.getProperty(JcrPackageDefinition.PN_SUB_PACKAGES).getValues();
                for (Value v: subIds) {
                    // reverse installation order
                    subPackages.addLast(PackageId.fromString(v.getString()));
                }
            }
            if (subPackages.size() > 0) {
                JcrPackageManagerImpl packMgr = new JcrPackageManagerImpl(s);
                for (PackageId id: subPackages) {
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
        // override import mode
        opts.setImportMode(ImportMode.REPLACE);
        snap.extract(opts);
        snap.getNode().remove();
        s.save();
        // revert installed flags on this package
        JcrPackageDefinitionImpl def = (JcrPackageDefinitionImpl) getDefinition();
        def.clearLastUnpacked(true);
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
    private Node getContent() throws RepositoryException {
        return node.getNode(JcrConstants.JCR_CONTENT);
    }

    /**
     * {@inheritDoc}
     */
    public Property getData() throws RepositoryException {
        return getContent().getProperty(JcrConstants.JCR_DATA);
    }

    /**
     * {@inheritDoc}
     */
    public Node getDefNode() throws RepositoryException {
        Node content = getContent();
        return content.hasNode(NN_VLT_DEFINITION)
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
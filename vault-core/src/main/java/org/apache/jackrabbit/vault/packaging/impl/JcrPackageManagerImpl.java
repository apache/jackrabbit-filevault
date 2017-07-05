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
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.jcr.Binary;
import javax.jcr.ItemExistsException;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.jackrabbit.vault.fs.api.ProgressTrackerListener;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.fs.config.MetaInf;
import org.apache.jackrabbit.vault.fs.impl.ArchiveWrapper;
import org.apache.jackrabbit.vault.fs.impl.SubPackageFilterArchive;
import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.fs.io.ImportOptions;
import org.apache.jackrabbit.vault.fs.io.MemoryArchive;
import org.apache.jackrabbit.vault.fs.io.ZipArchive;
import org.apache.jackrabbit.vault.fs.io.ZipStreamArchive;
import org.apache.jackrabbit.vault.fs.spi.CNDReader;
import org.apache.jackrabbit.vault.fs.spi.NodeTypeInstaller;
import org.apache.jackrabbit.vault.fs.spi.ServiceProviderFactory;
import org.apache.jackrabbit.vault.packaging.Dependency;
import org.apache.jackrabbit.vault.packaging.ExportOptions;
import org.apache.jackrabbit.vault.packaging.JcrPackage;
import org.apache.jackrabbit.vault.packaging.JcrPackageDefinition;
import org.apache.jackrabbit.vault.packaging.JcrPackageManager;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.apache.jackrabbit.vault.packaging.Version;
import org.apache.jackrabbit.vault.packaging.events.PackageEvent;
import org.apache.jackrabbit.vault.util.InputStreamPump;
import org.apache.jackrabbit.vault.util.JcrConstants;
import org.apache.jackrabbit.vault.util.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extends the {@code PackageManager} by JCR specific methods
 */
public class JcrPackageManagerImpl extends PackageManagerImpl implements JcrPackageManager {

    /**
     * default logger
     */
    private static final Logger log = LoggerFactory.getLogger(JcrPackageManagerImpl.class);

    /**
     * name of node types resource
     */
    private static final String DEFAULT_NODETYPES = "nodetypes.cnd";

    /**
     * suggested folder types
     */
    private final static String[] FOLDER_TYPES = {"sling:Folder", "nt:folder", "nt:unstructured", null};

    /**
     * root path for packages
     */
    final static String PACKAGE_ROOT_PATH = "/etc/packages";

    /**
     * root path prefix for packages
     */
    public final static String PACKAGE_ROOT_PATH_PREFIX = "/etc/packages/";

    /**
     * root path for packages
     */
    public final static String ARCHIVE_PACKAGE_ROOT_PATH = "/jcr_root/etc/packages";

    /**
     * internal session
     */
    private final Session session;

    /**
     * package root (/etc/packages)
     */
    private Node packRoot;

    /**
     * Creates a new package manager using the given session.
     *
     * @param session repository session
     */
    public JcrPackageManagerImpl(Session session) {
        this.session = session;
        initNodeTypes();
    }

    @Override
    public JcrPackage open(PackageId id) throws RepositoryException {
        String path = getInstallationPath(id);
        String[] exts = new String[]{"", ".zip", ".jar"};
        for (String ext: exts) {
            if (session.nodeExists(path + ext)) {
                return open(session.getNode(path + ext));
            }
        }
        return null;
    }

    @Override
    public JcrPackage open(Node node) throws RepositoryException {
        return open(node, false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JcrPackage open(Node node, boolean allowInvalid) throws RepositoryException {
        JcrPackage pack = new JcrPackageImpl(this, node);
        if (pack.isValid()) {
            return pack;
        } else if (allowInvalid
                && node.isNodeType(JcrConstants.NT_HIERARCHYNODE)
                && node.hasProperty(JcrConstants.JCR_CONTENT + "/" + JcrConstants.JCR_DATA)) {
            return pack;
        } else {
            return null;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PackageId resolve(Dependency dependency, boolean onlyInstalled) throws RepositoryException {
        if (!getPackageRoot().hasNode(dependency.getGroup())) {
            return null;
        }
        Node groupNode = getPackageRoot().getNode(dependency.getGroup());
        NodeIterator iter = groupNode.getNodes();
        PackageId bestId = null;
        while (iter.hasNext()) {
            Node child = iter.nextNode();
            if (".snapshot".equals(child.getName())) {
                continue;
            }
            JcrPackageImpl pack = new JcrPackageImpl(this, child);
            if (pack.isValid()) {
                if (onlyInstalled && !pack.isInstalled()) {
                    continue;
                }
                PackageId id = pack.getDefinition().getId();
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
     * {@inheritDoc}
     */
    @Override
    public PackageId[] usage(PackageId id) throws RepositoryException {
        TreeSet<PackageId> usages = new TreeSet<PackageId>();
        for (JcrPackage p: listPackages()) {
            if (!p.isInstalled()) {
                continue;
            }
            for (Dependency dep: p.getDefinition().getDependencies()) {
                if (dep.matches(id)) {
                    usages.add(p.getDefinition().getId());
                    break;
                }
            }
        }
        return usages.toArray(new PackageId[usages.size()]);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JcrPackage upload(InputStream in, boolean replace) throws RepositoryException, IOException {
        return upload(in, replace, false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JcrPackage upload(InputStream in, boolean replace, boolean strict)
            throws RepositoryException, IOException {

        MemoryArchive archive = new MemoryArchive(true);
        InputStreamPump pump = new InputStreamPump(in , archive);

        // this will cause the input stream to be consumed and the memory archive being initialized.
        Binary bin = session.getValueFactory().createBinary(pump);
        if (pump.getError() != null) {
            Exception error = pump.getError();
            log.error("Error while reading from input stream.", error);
            bin.dispose();
            throw new IOException("Error while reading from input stream", error);
        }

        if (archive.getJcrRoot() == null) {
            String msg = "Stream is not a content package. Missing 'jcr_root'.";
            log.error(msg);
            bin.dispose();
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
            bin.dispose();
            throw new IOException("Package does not contain a path specification or valid package id.");
        }
        if (!pid.isValid()) {
            throw new RepositoryException("Unable to create package. Illegal package name.");
        }

        // create parent node
        String path = getInstallationPath(pid) + ".zip";
        String parentPath = Text.getRelativeParent(path, 1);
        String name = Text.getName(path);
        Node parent = mkdir(parentPath, false);

        // remember installation state properties (GRANITE-2018)
        JcrPackageDefinitionImpl.State state = null;
        Calendar oldCreatedDate = null;

        if (parent.hasNode(name)) {
            JcrPackage oldPackage = new JcrPackageImpl(this, parent.getNode(name));
            JcrPackageDefinitionImpl oldDef = (JcrPackageDefinitionImpl) oldPackage.getDefinition();
            if (oldDef != null) {
                state = oldDef.getState();
                oldCreatedDate = oldDef.getCreated();
            }

            if (replace) {
                parent.getNode(name).remove();
            } else {
                throw new ItemExistsException("Package already exists: " + path);
            }
        }
        JcrPackage jcrPack = null;
        try {
            jcrPack = createNew(parent, pid, bin, archive);
            JcrPackageDefinitionImpl def = (JcrPackageDefinitionImpl) jcrPack.getDefinition();
            // only transfer the old package state to the new state in case both packages have the same create date
            if (state != null && oldCreatedDate != null && oldCreatedDate.compareTo(def.getCreated()) == 0) {
                def.setState(state);
            }
            dispatch(PackageEvent.Type.UPLOAD, pid, null);
            return jcrPack;
        } finally {
            bin.dispose();
            if (jcrPack == null) {
                session.refresh(false);
            } else {
                session.save();
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Nonnull
    @Override
    public PackageId[] extract(@Nonnull Archive archive, @Nonnull ImportOptions options, boolean replace)
            throws RepositoryException, PackageException, IOException {

        SubPackageFilterArchive spfArchive = null;
        if (!options.isNonRecursive()) {
            spfArchive = new SubPackageFilterArchive(archive);
            archive = spfArchive;
        } else {
            archive = new ArchiveWrapper(archive);
        }
        ZipVaultPackage pkg = new ZipVaultPackage(archive, true);

        PackageId pid = pkg.getId();
        JcrPackage jcrPack = upload(pkg, replace, null);
        jcrPack = new JcrPackageImpl(this, jcrPack.getNode(), pkg);
        jcrPack.extract(options);

        Set<PackageId> ids = new HashSet<>();
        ids.add(pid);

        if (spfArchive != null) {
            for (Archive.Entry e: spfArchive.getSubPackageEntries()) {
                InputStream in = spfArchive.openInputStream(e);
                Archive subArchive = new ZipStreamArchive(in);
                PackageId[] subIds = extract(subArchive, options, replace);
                ids.addAll(Arrays.asList(subIds));
                subArchive.close();
            }
        }

        pkg.close();
        jcrPack.close();
        return ids.toArray(new PackageId[ids.size()]);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JcrPackage upload(File file, boolean isTmpFile, boolean replace, String nameHint)
            throws RepositoryException, IOException {
        return upload(file, isTmpFile, replace, nameHint, false);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public JcrPackage upload(File file, boolean isTmpFile, boolean replace, String nameHint, boolean strict)
            throws RepositoryException, IOException {
        ZipVaultPackage pack = new ZipVaultPackage(file, isTmpFile, strict);
        return upload(pack, replace, nameHint);
    }

    private JcrPackage upload(ZipVaultPackage pkg, boolean replace, String nameHint)
            throws RepositoryException, IOException {

        // open zip packages
        if (pkg.getArchive().getJcrRoot() == null) {
            String msg = "Zip File is not a content package. Missing 'jcr_root'.";
            log.error(msg);
            pkg.close();
            throw new IOException(msg);
        }

        // invalidate pid if path is unknown
        PackageId pid = pkg.getId();
        if (pid == null) {
            if (nameHint == null || nameHint.length() == 0) {
                throw new IOException("Package does not contain a path specification and not name hint is given.");
            }
            pid = new PackageId(nameHint);
        }
        if (!pid.isValid()) {
            throw new RepositoryException("Unable to create package. Illegal package name.");
        }

        // create parent node
        String path = getInstallationPath(pid) + ".zip";
        String parentPath = Text.getRelativeParent(path, 1);
        String name = Text.getName(path);
        Node parent = mkdir(parentPath, false);

        // remember installation state properties (GRANITE-2018)
        JcrPackageDefinitionImpl.State state = null;

        if (parent.hasNode(name)) {
            JcrPackage oldPackage = new JcrPackageImpl(this, parent.getNode(name));
            JcrPackageDefinitionImpl oldDef = (JcrPackageDefinitionImpl) oldPackage.getDefinition();
            if (oldDef != null) {
                state = oldDef.getState();
            }

            if (replace) {
                parent.getNode(name).remove();
            } else {
                throw new ItemExistsException("Package already exists: " + path);
            }
        }
        JcrPackage jcrPack = null;
        try {
            jcrPack = createNew(parent, pid, pkg, false);
            JcrPackageDefinitionImpl def = (JcrPackageDefinitionImpl) jcrPack.getDefinition();
            if (state != null) {
                def.setState(state);
            }
            dispatch(PackageEvent.Type.UPLOAD, pid, null);
            return jcrPack;
        } finally {
            if (jcrPack == null) {
                session.refresh(false);
            } else {
                session.save();
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JcrPackage create(Node folder, String name)
            throws RepositoryException, IOException {
        if (folder == null) {
            folder = getPackageRoot();
        }
        return createNew(folder, new PackageId(name), null, true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JcrPackage create(String group, String name)
            throws RepositoryException, IOException {
        return create(group, name, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JcrPackage create(String group, String name, String version)
            throws RepositoryException, IOException {
        // sanitize name
        String ext = Text.getName(name, '.');
        if ("zip".equals(ext) || "jar".equals(ext)) {
            name = name.substring(0, name.length() - 4);
        }
        if (!PackageId.isValid(group, name, version)) {
            throw new RepositoryException("Unable to create package. Illegal package name.");
        }
        PackageId pid = new PackageId(group, name, version);
        Node folder = mkdir(Text.getRelativeParent(getInstallationPath(pid), 1), false);
        return createNew(folder, pid, null, true);
    }

    /**
     * Creates a new jcr vault package.
     *
     * @param parent the parent node
     * @param pid the package id of the new package.
     * @param pack the underlying zip package or null.
     * @param autoSave if {@code true} the changes are persisted immediately
     * @return the created jcr vault package.
     * @throws RepositoryException if an repository error occurs
     * @throws IOException if an I/O error occurs
     *
     * @since 2.3.0
     */
    @Nonnull
    public JcrPackage createNew(@Nonnull Node parent, @Nonnull PackageId pid, @Nullable VaultPackage pack, boolean autoSave)
            throws RepositoryException, IOException {
        Node node = parent.addNode(Text.getName(getInstallationPath(pid) + ".zip"), JcrConstants.NT_FILE);
        Node content = node.addNode(JcrConstants.JCR_CONTENT, JcrConstants.NT_RESOURCE);
        content.addMixin(JcrPackage.NT_VLT_PACKAGE);
        Node defNode = content.addNode(JcrPackage.NN_VLT_DEFINITION);
        JcrPackageDefinition def = new JcrPackageDefinitionImpl(defNode);
        def.set(JcrPackageDefinition.PN_NAME, pid.getName(), false);
        def.set(JcrPackageDefinition.PN_GROUP, pid.getGroup(), false);
        def.set(JcrPackageDefinition.PN_VERSION, pid.getVersionString(), false);
        def.touch(null, false);
        content.setProperty(JcrConstants.JCR_LASTMODIFIED, Calendar.getInstance());
        content.setProperty(JcrConstants.JCR_MIMETYPE, JcrPackage.MIME_TYPE);
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
                parent.getSession().save();
            }
        } finally {
            IOUtils.closeQuietly(in);
        }
        dispatch(PackageEvent.Type.CREATE, pid, null);
        return new JcrPackageImpl(this, node, (ZipVaultPackage) pack);
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
    @Nonnull
    public JcrPackage createNew(@Nonnull Node parent, @Nonnull PackageId pid, @Nonnull Binary bin, @Nonnull MemoryArchive archive)
            throws RepositoryException, IOException {
        Node node = parent.addNode(Text.getName(getInstallationPath(pid) + ".zip"), JcrConstants.NT_FILE);
        Node content = node.addNode(JcrConstants.JCR_CONTENT, JcrConstants.NT_RESOURCE);
        content.addMixin(JcrPackage.NT_VLT_PACKAGE);
        Node defNode = content.addNode(JcrPackage.NN_VLT_DEFINITION);
        JcrPackageDefinitionImpl def = new JcrPackageDefinitionImpl(defNode);
        def.set(JcrPackageDefinition.PN_NAME, pid.getName(), false);
        def.set(JcrPackageDefinition.PN_GROUP, pid.getGroup(), false);
        def.set(JcrPackageDefinition.PN_VERSION, pid.getVersionString(), false);
        def.touch(null, false);
        content.setProperty(JcrConstants.JCR_LASTMODIFIED, Calendar.getInstance());
        content.setProperty(JcrConstants.JCR_MIMETYPE, JcrPackage.MIME_TYPE);
        content.setProperty(JcrConstants.JCR_DATA, bin);
        def.unwrap(archive, false);
        dispatch(PackageEvent.Type.CREATE, pid, null);
        return new JcrPackageImpl(this, node);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void remove(JcrPackage pack) throws RepositoryException {
        PackageId pid = pack.getDefinition().getId();
        JcrPackage snap = pack.getSnapshot();
        if (snap != null) {
            snap.getNode().remove();
        }
        pack.getNode().remove();
        session.save();
        dispatch(PackageEvent.Type.REMOVE, pid, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JcrPackage rename(JcrPackage pack, String group, String name)
            throws PackageException, RepositoryException {
        return rename(pack, group, name, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JcrPackage rename(JcrPackage pack, String group, String name, String version)
            throws PackageException, RepositoryException {
        if (!pack.isValid()) {
            throw new PackageException("Package is not valid.");
        }
        if (pack.getSize() > 0 && !pack.getDefinition().isUnwrapped()) {
            throw new PackageException("Package definition not unwrapped.");
        }
        if (!PackageId.isValid(group, name, version)) {
            throw new RepositoryException("Unable to rename package. Illegal package name.");
        }

        JcrPackageDefinition def = pack.getDefinition();
        PackageId id = def.getId();
        PackageId newId = new PackageId(
                group == null ? id.getGroup() : group,
                name == null ? id.getName() : name,
                version == null ? id.getVersion() : Version.create(version)
        );
        String dstPath = getInstallationPath(newId) + ".zip";
        if (id.equals(newId) && pack.getNode().getPath().equals(dstPath)) {
            log.debug("Package id not changed. won't rename.");
            return pack;
        }
        def.setId(newId, false);

        // only move if not already at correct location
        if (!pack.getNode().getPath().equals(dstPath)) {
            if (session.nodeExists(dstPath)) {
                throw new PackageException("Node at " + dstPath + " already exists.");
            }
            // ensure parent path exists
            mkdir(Text.getRelativeParent(dstPath, 1), false);
            session.move(pack.getNode().getPath(), dstPath);
        }

        session.save();
        Node newNode = session.getNode(dstPath);
        dispatch(PackageEvent.Type.RENAME, id, new PackageId[]{newId});
        return open(newNode);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void assemble(JcrPackage pack, ProgressTrackerListener listener)
            throws PackageException, RepositoryException, IOException {
        pack.verifyId(true, true);
        assemble(pack.getNode(), pack.getDefinition(), listener);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void assemble(Node packNode, JcrPackageDefinition definition,
                         ProgressTrackerListener listener)
            throws PackageException, RepositoryException, IOException {
        Calendar now = Calendar.getInstance();
        JcrPackageDefinitionImpl def = (JcrPackageDefinitionImpl) definition;
        validateSubPackages(def);
        def.sealForAssembly(now, true);

        ExportOptions opts = new ExportOptions();
        opts.setMetaInf(def.getMetaInf());
        opts.setListener(listener);
        opts.setPostProcessor(def.getInjectProcessor());

        VaultPackage pack = assemble(packNode.getSession(), opts, (File) null);
        PackageId id = pack.getId();

        // update this content
        Node contentNode = packNode.getNode(JcrConstants.JCR_CONTENT);
        InputStream in;
        try {
            in = FileUtils.openInputStream(pack.getFile());
        } catch (IOException e) {
            throw new PackageException(e);
        }
        // stay jcr 1.0 compatible
        //noinspection deprecation
        contentNode.setProperty(JcrConstants.JCR_DATA, in);
        contentNode.setProperty(JcrConstants.JCR_LASTMODIFIED, now);
        contentNode.setProperty(JcrConstants.JCR_MIMETYPE, JcrPackage.MIME_TYPE);
        packNode.getSession().save();
        pack.close();
        dispatch(PackageEvent.Type.ASSEMBLE, id, null);
    }

    /**
     * {@inheritDoc}
     */
    private void validateSubPackages(JcrPackageDefinitionImpl def)
            throws RepositoryException, PackageException {
        List<JcrPackage> subs = listPackages(def.getMetaInf().getFilter());
        PackageId id = def.getId();
        for (JcrPackage p: subs) {
            // check if not include itself
            if (p.getDefinition().getId().equals(id)) {
                throw new PackageException("A package cannot include itself. Check filter definition.");
            }
            if (!p.isSealed()) {
                throw new PackageException("Only sealed (built) sub packages allowed: " + p.getDefinition().getId());
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void assemble(JcrPackageDefinition definition,
                         ProgressTrackerListener listener, OutputStream out)
            throws IOException, RepositoryException, PackageException {
        JcrPackageDefinitionImpl def = (JcrPackageDefinitionImpl) definition;
        validateSubPackages(def);
        Calendar now = Calendar.getInstance();
        def.sealForAssembly(now, true);

        ExportOptions opts = new ExportOptions();
        opts.setMetaInf(def.getMetaInf());
        opts.setListener(listener);
        opts.setPostProcessor(def.getInjectProcessor());

        assemble(def.getNode().getSession(), opts, out);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void rewrap(JcrPackage pack, ProgressTrackerListener listener)
            throws PackageException, RepositoryException, IOException {
        VaultPackage src = ((JcrPackageImpl) pack).getPackage(true);

        Calendar now = Calendar.getInstance();
        pack.verifyId(true, false);
        JcrPackageDefinitionImpl def = (JcrPackageDefinitionImpl) pack.getDefinition();
        def.sealForRewrap(now, true);

        ExportOptions opts = new ExportOptions();
        opts.setMetaInf(def.getMetaInf());
        opts.setListener(listener);
        opts.setPostProcessor(def.getInjectProcessor());

        VaultPackage dst = rewrap(opts, src, (File) null);

        // update this content
        Node packNode = pack.getNode();
        Node contentNode = packNode.getNode(JcrConstants.JCR_CONTENT);
        InputStream in;
        try {
            in = FileUtils.openInputStream(dst.getFile());
        } catch (IOException e) {
            throw new PackageException(e);
        }
        // stay jcr 1.0 compatible
        //noinspection deprecation
        contentNode.setProperty(JcrConstants.JCR_DATA, in);
        contentNode.setProperty(JcrConstants.JCR_LASTMODIFIED, now);
        contentNode.setProperty(JcrConstants.JCR_MIMETYPE, JcrPackage.MIME_TYPE);
        packNode.getSession().save();
        dst.close();
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public Node getPackageRoot() throws RepositoryException {
        return getPackageRoot(false);
    }

    /**
     * yet another Convenience method to create intermediate nodes.
     * @param path path to create
     * @param autoSave if {@code true} all changes are automatically persisted
     * @return the node
     * @throws RepositoryException if an error occurrs
     */
    protected Node mkdir(String path, boolean autoSave) throws RepositoryException {
        if (session.nodeExists(path)) {
            return session.getNode(path);
        }
        String parentPath = Text.getRelativeParent(path, 1);
        if (path == null || ("/".equals(path) && parentPath.equals(path))) {
            throw new RepositoryException("could not crete intermediate nodes");
        }
        Node parent = mkdir(parentPath, autoSave);
        Node node = null;
        RepositoryException lastError = null;
        for (int i=0; node == null && i<FOLDER_TYPES.length; i++) {
            try {
                node = parent.addNode(Text.getName(path), FOLDER_TYPES[i]);
            } catch (RepositoryException e) {
                lastError = e;
            }
        }
        if (node == null) {
            throw lastError;
        }
        if (autoSave) {
            parent.getSession().save();
        }
        return node;
    }

    /**
     * Initializes vlt node types (might not be the correct location)
     */
    private void initNodeTypes() {
        // check if node types are registered
        try {
            session.getWorkspace().getNodeTypeManager().getNodeType(JcrPackage.NT_VLT_PACKAGE);
            // also check/register nodetypes needed for assembly
            session.getWorkspace().getNodeTypeManager().getNodeType("vlt:HierarchyNode");
            session.getWorkspace().getNodeTypeManager().getNodeType("vlt:FullCoverage");
            return;
        } catch (RepositoryException e) {
            // ignore
        }
        InputStream in = null;
        try {
            in = getClass().getResourceAsStream(DEFAULT_NODETYPES);
            if (in == null) {
                throw new InternalError("Could not load " + DEFAULT_NODETYPES + " resource.");
            }
            NodeTypeInstaller installer = ServiceProviderFactory.getProvider().getDefaultNodeTypeInstaller(session);
            CNDReader types = ServiceProviderFactory.getProvider().getCNDReader();
            types.read(new InputStreamReader(in, "utf8"), DEFAULT_NODETYPES, null);
            installer.install(null, types);
        } catch (Throwable e) {
            log.warn("Error while registering nodetypes. Package installation might not work correctly.", e);
        } finally {
            IOUtils.closeQuietly(in);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Node getPackageRoot(boolean noCreate) throws RepositoryException {
        if (packRoot == null) {
            if (session.nodeExists(PACKAGE_ROOT_PATH)) {
                packRoot = session.getNode(PACKAGE_ROOT_PATH);
            } else if (noCreate) {
                return null;
            } else {
                // assert that the session has no pending changes
                if (session.hasPendingChanges()) {
                    throw new RepositoryException("Unwilling to create package root folder while session has transient changes.");
                }
                // try to create the missing intermediate nodes
                String etcPath = Text.getRelativeParent(PACKAGE_ROOT_PATH, 1);
                Node etc;
                if (session.nodeExists(etcPath)) {
                    etc = session.getNode(etcPath);
                } else {
                    etc = session.getRootNode().addNode(Text.getName(etcPath), JcrConstants.NT_FOLDER);
                }
                Node pack = etc.addNode(Text.getName(PACKAGE_ROOT_PATH), JcrConstants.NT_FOLDER);
                try {
                    session.save();
                } finally {
                    session.refresh(false);
                }
                packRoot = pack;
            }
        }
        return packRoot;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<JcrPackage> listPackages() throws RepositoryException {
        return listPackages(null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<JcrPackage> listPackages(WorkspaceFilter filter) throws RepositoryException {
        Node root = getPackageRoot(true);
        if (root == null) {
            return Collections.emptyList();
        } else {
            List<JcrPackage> packages = new LinkedList<JcrPackage>();
            listPackages(root, packages, filter, false, false);
            Collections.sort(packages);
            return packages;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<JcrPackage> listPackages(String group, boolean built) throws RepositoryException {
        Node pRoot = getPackageRoot(true);
        if (pRoot == null) {
            return Collections.emptyList();
        }
        List<JcrPackage> packages = new LinkedList<JcrPackage>();
        if (group == null) {
            listPackages(pRoot, packages, null, built, false);
        } else {
            Node root = pRoot;
            if (group.length() > 0) {
                if (group.equals(pRoot.getPath())) {
                    group = "";
                } else if (group.startsWith(pRoot.getPath() + "/")) {
                    group = group.substring(pRoot.getPath().length() + 1);
                }
                if (group.startsWith("/")) {
                    // don't scan outside /etc/packages
                    return packages;
                } else if (group.length() > 0) {
                    if (root.hasNode(group)) {
                        root = root.getNode(group);
                    } else {
                        return packages;
                    }
                }
            }
            listPackages(root, packages, null, built, true);
        }
        Collections.sort(packages);
        return packages;
    }

    /**
     * internally adds the packages below {@code root} to the given list
     * recursively.
     *
     * @param root root node
     * @param packages list for the packages
     * @param filter optional filter to filter out packages
     * @param built if {@code true} only packages with size > 0 are returned
     * @param shallow if {@code true} don't recurs
     * @throws RepositoryException if an error occurs
     */
    private void listPackages(Node root, List<JcrPackage> packages,
                              WorkspaceFilter filter, boolean built, boolean shallow)
            throws RepositoryException {
        if (root != null) {
            for (NodeIterator iter = root.getNodes(); iter.hasNext();) {
                Node child = iter.nextNode();
                if (".snapshot".equals(child.getName())) {
                    continue;
                }
                JcrPackageImpl pack = new JcrPackageImpl(this, child);
                if (pack.isValid()) {
                    // skip packages with illegal names
                    JcrPackageDefinition jDef = pack.getDefinition();
                    if (jDef != null && !jDef.getId().isValid()) {
                        continue;
                    }
                    if (filter == null || filter.contains(child.getPath())) {
                        if (!built || pack.getSize() > 0) {
                            packages.add(pack);
                        }
                    }
                } else if (child.hasNodes() && !shallow){
                    listPackages(child, packages, filter, built, false);
                }
            }
        }
    }

    /**
     * Returns the path of this package. please note that since 2.3 this also
     * includes the version, but never the extension (.zip).
     *
     * Note that the exact storage location is implementation detail and this method should only be used internally for
     * backward compatibility use cases.
     *
     * @param pid the package id
     * @return the path of this package
     */
    public static String getInstallationPath(PackageId pid) {
        StringBuilder b = new StringBuilder(PACKAGE_ROOT_PATH_PREFIX);
        String group = pid.getGroup();
        if (group.length() > 0) {
            b.append(group);
            b.append("/");
        }
        b.append(pid.getName());
        String version = pid.getVersion().toString();
        if (version.length() > 0) {
            b.append("-").append(version);
        }
        return b.toString();
    }

}
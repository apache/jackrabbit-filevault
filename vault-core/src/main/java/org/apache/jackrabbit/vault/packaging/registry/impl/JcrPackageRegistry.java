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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Calendar;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.jcr.Binary;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.jackrabbit.vault.fs.config.MetaInf;
import org.apache.jackrabbit.vault.fs.io.MemoryArchive;
import org.apache.jackrabbit.vault.fs.spi.CNDReader;
import org.apache.jackrabbit.vault.fs.spi.NodeTypeInstaller;
import org.apache.jackrabbit.vault.fs.spi.ServiceProviderFactory;
import org.apache.jackrabbit.vault.packaging.Dependency;
import org.apache.jackrabbit.vault.packaging.JcrPackage;
import org.apache.jackrabbit.vault.packaging.JcrPackageDefinition;
import org.apache.jackrabbit.vault.packaging.NoSuchPackageException;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.apache.jackrabbit.vault.packaging.PackageExistsException;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.apache.jackrabbit.vault.packaging.Version;
import org.apache.jackrabbit.vault.packaging.events.PackageEvent;
import org.apache.jackrabbit.vault.packaging.events.impl.PackageEventDispatcher;
import org.apache.jackrabbit.vault.packaging.impl.JcrPackageDefinitionImpl;
import org.apache.jackrabbit.vault.packaging.impl.JcrPackageImpl;
import org.apache.jackrabbit.vault.packaging.impl.JcrPackageManagerImpl;
import org.apache.jackrabbit.vault.packaging.impl.PackagePropertiesImpl;
import org.apache.jackrabbit.vault.packaging.impl.ZipVaultPackage;
import org.apache.jackrabbit.vault.packaging.registry.DependencyReport;
import org.apache.jackrabbit.vault.packaging.registry.ExecutionPlanBuilder;
import org.apache.jackrabbit.vault.packaging.registry.PackageRegistry;
import org.apache.jackrabbit.vault.packaging.registry.RegisteredPackage;
import org.apache.jackrabbit.vault.util.InputStreamPump;
import org.apache.jackrabbit.vault.util.JcrConstants;
import org.apache.jackrabbit.vault.util.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@code JcrPackagePersistence}...
 */
public class JcrPackageRegistry implements PackageRegistry {

    /**
     * default logger
     */
    private static final Logger log = LoggerFactory.getLogger(JcrPackageRegistry.class);

    /**
     * name of node types resource
     */
    private static final String DEFAULT_NODETYPES = "nodetypes.cnd";

    /**
     * root path for packages
     */
    public static final String PACKAGE_ROOT_PATH = "/etc/packages";

    public static final String PACKAGE_ROOT_PATH_PREFIX = "/etc/packages/";

    /**
     * suggested folder types
     */
    private final static String[] FOLDER_TYPES = {"sling:Folder", "nt:folder", "nt:unstructured", null};

    /**
     * internal session
     */
    private final Session session;

    @Nullable
    private PackageEventDispatcher dispatcher;

    /**
     * package root (/etc/packages)
     */
    private Node packRoot;

    public JcrPackageRegistry(Session session) {
        this.session = session;
        initNodeTypes();
    }

    @Nullable
    PackageEventDispatcher getDispatcher() {
        return dispatcher;
    }

    public void setDispatcher(@Nullable PackageEventDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    public void dispatch(@Nonnull PackageEvent.Type type, @Nonnull PackageId id, @Nullable PackageId[] related) {
        if (dispatcher == null) {
            return;
        }
        dispatcher.dispatch(type, id, related);
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
        try (InputStream in = JcrPackageManagerImpl.class.getResourceAsStream(DEFAULT_NODETYPES)){
            if (in == null) {
                throw new InternalError("Could not load " + DEFAULT_NODETYPES + " resource.");
            }
            NodeTypeInstaller installer = ServiceProviderFactory.getProvider().getDefaultNodeTypeInstaller(session);
            CNDReader types = ServiceProviderFactory.getProvider().getCNDReader();
            types.read(new InputStreamReader(in, "utf8"), DEFAULT_NODETYPES, null);
            installer.install(null, types);
        } catch (Throwable e) {
            log.warn("Error while registering nodetypes. Package installation might not work correctly.", e);
        }
    }

    /**
     * {@inheritDoc}
     */
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

    @Nullable
    @Override
    public RegisteredPackage open(@Nonnull PackageId id) throws IOException {
        try {
            Node node = getPackageNode(id);
            return node == null ? null : new JcrRegisteredPackage(open(node, false));
        } catch (RepositoryException e) {
            throw new IOException(e);
        }
    }

    @Override
    public boolean contains(@Nonnull PackageId id) throws IOException {
        try {
            return getPackageNode(id) != null;
        } catch (RepositoryException e) {
            throw new IOException(e);
        }
    }

    @Nullable
    private Node getPackageNode(@Nonnull PackageId id) throws RepositoryException {
        String path = getInstallationPath(id);
        String[] exts = new String[]{"", ".zip", ".jar"};
        for (String ext: exts) {
            if (session.nodeExists(path + ext)) {
                return session.getNode(path + ext);
            }
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
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
    public PackageId resolve(Dependency dependency, boolean onlyInstalled) throws IOException {
        try {
            Node root = getPackageRoot(false);
            if (!root.hasNode(dependency.getGroup())) {
                return null;
            }
            Node groupNode = root.getNode(dependency.getGroup());
            NodeIterator iter = groupNode.getNodes();
            PackageId bestId = null;
            while (iter.hasNext()) {
                Node child = iter.nextNode();
                if (".snapshot".equals(child.getName())) {
                    continue;
                }
                try (JcrPackageImpl pack = new JcrPackageImpl(this, child)) {
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
            }
            return bestId;
        } catch (RepositoryException e) {
            throw new IOException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Nonnull
    @Override
    public PackageId[] usage(PackageId id) throws IOException {
        TreeSet<PackageId> usages = new TreeSet<PackageId>();
        for (PackageId pid: packages()) {
            try (RegisteredPackage pkg = open(pid)) {
                if (pkg == null || !pkg.isInstalled()) {
                    continue;
                }
                //noinspection resource
                for (Dependency dep: pkg.getPackage().getDependencies()) {
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
    public DependencyReport analyzeDependencies(@Nonnull PackageId id, boolean onlyInstalled) throws IOException, NoSuchPackageException {
        List<Dependency> unresolved = new LinkedList<Dependency>();
        List<PackageId> resolved = new LinkedList<PackageId>();
        try (RegisteredPackage pkg = open(id)) {
            if (pkg == null) {
                throw new NoSuchPackageException().setId(id);
            }
            //noinspection resource
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
                resolved.toArray(new PackageId[resolved.size()])
        );
    }

    @Nonnull
    @Override
    public PackageId register(@Nonnull InputStream in, boolean replace) throws IOException, PackageExistsException {
        try (JcrPackage pkg = upload(in, replace)){
            //noinspection resource
            return pkg.getPackage().getId();
        } catch (RepositoryException e) {
            throw new IOException(e);
        }
    }

    public JcrPackage upload(InputStream in, boolean replace)
            throws RepositoryException, IOException, PackageExistsException {

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
            pid = createRandomPid();
        }
        if (!pid.isValid()) {
            bin.dispose();
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
            try (JcrPackage oldPackage = new JcrPackageImpl(this, parent.getNode(name))) {
                JcrPackageDefinitionImpl oldDef = (JcrPackageDefinitionImpl) oldPackage.getDefinition();
                if (oldDef != null) {
                    state = oldDef.getState();
                    oldCreatedDate = oldDef.getCreated();
                }
            }

            if (replace) {
                parent.getNode(name).remove();
            } else {
                throw new PackageExistsException("Package already exists: " + pid).setId(pid);
            }
        }
        JcrPackage jcrPack = null;
        try {
            jcrPack = createNew(parent, pid, bin, archive);
            JcrPackageDefinitionImpl def = (JcrPackageDefinitionImpl) jcrPack.getDefinition();
            Calendar newCreateDate = def == null ? null : def.getCreated();
            // only transfer the old package state to the new state in case both packages have the same create date
            if (state != null && newCreateDate != null && oldCreatedDate != null && oldCreatedDate.compareTo(newCreateDate) == 0) {
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

    @Nonnull
    @Override
    public PackageId register(@Nonnull File file, boolean replace) throws IOException, PackageExistsException {
        ZipVaultPackage pack = new ZipVaultPackage(file, false, true);
        try (JcrPackage pkg = upload(pack, replace)) {
            //noinspection resource
            return pkg.getPackage().getId();
        } catch (RepositoryException e) {
            throw new IOException(e);
        }
    }

    @Nonnull
    @Override
    public PackageId registerExternal(@Nonnull File file, boolean replace) throws IOException, PackageExistsException {
        throw new UnsupportedOperationException("linking files to repository persistence is not supported.");
    }

    public JcrPackage upload(ZipVaultPackage pkg, boolean replace) throws RepositoryException, IOException, PackageExistsException {

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
            pid = createRandomPid();
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
            try (JcrPackage oldPackage = new JcrPackageImpl(this, parent.getNode(name))) {
                JcrPackageDefinitionImpl oldDef = (JcrPackageDefinitionImpl) oldPackage.getDefinition();
                if (oldDef != null) {
                    state = oldDef.getState();
                }
            }

            if (replace) {
                parent.getNode(name).remove();
            } else {
                throw new PackageExistsException("Package already exists: " + pid).setId(pid);
            }
        }
        JcrPackage jcrPack = null;
        try {
            jcrPack = createNew(parent, pid, pkg, false);
            JcrPackageDefinitionImpl def = (JcrPackageDefinitionImpl) jcrPack.getDefinition();
            if (state != null && def != null) {
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
     * yet another Convenience method to create intermediate nodes.
     * @param path path to create
     * @param autoSave if {@code true} all changes are automatically persisted
     * @return the node
     * @throws RepositoryException if an error occurrs
     */
    public Node mkdir(String path, boolean autoSave) throws RepositoryException {
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
            if (lastError != null) {
                throw lastError;
            } else {
                throw new RepositoryException("Unable to create path: " + path);
            }
        }
        if (autoSave) {
            parent.getSession().save();
        }
        return node;
    }

    /**
     * {@inheritDoc}
     */
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
    private JcrPackage createNew(@Nonnull Node parent, @Nonnull PackageId pid, @Nonnull Binary bin, @Nonnull MemoryArchive archive)
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

    @SuppressWarnings("resource")
    @Override
    public void remove(@Nonnull PackageId id) throws IOException, NoSuchPackageException {
        JcrRegisteredPackage pkg = (JcrRegisteredPackage) open(id);
        if (pkg == null) {
            throw new NoSuchPackageException().setId(id);
        }
        JcrPackage pack = pkg.getJcrPackage();
        try {
            JcrPackage snap = pack.getSnapshot();
            if (snap != null) {
                snap.getNode().remove();
            }
            pack.getNode().remove();
            session.save();
        } catch (RepositoryException e) {
            throw new IOException(e);
        }
        dispatch(PackageEvent.Type.REMOVE, id, null);
    }

    /**
     * {@inheritDoc}
     */
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
        return open(newNode, false);
    }


    @Nonnull
    @Override
    public Set<PackageId> packages() throws IOException {
        try {
            Node pRoot = getPackageRoot(true);
            if (pRoot == null) {
                return Collections.emptySet();
            }
            Set<PackageId> packages = new TreeSet<PackageId>();
            listPackages(pRoot, packages);
            return packages;
        } catch (RepositoryException e) {
            throw new IOException(e);
        }
    }

    /**
     * internally adds the packages below {@code root} to the given list
     * recursively.
     *
     * @param root root node
     * @param packages list for the packages
     * @throws RepositoryException if an error occurs
     */
    private void listPackages(Node root, Set<PackageId> packages) throws RepositoryException {
        for (NodeIterator iter = root.getNodes(); iter.hasNext();) {
            Node child = iter.nextNode();
            if (".snapshot".equals(child.getName())) {
                continue;
            }
            try (JcrPackageImpl pack = new JcrPackageImpl(this, child)) {
                if (pack.isValid()) {
                    // skip packages with illegal names
                    JcrPackageDefinition jDef = pack.getDefinition();
                    if (jDef == null || !jDef.getId().isValid()) {
                        continue;
                    }
                    packages.add(jDef.getId());
                } else if (child.hasNodes()) {
                    listPackages(child, packages);
                }
            }
        }
    }

    /**
     * Returns the path of this package. please note that since 2.3 this also
     * includes the version, but never the extension (.zip).
     *
     * @return the path of this package
     * @since 2.2
     */
    public static String getInstallationPath(PackageId id) {
        StringBuilder b = new StringBuilder(PACKAGE_ROOT_PATH_PREFIX);
        if (id.getGroup().length() > 0) {
            b.append(id.getGroup());
            b.append("/");
        }
        b.append(id.getName());
        String v = id.getVersion().toString();
        if (v.length() > 0) {
            b.append("-").append(v);
        }
        return b.toString();
    }

    /**
     * Creates a random package id for packages that lack one.
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

}
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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.Calendar;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

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
import org.apache.jackrabbit.vault.fs.io.MemoryArchive;
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
import org.apache.jackrabbit.vault.util.InputStreamPump;
import org.apache.jackrabbit.vault.util.JcrConstants;
import org.apache.jackrabbit.vault.util.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extends the <code>PackageManager</code> by JCR specific methods
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
    private final static String PACKAGE_ROOT_PATH = "/etc/packages";

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

    public JcrPackage open(PackageId id) throws RepositoryException {
        String path = id.getInstallationPath();
        String[] exts = new String[]{"", ".zip", ".jar"};
        for (String ext: exts) {
            if (session.nodeExists(path + ext)) {
                return open(session.getNode(path + ext));
            }
        }
        return null;
    }

    public JcrPackage open(Node node) throws RepositoryException {
        return open(node, false);
    }

    /**
     * {@inheritDoc}
     */
    public JcrPackage open(Node node, boolean allowInvalid) throws RepositoryException {
        JcrPackage pack = new JcrPackageImpl(node);
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
            JcrPackageImpl pack = new JcrPackageImpl(child);
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
    public JcrPackage upload(InputStream in, boolean replace) throws RepositoryException, IOException {
        return upload(in, replace, false);
    }

    /**
     * {@inheritDoc}
     */
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
        if (pid == null || pid.getInstallationPath().equals(ZipVaultPackage.UNKNOWN_PATH)) {
            bin.dispose();
            throw new IOException("Package does not contain a path specification or valid package id.");
        }
        if (!pid.isValid()) {
            throw new RepositoryException("Unable to create package. Illegal package name.");
        }

        // create parent node
        String path = pid.getInstallationPath() + ".zip";
        String parentPath = Text.getRelativeParent(path, 1);
        String name = Text.getName(path);
        Node parent = mkdir(parentPath, false);

        // remember installation state properties (GRANITE-2018)
        JcrPackageDefinitionImpl.State state = null;

        if (parent.hasNode(name)) {
            JcrPackage oldPackage = new JcrPackageImpl(parent.getNode(name));
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
            jcrPack = JcrPackageImpl.createNew(parent, pid, bin, archive);
            if (jcrPack != null) {
                JcrPackageDefinitionImpl def = (JcrPackageDefinitionImpl) jcrPack.getDefinition();
                if (state != null) {
                    def.setState(state);
                }
            }
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
    public JcrPackage upload(File file, boolean isTmpFile, boolean replace, String nameHint)
            throws RepositoryException, IOException {
        return upload(file, isTmpFile, replace, nameHint, false);
    }

    /**
     * {@inheritDoc}
     */
    public JcrPackage upload(File file, boolean isTmpFile, boolean replace, String nameHint, boolean strict)
            throws RepositoryException, IOException {

        // open zip packages
        ZipVaultPackage pack = new ZipVaultPackage(file, isTmpFile, strict);
        if (pack.getArchive().getJcrRoot() == null) {
            String msg = "Zip File is not a content package. Missing 'jcr_root'.";
            log.error(msg);
            pack.close();
            throw new IOException(msg);
        }

        // invalidate pid if path is unknown
        PackageId pid = pack.getId();
        if (pid != null && pid.getInstallationPath().equals(ZipVaultPackage.UNKNOWN_PATH)) {
            pid = null;
        }
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
        String path = pid.getInstallationPath() + ".zip";
        String parentPath = Text.getRelativeParent(path, 1);
        String name = Text.getName(path);
        Node parent = mkdir(parentPath, false);

        // remember installation state properties (GRANITE-2018)
        JcrPackageDefinitionImpl.State state = null;

        if (parent.hasNode(name)) {
            JcrPackage oldPackage = new JcrPackageImpl(parent.getNode(name));
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
            jcrPack = JcrPackageImpl.createNew(parent, pid, pack, false);
            if (jcrPack != null) {
                JcrPackageDefinitionImpl def = (JcrPackageDefinitionImpl) jcrPack.getDefinition();
                if (state != null) {
                    def.setState(state);
                }
            }
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
    public JcrPackage create(Node folder, String name)
            throws RepositoryException, IOException {
        if (folder == null) {
            folder = getPackageRoot();
        }
        return JcrPackageImpl.createNew(folder, new PackageId(name), null, true);
    }

    /**
     * {@inheritDoc}
     */
    public JcrPackage create(String group, String name)
            throws RepositoryException, IOException {
        return create(group, name, null);
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
        Node folder = mkdir(Text.getRelativeParent(pid.getInstallationPath(), 1), false);
        try {
            return JcrPackageImpl.createNew(folder, pid, null, false);
        } finally {
            session.save();
        }
    }

    /**
     * {@inheritDoc}
     */
    public void remove(JcrPackage pack) throws RepositoryException {
        JcrPackage snap = pack.getSnapshot();
        if (snap != null) {
            snap.getNode().remove();
        }
        pack.getNode().remove();
        session.save();
    }

    /**
     * {@inheritDoc}
     */
    public JcrPackage rename(JcrPackage pack, String group, String name)
            throws PackageException, RepositoryException {
        return rename(pack, group, name, null);
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
        String dstPath = newId.getInstallationPath() + ".zip";
        if (id.equals(newId) && pack.getNode().getPath().equals(dstPath)) {
            log.info("Package id not changed. won't rename.");
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
        return open(newNode);
    }

    /**
     * {@inheritDoc}
     */
    public void assemble(JcrPackage pack, ProgressTrackerListener listener)
            throws PackageException, RepositoryException, IOException {
        pack.verifyId(true, true);
        assemble(pack.getNode(), pack.getDefinition(), listener);
    }

    /**
     * {@inheritDoc}
     */
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
        packNode.save();
        pack.close();
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
        packNode.save();
        dst.close();
    }


    /**
     * {@inheritDoc}
     */
    public Node getPackageRoot() throws RepositoryException {
        return getPackageRoot(false);
    }

    /**
     * yet another Convenience method to create intermediate nodes.
     * @param path path to create
     * @param autoSave if <code>true</code> all changes are automatically persisted
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
            parent.save();
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
    public List<JcrPackage> listPackages() throws RepositoryException {
        return listPackages(null);
    }

    /**
     * {@inheritDoc}
     */
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
     * internally adds the packages below <code>root</code> to the given list
     * recursively.
     *
     * @param root root node
     * @param packages list for the packages
     * @param filter optional filter to filter out packages
     * @param built if <code>true</code> only packages with size > 0 are returned
     * @param shallow if <code>true</code> don't recurs
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
                JcrPackageImpl pack = new JcrPackageImpl(child);
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
}
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

import static org.apache.jackrabbit.vault.packaging.registry.impl.AbstractPackageRegistry.DEFAULT_PACKAGE_ROOT_PATH;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.jcr.ItemExistsException;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.commons.io.FileUtils;
import org.apache.jackrabbit.vault.fs.api.ProgressTrackerListener;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.fs.config.DefaultMetaInf;
import org.apache.jackrabbit.vault.fs.impl.ArchiveWrapper;
import org.apache.jackrabbit.vault.fs.impl.SubPackageFilterArchive;
import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.fs.io.ImportOptions;
import org.apache.jackrabbit.vault.fs.io.ZipStreamArchive;
import org.apache.jackrabbit.vault.packaging.Dependency;
import org.apache.jackrabbit.vault.packaging.ExportOptions;
import org.apache.jackrabbit.vault.packaging.JcrPackage;
import org.apache.jackrabbit.vault.packaging.JcrPackageDefinition;
import org.apache.jackrabbit.vault.packaging.JcrPackageManager;
import org.apache.jackrabbit.vault.packaging.NoSuchPackageException;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.apache.jackrabbit.vault.packaging.PackageExistsException;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.apache.jackrabbit.vault.packaging.events.PackageEvent;
import org.apache.jackrabbit.vault.packaging.events.impl.PackageEventDispatcher;
import org.apache.jackrabbit.vault.packaging.registry.PackageRegistry;

import org.apache.jackrabbit.vault.packaging.registry.impl.AbstractPackageRegistry;
import org.apache.jackrabbit.vault.packaging.registry.impl.JcrPackageRegistry;
import org.apache.jackrabbit.vault.packaging.registry.impl.JcrRegisteredPackage;
import org.apache.jackrabbit.vault.util.JcrConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Extends the {@code PackageManager} by JCR specific methods
 */
public class JcrPackageManagerImpl extends PackageManagerImpl implements JcrPackageManager {

    /**
     * root path for packages
     */
    public final static String ARCHIVE_PACKAGE_ROOT_PATH = "/jcr_root/etc/packages";

    /**
     * internal package persistence
     */
    private final JcrPackageRegistry registry;

    /**
     * Creates a new package manager using the given session. This method allows to specify one more or package
     * registry root paths, where the first will be the primary when installing new packages. The others serve as
     * backward compatibility to read existing packages.
     *
     * @param session repository session
     * @param roots the root paths to store the packages.
     * @deprecated Use {@link #JcrPackageManagerImpl(Session, String[], String[], String[], boolean)} instead.
     */
    @Deprecated
    public JcrPackageManagerImpl(@NotNull Session session, @Nullable String[] roots) {
        this(new JcrPackageRegistry(session, roots));
    }

    public JcrPackageManagerImpl(@NotNull Session session, @Nullable String[] roots, @Nullable String[] authIdsForHookExecution, @Nullable String[] authIdsForRootInstallation, boolean isStrict) {
        this(new JcrPackageRegistry(session, new AbstractPackageRegistry.SecurityConfig(authIdsForHookExecution, authIdsForRootInstallation), isStrict, roots));
    }

    protected JcrPackageManagerImpl(JcrPackageRegistry registry) {
        this.registry = registry;
    }

    private RepositoryException unwrapRepositoryException(Exception e) {
        if (e.getCause() instanceof RepositoryException) {
            return (RepositoryException) e.getCause();
        }
        return new RepositoryException(e);
    }

    public PackageRegistry getRegistry() {
        return registry;
    }
    
    public JcrPackageRegistry getInternalRegistry() {
        return registry;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JcrPackage open(PackageId id) throws RepositoryException {
        try {
            //noinspection resource
            JcrRegisteredPackage pkg = (JcrRegisteredPackage) registry.open(id);
            return pkg == null ? null : pkg.getJcrPackage();
        } catch (IOException e) {
            throw unwrapRepositoryException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JcrPackage open(Node node) throws RepositoryException {
        return registry.open(node, false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JcrPackage open(Node node, boolean allowInvalid) throws RepositoryException {
        return registry.open(node, allowInvalid);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PackageId resolve(Dependency dependency, boolean onlyInstalled) throws RepositoryException {
        try {
            return registry.resolve(dependency, onlyInstalled);
        } catch (IOException e) {
            throw unwrapRepositoryException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PackageId[] usage(PackageId id) throws RepositoryException {
        try {
            return registry.usage(id);
        } catch (IOException e) {
            throw unwrapRepositoryException(e);
        }
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
        try {
            return registry.upload(in, replace);
        } catch (PackageExistsException e) {
            throw new ItemExistsException(e.getMessage(), e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @NotNull
    @Override
    public PackageId[] extract(@NotNull Archive archive, @NotNull ImportOptions options, boolean replace)
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
        JcrPackage jcrPack = registry.upload(pkg, replace);
        jcrPack = new JcrPackageImpl(registry, jcrPack.getNode(), pkg);
        jcrPack.extract(options);

        Set<PackageId> ids = new HashSet<>();
        ids.add(pid);

        if (spfArchive != null) {
            for (Archive.Entry e: spfArchive.getSubPackageEntries()) {
                InputStream in = spfArchive.openInputStream(e);
                if (in != null) {
                    try (Archive subArchive = new ZipStreamArchive(in)) {
                        PackageId[] subIds = extract(subArchive, options, replace);
                        ids.addAll(Arrays.asList(subIds));
                    }
                }
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
        try {
            return registry.upload(pack, replace);
        } catch (PackageExistsException e) {
            throw new ItemExistsException(e.getMessage(), e);
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
        return registry.createNew(folder, new PackageId(name), null, true);
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
        return registry.create(group, name, version);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void remove(JcrPackage pack) throws RepositoryException {
        try {
            registry.remove(pack.getDefinition().getId());
        } catch (IOException e) {
            throw unwrapRepositoryException(e);
        } catch (NoSuchPackageException e) {
            // old implementation ignored this ignore
        }
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
        return registry.rename(pack, group, name, version);
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
        def.sealForAssembly(now);

        DefaultMetaInf inf = (DefaultMetaInf) def.getMetaInf();
        CompositeExportProcessor processor = new CompositeExportProcessor();

        // tweak filter for primary root, in case it contains sub-packages
        if (!registry.getPackRootPaths()[0].equals(DEFAULT_PACKAGE_ROOT_PATH)) {
            SubPackageExportProcessor sp = new SubPackageExportProcessor(this, packNode.getSession());
            WorkspaceFilter newFilter = sp.prepare(inf.getFilter());
            if (newFilter != null) {
                inf.setFilter(newFilter);
                processor.addProcessor(sp);
            }
        }

        ExportOptions opts = new ExportOptions();
        opts.setMetaInf(inf);
        opts.setListener(listener);
        processor.addProcessor(def.getInjectProcessor());
        opts.setPostProcessor(processor);

        VaultPackage pack = assemble(packNode.getSession(), opts, (File) null);
        PackageId id = pack.getId();

        // update this content
        Node contentNode = packNode.getNode(JcrConstants.JCR_CONTENT);
        
        try (InputStream in = FileUtils.openInputStream(pack.getFile())){
            // stay jcr 1.0 compatible
            //noinspection deprecation
            contentNode.setProperty(JcrConstants.JCR_DATA, in);
            contentNode.setProperty(JcrConstants.JCR_LASTMODIFIED, now);
            contentNode.setProperty(JcrConstants.JCR_MIMETYPE, JcrPackage.MIME_TYPE);
            packNode.getSession().save();
        } catch (IOException e) {
            throw new PackageException(e);
        }
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
        def.sealForAssembly(now);

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
        def.sealForRewrap(now);

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
     * yet another Convenience method to create intermediate nodes.
     * @param path path to create
     * @param autoSave if {@code true} all changes are automatically persisted
     * @return the node
     * @throws RepositoryException if an error occurrs
     */
    protected Node mkdir(String path, boolean autoSave) throws RepositoryException {
        return registry.mkdir(path, autoSave);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Node getPackageRoot() throws RepositoryException {
        return registry.getPrimaryPackageRoot(true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Node getPackageRoot(boolean noCreate) throws RepositoryException {
        return registry.getPrimaryPackageRoot(!noCreate);
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
        List<JcrPackage> packages = new LinkedList<JcrPackage>();
        for (Node root: registry.getPackageRoots()) {
            listPackages(root, packages, filter, false, false);
        }
        Collections.sort(packages);
        return packages;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<JcrPackage> listPackages(String group, boolean built) throws RepositoryException {
        List<JcrPackage> packages = new LinkedList<JcrPackage>();
        for (Node root: registry.getPackageRoots()) {
            listPackages(root, packages, group, built);
        }
        Collections.sort(packages);
        return packages;
    }

    /**
     * internally lists all the packages below the given package root and adds them to the given list.
     * @param pkgRoot the package root
     * @param packages the list of packages
     * @param group optional group to search below
     * @param built if {@code true} only packages with size > 0 are returned
     * @throws RepositoryException if an error occurrs
     */
    private void listPackages(Node pkgRoot, List<JcrPackage> packages, String group, boolean built) throws RepositoryException {
        if (group == null || group.length() == 0) {
            listPackages(pkgRoot, packages, null, built, false);
        } else {
            if (group.equals(pkgRoot.getPath())) {
                group = "";
            } else if (group.startsWith(pkgRoot.getPath() + "/")) {
                group = group.substring(pkgRoot.getPath().length() + 1);
            }
            if (group.startsWith("/")) {
                // don't scan outside the roots
                return;
            }
            Node root = pkgRoot;
            if (group.length() > 0) {
                if (root.hasNode(group)) {
                    root = root.getNode(group);
                } else {
                    return;
                }
            }
            listPackages(root, packages, null, built, true);
        }
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
                JcrPackageImpl pack = new JcrPackageImpl(registry, child);
                try {
                    if (pack.isValid()) {
                        // skip packages with illegal names
                        JcrPackageDefinition jDef = pack.getDefinition();
                        if (jDef != null && !jDef.getId().isValid()) {
                            pack.close();
                            continue;
                        }
                        if (filter == null || filter.contains(child.getPath())) {
                            if (!built || pack.getSize() > 0) {
                                packages.add(pack);
                                continue;
                            }
                        }
                        pack.close();
                    } else if (child.hasNodes() && !shallow){
                        listPackages(child, packages, filter, built, false);
                    } else {
                        pack.close();
                    }
                } catch (RepositoryException e) {
                    pack.close();
                    throw e;
                }
            }
        }
    }

    @Override
    public void setDispatcher(@Nullable PackageEventDispatcher dispatcher) {
        super.setDispatcher(dispatcher);
        registry.setDispatcher(dispatcher);
    }
}

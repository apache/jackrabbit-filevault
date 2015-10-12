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

package org.apache.jackrabbit.vault.fs.io;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.version.Version;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.jackrabbit.spi.commons.namespace.NamespaceMapping;
import org.apache.jackrabbit.spi.commons.namespace.NamespaceResolver;
import org.apache.jackrabbit.spi.commons.namespace.SessionNamespaceResolver;
import org.apache.jackrabbit.vault.fs.DirectoryArtifact;
import org.apache.jackrabbit.vault.fs.HintArtifact;
import org.apache.jackrabbit.vault.fs.api.Artifact;
import org.apache.jackrabbit.vault.fs.api.ArtifactType;
import org.apache.jackrabbit.vault.fs.api.ImportInfo;
import org.apache.jackrabbit.vault.fs.api.NodeNameList;
import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.api.PathMapping;
import org.apache.jackrabbit.vault.fs.api.SerializationType;
import org.apache.jackrabbit.vault.fs.api.VaultInputSource;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.fs.config.ConfigurationException;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.fs.config.MetaInf;
import org.apache.jackrabbit.vault.fs.config.VaultSettings;
import org.apache.jackrabbit.vault.fs.impl.ArtifactSetImpl;
import org.apache.jackrabbit.vault.fs.impl.io.FileArtifactHandler;
import org.apache.jackrabbit.vault.fs.impl.io.FolderArtifactHandler;
import org.apache.jackrabbit.vault.fs.impl.io.GenericArtifactHandler;
import org.apache.jackrabbit.vault.fs.impl.io.ImportInfoImpl;
import org.apache.jackrabbit.vault.fs.impl.io.InputSourceArtifact;
import org.apache.jackrabbit.vault.fs.impl.io.XmlAnalyzer;
import org.apache.jackrabbit.vault.fs.spi.ACLManagement;
import org.apache.jackrabbit.vault.fs.spi.CNDReader;
import org.apache.jackrabbit.vault.fs.spi.DefaultNodeTypeSet;
import org.apache.jackrabbit.vault.fs.spi.NodeTypeInstaller;
import org.apache.jackrabbit.vault.fs.spi.NodeTypeSet;
import org.apache.jackrabbit.vault.fs.spi.PrivilegeDefinitions;
import org.apache.jackrabbit.vault.fs.spi.PrivilegeInstaller;
import org.apache.jackrabbit.vault.fs.spi.ProgressTracker;
import org.apache.jackrabbit.vault.fs.spi.ServiceProviderFactory;
import org.apache.jackrabbit.vault.fs.spi.UserManagement;
import org.apache.jackrabbit.vault.util.Constants;
import org.apache.jackrabbit.vault.util.PlatformNameFormat;
import org.apache.jackrabbit.vault.util.Text;
import org.apache.jackrabbit.vault.util.Tree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <code>AbstractImporter</code>
 *
 * file/directory combinations
 *
 * 1. plain files
 *    + foo
 *      - test.gif
 *
 * 2. plain files + special folder
 *    + foo
 *      - .content.xml
 *      + bar
 *        - test.gif
 *
 * 3. special file
 *    + foo
 *      - test.gif
 *      - test.gif.dir
 *        - .content.xml
 *
 * 4. special file + sub files
 *    + foo
 *      - test.gif
 *      - test.gif.dir
 *        - .content.xml
 *        + _jcr_content
 *          - thumbnail.gif
 *
 * 4. special file + sub special files
 *    + foo
 *      - test.gif
 *      - test.gif.dir
 *        - .content.xml
 *        + _jcr_content
 *          - thumbnail.gif
 *          + thumbnail.gif.dir
 *            - .content.xml
 *
 * 5. file/folder structure
 *    + foo
 *      + en
 *        - .content.xml
 *        + _cq_content
 *          - thumbnail.gif
 *        + company
 *          - .content.xml
 */
public class Importer {

    /**
     * default logger
     */
    private static final Logger log = LoggerFactory.getLogger(Importer.class);

    /**
     * workspace filter to use during import
     */
    private WorkspaceFilter filter;

    /**
     * tree constructed of the filter roots
     */
    private final Tree<PathFilterSet> filterTree = new Tree<PathFilterSet>();

    /**
     * tracker to use for tracking messages
     */
    private ProgressTracker tracker;

    /**
     * the node types detected during the prepare phase that needed to by registered
     */
    private final DefaultNodeTypeSet nodeTypes = new DefaultNodeTypeSet("internal");

    /**
     * autosave structure that tracks the number of modified nodes
     */
    private AutoSave autoSave = new AutoSave();

    /**
     * set of paths to versionable nodes that need to be checked in after import
     */
    private final Set<String> nodesToCheckin = new HashSet<String>();

    /**
     * map of group memberships that need to be applied after import
     */
    private final Map<String, String[]> memberships = new HashMap<String, String[]>();

    /**
     * general flag that indicates if the import had (recoverable) errors
     */
    private boolean hasErrors = false;

    /**
     * overall handler for importing folder artifacts
     */
    private final FolderArtifactHandler folderHandler = new FolderArtifactHandler();

    /**
     * overall handler for importing generic artifacts
     */
    private final GenericArtifactHandler genericHandler = new GenericArtifactHandler();

    /**
     * overall handler for importing file artifacts
     */
    private final FileArtifactHandler fileHandler = new FileArtifactHandler();

    /**
     * list of archive entries that are detected as lowlevel patches and need to be copied to the
     * filesystem after import.
     */
    private final List<Archive.Entry> patches = new LinkedList<Archive.Entry>();

    private Map<String, TxInfo> intermediates = new LinkedHashMap<String, TxInfo>();

    private Archive archive;

    /**
     * list of paths to subpackages that were detected during the prepare phase
     */
    private final List<String> subPackages = new LinkedList<String>();

    /**
     * overall acl management behavior
     */
    private final ACLManagement aclManagement = ServiceProviderFactory.getProvider().getACLManagement();

    /**
     * overall user management behavior
     */
    private final UserManagement userManagement = ServiceProviderFactory.getProvider().getUserManagement();

    /**
     * the import options
     */
    private final ImportOptions opts;

    /**
     * path mapping from the import options
     */
    private PathMapping pathMapping;

    /**
     * the checkpoint state of the autosave. used for recovering from stale item errors during install.
     */
    private AutoSave cpAutosave;

    /**
     * the checkpoint tx info. used for recovering from stale item errors during install.
     */
    private TxInfo cpTxInfo;

    /**
     * the checkpoint import info.
     */
    private ImportInfo cpImportInfo;

    /**
     * retry counter for the batch auto recovery
     */
    private int recoveryRetryCounter;

    /**
     * list of processed tx infos since the last auto save.
     */
    private final List<TxInfo> processedInfos = new ArrayList<TxInfo>();

    /**
     * list of intermediate infos that were removed since the last auto save
     */
    private Map<String, TxInfo> removedIntermediates = new LinkedHashMap<String, TxInfo>();

    public Importer() {
         opts = new ImportOptions();
    }

    public Importer(ImportOptions opts) {
         this.opts = opts;
    }

    public ImportOptions getOptions() {
        return opts;
    }

    public List<String> getSubPackages() {
        return subPackages;
    }

    /**
     * Debug settings to allows to produce failures after each <code>failAfterEach</code> save.
     * @param failAfterEach cardinal indicating when to fail
     */
    public void setDebugFailAfterSave(int failAfterEach) {
        autoSave.setDebugFailEach(failAfterEach);
    }

    protected void track(String action, String path) {
        if ("E".equals(action)) {
            log.error("{} {}", action, path);
        } else {
            log.debug("{} {}", action, path);
        }
        if (tracker != null) {
            tracker.track(action, path);
        }
    }

    protected void track(Exception e, String path) {
        log.error("E {} ({})", path, e.toString());
        if (tracker != null) {
            tracker.track(e, path);
        }
    }

    /**
     /**
     * Runs the importer
     *
     * @param archive the archive to import
     * @param importRoot the root node to import
     *
     * @throws org.apache.jackrabbit.vault.fs.config.ConfigurationException if the importer is not properly configured
     * @throws java.io.IOException if an I/O error occurs
     * @throws javax.jcr.RepositoryException if an repository error occurs
     *
     * @since 2.3.20
     */
    public void run(Archive archive, Node importRoot)
            throws IOException, RepositoryException, ConfigurationException {
        this.archive = archive;

        // init tracker
        if (opts.getListener() == null) {
            tracker = null;
        } else {
            if (tracker == null) {
                tracker = new ProgressTracker();
            }
            tracker.setListener(opts.getListener());
        }

        // check format version
        int version = archive.getMetaInf().getPackageFormatVersion();
        if (version > MetaInf.FORMAT_VERSION_2) {
            String msg = "Content format version not supported (" + version + " > " + MetaInf.FORMAT_VERSION_2 + ")";
            log.warn(msg);
            throw new IOException(msg);
        }

        // init autosave
        if (opts.getAutoSaveThreshold() >= 0) {
            autoSave.setThreshold(opts.getAutoSaveThreshold());
        }
        autoSave.setDryRun(opts.isDryRun());
        autoSave.setTracker(tracker);

        // enable this to test auto-recovery of batch saves
        // autoSave.setDebugFailEach(1);
        // autoSave.setThreshold(4);

        // propagate access control handling
        if (opts.getAccessControlHandling() == null) {
            opts.setAccessControlHandling(AccessControlHandling.IGNORE);
        }
        fileHandler.setAcHandling(opts.getAccessControlHandling());
        genericHandler.setAcHandling(opts.getAccessControlHandling());
        folderHandler.setAcHandling(opts.getAccessControlHandling());

        filter = opts.getFilter();
        if (filter == null) {
            filter = archive.getMetaInf().getFilter();
        }
        if (filter == null) {
            filter = new DefaultWorkspaceFilter();
        }

        // check path remapping
        pathMapping = opts.getPathMapping();
        if (pathMapping != null) {
            filter = filter.translate(pathMapping);
        } else {
            pathMapping = PathMapping.IDENTITY;
        }

        // set import mode if possible
        if (opts.getImportMode() != null) {
            if (filter instanceof DefaultWorkspaceFilter) {
                ((DefaultWorkspaceFilter) filter).setImportMode(opts.getImportMode());
            } else {
                log.warn("Unable to override import mode, incompatible filter: {}", filter.getClass().getName());
            }
        }
        // build filter tree
        for (PathFilterSet set: filter.getFilterSets()) {
            filterTree.put(set.getRoot(), set);
        }

        String parentPath = importRoot.getPath();
        if (parentPath.equals("/")) {
            parentPath = "";
        }

        track("Collecting import information...", "");
        Session session = importRoot.getSession();
        TxInfo root = prepare(archive.getJcrRoot(), parentPath, new SessionNamespaceResolver(session));
        if (filter!=null && filter.getFilterSets() != null && filter.getFilterSets().size() > 0 ) {
            root = postFilter(root);
        }

        log.debug("Access control handling set to {}", opts.getAccessControlHandling());
        if (opts.isDryRun()) {
            track("Dry Run: Skipping node types installation (might lead to errors).", "");
            track("Simulating content import...", "");
        } else {
            track("Installing node types...", "");
            installNodeTypes(session);
            track("Installing privileges...", "");
            registerPrivileges(session);
            log.debug("Starting content import. autosave is {}", autoSave);
            track("Importing content...", "");
        }
        cpAutosave = autoSave.copy();
        LinkedList<TxInfo> skipList = new LinkedList<TxInfo>();
        while (recoveryRetryCounter++ < 10) {
            try {
                commit(session, root, skipList);
                autoSave.save(session);
                break;
            } catch (RepositoryException e) {
                if (recoveryRetryCounter == 10) {
                    log.error("Error while committing changes. Aborting.");
                    throw e;
                } else {
                    log.warn("Error while committing changes. Retrying import from checkpoint at {}. Retries {}/10",
                            cpTxInfo == null ? "/" : cpTxInfo.path, recoveryRetryCounter);
                    autoSave = cpAutosave.copy();
                    // build skip list
                    skipList.clear();
                    TxInfo info = cpTxInfo;
                    while (info != null && info.parent != null) {
                        skipList.addFirst(info);
                        info = info.parent;
                    }
                    // reset any intermediate changes in this run
                    intermediates.putAll(removedIntermediates);
                    for (TxInfo i: removedIntermediates.values()) {
                        i.isIntermediate = 1;
                    }
                    removedIntermediates.clear();
                    processedInfos.clear();
                    session.refresh(false);
                }
            }
        }
        checkinNodes(session);
        applyMemberships(session);
        applyPatches();
        if (opts.isDryRun()) {
            if (hasErrors) {
                track("Package import simulation finished. (with errors, check logs!)", "");
                log.error("There were errors during package install simulation. Please check the logs for details.");
            } else {
                track("Package import simulation finished.", "");
            }
        } else {
            if (hasErrors) {
                track("Package imported (with errors, check logs!)", "");
                log.error("There were errors during package install. Please check the logs for details.");
            } else {
                track("Package imported.", "");
            }
        }
    }

    private TxInfo postFilter(TxInfo root) {
        TxInfo modifierRoot = root;
        if (filter.contains(modifierRoot.path)){
            return modifierRoot;
        }
        if (filter.isAncestor(modifierRoot.path)) {
            for (String k : modifierRoot.children().keySet()) {
                TxInfo child = modifierRoot.children().get(k);
                modifierRoot.children().put(k, postFilter(child));
            }
        }
        else {
            modifierRoot.discard();
        }
        return modifierRoot;
    }

    public boolean hasErrors() {
        return hasErrors;
    }

    protected VaultSettings getSettings() {
        return archive.getMetaInf().getSettings();
    }

    private void installNodeTypes(Session session)
            throws IOException, RepositoryException {
        Collection<NodeTypeSet> metaTypes = archive.getMetaInf().getNodeTypes();
        if (metaTypes != null) {
            for (NodeTypeSet cnd: metaTypes) {
                nodeTypes.add(cnd);
            }
        }
        if (!nodeTypes.getNodeTypes().isEmpty()) {
            NodeTypeInstaller installer = ServiceProviderFactory.getProvider().getDefaultNodeTypeInstaller(session);
            try {
                log.debug("Installing node types...");
                installer.install(tracker, nodeTypes);
            } catch (RepositoryException e) {
                if (opts.isStrict()) {
                    throw e;
                }
                track(e, "Packaged node types");
            }
        } else {
            log.debug("No node types provided.");
        }
    }

    private void registerPrivileges(Session session) throws IOException, RepositoryException {
        PrivilegeDefinitions privileges = archive.getMetaInf().getPrivileges();
        if (privileges != null && !privileges.getDefinitions().isEmpty()) {
            PrivilegeInstaller installer = ServiceProviderFactory.getProvider().getDefaultPrivilegeInstaller(session);
            try {
                log.debug("Registering privileges...");
                installer.install(tracker, privileges);
            } catch (RepositoryException e) {
                if (opts.isStrict()) {
                    throw e;
                }
                track(e, "Packaged privileges");
            }
        } else {
            log.debug("No privileges provided.");
        }
    }

    /**
     * Checks if the given file name is excluded
     * @param fileName the file name
     * @return <code>true</code> if excluded
     */
    protected boolean isExcluded(String fileName) {
        // hard coded exclusion of .vlt files/directories
        return getSettings().getIgnoredNames().contains(fileName)
                || fileName.equals(".vlt")
                || fileName.startsWith(".vlt-");
    }

    private TxInfo prepare(Archive.Entry jcrRoot, String parentPath, NamespaceResolver resolver)
            throws IOException, RepositoryException {
        TxInfo root = new TxInfo(null, parentPath);

        // special check for .content.xml in root directory
        Archive.Entry contentXml = jcrRoot.getChild(Constants.DOT_CONTENT_XML);
        if (contentXml != null) {
            if (contentXml.isDirectory()) {
                throw new IllegalArgumentException(Constants.DOT_CONTENT_XML + " is not a file");
            }
            // in this case, create a new info
            root.artifacts.add(new InputSourceArtifact(
                    null,
                    "",
                    "",
                    ArtifactType.PRIMARY,
                    archive.getInputSource(contentXml),
                    SerializationType.XML_DOCVIEW
            ));
        }
        root.artifacts.add(new DirectoryArtifact(Text.getName(parentPath)));

        prepare(jcrRoot, root, resolver);

        // go over the filter roots and create intermediates for the parents if needed (bug #25370)
        for (PathFilterSet sets: filter.getFilterSets()) {
            String rootPath = sets.getRoot();
            // make filter root relative to import root
            if (parentPath.length() > 0 && rootPath.startsWith(parentPath)) {
                rootPath = rootPath.substring(parentPath.length());
            }
            String[] segments = Text.explode(rootPath, '/');
            TxInfo current = root;
            StringBuilder path = new StringBuilder();
            for (final String name : segments) {
                path.append('/').append(name);
                TxInfo child = current.children().get(name);
                if (child == null) {
                    log.debug("Creating missing intermediate directory artifact for {}", name);
                    child = current.addChild(new TxInfo(current, path.toString()));
                    child.isIntermediate = 1;
                    intermediates.put(path.toString(), child);
                }
                current = child;
            }
        }
        return root;
    }

    private void prepare(Archive.Entry directory, TxInfo parentInfo, NamespaceResolver resolver)
            throws IOException, RepositoryException {
        Collection<? extends Archive.Entry> files = directory.getChildren();
        if (files == null) {
            return;
        }
        // first process the directories
        for (Archive.Entry file: files) {
            if (file.isDirectory()) {
                String fileName = file.getName();
                if (isExcluded(fileName)) {
                    continue;
                }
                String repoName = PlatformNameFormat.getRepositoryName(fileName);
                String repoPath = parentInfo.path + "/" + repoName;
                if (repoName.endsWith(".dir")) {
                    // fix repo path
                    repoName = repoName.substring(0, repoName.length() - 4);
                    repoPath = parentInfo.path + "/" + repoName;
                }

                // remap if needed
                String mappedPath = pathMapping.map(repoPath);
                if (!mappedPath.equals(repoPath)) {
                    String mappedParent = Text.getRelativeParent(mappedPath, 1);
                    if (!mappedParent.equals(parentInfo.path)) {
                        log.warn("remapping other than renames not supported yet ({} -> {}).", repoPath, mappedPath);
                    } else {
                        log.info("remapping detected {} -> {}", repoPath, mappedPath);
                        repoPath = mappedPath;
                        repoName = Text.getName(repoPath);
                    }
                }

                TxInfo info = parentInfo.addChild(new TxInfo(parentInfo, repoPath));
                log.debug("Creating directory artifact for {}", repoName);
                Artifact parent = new DirectoryArtifact(repoName);
                info.artifacts.add(parent);

                Archive.Entry contentXml = file.getChild(Constants.DOT_CONTENT_XML);
                if (contentXml != null) {
                    if (contentXml.isDirectory()) {
                        throw new IllegalArgumentException(Constants.DOT_CONTENT_XML + " is not a file");
                    }
                    // in this case, create a new info
                    info.artifacts.add(new InputSourceArtifact(
                            parent,
                            Constants.DOT_CONTENT_XML,
                            "",
                            ArtifactType.PRIMARY,
                            archive.getInputSource(contentXml),
                            SerializationType.XML_DOCVIEW
                    ));
                } else {
                    // this is an empty directory and potential intermediate
                    info.isIntermediate = 1;
                    intermediates.put(repoPath, info);
                    log.debug("Detecting intermediate directory {}", repoName);
                }
                prepare(file, info, resolver);
            }
        }
        // second the files
        for (Archive.Entry file: files) {
            if (!file.isDirectory()) {
                String fileName = file.getName();
                if (isExcluded(fileName)) {
                    continue;
                }
                String repoName = PlatformNameFormat.getRepositoryName(fileName);
                String repoPath = parentInfo.path + "/" + repoName;
                if (file.getName().equals(Constants.DOT_CONTENT_XML)) {
                    continue;
                }
                if (opts.getPatchDirectory() != null && repoPath.startsWith(opts.getPatchParentPath())) {
                    patches.add(file);
                    if (!opts.isPatchKeepInRepo()) {
                        continue;
                    }
                }
                if (repoPath.startsWith("/etc/packages/") && (repoPath.endsWith(".jar") || repoPath.endsWith(".zip"))) {
                    subPackages.add(repoPath);
                }

                // remap if needed
                String mappedPath = pathMapping.map(repoPath);
                if (!mappedPath.equals(repoPath)) {
                    String mappedParent = Text.getRelativeParent(mappedPath, 1);
                    if (!mappedParent.equals(parentInfo.path)) {
                        log.warn("remapping other than renames not supported yet ({} -> {}).", repoPath, mappedPath);
                    } else {
                        log.info("remapping detected {} -> {}", repoPath, mappedPath);
                        repoPath = mappedPath;
                        repoName = Text.getName(repoPath);
                    }
                }

                String repoBase = repoName;
                String ext = "";
                int idx = repoName.lastIndexOf('.');
                if (idx > 0) {
                    repoBase = repoName.substring(0, idx);
                    ext = repoName.substring(idx);
                }

                SerializationType serType = SerializationType.GENERIC;
                ArtifactType type = ArtifactType.PRIMARY;
                VaultInputSource is = archive.getInputSource(file);
                if (ext.equals(".xml")) {
                    // this can either be an generic exported docview or a 'user-xml' that is imported as file
                    // btw: this only works for input sources that can refetch their input stream
                    serType = XmlAnalyzer.analyze(is);
                    if (serType == SerializationType.XML_DOCVIEW) {
                        // in this case, the extension was added by the exporter.
                        repoName = repoBase;
                    } else {
                        ext = "";
                        serType = SerializationType.GENERIC;
                        type = ArtifactType.FILE;
                    }
                } else if (ext.equals(".cnd")) {
                    if (opts.getCndPattern().matcher(repoPath).matches()) {
                        InputStream in = is.getByteStream();
                        try {
                            Reader r = new InputStreamReader(in, "utf8");
                            CNDReader reader = ServiceProviderFactory.getProvider().getCNDReader();
                            // provide session namespaces
                            reader.read(r, is.getSystemId(), new NamespaceMapping(resolver));
                            nodeTypes.add(reader);
                            log.debug("Loaded nodetypes from {}.", repoPath);
                        } catch (IOException e1) {
                            log.error("Error while reading CND.", e1);
                        } finally {
                            IOUtils.closeQuietly(in);
                        }
                    }
                    ext = "";
                    type = ArtifactType.FILE;
                } else if (ext.equals(".xcnd")) {
                    serType = SerializationType.CND;
                    repoName = repoBase;
                } else if (ext.equals(".binary")) {
                    serType = SerializationType.GENERIC;
                    type = ArtifactType.BINARY;
                    repoName = repoBase;
                } else {
                    ext = "";
                    type = ArtifactType.FILE;
                }
                if (type != ArtifactType.PRIMARY) {
                    // check if info already exists (in case of .dir artifacts)
                    TxInfo parent = parentInfo.children().get(repoName);
                    if (parent == null) {
                        if (type == ArtifactType.BINARY) {
                            // search next parent for binary artifacts
                            parent = parentInfo;
                            while (parent != null && parent.isIntermediate > 0) {
                                parent = parent.parent;
                            }
                            if (parent == null) {
                                log.warn("No parent info found {}. using direct.");
                                parent = parentInfo;
                            }
                        } else {
                            // "normal" file
                            TxInfo tx = new TxInfo(parentInfo, parentInfo.path + "/" + repoName);
                            log.debug("Creating file artifact for {}", repoName);
                            tx.artifacts.add(new InputSourceArtifact(null,
                                    repoName, ext, type, is, serType
                            ));
                            parentInfo.addChild(tx);
                        }
                    }
                    if (parent != null) {
                        String path = parentInfo.path + "/" + repoName;
                        String relPath = parent.name + path.substring(parent.path.length());
                        log.debug("Attaching {} artifact {}", type, path);
                        parent.artifacts.add(new InputSourceArtifact(null,
                                relPath, ext, type, is, serType
                        ));
                    }
                }
                if (type == ArtifactType.PRIMARY) {
                    // if primary artifact, add new tx info
                    TxInfo tx = new TxInfo(parentInfo, parentInfo.path + "/" + repoName);
                    log.debug("Creating primary artifact for {}", repoName);
                    tx.artifacts.add(new InputSourceArtifact(null,
                            repoName, ext, type, is, serType
                    ));
                    parentInfo.addChild(tx);
                }
            }
        }
        // sort the child infos according to the workspace filter rules if possible
        Tree.Node<PathFilterSet> filterNode = filterTree.getNode(parentInfo.path);
        if (filterNode != null) {
            parentInfo.sort(filterNode.getChildren().keySet());
        }
    }

    private void commit(Session session, TxInfo info, LinkedList<TxInfo> skipList) throws RepositoryException, IOException {
        try {
            ImportInfo imp = null;
            if (skipList.isEmpty()) {
                if (info == cpTxInfo) {
                    // don't need to import again, just set import info
                    log.debug("skipping last checkpoint info {}", info.path);
                    imp = cpImportInfo;
                } else {
                    imp = commit(session, info);
                    if (imp != null) {
                        nodesToCheckin.addAll(imp.getToVersion());
                        memberships.putAll(imp.getMemberships());
                        autoSave.modified(imp.numModified());
                    }
                }
            } else if (log.isDebugEnabled()) {
                StringBuilder skips = new StringBuilder();
                for (TxInfo i: skipList) {
                    skips.append(i.path).append(',');
                }
                log.debug("skip list: {}", skips);
            }

            if (autoSave.needsSave()) {
                autoSave.save(session);
                // save checkpoint
                cpTxInfo = info;
                cpAutosave = autoSave.copy();
                cpImportInfo = imp;
                recoveryRetryCounter = 0;
                /*
                todo: check retry logic if it's ok to discard all processed infos or if some ancestors should be excluded
                // discard processed infos to free some memory
                for (TxInfo i: processedInfos) {
                    i.discard();
                }
                */
                removedIntermediates.clear();
                processedInfos.clear();
            }

            // copy the children collection since children could be removed during remapping
            List<TxInfo> children = new ArrayList<TxInfo>(info.children().values());

            // traverse children but skip the ones not in the skip list
            TxInfo next = skipList.isEmpty() ? null : skipList.removeFirst();
            for (TxInfo child: children) {
                if (next == null || next == child) {
                    commit(session, child, skipList);
                    // continue normally after lng child was found
                    next = null;
                } else {
                    log.debug("skipping {}", child.path);
                }
            }

            // see if any child nodes need to be reordered
            if (info.nameList != null) {
                Node node = info.getNode(session);
                if (node == null) {
                    log.warn("Unable to restore order of {}. Node does not exist.", info.path);
                } else if (info.nameList.needsReorder(node)) {
                    log.debug("Restoring order of {}.", info.path);
                    info.nameList.restoreOrder(node);
                }
            }
            processedInfos.add(info);
        } catch (RepositoryException e) {
            log.error("Error while committing {}: {}", info.path, e.toString());
            throw e;
        }
    }

    private ImportInfoImpl commit(Session session, TxInfo info) throws RepositoryException, IOException {
        log.debug("committing {}", info.path);
        ImportInfoImpl imp = null;
        if (info.artifacts == null) {
            log.debug("S {}", info.path);
        } else if (info.artifacts.isEmpty()) {
            // intermediate directory, check if node exists and filter
            // matches. in this case remove the node (bug #25370)
            // but only if intermediate is not processed yet (bug #42562)
            if (filter.contains(info.path) && session.nodeExists(info.path) && info.isIntermediate < 2) {
                Node node = session.getNode(info.path);
                imp = new ImportInfoImpl();
                if (aclManagement.isACLNode(node)) {
                    if (opts.getAccessControlHandling() == AccessControlHandling.OVERWRITE
                            || opts.getAccessControlHandling() == AccessControlHandling.CLEAR) {
                        imp.onDeleted(info.path);
                        aclManagement.clearACL(node.getParent());
                    }
                } else {
                    imp.onDeleted(info.path);
                    node.remove();
                }
            }
        } else if (info.artifacts.getPrimaryData() !=null && info.artifacts.size() == 1) {
            // simple case, only 1 primary artifact
            Node node = info.getParentNode(session);
            if (node == null) {
                imp = new ImportInfoImpl();
                imp.onError(info.path, new IllegalStateException("Parent node not found."));
            } else {
                imp = genericHandler.accept(filter, node, info.artifacts.getPrimaryData().getRelativePath(), info.artifacts);
                if (imp == null) {
                    throw new IllegalStateException("generic handler did not accept " + info.path);
                }
            }
        } else if (info.artifacts.getDirectory() != null) {
            for (TxInfo child: info.children().values()) {
                // add the directory artifacts as hint to this one.
                if (child.artifacts == null) {
                    // in this case it's some deleted intermediate directory???
                    String path = info.name + "/" + child.name;
                    info.artifacts.add(new HintArtifact(path));

                } else {
                    for (Artifact a: child.artifacts.values()) {
                        String path = info.name + "/" + a.getRelativePath();
                        info.artifacts.add(new HintArtifact(path));
                    }
                }
            }
            Node node = info.getParentNode(session);
            if (node == null) {
                imp = new ImportInfoImpl();
                imp.onError(info.path, new IllegalStateException("Parent node not found."));
            } else {
                if (info.isIntermediate == 2) {
                    // skip existing intermediate
                    log.debug("skipping intermediate node at {}", info.path);
                } else if (info.artifacts.getPrimaryData() == null) {
                    // create nt:folder node if not exists
                    imp = folderHandler.accept(filter, node, info.name,  info.artifacts);
                    if (imp == null) {
                        throw new IllegalStateException("folder handler did not accept " + info.path);
                    }
                } else {
                    imp = genericHandler.accept(filter, node, info.artifacts.getDirectory().getRelativePath(), info.artifacts);
                    if (imp == null) {
                        throw new IllegalStateException("generic handler did not accept " + info.path);
                    }
                }
            }
        } else if (info.artifacts.size(ArtifactType.FILE) > 0) {
            Node node = info.getParentNode(session);
            if (node == null) {
                imp = new ImportInfoImpl();
                imp.onError(info.path, new IllegalStateException("Parent node not found."));
            } else {
                imp = fileHandler.accept(filter, node, info.name,  info.artifacts);
                if (imp == null) {
                    throw new IllegalStateException("file handler did not accept " + info.path);
                }
            }
        } else {
            throw new UnsupportedOperationException("ArtifactSet not supported: " + info.artifacts);
        }

        if (imp != null) {
            for (Map.Entry<String, ImportInfo.Info> entry: imp.getInfos().entrySet()) {
                String path = entry.getKey();
                ImportInfo.Type type = entry.getValue().getType();
                if (type != ImportInfoImpl.Type.DEL) {
                    // mark intermediates as processed
                    TxInfo im = intermediates.remove(path);
                    if (im != null) {
                        log.debug("P {}", path);
                        removedIntermediates.put(path, im);
                        im.isIntermediate = 2;
                    }
                }
                switch (type) {
                    case CRE:
                        track("A", path);
                        autoSave.markResolved(path);
                        break;
                    case DEL:
                        track("D", path);
                        autoSave.markResolved(path);
                        break;
                    case MOD:
                        track("U", path);
                        autoSave.markResolved(path);
                        break;
                    case NOP:
                        track("-", path);
                        autoSave.markResolved(path);
                        break;
                    case REP:
                        track("R", path);
                        autoSave.markResolved(path);
                        break;
                    case MIS:
                        track("!", path);
                        autoSave.markMissing(path);
                        break;
                    case ERR:
                        Exception error = entry.getValue().getError();
                        if (error == null) {
                            track("E", path);
                        } else {
                            track(error, path);
                        }
                        hasErrors = true;
                        break;
                }

                // see if any child nodes need to be reordered and remember namelist.
                // only restore order if in filter scope if freshly created
                NodeNameList nameList = entry.getValue().getNameList();
                if (nameList != null && (filter.contains(path) || type == ImportInfo.Type.CRE)) {
                    TxInfo subInfo = info.findChild(path);
                    if (subInfo != null) {
                        subInfo.nameList = nameList;
                    }
                }
            }
            // remap the child tree in case some of the nodes where moved during import (e.g. authorizable)
            // todo: this could be a problem during error recovery
            info = info.remap(imp.getRemapped());
        }
        log.debug("committed {}", info.path);
        return imp;
    }

    public void checkinNodes(Session session) {
        if (nodesToCheckin.isEmpty()) {
            return;
        }
        if (opts.isDryRun()) {
            track("Dry run: Would commit versions...", "");
        } else {
            track("Committing versions...", "");
        }
        for (String path: nodesToCheckin) {
            try {
                Node node = session.getNode(path);
                try {
                    if (opts.isDryRun()) {
                        track("V", String.format("%s (---)", path));
                    } else {
                        Version v = node.checkin();
                        track("V", String.format("%s (%s)", path, v.getName()));
                    }
                } catch (RepositoryException e) {
                    log.error("Error while checkin node {}: {}",path, e.toString());
                }
            } catch (RepositoryException e) {
                log.error("Error while retrieving node to be versioned at {}.", path, e);
            }
        }
        nodesToCheckin.clear();
    }

    public void applyMemberships(Session session) {
        if (memberships.isEmpty()) {
            return;
        }
        if (opts.isDryRun()) {
            track("Dry run: Would apply merged group memberships...", "");
        } else {
            track("Applying merged group memberships...", "");
        }
        for (String id: memberships.keySet()) {
            String[] members = memberships.get(id);
            String authPath = userManagement.getAuthorizablePath(session, id);
            if (authPath != null) {
                if (!opts.isDryRun()) {
                    userManagement.addMembers(session, id, members);
                }
                track("U", String.format("%s", authPath));
            }
        }
        try {
            session.save();
        } catch (RepositoryException e) {
            log.error("Error while updating memberships.", e);
            try {
                session.refresh(false);
            } catch (RepositoryException e1) {
                // ignore
            }
        }
        memberships.clear();
    }

    private void applyPatches() {
        for (Archive.Entry e: patches) {
            String name = e.getName();
            File target = new File(opts.getPatchDirectory(), name);
            if (opts.isDryRun()) {
                log.info("Dry run: Would copy patch {} to {}", name, target.getPath());
            } else {
                log.info("Copying patch {} to {}", name, target.getPath());
                InputStream in = null;
                OutputStream out = null;
                try {
                    in = archive.getInputSource(e).getByteStream();
                    out = FileUtils.openOutputStream(target);
                    IOUtils.copy(in, out);
                } catch (IOException e1) {
                    log.error("Error while copying patch.", e);
                } finally {
                    IOUtils.closeQuietly(in);
                    IOUtils.closeQuietly(out);
                }
            }
            track("P", name);
        }
    }

    private static class TxInfo {

        private TxInfo parent;

        private final String path;

        private final String name;

        private ArtifactSetImpl artifacts = new ArtifactSetImpl();

        private Map<String, TxInfo> children;

        private byte isIntermediate = 0;

        private NodeNameList nameList;

        public TxInfo(TxInfo parent, String path) {
            log.debug("New TxInfo {}" , path);
            this.parent = parent;
            this.path = path;
            this.name = Text.getName(path);
        }

        public TxInfo addChild(TxInfo child) {
            if (children == null) {
                children = new LinkedHashMap<String, TxInfo>();
            }
            children.put(child.name, child);
            return child;
        }

        public Map<String, TxInfo> children() {
            if (children == null) {
                return Collections.emptyMap();
            } else {
                return children;
            }
        }

        public void sort(Collection<String> names) {
            if (children == null || children.size() <=1 || names == null || names.isEmpty()) {
                return;
            }
            Map<String, TxInfo> ret = new LinkedHashMap<String, TxInfo>();
            Iterator<String> iter = names.iterator();
            while (iter.hasNext() && children.size() > 1) {
                String name = iter.next();
                TxInfo info = children.remove(name);
                if (info != null) {
                    ret.put(name, info);
                }
            }
            ret.putAll(children);
            children = ret;
        }

        public Node getParentNode(Session s) throws RepositoryException {
            String parentPath = emptyPathToRoot(Text.getRelativeParent(path, 1));
            return s.nodeExists(parentPath)
                    ? s.getNode(parentPath)
                    : null;
        }

        public Node getNode(Session s) throws RepositoryException {
            String p = emptyPathToRoot(path);
            return s.nodeExists(p)
                    ? s.getNode(p)
                    : null;
        }

        public void discard() {
            log.debug("discarding {}", path);
            artifacts = null;
            children = null;
        }

        public TxInfo findChild(String absPath) {
            if (path.equals(absPath)) {
                return this;
            }
            if (!absPath.startsWith(path + "/")) {
                return null;
            }
            absPath = absPath.substring(path.length());
            TxInfo root = this;
            for (String name: Text.explode(absPath, '/')) {
                root = root.children().get(name);
                if (root == null) {
                    break;
                }
            }
            return root;
        }

        public TxInfo remap(PathMapping mapping) {
            String mappedPath = mapping.map(path, true);
            if (mappedPath.equals(path)) {
                return this;
            }

            TxInfo ret = new TxInfo(parent, mappedPath);

            // todo: what should we do with the artifacts ?
            ret.artifacts.addAll(artifacts);

            // todo: do we need to remap the namelist, too?
            ret.nameList = nameList;

            ret.isIntermediate = isIntermediate;

            if (children != null) {
                for (TxInfo child: children.values()) {
                    child = child.remap(mapping);
                    child.parent = this;
                    ret.addChild(child);
                }
            }

            // ensure that our parent links the new info
            if (parent.children != null) {
                parent.children.put(ret.name, ret);
            }

            return ret;
        }

        private static String emptyPathToRoot(String path) {
            return path == null || path.length() == 0 ? "/" : path;
        }
    }

}
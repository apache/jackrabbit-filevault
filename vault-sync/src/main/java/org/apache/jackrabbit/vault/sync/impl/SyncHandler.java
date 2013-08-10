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
package org.apache.jackrabbit.vault.sync.impl;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.jci.monitor.FilesystemAlterationListener;
import org.apache.commons.jci.monitor.FilesystemAlterationObserver;
import org.apache.commons.jci.monitor.FilesystemAlterationObserverImpl;
import org.apache.jackrabbit.util.Text;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.fs.config.ExportRoot;
import org.apache.jackrabbit.vault.util.Constants;
import org.apache.jackrabbit.vault.util.PathComparator;
import org.apache.jackrabbit.vault.util.PlatformNameFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
* <code>SyncHandler</code>...
*/
public class SyncHandler implements FilesystemAlterationListener {

    /**
     * default logger
     */
    private static final Logger log = LoggerFactory.getLogger(SyncHandler.class);

    private static final String DEFAULT_FILTER = "default-filter.xml";

    private final File fileRoot;

    private final FilesystemAlterationObserverImpl observer;

    private final Set<String> pendingJcrChanges = new HashSet<String>();

    private final Map<String, File> pendingFsChanges = new TreeMap<String, File>(new PathComparator(Constants.FS_NATIVE.charAt(0)));

    private final SyncLog syncLog;

    private String[] preparedJcrChanges;

    private final SyncConfig cfg;

    private ExportRoot vltExportRoot;

    private WorkspaceFilter filter;

    private FStat filterStat;

    private FStat configStat;


    // default to exclude all hidden files and directories
    private Pattern[] excluded = new Pattern[]{
            Pattern.compile("\\..*")
    };

    private FileFilter fileFilter = new FileFilter() {
        public boolean accept(File file) {
            String name = file.getName();
            if (file.isHidden()) {
                return false;
            }
            for (Pattern p:excluded) {
                if (p.matcher(name).matches()) {
                    return false;
                }
            }
            return true;
        }
    };


    public SyncHandler(File fileRoot) {
        this.fileRoot = fileRoot;
        vltExportRoot = ExportRoot.findRoot(fileRoot);
        syncLog = new SyncLog(new File(fileRoot, SyncConstants.SYNCLOG_FILE_NAME));
        syncLog.log("Vault sync service started and observing this directory.");
        cfg = new SyncConfig(new File(fileRoot, SyncConstants.CONFIG_FILE_NAME));
        try {
            cfg.init();
            configStat = new FStat(cfg.getFile());
        } catch (IOException e) {
            log.error("Error while initializing configuration file: {}", e.toString());
        }
        filterStat = new FStat();
        updateFilter();
        syncLog.log("Syncing in %s is %s by " + SyncConstants.CONFIG_FILE_NAME, fileRoot.getAbsolutePath(), cfg.isDisabled() ? "disabled" : "enabled");

        observer = new FilesystemAlterationObserverImpl(fileRoot);
        // "initialize" internal structure of observer (need dummy listener, otherwise events are not processed)
        observer.addListener(new DummyListener());
        observer.checkAndNotify();
        observer.addListener(this);
    }

    private void updateFilter() {
        try {
            File filterFile;
            // check for vlt context
            if (vltExportRoot != null && vltExportRoot.isValid()) {
                filterFile = new File(vltExportRoot.getMetaDir(), Constants.FILTER_XML);
            } else {
                filterFile = new File(fileRoot, SyncConstants.FILTER_FILE_NAME).getCanonicalFile();
                if (!filterFile.exists()) {
                    // init default filter
                    InputStream in = SyncConfig.class.getResourceAsStream(DEFAULT_FILTER);
                    if (in == null) {
                        log.error("Unable to load default filter.");
                    } else {
                        OutputStream out = null;
                        try {
                            out = FileUtils.openOutputStream(filterFile);
                            IOUtils.copy(in, out);
                        } finally {
                            IOUtils.closeQuietly(in);
                            IOUtils.closeQuietly(out);
                        }
                    }
                }
            }
            if (filterStat.modified(filterFile)) {
                filter = loadFilter(filterFile);
            }
        } catch (IOException e) {
            log.warn("Unable to read filter file: {}", e.toString());
        }
    }

    private WorkspaceFilter loadFilter(File filterFile) {
        if (filterFile.isFile()) {
            DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
            try {
                filter.load(filterFile);
                syncLog.log("Sync root configured with filter %s", filterFile.getAbsolutePath());
                return filter;
            } catch (Exception e) {
                log.error("Error while loading sync filter: " + e.toString());
            }
        } else {
            log.info("Filter file missing: {}", filterFile.getAbsolutePath());
        }
        return null;
    }

    public boolean covers(String path) {
        return filter != null && filter.covers(path);
    }

    public boolean contains(String path) {
        return filter != null && filter.contains(path);
    }

    public void prepareForSync() {
        // assert locked
        preparedJcrChanges = pendingJcrChanges.toArray(new String[pendingJcrChanges.size()]);
        pendingJcrChanges.clear();
        pendingFsChanges.clear();
    }

    public void sync(Session session) throws RepositoryException, IOException {
        updateConfig();
        updateFilter();
        if (filter == null) {
            log.info("No filter present or configured in {}. Not syncing.", fileRoot.getAbsolutePath());
            observer.checkAndNotify();
            return;
        }

        log.debug("Starting sync cycle for {}.", this);
        if (cfg.isDisabled()) {
            log.debug("Syncing is disabled in {}.", fileRoot.getAbsolutePath());
            // flush changes
            observer.checkAndNotify();
            pendingFsChanges.clear();
            return;
        }
        // check if full sync is requested
        SyncMode syncOnce = cfg.getSyncOnce();
        if (syncOnce != null) {
            cfg.setSyncOnce(null);
            try {
                cfg.save();
            } catch (IOException e) {
                log.error("Error while saving config", e);
            }
            log.info("Sync Once requested: {}", syncOnce);
            syncTree(session, syncOnce);
        } else {
            SyncResult res = syncToDisk(session);
            log.debug("Scanning filesystem for changes {}", this);
            observer.checkAndNotify();
            syncToJcr(session, res);
            // todo: maybe save after each import?
            session.save();
            res.dump();
        }
        log.debug("Sync cycle completed for {}", this);
    }

    private void updateConfig() {
        try {
            if (configStat.modified(cfg.getFile())) {
                // detect enabled/disabled changes
                boolean wasDisabled = cfg.isDisabled();
                cfg.load();
                if (wasDisabled != cfg.isDisabled()) {
                    syncLog.log("Syncing in %s is %s by " + SyncConstants.CONFIG_FILE_NAME, fileRoot.getAbsolutePath(), cfg.isDisabled() ? "disabled" : "enabled");
                }
            }
        } catch (IOException e) {
            // ignore
            log.warn("Error while loading config: " + e.toString());
        }
    }

    private TreeSync createTreeSync(SyncMode mode) {
        TreeSync sync = new TreeSync(syncLog, fileFilter, filter);
        sync.setSyncMode(mode);
        return sync;
    }

    private void syncTree(Session session, SyncMode direction) throws RepositoryException, IOException {
        TreeSync tree = createTreeSync(direction);
        tree.sync(session.getRootNode(), fileRoot);
        // flush fs changes
        observer.checkAndNotify();
        pendingFsChanges.clear();
    }

    private SyncResult syncToDisk(Session session) throws RepositoryException, IOException {
        SyncResult res = new SyncResult();
        for (String path: preparedJcrChanges) {
            boolean recursive = path.endsWith("/");
            if (recursive) {
                path = path.substring(0, path.length() - 1);
            }
            if (!contains(path)) {
                log.debug("**** rejected. filter does not include {}", path);
                continue;
            }
            File file = getFileForJcrPath(path);
            log.debug("**** about sync jcr:/{} -> file://{}", path, file.getAbsolutePath());
            Node node;
            Node parentNode;
            if (session.nodeExists(path)) {
                node = session.getNode(path);
                parentNode = node.getParent();
            } else {
                node = null;
                String parentPath = Text.getRelativeParent(path, 1);
                parentNode = session.nodeExists(parentPath)
                        ? session.getNode(parentPath)
                        : null;
            }
            TreeSync tree = createTreeSync(SyncMode.JCR2FS);
            res.merge(tree.syncSingle(parentNode, node, file, recursive));
        }
        return res;
    }

    private void syncToJcr(Session session, SyncResult res) throws RepositoryException, IOException {
        for (String filePath: pendingFsChanges.keySet()) {
            if (res.getByFsPath(filePath) != null) {
                log.debug("ignoring change triggered by previous JCR->FS update. {}", filePath);
                return;
            }
            File file = pendingFsChanges.get(filePath);
            String path = getJcrPathForFile(file);
            log.debug("**** about sync file:/{} -> jcr://{}", file.getAbsolutePath(), path);
            if (!contains(path)) {
                log.debug("**** rejected. filter does not include {}", path);
                continue;
            }
            Node node;
            Node parentNode;
            if (session.nodeExists(path)) {
                node = session.getNode(path);
                parentNode = node.getParent();
            } else {
                node = null;
                String parentPath = Text.getRelativeParent(path, 1);
                parentNode = session.nodeExists(parentPath)
                        ? session.getNode(parentPath)
                        : null;
            }
            TreeSync tree = createTreeSync(SyncMode.FS2JCR);
            tree.setSyncMode(SyncMode.FS2JCR);
            res.merge(tree.syncSingle(parentNode, node, file, false));
        }
    }

    public File getFileForJcrPath(String path) {
        String[] segs = Text.explode(path, '/');
        File file = fileRoot;
        for (String seg : segs) {
            file = new File(file, PlatformNameFormat.getPlatformName(seg));
        }
        return file;
    }

    public String getJcrPathForFile(File file) {
        StringBuilder s = new StringBuilder();
        while (!file.equals(fileRoot)) {
            s.insert(0, PlatformNameFormat.getRepositoryName(file.getName())).insert(0, '/');
            file = file.getParentFile();
        }
        return s.toString();
    }

    private void onChange(File file, String type) {
        boolean accept = fileFilter.accept(file);
        log.debug("{}({}), accepted={}", new Object[]{type, file.getAbsolutePath(), accept});
        if (!accept) {
            return;
        }
        pendingFsChanges.put(file.getAbsolutePath(), file);
    }

    public void onFileCreate(File file) {
        onChange(file, "onFileCreate");
    }

    public void onFileChange(File file) {
        onChange(file, "onFileChange");
    }

    public void onFileDelete(File file) {
        onChange(file, "onFileDelete");
    }

    public void onDirectoryCreate(File file) {
        onChange(file, "onDirectoryCreate");
    }


    public void onDirectoryDelete(File file) {
        onChange(file, "onDirectoryDelete");
    }

    public void onStart(FilesystemAlterationObserver filesystemAlterationObserver) { }

    public void onStop(FilesystemAlterationObserver filesystemAlterationObserver) { }

    public void onDirectoryChange(File file) { }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("SyncSpec");
        sb.append("{fileRoot=").append(fileRoot);
        sb.append('}');
        return sb.toString();
    }

    public void registerPendingJcrChange(String path) {
        pendingJcrChanges.add(path);
    }

    private static final class FStat {

        private String path;

        private long lastModified;

        private FStat() {
            path = "";
        }

        private FStat(File file) throws IOException {
            this.path = file.getCanonicalPath();
            this.lastModified = file.lastModified();
        }

        public boolean modified(File file) throws IOException {
            String newPath = file.getCanonicalPath();
            long newLastModified = file.lastModified();
            if (!newPath.equals(path) || lastModified != newLastModified) {
                path = newPath;
                lastModified = newLastModified;
                return true;
            } else {
                return false;
            }
        }
    }

    private static final class DummyListener implements FilesystemAlterationListener {
        public void onStart(FilesystemAlterationObserver filesystemAlterationObserver) { }
        public void onFileCreate(File file) { }
        public void onFileChange(File file) { }
        public void onFileDelete(File file) { }
        public void onDirectoryCreate(File file) { }
        public void onDirectoryChange(File file) { }
        public void onDirectoryDelete(File file) { }
        public void onStop(FilesystemAlterationObserver filesystemAlterationObserver) { }
    }

}
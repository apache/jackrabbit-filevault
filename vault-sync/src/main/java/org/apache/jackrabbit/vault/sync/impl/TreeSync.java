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
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import javax.jcr.Binary;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.nodetype.NodeType;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.jackrabbit.vault.fs.api.SerializationType;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.util.FileInputSource;
import org.apache.jackrabbit.vault.util.MimeTypes;
import org.apache.jackrabbit.vault.util.PlatformNameFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <code>TreeSync</code>...
 */
public class TreeSync {

    /**
     * default logger
     */
    private static final Logger log = LoggerFactory.getLogger(TreeSync.class);

    enum Type {
        FILE,
        DIRECTORY,
        MISSING,
        UNSUPPORTED,
        FULL_COVERAGE
    }

    enum FilterStatus {
        CONTAINED,
        COVERED,
        ANCESTOR,
        OUTSIDE
    }

    private SyncMode syncMode = SyncMode.JCR2FS;

    private boolean preserveFileDate = true;

    private final SyncLog syncLog;

    private final FileFilter fileFilter;

    private final WorkspaceFilter wspFilter;

    // currently hard coded
    private final String[] FULL_COVERAGE_NTS = {
            "rep:AccessControl",
            "rep:Policy",
            "cq:Widget",
            "cq:EditConfig",
            "cq:WorkflowModel",
            "vlt:FullCoverage",
            "mix:language",
            "sling:OsgiConfig"
    };

    public TreeSync(SyncLog syncLog, FileFilter fileFilter, WorkspaceFilter wspFilter) {
        this.syncLog = syncLog;
        this.fileFilter = fileFilter;
        this.wspFilter = wspFilter;
    }

    public void setSyncMode(SyncMode syncMode) {
        this.syncMode = syncMode;
    }

    public void setPreserveFileDate(boolean preserveFileDate) {
        this.preserveFileDate = preserveFileDate;
    }

    public SyncResult sync(Node node, File dir) throws RepositoryException, IOException {
        SyncResult result = new SyncResult();
        sync(result, node, dir);
        result.dump();
        return result;
    }

    public SyncResult syncSingle(Node parentNode, Node node, File file, boolean recursive) throws RepositoryException, IOException {
        Entry e;
        if (node == null) {
            e = new Entry(parentNode, file);
            e.jcrType = Type.MISSING;
        } else {
            e = new Entry(parentNode, file.getParentFile(), node);
            e.jcrType = getJcrType(node);
        }
        e.fsType = getFsType(file);
        e.fStat = getFilterStatus(e.getJcrPath());
        SyncResult res = new SyncResult();
        sync(res, e, recursive);
        return res;
    }

    private FilterStatus getFilterStatus(String path) {
        if (path == null) {
            return FilterStatus.CONTAINED;
        } else if (wspFilter.contains(path)) {
            return FilterStatus.CONTAINED;
        } else if (wspFilter.covers(path)) {
            return FilterStatus.COVERED;
        } else if (wspFilter.isAncestor(path)) {
            return FilterStatus.ANCESTOR;
        } else {
            return FilterStatus.OUTSIDE;
        }
    }

    private Type getJcrType(Node node) throws RepositoryException {
        if (node == null) {
            return Type.MISSING;
        } else if (node.isNodeType(NodeType.NT_FILE)) {
            if (node.getMixinNodeTypes().length == 0) {
                // only ok, if pure nt:file
                Node content = node.getNode(Node.JCR_CONTENT);
                if (content.isNodeType(NodeType.NT_RESOURCE) && content.getMixinNodeTypes().length == 0) {
                    return Type.FILE;
                }
            }
            return Type.UNSUPPORTED;
        }
        for (String nt: FULL_COVERAGE_NTS) {
            try {
                if (node.isNodeType(nt)) {
                    return Type.FULL_COVERAGE;
                }
            } catch (RepositoryException e) {
                // ignore
            }
        }
        if (node.isNodeType(NodeType.NT_HIERARCHY_NODE)) {
            return Type.DIRECTORY;
        } else {
            return Type.UNSUPPORTED;
        }
    }

    private Type getFsType(File file) {
        if (!file.exists()) {
            return Type.MISSING;
        } else if (file.isDirectory()) {
            // ignore directories ending with .dir as they are special directories for vlt
            if (file.getName().endsWith(".dir")) {
                return Type.UNSUPPORTED;
            } else {
                return Type.DIRECTORY;
            }
        } else if (file.isFile()) {
            // check for vlt serialized XMLs and mark them as unsupported
            try {
                SerializationType type = XmlAnalyzer.analyze(new FileInputSource(file));
                if (type == SerializationType.XML_DOCVIEW) {
                    return Type.UNSUPPORTED;
                }
            } catch (IOException e) {
                log.warn("Unable to analyze {}: {}", file.getAbsolutePath(), e.toString());
                return Type.UNSUPPORTED;
            }
            return Type.FILE;
        } else {
            return Type.UNSUPPORTED;
        }
    }

    private void sync(SyncResult res, Node node, File dir) throws RepositoryException, IOException {
        Map<String, Entry> jcrEntries = new HashMap<String, Entry>();
        Map<String, Entry> fsEntries = new HashMap<String, Entry>();
        NodeIterator iter = node.getNodes();
        while (iter.hasNext()) {
            Node child = iter.nextNode();
            Entry e = new Entry(node, dir, child);
            e.jcrType = getJcrType(child);
            e.fStat = getFilterStatus(e.getJcrPath());
            jcrEntries.put(e.jcrName, e);
            fsEntries.put(e.file.getName(), e);
        }
        if (dir.isDirectory()) {
            for (File file: dir.listFiles(fileFilter)) {
                Entry e = fsEntries.get(file.getName());
                if (e == null) {
                    e = new Entry(node, file);
                }
                e.fsType = getFsType(file);
                e.fStat = getFilterStatus(e.getJcrPath());
                jcrEntries.put(e.jcrName, e);
                fsEntries.put(e.file.getName(), e);
            }
        }
        // process
        for (Entry e: jcrEntries.values()) {
            sync(res, e, true);
        }
    }

    private void sync(SyncResult res, Entry e, boolean recursive) throws RepositoryException, IOException {
        if (e.fStat == FilterStatus.OUTSIDE) {
            if (syncMode == SyncMode.JCR2FS) {
                if (e.fsType == Type.FILE) {
                    deleteFile(res, e);
                } else if (e.fsType == Type.DIRECTORY) {
                    deleteDirectory(res, e);
                }
            }
        } else if (e.jcrType == Type.DIRECTORY) {
            if (e.fsType == Type.DIRECTORY) {
                if (recursive) {
                    sync(res, e.node, e.file);
                }
            } else if (e.fsType == Type.MISSING) {
                if (syncMode == SyncMode.FS2JCR) {
                    if (e.fStat == FilterStatus.CONTAINED) {
                        deleteFolder(res, e);
                    }
                } else {
                    createDirectory(res, e);
                    if (recursive) {
                        sync(res, e.node, e.file);
                    }
                }
            } else {
                logConflict(e);
            }
        } else if (e.jcrType == Type.FILE) {
            if (e.fsType == Type.FILE) {
                if (e.fStat == FilterStatus.CONTAINED) {
                    syncFiles(res, e);
                }
            } else if (e.fsType == Type.MISSING) {
                if (e.fStat == FilterStatus.CONTAINED) {
                    if (syncMode == SyncMode.FS2JCR) {
                        deleteNtFile(res, e);
                    } else {
                        writeFile(res, e);
                    }
                }
            } else {
                logConflict(e);
            }
        } else if (e.jcrType == Type.FULL_COVERAGE) {
            log.debug("refusing to traverse full coverage aggregates {}", e.node.getPath());
        } else if (e.jcrType == Type.UNSUPPORTED) {
            log.debug("refusing to traverse unsupported {}", e.node.getPath());
        } else if (e.jcrType == Type.MISSING) {
            if (e.fsType == Type.FILE) {
                if (e.fStat == FilterStatus.CONTAINED) {
                    if (syncMode == SyncMode.FS2JCR) {
                        writeNtFile(res, e);
                    } else {
                        deleteFile(res, e);
                    }
                }
            } else if (e.fsType == Type.DIRECTORY) {
                if (e.fStat == FilterStatus.CONTAINED) {
                    if (syncMode == SyncMode.FS2JCR) {
                        writeFolder(res, e);
                        if (e.node != null && recursive) {
                            sync(res, e.node, e.file);
                        }
                    } else {
                        deleteDirectory(res, e);
                    }
                } else {
                    if (syncMode == SyncMode.FS2JCR) {
                        // create intermediate node???
                        log.warn("Creation of unknown intermediate nodes not supported yet. fsPath={} jcrPath={}", e.getFsPath(), e.getJcrPath());
                    }
                }

            } else {
                logConflict(e);
            }
        }
    }

    private void deleteFolder(SyncResult res, Entry e) throws RepositoryException {
        String path = e.node.getPath();
        e.node.remove();
        syncLog.log("D jcr:/%s/", path);
        res.addEntry(path, e.getFsPath(), SyncResult.Operation.DELETE_JCR);
    }

    private void deleteNtFile(SyncResult res, Entry e) throws RepositoryException {
        String path = e.node.getPath();
        e.node.remove();
        syncLog.log("D jcr:/%s", path);
        res.addEntry(path, e.getFsPath(), SyncResult.Operation.DELETE_JCR);
    }

    private void deleteFile(SyncResult res, Entry e) throws IOException, RepositoryException {
        res.addEntry(e.getJcrPath(), e.getFsPath(), SyncResult.Operation.DELETE_FS);
        String path = e.file.getAbsolutePath();
        FileUtils.forceDelete(e.file);
        syncLog.log("D file://%s", path);
    }

    private void deleteDirectory(SyncResult res, Entry e) throws IOException, RepositoryException {
        deleteRecursive(res, e.file, e.getJcrPath());
    }

    private void deleteRecursive(SyncResult res, File directory, String jcrPath) throws IOException {
        if (!directory.exists()) {
            String message = directory + " does not exist";
            throw new IllegalArgumentException(message);
        }

        if (!directory.isDirectory()) {
            String message = directory + " is not a directory";
            throw new IllegalArgumentException(message);
        }

        File[] files = directory.listFiles();
        if (files == null) {
            throw new IOException("Failed to list contents of " + directory);
        }

        for (File file : files) {
            String subPath = jcrPath + "/" + PlatformNameFormat.getPlatformName(file.getName());
            if (file.isDirectory()) {
                deleteRecursive(res, file, subPath);
            } else {
                FileUtils.forceDelete(file);
                syncLog.log("D file://%s", file.getAbsolutePath());
                res.addEntry(subPath, file.getAbsolutePath(), SyncResult.Operation.DELETE_FS);
            }
        }
        directory.delete();
        syncLog.log("D file://%s/", directory.getAbsolutePath());
        res.addEntry(jcrPath, directory.getAbsolutePath(), SyncResult.Operation.DELETE_FS);
    }

    private void createDirectory(SyncResult res, Entry e) throws RepositoryException {
        e.file.mkdir();
        syncLog.log("A file://%s/", e.getFsPath());
        res.addEntry(e.getJcrPath(), e.getFsPath(), SyncResult.Operation.UPDATE_FS);
    }

    private void syncFiles(SyncResult res, Entry e) throws RepositoryException, IOException {
        if (syncMode == SyncMode.FS2JCR) {
            writeNtFile(res, e);
        } else {
            writeFile(res, e);
        }
    }

    private void writeFile(SyncResult res, Entry e) throws IOException, RepositoryException {
        String action = e.file.exists() ? "U" : "A";
        Binary bin = null;
        InputStream in = null;
        OutputStream out = null;
        try {
            bin = e.node.getProperty("jcr:content/jcr:data").getBinary();
            in = bin.getStream();
            out = FileUtils.openOutputStream(e.file);
            IOUtils.copy(in, out);
            if (preserveFileDate) {
                Calendar lastModified = e.node.getProperty("jcr:content/jcr:lastModified").getDate();
                e.file.setLastModified(lastModified.getTimeInMillis());
            }
            syncLog.log("%s file://%s", action, e.file.getAbsolutePath());
            res.addEntry(e.getJcrPath(), e.getFsPath(), SyncResult.Operation.UPDATE_FS);
        } finally {
            IOUtils.closeQuietly(in);
            IOUtils.closeQuietly(out);
            if (bin != null) {
                bin.dispose();
            }
        }
    }

    private void writeNtFile(SyncResult res, Entry e) throws RepositoryException, IOException {
        Node ntFile = e.node;
        Node content;
        String action = "A";
        if (ntFile == null) {
            e.node = ntFile = e.parentNode.addNode(e.jcrName, NodeType.NT_FILE);
            content = ntFile.addNode(Node.JCR_CONTENT, NodeType.NT_RESOURCE);
        } else {
            content = ntFile.getNode(Node.JCR_CONTENT);
            action = "U";
        }
        Calendar cal = Calendar.getInstance();
        if (preserveFileDate) {
            cal.setTimeInMillis(e.file.lastModified());
        }
        InputStream in = FileUtils.openInputStream(e.file);
        Binary bin = content.getSession().getValueFactory().createBinary(in);
        content.setProperty(Property.JCR_DATA, bin);
        content.setProperty(Property.JCR_LAST_MODIFIED, cal);
        content.setProperty(Property.JCR_MIMETYPE, MimeTypes.getMimeType(e.file.getName(), MimeTypes.APPLICATION_OCTET_STREAM));
        syncLog.log("%s jcr://%s", action, ntFile.getPath());
        res.addEntry(e.getJcrPath(), e.getFsPath(), SyncResult.Operation.UPDATE_JCR);
    }

    private void writeFolder(SyncResult res, Entry e) throws RepositoryException {
        e.node = e.parentNode.addNode(e.jcrName, NodeType.NT_FOLDER);
        syncLog.log("A jcr://%s/", e.node.getPath());
        res.addEntry(e.getJcrPath(), e.getFsPath(), SyncResult.Operation.UPDATE_JCR);
    }

    private void logConflict(Entry e) {
        log.error("Sync conflict. JCR type is {}, but FS type is {}", e.jcrType, e.fsType);
    }

    private static final class Entry {

        private final File file;

        private Type fsType = Type.MISSING;

        private final Node parentNode;

        private Node node;

        private final String jcrName;

        private Type jcrType = Type.MISSING;

        private FilterStatus fStat = FilterStatus.OUTSIDE;

        private Entry(Node parentNode, File file) {
            this.parentNode = parentNode;
            this.file = file;
            this.jcrName = PlatformNameFormat.getRepositoryName(file.getName());
        }

        private Entry(Node parentNode, File parentDir, Node node) throws RepositoryException {
            this.parentNode = parentNode;
            this.node = node;
            this.jcrName = node.getName();
            this.file = new File(parentDir, PlatformNameFormat.getPlatformName(jcrName));
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();
            sb.append("Entry");
            sb.append("{fsName='").append(file.getName()).append('\'');
            sb.append(", fsType=").append(fsType);
            sb.append(", jcrName='").append(jcrName).append('\'');
            sb.append(", jcrType=").append(jcrType);
            sb.append('}');
            return sb.toString();
        }

        public String getFsPath() {
            return file.getAbsolutePath();
        }

        public String getJcrPath() throws RepositoryException {
            if (parentNode == null && node == null) {
                return null;
            }
            return node == null
                    ? parentNode.getPath() + "/" + jcrName
                    : node.getPath();

        }
    }
}
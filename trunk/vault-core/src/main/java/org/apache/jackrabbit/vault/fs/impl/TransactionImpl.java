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

package org.apache.jackrabbit.vault.fs.impl;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.jcr.RepositoryException;

import org.apache.jackrabbit.vault.fs.DirectoryArtifact;
import org.apache.jackrabbit.vault.fs.api.Aggregate;
import org.apache.jackrabbit.vault.fs.api.Artifact;
import org.apache.jackrabbit.vault.fs.api.ArtifactType;
import org.apache.jackrabbit.vault.fs.api.ImportArtifact;
import org.apache.jackrabbit.vault.fs.api.ImportInfo;
import org.apache.jackrabbit.vault.fs.api.SerializationType;
import org.apache.jackrabbit.vault.fs.api.VaultFile;
import org.apache.jackrabbit.vault.fs.api.VaultFileOutput;
import org.apache.jackrabbit.vault.fs.api.VaultFileSystem;
import org.apache.jackrabbit.vault.fs.api.VaultFsTransaction;
import org.apache.jackrabbit.vault.fs.api.VaultInputSource;
import org.apache.jackrabbit.vault.fs.impl.io.DocViewAnalyzer;
import org.apache.jackrabbit.vault.fs.impl.io.ImportInfoImpl;
import org.apache.jackrabbit.vault.fs.impl.io.InputSourceArtifact;
import org.apache.jackrabbit.vault.fs.impl.io.XmlAnalyzer;
import org.apache.jackrabbit.vault.fs.io.AutoSave;
import org.apache.jackrabbit.vault.fs.io.DocViewAnalyzerListener;
import org.apache.jackrabbit.vault.util.Constants;
import org.apache.jackrabbit.vault.util.PathComparator;
import org.apache.jackrabbit.vault.util.PathUtil;
import org.apache.jackrabbit.vault.util.PlatformNameFormat;
import org.apache.jackrabbit.vault.util.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides transactional brackets around a write back transaction to
 * the Vault filesystem. a transaction is always needed due to the fact that
 * several jcr files could belong to the same artifact.
 *
 * TODO: check all vault operations!
 *
 */
public class TransactionImpl implements VaultFsTransaction {

    /**
     * default logger
     */
    private static final Logger log = LoggerFactory.getLogger(TransactionImpl.class);

    private final VaultFileSystem fs;

    private final List<Change> changes = new LinkedList<Change>();

    private final Map<String, DotXmlInfo> dotXmlNodes = new HashMap<String, DotXmlInfo>();

    private boolean verbose;

    private final AutoSave autoSave = new AutoSave();

    public TransactionImpl(VaultFileSystem fs) {
        this.fs = fs;
    }

    public AutoSave getAutoSave() {
        return autoSave;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public void delete(VaultFile file) throws IOException {
        if (file != null) {
            changes.add(new Change(Type.DELETED, file, null));
        }
    }

    public void modify(VaultFile file, VaultInputSource input) throws IOException {
        final Change change = new Change(Type.MODIFIED, file, input);
        changes.add(change);

        // special handling for .content.xml
        if (file.getName().equals(Constants.DOT_CONTENT_XML)) {
            String repoParentPath = file.getAggregate().getPath();
            // analyze the xml to scan for added nodes
            DocViewAnalyzer.analyze(
                new DocViewAnalyzerListener(){
                    public void onNode(String path, boolean intermediate, String nodeType) {
                        if (!intermediate) {
                            dotXmlNodes.put(path, new DotXmlInfo(change, nodeType == null));
                        }
                    }
                }, fs.getAggregateManager().getSession(), repoParentPath, input);
        }
    }

    public VaultFileOutput add(String path, VaultInputSource input)
            throws IOException, RepositoryException {

        String repoPath = PlatformNameFormat.getRepositoryPath(path, true);
        String repoName = Text.getName(repoPath);
        String parentPath = Text.getRelativeParent(path, 1);
        String repoParentPath = Text.getRelativeParent(repoPath, 1);

        if (repoName.equals(Constants.DOT_CONTENT_XML)) {
            // special handling for .content.xml
            final Change change = new Change(Type.ADDED_X, repoParentPath, path, input);
            changes.add(change);

            // analyze the xml to scan for added nodes
            DocViewAnalyzer.analyze(
                new DocViewAnalyzerListener(){
                    public void onNode(String path, boolean intermediate, String nodeType) {
                        if (!intermediate) {
                            dotXmlNodes.put(path, new DotXmlInfo(change, nodeType == null));
                        }
                    }
                }, fs.getAggregateManager().getSession(), repoParentPath, input);
            // create artifact
            String parentExt = parentPath.endsWith(".dir") ? ".dir" : "";
            Artifact parent = new DirectoryArtifact(Text.getName(repoParentPath), parentExt);
            InputSourceArtifact isa = new InputSourceArtifact(
                    parent,
                    Constants.DOT_CONTENT_XML,
                    "",
                    ArtifactType.PRIMARY, input,
                    SerializationType.XML_DOCVIEW);
            isa.setContentType("text/xml");
            // attach to change
            change.isa = isa;
            return new VaultFileOutputImpl(change);

        } else {
            // normal file, detect type
            SerializationType serType = SerializationType.GENERIC;
            ArtifactType aType = ArtifactType.FILE;
            String extension = "";
            int idx = repoName.lastIndexOf('.');
            if (idx > 0) {
                String base = repoName.substring(0, idx);
                String ext = repoName.substring(idx);
                if (ext.equals(".xml")) {
                    // this can either be an generic exported docview or
                    // a 'user-xml' that is imported as file
                    // btw: this only works for input sources that can
                    //      refetch their input stream
                    serType = XmlAnalyzer.analyze(input);
                    if (serType == SerializationType.XML_DOCVIEW) {
                        // in this case, the extension was added by the exporter.
                        aType = ArtifactType.PRIMARY;
                        repoName = base;
                        extension = ext;
                    } else {
                        // force generic
                        serType = SerializationType.GENERIC;
                    }
                } else if (ext.equals(".xcnd")) {
                    aType = ArtifactType.PRIMARY;
                    serType = SerializationType.CND;
                    repoName = base;
                    extension = ext;
                } else if (ext.equals(".binary")) {
                    aType = ArtifactType.BINARY;
                    repoName = base;
                    extension = ext;
                }
            }
            InputSourceArtifact isa = new InputSourceArtifact(null, repoName,
                    extension, aType, input, serType);

            Change change = new Change(Type.ADDED, repoParentPath + "/" + repoName, path, input);
            change.isa = isa;
            changes.add(change);
            return new VaultFileOutputImpl(change);
        }
    }

    public void mkdir(String path) throws IOException, RepositoryException {
        changes.add(new Change(Type.MKDIR, PlatformNameFormat.getRepositoryPath(path), path, null));
    }

    public Collection<Info> commit() throws RepositoryException, IOException {
        Map<String, Info> infos = new HashMap<String, Info>();

        // remember all nodes to checkin again
        ImportInfoImpl allInfos = new ImportInfoImpl();

        // first scan all changes for additions that need to be attached to a
        // .content.xml change
        if (!dotXmlNodes.isEmpty()) {
            Iterator<Change> iter = changes.iterator();
            while (iter.hasNext()) {
                Change c = iter.next();
                if (c.type == Type.ADDED) {
                    if (c.isa.getType() == ArtifactType.BINARY) {
                        DotXmlInfo dxi = dotXmlNodes.get(c.repoPath);
                        if (dxi != null) {
                            dxi.change.add(c);
                            iter.remove();
                        }
                    }
                } else if (c.type == Type.MKDIR) {
                    DotXmlInfo dxi = dotXmlNodes.get(c.repoPath);
                    if (dxi != null) {
                        iter.remove();
                    }
                }
            }
        }

        // process the changes and group them by artifact path
        Map<String, TxInfo> modified = new TreeMap<String, TxInfo>(new PathComparator());
        boolean ignoreMP = true;
        while (!changes.isEmpty()) {
            int size = changes.size();
            // process as many changes that have a parent file
            Iterator<Change> iter = changes.iterator();
            while (iter.hasNext()) {
                Change change = iter.next();
                if (processChange(change, modified, ignoreMP)) {
                    changes.remove(change);
                    iter = changes.iterator();
                }
            }
            if (changes.size() == size) {
                if (ignoreMP) {
                    ignoreMP = false;
                } else {
                    for (Change c: changes) {
                        infos.put(c.filePath, new Info(Type.ERROR, c.filePath));
                    }
                    // abort iteration
                    changes.clear();
                }
            } else {
                // write back the current collected modifications and generate a
                // new modified info list
                for (TxInfo info : modified.values()) {

                    // check if primary artifact is still present
                    if (info.out == null && info.aggregate == null) {
                        // this was an intermediate directory delete
                        for (String path: info.original.keySet()) {
                            infos.put(path, new Info(Type.DELETED, path));
                            if (verbose) {
                                log.info("...comitting  DEL {}", path);
                            }
                        }
                    } else if (info.out.getArtifacts().isEmpty() && info.aggregate != null) {
                        // delete entire node if aggregate is still attached
                        if (info.aggregate.isAttached()) {
                            info.aggregate.remove(false);
                        }
                        // generate infos for the deleted ones
                        for (String path: info.original.keySet()) {
                            infos.put(path, new Info(Type.DELETED, path));
                            if (verbose) {
                                log.info("...comitting  DEL {}", path);
                            }
                        }
                        // mark the primary artifact of the parent as modified
                        // TODO fix
                        String cXmlPath = info.parentFile.getPath();
                        if (cXmlPath.endsWith("/")) {
                            cXmlPath+= Constants.DOT_CONTENT_XML;
                        } else {
                            cXmlPath+= "/" + Constants.DOT_CONTENT_XML;
                        }
                        Info i = infos.get(cXmlPath);
                        if (i == null) {
                            infos.put(cXmlPath, new Info(Type.MODIFIED, cXmlPath));
                        }
                    } else if (info.aggregate == null) {
                        // this was and addition
                        // for now, just guess from the artifacts the new files
                        String parentPath = info.parentFile.getPath();
                        if (!parentPath.endsWith("/")) {
                            parentPath += "/";
                        }
                        for (Artifact a: info.out.getArtifacts().values()) {
                            if (a instanceof ImportArtifact) {
                                String path = parentPath + a.getPlatformPath();
                                infos.put(path, new Info(Type.ADDED, path));
                            }
                        }

                        ImportInfo ret = info.out.close();
                        if (ret != null) {
                            allInfos.merge(ret);
                            if (verbose) {
                                for (Map.Entry e: ret.getModifications().entrySet()) {
                                    log.info("...comitting  {} {}", e.getValue(), e.getKey());
                                }
                            }
                        }
                        // modify parent
                        infos.put(info.parentFile.getPath(), new Info(Type.MODIFIED, info.parentFile.getPath()));

                    } else {
                        // this was a modification
                        ImportInfo ret = info.out.close();
                        if (ret != null) {
                            allInfos.merge(ret);
                        }
                        for (VaultFile file: info.original.values()) {
                            infos.put(file.getPath(), new Info(Type.MODIFIED, file.getPath()));
                            if (verbose) {
                                log.info("...comitting  UPD {}", file.getPath());
                            }
                        }
                        if (verbose && ret != null) {
                            for (Map.Entry e: ret.getModifications().entrySet()) {
                                log.info("...comitting  {} {}", e.getValue(), e.getKey());
                            }
                        }
                    }
                }
            }
            modified.clear();
            fs.invalidate();
        }
        if (verbose) {
            log.info("Persisting changes...");
        }
        if (allInfos.numErrors() > 0) {
            try {
                fs.getAggregateManager().getSession().refresh(false);
            } catch (RepositoryException e) {
                // ignore
            }
            throw new RepositoryException("There were errors during commit. Aborting transaction.");
        }
        fs.getAggregateManager().getSession().save();
        allInfos.checkinNodes(fs.getAggregateManager().getSession());
        fs.invalidate();
        return infos.values();
    }


    private boolean processChange(Change change, Map<String, TxInfo> modified, boolean ignoreMP)
            throws RepositoryException, IOException {
        switch (change.type) {
            case ADDED_X: {
                // special handling for .content.xml
                // filePath: /vltTest/foo/.content.xml
                // repoPath: /vltTest/foo

                // parentPath: /vltTest/foo
                String parentPath = Text.getRelativeParent(change.filePath, 1);
                VaultFile parent = fs.getFile(parentPath);
                TxInfo txInfo;
                String repoPath;
                if (parent == null) {
                    // parentPath: /vltTest
                    parentPath = Text.getRelativeParent(parentPath, 1);
                    parent = fs.getFile(parentPath);
                    repoPath = change.repoPath;
                    String repoName = Text.getName(repoPath);
                    if (parent == null) {
                        // special case if parent is an intermediate directory
                        // that is not created yet. for example _jcr_content
                        // /header.png/_jcr_content/renditions

                        // check if parent node exists
                        if (!fs.getAggregateManager().getSession().nodeExists(Text.getRelativeParent(repoPath, 1))) {
                            return false;
                        }
                        while ((parent == null || parent.getAggregate() == null)
                                && parentPath.length() > 0) {
                            String parentName = Text.getName(parentPath);
                            if (parentName.endsWith(".dir")) {
                                parentName = parentName.substring(0, parentName.length() - 4);
                            }
                            repoName = PlatformNameFormat.getRepositoryName(parentName) + "/" + repoName;
                            parentPath = Text.getRelativeParent(parentPath, 1);
                            parent = fs.getFile(parentPath);
                        }
                        if (parent == null || parent.getAggregate() == null) {
                            return false;
                        }
                        String repoRelPath = Text.getName(parentPath) + "/" + repoName;
                        txInfo = modified.get(parent.getAggregate().getPath());
                        if (txInfo == null) {
                            txInfo = new TxInfo(parent.getAggregate());
                            modified.put(parent.getAggregate().getPath(), txInfo);
                        }
                        txInfo.out.getArtifacts().add(new InputSourceArtifact(null,
                                repoRelPath, change.isa.getExtension(),
                                ArtifactType.FILE,
                                change.isa.getInputSource(), change.isa.getSerializationType()
                        ));
                    } else {
                        // repoPath: /vltTest.foo
                        assertInFilter(repoPath);
                        txInfo = modified.get(repoPath);
                        if (txInfo == null) {
                            txInfo = new TxInfo(repoPath,
                                    ((AggregateImpl) parent.getAggregate()).create(repoName));
                            txInfo.parentFile = parent;
                            modified.put(repoPath, txInfo);
                        }
                        txInfo.out.getArtifacts().add(change.isa);
                    }
                } else {
                    // repoPath: /vltTest/foo
                    repoPath = parent.getAggregate().getPath();
                    assertInFilter(repoPath);
                    txInfo = modified.get(repoPath);
                    if (txInfo == null) {
                        txInfo = new TxInfo(parent.getAggregate());
                        txInfo.parentFile = parent;
                        modified.put(repoPath, txInfo);
                    }
                    txInfo.out.getArtifacts().add(change.isa);
                }
                if (txInfo == null) {
                    return false;
                }
                // add sub changes
                if (change.subChanges != null) {
                    for (Change sc: change.subChanges) {
                        // need to adjust relative path of input
                        // repoPath    = /vltTest/foo
                        // sc.repoPath = /vltTest/foo/text/file
                        // relPath     = foo/text/file
                        String relPath = PathUtil.getRelativePath(repoPath, sc.repoPath);
                        relPath  = Text.getName(repoPath) + "/" + relPath;
                        if (!relPath.equals(sc.isa.getRelativePath())) {
                            // todo: check if correct platform path
                            sc.isa = new InputSourceArtifact(
                                    null,
                                    relPath,
                                    "",
                                    sc.isa.getType(),
                                    sc.isa.getInputSource(),
                                    sc.isa.getSerializationType()
                            );
                        }
                        txInfo.out.getArtifacts().add(sc.isa);
                    }
                }
                if (verbose) {
                    log.info("...scheduling ADD {}/{}", parent.getPath(), Constants.DOT_CONTENT_XML);
                }
            } break;
            case ADDED: {
                // get parent file
                String parentPath = Text.getRelativeParent(change.filePath, 1);

                // stop processing if parent file does not exist.
                VaultFile parent = fs.getFile(parentPath);
                String repoName = change.isa.getRelativePath();
                if (parent == null || parent.getAggregate() == null) {
                    if (ignoreMP) {
                        return false;
                    }
                    // Hack: if parent is intermediate directory, search
                    // next valid parent and modify its artifact set.
                    // since we cannot easily determine if the parent is an
                    // intermediate directory, we just process all failing ones
                    // at the end.
                    while ((parent == null || parent.getAggregate() == null)
                            && parentPath.length() > 0) {
                        String parentName = Text.getName(parentPath);
                        if (parentName.endsWith(".dir")) {
                            parentName = parentName.substring(0, parentName.length() - 4);
                        }
                        repoName = PlatformNameFormat.getRepositoryName(parentName) + "/" + repoName;
                        parentPath = Text.getRelativeParent(parentPath, 1);
                        parent = fs.getFile(parentPath);
                    }
                    if (parent == null) {
                        // no parent found ?
                        return false;
                    }
                    String repoPath = parent.getAggregate().getPath();
                    String repoRelPath = Text.getName(repoPath) + "/" + repoName;
                    if (!repoPath.endsWith("/")) {
                        repoPath += "/";
                    }
                    repoPath += repoName;
                    assertInFilter(repoPath);
                    if (false && change.isa.getSerializationType() == SerializationType.XML_DOCVIEW) {
                        // special case that full coverage is below a intermediate
                        // ignore and wait for next cycle
                    } else {
                        TxInfo txInfo = modified.get(parent.getAggregate().getPath());
                        if (txInfo == null) {
                            txInfo = new TxInfo(parent.getAggregate());
                            modified.put(parent.getAggregate().getPath(), txInfo);
                        }
                        txInfo.out.getArtifacts().add(new InputSourceArtifact(null,
                                repoRelPath, change.isa.getExtension(),
                                ArtifactType.FILE,
                                change.isa.getInputSource(), change.isa.getSerializationType()
                        ));
                    }
                } else {
                    String repoPath = parent.getAggregate().getPath();

                    if (!repoPath.endsWith("/")) {
                        repoPath += "/";
                    }
                    repoPath += repoName;
                    assertInFilter(repoPath);
                    TxInfo txInfo = modified.get(repoPath);
                    if (txInfo == null) {
                        txInfo = new TxInfo(repoPath, ((AggregateImpl) parent.getAggregate()).create(repoName));
                        txInfo.setParentFile(parent);
                        modified.put(repoPath, txInfo);
                    }
                    txInfo.out.getArtifacts().add(change.isa);

                }
                if (verbose) {
                    log.info("...scheduling ADD {}/{}", parent.getPath(), repoName);
                }
            } break;
            case MKDIR:{
                // get parent file
                String parentPath = Text.getRelativeParent(change.filePath, 1);
                String name = Text.getName(change.filePath);
                VaultFile parent = fs.getFile(parentPath);
                if (parent == null || parent.isTransient()) {
                    return false;
                }
                String repoName = PlatformNameFormat.getRepositoryName(name);
                int idx = repoName.lastIndexOf('.');
                if (idx > 0) {
                    String base = repoName.substring(0, idx);
                    String ext = repoName.substring(idx);
                    if (ext.equals(".dir")) {
                        // assume no directories with .dir extension
                        repoName = base;
                    }
                }
                String repoPath = parent.getAggregate().getPath();
                if (!repoPath.endsWith("/")) {
                    repoPath += "/";
                }
                repoPath += repoName;
                assertInFilter(repoPath);
                TxInfo txInfo = modified.get(repoPath);
                if (txInfo == null) {
                    txInfo = new TxInfo(repoPath, ((AggregateImpl) parent.getAggregate()).create(repoName));
                    txInfo.setParentFile(parent);
                    modified.put(repoPath, txInfo);
                }
                txInfo.out.addArtifact(new DirectoryArtifact(repoName));
                if (verbose) {
                    log.info("...scheduling MKD {}/{}", parent.getPath(), repoName);
                }
            } break;
            case DELETED: {
                Aggregate an = change.file.getAggregate();
                if (an == null) {
                    // intermediate directory
                    // can't handle here
                    assertInFilter(change.repoPath);
                    TxInfo txInfo = new TxInfo(change.repoPath, null);
                    txInfo.original.put(change.file.getPath(), change.file);
                    modified.put(txInfo.artifactsPath, txInfo);
                } else {
                    assertInFilter(an.getPath());
                    TxInfo txInfo = modified.get(an.getPath());
                    if (txInfo == null) {
                        txInfo = new TxInfo(an);
                        VaultFile dir = null;
                        for (VaultFile rel : change.file.getRelated()) {
                            txInfo.original.put(rel.getPath(), rel);
                            if (rel.isDirectory()) {
                                dir = rel;
                            }
                        }
                        modified.put(txInfo.artifactsPath, txInfo);
                        // set parent file
                        if (dir == null) {
                            txInfo.parentFile = change.file.getParent();
                        } else {
                            txInfo.parentFile = dir.getParent();
                        }
                    }
                    txInfo.out.getArtifacts().remove(change.file.getArtifact());
                    if (verbose) {
                        log.info("...scheduling DEL {}", an.getPath());
                    }
                }
            } break;
            case MODIFIED: {
                Aggregate an = change.file.getAggregate();
                TxInfo txInfo = modified.get(an.getPath());
                if (txInfo == null) {
                    txInfo = new TxInfo(an);
                    VaultFile dir = null;
                    for (VaultFile rel: change.file.getRelated()) {
                        txInfo.original.put(rel.getPath(), rel);
                        if (rel.isDirectory()) {
                            dir = rel;
                        }
                    }
                    modified.put(txInfo.artifactsPath, txInfo);
                    // set parent file
                    if (dir == null) {
                        txInfo.parentFile = change.file.getParent();
                    } else {
                        txInfo.parentFile = dir.getParent();
                    }
                }
                InputSourceArtifact isa = new InputSourceArtifact(change.file.getArtifact(), change.input);
                txInfo.out.getArtifacts().put(isa);
                // add sub changes
                if (change.subChanges != null) {
                    for (Change sc: change.subChanges) {
                        // need to adjust relative path of input
                        // repoPath    = /vltTest/foo
                        // sc.repoPath = /vltTest/foo/text/file
                        // relPath     = foo/text/file
                        String relPath = PathUtil.getRelativePath(change.repoPath, sc.repoPath);
                        relPath  = Text.getName(change.repoPath) + "/" + relPath;
                        if (!relPath.equals(sc.isa.getRelativePath())) {
                            // todo: check if correct platform path
                            sc.isa = new InputSourceArtifact(
                                    null,
                                    relPath,
                                    sc.isa.getExtension(),
                                    sc.isa.getType(),
                                    sc.isa.getInputSource(),
                                    sc.isa.getSerializationType()
                            );
                        }
                        txInfo.out.getArtifacts().add(sc.isa);
                    }
                }

                if (verbose) {
                    log.info("...scheduling UPD {}/{}", isa.getRelativePath());
                }
            } break;
            case MOVED:
            case ERROR:
                break;
        }
        return true;
    }

    private void assertInFilter(String repoPath) {
        if (!fs.getWorkspaceFilter().contains(repoPath)) {
            log.warn("{} is excluded by the workspace filter. continuing with unknown results.", repoPath);
        }
    }

    protected static class Change {

        private final Type type;

        private VaultFile file;

        private VaultInputSource input;

        private String filePath;

        private String repoPath;

        private InputSourceArtifact isa;

        private LinkedList<Change> subChanges;

        public Change(Type type, VaultFile file, VaultInputSource input) {
            this.type = type;
            this.file = file;
            this.repoPath = file.getAggregatePath();
            String relPath = file.getRepoRelPath();
            if (relPath != null && relPath.length() > 0) {
                this.repoPath += "/" + relPath;
            }
            this.input = input;
        }

        public Change(Type type, String repoPath, String filePath, VaultInputSource input) {
            this.type = type;
            this.repoPath = repoPath;
            this.input = input;
            this.filePath = filePath;
        }

        public void setInputSource(VaultInputSource input) {
            this.input = input;
        }

        public void setContentType(String contentType) {
            if (isa != null) {
                isa.setContentType(contentType);
            }
        }

        public void add(Change c) {
            if (subChanges == null) {
                subChanges = new LinkedList<Change>();
            }
            subChanges.add(c);
        }
    }

    private static class DotXmlInfo {

        private final Change change;

        private final boolean intermediate;

        private DotXmlInfo(Change change, boolean intermediate) {
            this.change = change;
            this.intermediate = intermediate;
        }
    }
    private static class TxInfo {

        private final AggregateImpl aggregate;

        private final String artifactsPath;

        private final AggregateBuilder out;

        private final Map<String, VaultFile> original = new HashMap<String, VaultFile>();

        private VaultFile parentFile;

        public TxInfo(Aggregate a) throws RepositoryException {
            aggregate = (AggregateImpl) a;
            artifactsPath = a.getPath();
            out = aggregate.getBuilder();
        }

        public TxInfo(String artifactsPath, AggregateBuilder out) {
            aggregate = null;
            this.artifactsPath = artifactsPath;
            this.out = out;
        }

        public void setParentFile(VaultFile parentFile) {
            this.parentFile = parentFile;
        }
    }

}
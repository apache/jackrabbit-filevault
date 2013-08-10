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
package org.apache.jackrabbit.vault.vlt;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.jcr.RepositoryException;

import org.apache.jackrabbit.vault.fs.api.VaultFile;
import org.apache.jackrabbit.vault.fs.api.VaultFileOutput;
import org.apache.jackrabbit.vault.fs.api.VaultFileSystem;
import org.apache.jackrabbit.vault.fs.api.VaultFsTransaction;
import org.apache.jackrabbit.vault.util.Constants;
import org.apache.jackrabbit.vault.util.FileInputSource;
import org.apache.jackrabbit.vault.util.LineOutputStream;
import org.apache.jackrabbit.vault.vlt.actions.Action;
import org.apache.jackrabbit.vault.vlt.meta.MetaDirectory;
import org.apache.jackrabbit.vault.vlt.meta.VltEntries;
import org.apache.jackrabbit.vault.vlt.meta.VltEntry;

/**
 * <code>VltDirectory</code>...
 *
 */
public class VltDirectory {

    public static final String META_DIR_NAME = ".vlt";

    private final VltContext ctx;

    private File dir;

    private MetaDirectory metaDir;

    //private final File entriesFile;

    private VltEntries entries;

    private FileList files;

    public VltDirectory(VltContext ctx, File directory) throws VltException {
        this.ctx = ctx;
        this.dir = directory;
        if (!dir.exists()) {
            throw ctx.error(dir.getPath(), "no such file or directory.");
        }
        if (!dir.isDirectory()) {
            throw ctx.error(dir.getPath(), "not a directory.");
        }
        if (dir.getName().equals(META_DIR_NAME)) {
            throw ctx.error(dir.getPath(), "meta directory not controllable.");
        }
        metaDir = VltContext.createMetaDirectory(new File(dir, META_DIR_NAME));
        //entriesFile = new File(metaDir, ENTRIES_FILE_NAME);

        init();
    }

    public VltDirectory getParent() throws VltException {
        return new VltDirectory(ctx, dir.getParentFile());
    }

    public String getAggregatePath() throws VltException {
        VltDirectory parent = getParent();
        VltEntries es = parent.getEntries();
        if (es != null) {
            VltEntry e = es.getEntry(dir.getName());
            if (e != null) {
                return e.getAggregatePath();
            }
        } else {
            // fallback if the parent is not controllable. check the
            // aggregate path of the .content.xml
            VltEntry e = entries == null ? null : entries.getEntry(Constants.DOT_CONTENT_XML);
            if (e != null) {
                return e.getAggregatePath();
            }
        }
        return null;
    }

    public VltContext getContext() {
        return ctx;
    }

    public boolean isControlled() {
        return entries != null;
    }

    public void assertControlled() throws VltException {
        if (entries == null) {
            throw ctx.exception(dir.getPath(), "Directory is not under vault control.", null);
        }
    }

    private void init() throws VltException {
        entries = metaDir.getEntries();
        if (entries != null) {
            files = new FileList(this, entries);
        }
    }

    public void close() {
        if (dir != null) {
            dir = null;
        }
        if (metaDir != null) {
            try {
                metaDir.close();
            } catch (IOException e) {
                // ignore
            }
            metaDir = null;
        }
    }
    
    public void control(String path, String aPath) throws VltException {
        if (!metaDir.exists()) {
            try {
                metaDir.create(path);
            } catch (IOException e) {
                throw ctx.exception(path, "Error while creating.", e);
            }
        }
        entries = metaDir.getEntries();
        if (entries == null) {
            throw ctx.error(metaDir.getFile().getPath(), "No entries found.");
        }
        try {
            metaDir.sync();
        } catch (IOException e) {
            throw ctx.exception(path, "Error while saving.", e);
        }
        files = new FileList(this, entries);
    }

    public void uncontrol()
            throws VltException {
        if (metaDir.exists()) {
            try {
                metaDir.delete();
            } catch (IOException e) {
                throw ctx.exception(getPath(), "Error while deleting meta directory", e);
            }
        }
        entries = null;
        files = null;
    }

    public MetaDirectory getMetaDirectory() {
        return metaDir;
    }

    public File getDirectory() {
        return dir;
    }

    public String getPath() {
        return dir.getPath();
    }

    public VltEntries getEntries() {
        return entries;
    }

    public VaultFile getRemoteDirectory(VltContext ctx) throws VltException {
        assertControlled();
        try {
            VaultFileSystem fs = ctx.getFileSystem(ctx.getMountpoint());
            return fs.getFile(entries.getPath());
        } catch (IOException e) {
            throw new VltException("Unable to get remote directory.", e);
        } catch (RepositoryException e) {
            throw new VltException("Unable to get remote directory.", e);
        }
    }

    public void prepareCommit(VaultFsTransaction tx, Collection<String> names,
                              boolean nonRecursive, boolean force)
            throws VltException {
        assertControlled();
        VaultFile remoteDir = getRemoteDirectory(ctx);
        if (remoteDir == null) {
            throw ctx.error(getPath(), "Remote directory does not exist.");
        }
        if (names.isEmpty()) {
            // add all files in this directory
            for (VltFile file : getFiles()) {
                prepareCommit(tx, remoteDir, file, nonRecursive, force);
            }
        } else {
            for (String name: names) {
                VltFile file = files.getFile(name);
                if (file == null) {
                    throw ctx.error(name, "no such file or directory.");
                }
                prepareCommit(tx, remoteDir, file, nonRecursive, force);
            }
        }
        saveEntries();
    }

    public void updateComitted(String path, String fileName) throws VltException {
        assertControlled();
        VltFile file = files().getFile(fileName);
        VaultFile remote;
        try {
            VaultFile rd = getRemoteDirectory(ctx);
            remote = rd == null ? null : rd.getChild(fileName);
        } catch (RepositoryException e) {
            throw ctx.exception(dir.getPath(), "Error while retrieving remote directory.", e);
        }
        if (file == null && remote == null) {
            // removed and file gone
            ctx.printAction(getPath() + Constants.FS_NATIVE + fileName, FileAction.DELETED, null);
        } else if (file == null) {
            // added
            update(remote, fileName);
        } else {
            FileAction a = file.commit(remote);
            if (a != FileAction.VOID) {
                entries.update(file);
                saveEntries();
                ctx.printAction(file, a);
            }
        }
    }

    private void update(VaultFile remote, String name)
            throws VltException {
        VltFile file = new VltFile(this, name, null);
        files.addFile(file);

        FileAction action = file.update(remote, false);

        // write back entries
        entries.update(file);
        saveEntries();
        ctx.printAction(file, action);
        sync();
    }

    private void prepareCommit(VaultFsTransaction tx, VaultFile remoteDir,
                               VltFile file, boolean nonRecursive, boolean force)
            throws VltException {
        VaultFile remoteFile;
        try {
            remoteFile = remoteDir == null
                    ? null
                    : remoteDir.getChild(file.getName());
        } catch (RepositoryException e) {
            throw ctx.exception(file.getPath(), "Error while retrieving status", e);
        }

        if (file.status(remoteFile) != FileAction.VOID && !force) {
            throw ctx.error(file.getPath(), "Some files need to be updated first." +
                    " Specify --force to overwrite remote files.");
        }
        try {
            switch (file.getStatus()) {
                case MODIFIED:
                    FileInputSource fis = new FileInputSource(file.getFile());
                    if (file.isBinary()) {
                        fis.setLineSeparator(LineOutputStream.LS_BINARY);
                    }
                    tx.modify(remoteFile, fis);
                    ctx.printMessage("sending....", file);
                    break;
                case DELETED:
                    tx.delete(remoteFile);
                    ctx.printMessage("deleting...", file);
                    break;
                case ADDED:
                    String path = this.getEntries().getPath();
                    if (path.endsWith("/")) {
                        path += file.getName();
                    } else {
                        path += "/" + file.getName();
                    }
                    if (file.canDescend()) {
                        tx.mkdir(path);
                    } else {
                        fis = new FileInputSource(file.getFile());
                        if (file.isBinary()) {
                            fis.setLineSeparator(LineOutputStream.LS_BINARY);
                        }
                        VaultFileOutput out = tx.add(path, fis);
                        // set the content type hint
                        out.setContentType(file.getContentType());
                    }
                    ctx.printMessage("adding.....", file);
                    break;
                default:
                    // ignore
            }
        } catch (IOException e) {
            ctx.exception(file.getPath(), "Error while preparing commit.", e);
        } catch (RepositoryException e) {
            ctx.exception(file.getPath(), "Error while preparing commit.", e);
        }

        if (file.canDescend() && !nonRecursive) {
            VltDirectory dir = file.descend();
            if (dir.isControlled()) {
                // add all files in this directory
                VaultFile remDir = dir.getRemoteDirectory(ctx);
                for (VltFile child: dir.getFiles()) {
                    dir.prepareCommit(tx, remDir, child, nonRecursive, force);
                }
                dir.saveEntries();
            }
            dir.close();
        }
    }

    public Collection<VltFile> getFiles() {
        if (files == null) {
            return Collections.emptySet();
        } else {
            return files.getFiles();
        }
    }

    public void apply(Action action, String name, boolean nonRecursive)
            throws VltException {
        apply(action, Arrays.asList(name), nonRecursive);
    }

    public void apply(Action action, Collection<String> names,
                      boolean nonRecursive) throws VltException {
        if (!action.run(this, null)) {
            return;
        }
        if (names.isEmpty()) {
            // add all files in this directory
            for (VltFile file : getFiles()) {
                apply(action, file, nonRecursive);
            }
        } else {
            for (String name: names) {
                // special check for jcr_root
                VltFile file;
                if (ctx.getExportRoot().getJcrRoot().getParentFile().equals(dir)) {
                    file = new VltFile(this, name, null);
                } else {
                    assertControlled();
                    file = files.getFile(name);
                }
                if (file == null) {
                    throw ctx.error(name, "no such file or directory.");
                }
                apply(action, file, nonRecursive);
            }
        }
    }

    private void apply(Action action, VltFile file, boolean nonRecursive)
            throws VltException {
        action.run(this, file, null);
        if (entries != null) {
            entries.update(file);
            sync();
        }
        if (file.canDescend() && !nonRecursive) {
            VltDirectory dir = file.descend();
            dir.apply(action, Collections.<String>emptyList(), nonRecursive);
            dir.close();
        }
    }


    public void sync() throws VltException {
        saveEntries();
        // reload files (todo: make better)
        files = new FileList(this, entries);
    }

    public void applyWithRemote(Action action, Collection<String> names, boolean nonRecursive)
            throws VltException {
        applyWithRemote(action, getRemoteDirectory(ctx), names, nonRecursive);
    }

    public void applyWithRemote(Action action, String name, boolean nonRecursive)
            throws VltException {
        applyWithRemote(action, getRemoteDirectory(ctx), Arrays.asList(name), nonRecursive);
    }

    public void applyWithRemote(Action action, VaultFile remoteDir, Collection<String> names,
                                boolean nonRecursive)
            throws VltException {
        if (!action.run(this, remoteDir)) {
            return;
        }
        if (names.isEmpty()) {
            // get the status of remote files
            Set<String> processed = new HashSet<String>();
            if (remoteDir != null && files != null) {
                Collection<? extends VaultFile> remoteFiles;
                try {
                    remoteFiles = remoteDir.getChildren();
                } catch (RepositoryException e) {
                    throw new VltException("Error while retrieving file.", e);
                }
                for (VaultFile remoteFile : remoteFiles) {
                    String name = remoteFile.getName();
                    processed.add(name);
                    applyWithRemote(action, files.getFile(name), remoteFile, nonRecursive);
                }
            }
            // second go over all local ones
            for (VltFile file: getFiles()) {
                if (!processed.contains(file.getName())) {
                    applyWithRemote(action, file, null, nonRecursive);
                }
            }
        } else {
            try {
                for (String name: names) {
                    VltFile file = files.getFile(name);
                    VaultFile remoteFile = remoteDir.getChild(name);
                    applyWithRemote(action, file, remoteFile, nonRecursive);
                }
            } catch (RepositoryException e) {
                throw new VltException("Error while retrieving file.", e);
            }
        }
    }

    public void applyWithRemote(Action action, VltFile file, VaultFile remoteFile,
                                boolean nonRecursive)
            throws VltException {
        // if remote file is missing, do depth first
        if (remoteFile == null && file != null && file.canDescend() && !nonRecursive) {
            VltDirectory dir = file.descend();
            dir.applyWithRemote(action, remoteFile, Collections.<String>emptyList(), nonRecursive);
            dir.close();
        }

        // run on 'this' file
        action.run(this, file, remoteFile);
        saveEntries();

        if (remoteFile != null) {
            // refetch file
            if (file == null) {
                file = files.getFile(remoteFile.getName());
            }

            // check again deep
            if (file != null && file.canDescend() && !nonRecursive) {
                VltDirectory dir = file.descend();
                dir.applyWithRemote(action, remoteFile, Collections.<String>emptyList(), nonRecursive);
                dir.close();
            }
        }
    }

    private void saveEntries() throws VltException {
        try {
            metaDir.sync();
        } catch (IOException e) {
            throw ctx.error(getPath(), "Error while saving entries: " + e);
        }
    }

    public FileList files() throws VltException {
        assertControlled();
        return files;
    }
}
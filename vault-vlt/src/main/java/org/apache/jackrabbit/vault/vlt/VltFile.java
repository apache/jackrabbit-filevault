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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.Reader;
import java.io.Writer;
import java.util.Properties;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.jackrabbit.vault.fs.VaultFileCopy;
import org.apache.jackrabbit.vault.fs.api.VaultFile;
import org.apache.jackrabbit.vault.util.Constants;
import org.apache.jackrabbit.vault.util.LineOutputStream;
import org.apache.jackrabbit.vault.util.MD5;
import org.apache.jackrabbit.vault.util.MimeTypes;
import org.apache.jackrabbit.vault.util.PathUtil;
import org.apache.jackrabbit.vault.util.diff.DiffWriter;
import org.apache.jackrabbit.vault.util.diff.Document;
import org.apache.jackrabbit.vault.util.diff.DocumentDiff;
import org.apache.jackrabbit.vault.util.diff.DocumentDiff3;
import org.apache.jackrabbit.vault.util.diff.DocumentSource;
import org.apache.jackrabbit.vault.util.diff.FileDocumentSource;
import org.apache.jackrabbit.vault.util.diff.Hunk3;
import org.apache.jackrabbit.vault.util.diff.LineElementsFactory;
import org.apache.jackrabbit.vault.vlt.meta.MetaFile;
import org.apache.jackrabbit.vault.vlt.meta.MetaFileDocSource;
import org.apache.jackrabbit.vault.vlt.meta.VltEntry;
import org.apache.jackrabbit.vault.vlt.meta.VltEntryInfo;

/**
 * <code>VltFile</code>...
 *
 */
public class VltFile implements DocumentSource {

    public static final String PROP_CONTENT_TYPE = "vlt:mime-type";

    /**
     * Possible state of this file
     */
    public enum State {
        CLEAN (" "),
        ADDED ("A"),
        CONFLICTED ("C"),
        DELETED ("D"),
        IGNORED ("I"),
        MODIFIED ("M"),
        REPLACED ("R"),
        UNKNOWN("?"),
        MISSING ("!"),
        OBSTRUCTED ("~"),
        VOID (" ");

        public final String letter;

        private State(String letter) {
            this.letter = letter;
        }

        public String toString() {
            return name().toLowerCase() + " (" + letter + ")";
        }
    }

    private final VltDirectory parent;

    private final File file;

    private final String name;

    private VltEntry entry;

    public VltFile(VltDirectory parent, String name, VltEntry entry)
            throws VltException {
        this.parent = parent;
        this.name = name;
        this.entry = entry;
        this.file = new File(parent.getDirectory(), name);
    }

    public Properties getProperties() throws VltException {
        Properties props = new Properties();
        if (entry != null) {
            VltEntryInfo info = entry.work();
            String ct = info.getContentType();
            if (ct != null) {
                props.put(PROP_CONTENT_TYPE, ct);
            }
        }
        return props;
    }

    public String getProperty(String name) throws VltException {
        if (entry != null) {
            VltEntryInfo info = entry.work();
            if (name.equals(PROP_CONTENT_TYPE)) {
                return info.getContentType();
            }
        }
        return null;
    }

    public void setProperty(String name, String value) throws VltException {
        if (entry == null) {
            throw error("Can't set property to non controlled file.");
        }
        VltEntryInfo info = entry.work();
        if (info == null) {
            throw error("Can't set property to non controlled file.");
        }
        if (name.equals(PROP_CONTENT_TYPE)) {
            if (!file.isDirectory()) {
                // silently ignore directories
                info.setContentType(value);
                parent.getContext().printMessage(this, name + "=" + value);
            }
        } else {
            throw error("Generic properies not supported, yet");
        }
    }
    
    public State getStatus() throws VltException {
        State state = State.VOID;
        if (entry == null) {
            if (file.exists()) {
                // special check for jcr_root
                if (file.equals(parent.getContext().getExportRoot().getJcrRoot())) {
                    state = State.CLEAN;
                } else {
                    state = State.UNKNOWN;
                }
            } else {
                state = State.VOID;
            }
        } else {
            switch (entry.getState()) {
                case CLEAN:
                    if (file.exists()) {
                        if (file.isDirectory()) {
                            VltDirectory dir = descend();
                            if (dir.isControlled()) {
                                state = State.CLEAN;
                            } else {
                                state = State.OBSTRUCTED;
                            }
                        } else {
                            VltEntryInfo work = entry.work();
                            VltEntryInfo base = entry.base();
                            assert work != null;
                            assert base != null;

                            try {
                                work.update(file, false);
                            } catch (IOException e) {
                                throw exception("Error while calculating status.", e);
                            }
                            state = work.isSame(base) ? State.CLEAN : State.MODIFIED;
                        }
                    } else {
                        state = State.MISSING;
                    }
                    break;
                case ADDED:
                    if (file.exists()) {
                        state = State.ADDED;
                    } else {
                        state = State.MISSING;
                    }
                    break;
                case CONFLICT:
                    state = State.CONFLICTED;
                    break;
                case DELETED:
                    state = State.DELETED;
                    break;
            }
        }
        return state;
    }

    public String getName() {
        return name;
    }

    public File getFile() {
        return file;
    }

    public String getPath() {
        return file.getPath();
    }

    public MetaFile getBaseFile(boolean create) throws VltException {
        try {
            return parent.getMetaDirectory().getBaseFile(name, create);
        } catch (IOException e) {
            throw new VltException(getPath(), "Error opening base file.", e);
        }
    }

    public String getContentType() {
        if (entry != null && !file.isDirectory()) {
            VltEntryInfo work = entry.work();
            if (work != null) {
                return work.getContentType();
            }
        }
        return null;
    }

    /**
     * Checks if this file has binary content. It does not actually read the
     * file data but calls {@link MimeTypes#isBinary(String)} with the content
     * type of the work file.
     * @return <code>true</code> if this is binary
     */
    public boolean isBinary() {
        return MimeTypes.isBinary(getContentType());
    }


    public MetaFile getTmpFile() throws VltException {
        try {
            return parent.getMetaDirectory().getTmpFile(name, true);
        } catch (IOException e) {
            throw new VltException(getPath(), "Error opening tmp file.", e);
        }
    }

    public boolean canDescend() {
        return file.isDirectory();
    }

    public VltDirectory descend() throws VltException {
        if (!canDescend()) {
            throw new VltException("Cannot descend into non directory.");
        }
        return new VltDirectory(parent.getContext(), file);
    }

    public VltEntry getEntry() {
        return entry;
    }

    public void diff() throws VltException {
        State state = getStatus();
        if (entry == null || entry.isDirectory()) {
            return;
        }
        VltEntryInfo work = entry.work();
        VltEntryInfo base = entry.base();
        if (work == null || base == null) {
            return;
        }
        switch (state) {
            case ADDED:
            case CONFLICTED:
            case DELETED:
            case MODIFIED:
                break;
            case IGNORED:
            case MISSING:
            case OBSTRUCTED:
            case REPLACED:
            case UNKNOWN:
            case VOID:
            case CLEAN:
                return;
        }
        if (MimeTypes.isBinary(work.getContentType()) || MimeTypes.isBinary(base.getContentType())) {
            PrintStream s = parent.getContext().getStdout();
            s.printf("Index: %s%n", getName());
            s.println("===================================================================");
            s.println("Cannot display: file marked as binary type.");
            s.printf("vlt:mime-type = %s%n", work.getContentType());
            s.flush();
            return;
        }
        try {
            // do the actual diff
            PrintStream s = parent.getContext().getStdout();
            DiffWriter out = new DiffWriter(new OutputStreamWriter(s, Constants.ENCODING));
            out.write("Index: ");
            out.write(getName());
            out.writeNewLine();
            out.write("===================================================================");
            out.writeNewLine();

            Reader r0 = getBaseFile(false) == null ? null : getBaseFile(false).getReader();
            Document d0 = new Document(this, LineElementsFactory.create(this, r0, false));
            Reader r1 = file.exists() ? new InputStreamReader(FileUtils.openInputStream(file), Constants.ENCODING) : null;
            Document d1 = new Document(this, LineElementsFactory.create(this, r1, false));

            DocumentDiff diff;
            try {
                diff = d0.diff(d1);
            } finally {
                IOUtils.closeQuietly(r0);
                IOUtils.closeQuietly(r1);
            }
            diff.write(out, 3);
            out.flush();
        } catch (IOException e) {
            throw exception("Error while writing diff.", e);
        }

    }

    public FileAction delete(boolean force) throws VltException {
        State state = getStatus();
        switch (state) {
            case ADDED:
            case CONFLICTED:
            case MODIFIED:
            case REPLACED:
                if (!force) {
                    parent.getContext().printMessage(this, "has local modification. use --force to delete anyway");
                    return FileAction.VOID;
                }
                break;
            case CLEAN:
            case MISSING:
            case DELETED:
                break;
            case IGNORED:
            case OBSTRUCTED:
            case UNKNOWN:
            case VOID:
                if (!force) {
                    parent.getContext().printMessage(this, "is not under version control. use --force to delete anyway");
                    return FileAction.VOID;
                }
                break;
        }
        if (entry != null && entry.delete(file)) {
            entry = null;
        }
        return FileAction.DELETED;
    }

    public FileAction commit(VaultFile remoteFile) throws VltException {
        if (remoteFile == null) {
            return doDelete(false);
        } else {
            return doUpdate(remoteFile, false);
        }
    }

    public boolean revert() throws VltException {
        State state = getStatus();
        switch (state) {
            case ADDED:
                doDelete(true);
                entry = null;
                return true;

            case CONFLICTED:
                resolved(true);
                // no break;
            case DELETED:
            case MISSING:
            case MODIFIED:
                doRevert();
                return true;

            case IGNORED:
            case CLEAN:
            case OBSTRUCTED:
            case REPLACED:
            case UNKNOWN:
            case VOID:
            default:
                return false;
        }
    }

    public boolean resolved(boolean force) throws VltException {
        if (getStatus() != State.CONFLICTED) {
            return false;
        }
        if (!force) {
            // check if the file still contains the diff markers
            boolean mayContainMarker = false;
            try {
                BufferedReader in = new BufferedReader(new FileReader(file));
                String line;
                while ((line = in.readLine()) != null) {
                    if (line.startsWith(Hunk3.MARKER_B[0])
                            || line.startsWith(Hunk3.MARKER_L[0])
                            || line.startsWith(Hunk3.MARKER_R[0])
                            || line.startsWith(Hunk3.MARKER_M[0])) {
                        mayContainMarker = true;
                        break;
                    }
                }
            } catch (IOException e) {
                throw exception("Error while reading file.", e);
            }
            if (mayContainMarker) {
                throw error("File still contains conflict markers. use --force to force resolve.");
            }
        }

        // resolve entry
        try {
            entry.resolved(getTmpFile(), file, getBaseFile(false));
        } catch (IOException e) {
            throw exception("Error while copying files.", e);
        }

        return true;
    }

    public FileAction update(VaultFile remoteFile, boolean force)
            throws VltException {
        State state  = getStatus();
        switch (state) {
            case IGNORED:
            case OBSTRUCTED:
            case REPLACED:
                if (!force || remoteFile == null) {
                    throw error("update not possible. file is " + state.name().toLowerCase() + ". " +
                            "Specify --force to overwrite existing files.");
                }
                return doUpdate(remoteFile, false);

            case ADDED:
                if (remoteFile != null) {
                    if (mergeableWithRemote(remoteFile) != FileAction.VOID) {
                        throw error("Failed to add file: object of the same name already exists.");
                    }
                    return doUpdate(remoteFile, false);
                } else {
                    return FileAction.VOID;
                }

            case CLEAN:
                if (remoteFile == null) {
                    return doDelete(false);
                } else {
                    if (file.isDirectory()) {
                        // do nothing
                        return FileAction.VOID;
                    } else {
                        return doUpdate(remoteFile, false);
                    }
                }

            case CONFLICTED:
                if (remoteFile == null) {
                    try {
                        if (!entry.revertConflict(file)) {
                            return FileAction.CONFLICTED;
                        }
                    } catch (IOException e) {
                        throw exception("Error during update.", e);
                    }
                    // refetch status, and delete file if clean
                    return doDelete(getStatus() != State.CLEAN);
                } else {
                    try {
                        if (!entry.revertConflict(file)) {
                            return doMerge(remoteFile, FileAction.CONFLICTED);
                        } else {
                            return doMerge(remoteFile, FileAction.UPDATED);
                        }
                    } catch (IOException e) {
                        throw exception("Error during update.", e);
                    }
                }

            case DELETED:
                if (remoteFile == null) {
                    // we can delete the entry since someone else deleted it as well
                    return doDelete(false);
                } else {
                    // just update base and entry, in case someone wants to revert
                    return doUpdate(remoteFile, true);
                }

            case MISSING:
                if (remoteFile == null) {
                    // if file is missing, just delete it
                    return doDelete(false);
                } else {
                    // do update
                    entry = null;
                    return doUpdate(remoteFile, false);
                }

            case MODIFIED:
                if (remoteFile == null) {
                    // keep the file
                    return doDelete(true);
                } else {
                    return doMerge(remoteFile, FileAction.VOID);
                }

            case UNKNOWN:
                if (remoteFile == null) {
                    // do nothing
                    return FileAction.VOID;
                } else {
                    // do update
                    if (file.exists() && !force) {
                        throw error("Failed to update: object of the same name already exists." +
                                " Specify --force to overwrite existing files.");
                    }
                    return doUpdate(remoteFile, false);
                }

            case VOID:
                // do update
                return doUpdate(remoteFile, false);

            default:
                throw exception("illegal state: " + state, null);
        }
    }

    public FileAction status(VaultFile remoteFile) throws VltException {
        State state  = getStatus();
        switch (state) {
            case IGNORED:
            case OBSTRUCTED:
            case REPLACED:
                return FileAction.CONFLICTED;

            case ADDED:
                if (remoteFile != null) {
                    return FileAction.CONFLICTED;
                } else {
                    return FileAction.VOID;
                }

            case CLEAN:
                if (remoteFile == null) {
                    return FileAction.DELETED;
                } else {
                    if (file.isDirectory()) {
                        // do nothing
                        return FileAction.VOID;
                    } else {
                        return equalsToRemote(remoteFile)
                                ? FileAction.VOID
                                : FileAction.UPDATED;
                    }
                }

            case CONFLICTED:
                // do not probe further
                return FileAction.CONFLICTED;

            case DELETED:
                return FileAction.VOID;

            case MISSING:
                return FileAction.ADDED;

            case MODIFIED:
                if (remoteFile == null) {
                    return FileAction.DELETED;
                } else {
                    return mergeableWithRemote(remoteFile);
                }

            case UNKNOWN:
                if (remoteFile == null) {
                    // do nothing
                    return FileAction.VOID;
                } else {
                    return FileAction.UPDATED;
                }

            case VOID:
                return FileAction.ADDED;

            default:
                throw exception("illegal state: " + state, null);
        }
    }

    public FileAction add(boolean force) throws VltException {
        State state = getStatus();
        switch (state) {
            case ADDED:
            case CLEAN:
            case CONFLICTED:
            case MISSING:
            case MODIFIED:
            case OBSTRUCTED:
            case REPLACED:
                parent.getContext().printMessage(this, "is already under version control");
                break;
            case DELETED:
                parent.getContext().printMessage(this, "replace not supported yet");
                break;
            case IGNORED:
                parent.getContext().printMessage(this, "failed to add. is ignored.");
                break;
            case UNKNOWN:
            case VOID:
                return doAdd(force);
        }
        return FileAction.VOID;
    }

    private FileAction doAdd(boolean force) throws VltException {
        assert entry == null;
        entry = parent.getEntries().update(getName(), null, null);
        VltEntryInfo work = entry.create(VltEntryInfo.Type.WORK);
        try {
            work.update(file, true);
        } catch (IOException e) {
            throw exception("Error while adding file", e);
        }
        String contentType = MimeTypes.getMimeType(file.getName(), MimeTypes.APPLICATION_OCTET_STREAM);
        work.setContentType(contentType);
        entry.put(work);
        return FileAction.ADDED;
    }

    private FileAction doDelete(boolean keepFile)
            throws VltException {
        // small hack to remove meta directory. should actually be somewhere else
        if (file.isDirectory()) {
            VltDirectory dir = new VltDirectory(parent.getContext(), file);
            dir.uncontrol();
        } else {
            try {
                if (getBaseFile(false) != null) {
                    getBaseFile(false).delete();
                }
            } catch (IOException e) {
                throw new VltException(getPath(), "Error while deleting base file.", e);
            }
        }
        if (!keepFile) {
            file.delete();
        }
        entry = null;
        return FileAction.DELETED;
    }

    private FileAction doMerge(VaultFile remoteFile, FileAction action)
            throws VltException {
        if (remoteFile.isDirectory()) {
            throw exception("Error while merging. remote is a directory.", null);
        }
        // abort merger if actions is already conflict
        if (action == FileAction.CONFLICTED) {
            return action;
        }
        MetaFile baseFile = getBaseFile(false);
        MetaFile tmpFile = getTmpFile();

        VltEntryInfo base = entry.base();
        VltEntryInfo work = entry.work();
        byte[] lineFeed = MimeTypes.isBinary(remoteFile.getContentType())
                ? null
                : LineOutputStream.LS_NATIVE;

        // get the remote file
        VaultFileCopy copy = null;
        boolean remoteUpdated = true;
        try {
            // first check size and last modified
            if (!base.checkModified(remoteFile)) {
                remoteUpdated = false;
            } else {
                File temp = tmpFile.openTempFile();
                copy = VaultFileCopy.copy(remoteFile, temp, lineFeed);
                // if tmp is equal to the base one, there was no update on the server
                if (copy.getMd5().equals(base.getMd5())) {
                    tmpFile.closeTempFile(tmpFile.length() >= 0);
                    remoteUpdated = false;
                } else {
                    tmpFile.closeTempFile(false);
                }
            }
        } catch (IOException e) {
            throw exception("Error while copying files.", e);
        }
        if (!remoteUpdated) {
            if (work.getMd5().equals(base.getMd5())) {
                // fix base
                base.setSize(work.getSize());
                base.setDate(work.getDate());
                return FileAction.VOID;
            } else if (remoteFile.lastModified() > 0) {
                // normal modification provided
                return action;
            }
        }

        try {
            // check if binary
            boolean remoteBT = getRemoteBinaryType(remoteFile, copy);
            boolean localBT = MimeTypes.isBinary(base.getContentType());
            if (remoteBT || localBT) {
                parent.getContext().printMessage(this, "can't merge. binary content");
                entry.conflict(file, baseFile, tmpFile);
                return FileAction.CONFLICTED;
            }

            // do a 3-way diff between the base, the local and the remote one.
            // we currently do not use document sources, since we don't really have
            // a label to provide (like rev. num, etc).
            Reader r0 = baseFile.getReader();
            Reader r1 = tmpFile.getReader();
            Document baseDoc = new Document(null, LineElementsFactory.create(new MetaFileDocSource(baseFile), r0, false));
            Document leftDoc = new Document(null, LineElementsFactory.create(new FileDocumentSource(file), false, Constants.ENCODING));
            Document rightDoc = new Document(null, LineElementsFactory.create(new MetaFileDocSource(tmpFile), r1, false));

            DocumentDiff3 diff;
            try {
                diff = baseDoc.diff3(leftDoc, rightDoc);
            } finally {
                IOUtils.closeQuietly(r0);
                IOUtils.closeQuietly(r1);
            }

            // save the diff output
            Writer out = new OutputStreamWriter(FileUtils.openOutputStream(file), Constants.ENCODING);
            try {
                diff.write(new DiffWriter(out), false);
            } catch (IOException e) {
                IOUtils.closeQuietly(out);
            }

            if (diff.hasConflicts()) {
                entry.conflict(file, baseFile, tmpFile);
                action = FileAction.CONFLICTED;
            } else {
                // make the tmp file the new base
                tmpFile.moveTo(baseFile);
                base.update(baseFile, true);
                action = FileAction.MERGED;
            }

            // and update the 'work'
            // check if MD5 changes and change action accordingly
            MD5 oldMd5 = work.getMd5();
            work.update(file, true);
            if (oldMd5.equals(work.getMd5())) {
                action = FileAction.VOID;
            }
            // check if remote file provided a last modified
            if (remoteFile.lastModified() == 0) {
                if (work.getMd5().equals(base.getMd5())) {
                    base.setDate(work.getDate());
                } else {
                    base.setDate(System.currentTimeMillis());
                }
            }

        } catch (IOException e) {
            throw exception("Error during merge operation.", e);
        }

        return action;
    }

    private boolean getRemoteBinaryType(VaultFile remoteFile, VaultFileCopy copy) {
        // check if binary
        boolean remoteBT = MimeTypes.isBinary(remoteFile.getContentType());
        if (copy != null && remoteBT != copy.isBinary()) {
            parent.getContext().printMessage(this, "Remote Binary type differs from actual data. Content Type: " + remoteFile.getContentType() + " Data is binary: " + copy.isBinary() + ". Using data type.");
            remoteBT = copy.isBinary();                
        }
        return remoteBT;
    }

    private FileAction mergeableWithRemote(VaultFile remoteFile)
            throws VltException {
        if (remoteFile.isDirectory() != file.isDirectory()) {
            return FileAction.CONFLICTED;
        }
        if (file.isDirectory()) {
            return FileAction.VOID;
        }

        MetaFile tmpFile = getTmpFile();

        VltEntryInfo base = entry.base();

        // get the remote file
        byte[] lineFeed = MimeTypes.isBinary(remoteFile.getContentType())
                ? null
                : LineOutputStream.LS_NATIVE;
        VaultFileCopy copy;
        try {
            File temp = tmpFile.openTempFile();
            copy = VaultFileCopy.copy(remoteFile, temp, lineFeed);
            if (base == null) {
                tmpFile.closeTempFile(true);
                // if base is null, file was only added so check the work entry
                VltEntryInfo work = entry.work();
                if (copy.getMd5().equals(work.getMd5())) {
                    return FileAction.VOID;
                } else {
                    return FileAction.CONFLICTED;
                }
            }
            
            // if tmp is equal to the base one, there was not update on the server
            if (copy.getMd5().equals(base.getMd5())) {
                tmpFile.closeTempFile(true);
                return FileAction.VOID;
            }
            // keep tmp file
            tmpFile.closeTempFile(false);

        } catch (IOException e) {
            throw exception("Error while copying files.", e);
        }

        // check if binary
        boolean remoteBT = getRemoteBinaryType(remoteFile, copy);
        if (remoteBT || MimeTypes.isBinary(base.getContentType())) {
            return FileAction.CONFLICTED;
        }

        try {
            // do a 3-way diff between the base, the local and the remote one.
            // we currently do not use document sources, since we don't really have
            // a label to provide (like rev. num, etc).

            MetaFile baseFile = getBaseFile(false);
            Reader r0 = baseFile.getReader();
            Reader r1 = tmpFile.getReader();
            Document baseDoc = new Document(null, LineElementsFactory.create(new MetaFileDocSource(baseFile), r0, false));
            Document leftDoc = new Document(null, LineElementsFactory.create(new FileDocumentSource(file), false, Constants.ENCODING));
            Document rightDoc = new Document(null, LineElementsFactory.create(new MetaFileDocSource(tmpFile), r1, false));

            DocumentDiff3 diff;
            try {
                diff = baseDoc.diff3(leftDoc, rightDoc);
            } finally {
                IOUtils.closeQuietly(r0);
                IOUtils.closeQuietly(r1);
            }

            if (diff.hasConflicts()) {
                return FileAction.CONFLICTED;
            } else {
                return FileAction.MERGED;
            }
        } catch (IOException e) {
            throw exception("Error during merge operation.", e);
        }
    }

    private void doRevert() throws VltException {
        if (entry.isDirectory()) {
            file.mkdir();
        } else {
            try {
                getBaseFile(false).copyTo(getFile(), true);
            } catch (IOException e) {
                throw exception("Error while copying files.", e);
            }
        }
        VltEntryInfo base = entry.base();
        entry.put(base.copyAs(VltEntryInfo.Type.WORK));
    }

    private boolean equalsToRemote(VaultFile remoteFile)
            throws VltException {
        MetaFile tmpFile = getTmpFile();

        // copy file
        byte[] lineFeed = MimeTypes.isBinary(remoteFile.getContentType())
                ? null
                : LineOutputStream.LS_NATIVE;
        VaultFileCopy copy;
        File temp = null;
        try {
            temp = tmpFile.openTempFile();
            copy = VaultFileCopy.copy(remoteFile, temp, lineFeed);
        } catch (IOException e) {
            throw exception("Error while copying files.", e);
        } finally {
            if (tmpFile != null) {
                try {
                    tmpFile.closeTempFile(true);
                } catch (IOException e) {
                    // ignore
                }
            }
        }
        // if md5 is equal, no update
        VltEntryInfo base = entry.base();
        return copy.getMd5().equals(base.getMd5());
    }

    private FileAction doUpdate(VaultFile remoteFile, boolean baseOnly)
            throws VltException {
        FileAction action;
        VltEntryInfo base;
        if (entry == null || entry.base() == null) {
            // new entry
            action = FileAction.ADDED;
            entry = parent.getEntries().update(getName(), remoteFile.getAggregatePath(), remoteFile.getRepoRelPath());
            base = entry.create(VltEntryInfo.Type.BASE);
            entry.put(base);
        } else {
            action = FileAction.UPDATED;
            base = entry.base();

            // quick check if modified
            if (!base.checkModified(remoteFile)) {
                return FileAction.VOID;
            }
        }
        long lastMod = remoteFile.lastModified();
        if (lastMod == 0) {
            lastMod = System.currentTimeMillis();
        }
        base.setDate(lastMod);

        if (remoteFile.isDirectory()) {
            if (!baseOnly) {
                // ensure controlled
                // todo: this does not belong here
                if (entry.work() != null) {
                    action = FileAction.VOID;
                } else {
                    entry.put(base.copyAs(VltEntryInfo.Type.WORK));
                }
                file.mkdir();
                file.setLastModified(base.getDate());
                VltDirectory dir = new VltDirectory(parent.getContext(), file);
                if (!dir.isControlled()) {
                    dir.control(remoteFile.getPath(), remoteFile.getControllingAggregate().getPath());
                    action = FileAction.ADDED;
                }
            }
        } else {
            MetaFile baseFile = getBaseFile(true);

            // copy file
            byte[] lineFeed = MimeTypes.isBinary(remoteFile.getContentType())
                    ? null
                    : LineOutputStream.LS_NATIVE;
            VaultFileCopy copy;
            try {
                File temp = baseFile.openTempFile();
                copy = VaultFileCopy.copy(remoteFile, temp, lineFeed);
                baseFile.closeTempFile(false);
            } catch (IOException e) {
                throw exception("Error while copying files.", e);
            }
            // if md5 is equal, no update
            if (copy.getMd5().equals(base.getMd5())) {
                action = FileAction.VOID;
            }

            if (action == FileAction.VOID
                    && (base.getContentType() != null || remoteFile.getContentType() != null)
                    && (base.getContentType() == null || !base.getContentType().equals(remoteFile.getContentType()))) {
                action = FileAction.UPDATED;
            }

            // update infos
            VltEntryInfo work = entry.work();
            base.setContentType(remoteFile.getContentType());
            base.setSize(copy.getLength());
            base.setMd5(copy.getMd5());
            if (!baseOnly) {
                // only copy if not equal
                if (work == null || !work.getMd5().equals(copy.getMd5()) || !getFile().exists()) {
                    try {
                        baseFile.copyTo(getFile(), true);
                        entry.put(base.copyAs(VltEntryInfo.Type.WORK));
                    } catch (IOException e) {
                        throw exception("Error while copying files.", e);
                    }
                }
            }
        }
        return action;
    }

    private VltException exception(String msg, Throwable cause) {
        return parent.getContext().exception(getPath(), msg, cause);
    }

    private VltException error(String msg) {
        return parent.getContext().error(getPath(), msg);
    }

    //-----------------------------------------------------< DocumentSource >---

    public String getLabel() {
        return getName();
    }

    public String getLocation() {
        File cwd = parent.getContext().getCwd();
        return PathUtil.getRelativeFilePath(cwd.getPath(), file.getPath());
    }
}
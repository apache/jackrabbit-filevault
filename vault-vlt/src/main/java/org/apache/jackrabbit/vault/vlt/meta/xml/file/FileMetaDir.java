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

package org.apache.jackrabbit.vault.vlt.meta.xml.file;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.jackrabbit.vault.util.Constants;
import org.apache.jackrabbit.vault.util.FileInputSource;
import org.apache.jackrabbit.vault.vlt.VltException;
import org.apache.jackrabbit.vault.vlt.meta.MetaDirectory;
import org.apache.jackrabbit.vault.vlt.meta.MetaFile;
import org.apache.jackrabbit.vault.vlt.meta.VltEntries;
import org.apache.jackrabbit.vault.vlt.meta.xml.XmlEntries;

/**
 * <code>FileMetaDir</code>...
 */
public class FileMetaDir implements MetaDirectory {

    public static final String ADDRESS_FILE_NAME = "repository.url";

    public static final String BASE_DIR_NAME = "base";

    public static final String TMP_DIR_NAME = "tmp";

    public static final String ENTRIES_FILE_NAME = "entries.xml";

    private final File dir;

    private final File tmpDir;

    private final File baseDir;

    private final File entriesFile;

    private XmlEntries entries;

    public FileMetaDir(File dir) {
        this.dir = dir;
        this.tmpDir = new File(dir, TMP_DIR_NAME);
        this.baseDir = new File(dir, BASE_DIR_NAME);
        this.entriesFile = new File(dir, ENTRIES_FILE_NAME);
    }

    public boolean exists() {
        return dir.exists();
    }

    public void create(String path) throws IOException {
        if (!dir.exists()) {
            dir.mkdirs();
            entries = new XmlEntries(path, true);
            sync();
        }
    }

    public void delete() throws IOException {
        FileUtils.deleteDirectory(dir);
    }

    public File getFile() {
        return dir;
    }


    public String getRepositoryUrl() throws IOException {
        File mpFile = new File(dir, ADDRESS_FILE_NAME);
        if (!mpFile.canRead()) {
            return null;
        }
        List lines = FileUtils.readLines(mpFile, Constants.ENCODING);
        if (lines.isEmpty()) {
            throw new IOException(mpFile.getPath() + " is empty.");
        }
        return (String) lines.get(0);
    }

    public void setRepositoryUrl(String url) throws IOException {
        File mpFile = new File(dir, ADDRESS_FILE_NAME);
        List<String> lines = new LinkedList<String>();
        lines.add(url);
        FileUtils.writeLines(mpFile, lines, Constants.ENCODING);
    }

    public VltEntries getEntries() throws VltException {
        if (entries == null) {
            if (entriesFile.exists()) {
                entries = XmlEntries.load(new FileInputSource(entriesFile));
            }
        }
        return entries;
    }

    public MetaFile getFile(String name) throws IOException {
        return getFile(name, false);
    }

    public MetaFile getFile(String name, boolean create) throws IOException {
        File file = new File(dir, name);
        if (file.exists() || create) {
            return new FileMetaFile(this, file);
        } else {
            return null;
        }
    }

    public MetaFile getTmpFile(String name, boolean create) throws IOException {
        File file = new File(tmpDir, name);
        if (file.exists() || create) {
            return new FileMetaFile(this, file);
        } else {
            return null;
        }
    }

    public MetaFile getBaseFile(String name, boolean create) throws IOException {
        File file = new File(baseDir, name);
        if (file.exists() || create) {
            return new FileMetaFile(this, file);
        } else {
            return null;
        }
    }

    public boolean hasFile(String name) throws IOException {
        File file = new File(dir, name);
        return file.exists();        
    }

    public void delete(String name) throws IOException {
        File file = new File(dir, name);
        FileUtils.forceDelete(file);
    }

    public void sync() throws IOException {
        if (entries != null) {
            if (entries.isDirty()) {
                entries.save(FileUtils.openOutputStream(entriesFile));
            }
        }
    }

    public void close() throws IOException {
        sync();
    }
}
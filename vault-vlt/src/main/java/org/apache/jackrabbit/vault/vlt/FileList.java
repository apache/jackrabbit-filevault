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
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.jackrabbit.vault.vlt.meta.Ignored;
import org.apache.jackrabbit.vault.vlt.meta.VltEntries;
import org.apache.jackrabbit.vault.vlt.meta.VltEntry;

/**
 * <code>FileList</code>...
 *
 */
public class FileList {

    private final Map<String, VltFile> files = new TreeMap<String, VltFile>();

    private final VltDirectory dir;

    private final VltEntries entries;

    private final Set<String> globalIgnores;

    private List<Pattern> ignored = new LinkedList<Pattern>();

    public FileList(VltDirectory dir, VltEntries entries) throws VltException {
        this.dir = dir;
        this.entries = entries;
        globalIgnores = new HashSet<String>(
                dir.getContext().getMetaInf().getSettings().getIgnoredNames());
        globalIgnores.add(Ignored.FILE_NAME);
        loadIgnored(dir.getDirectory());
        scanDirectory(dir.getDirectory());
        scanEntries();
    }

    private void loadIgnored(File directory) throws VltException {
        try {
            File file = new File(directory, Ignored.FILE_NAME);
            if (file.isFile() && file.canRead()) {
                for (Object o: FileUtils.readLines(file, "utf-8")) {
                    ignored.add(createPatternFromGlob(o.toString()));
                }
            }
        } catch (IOException e) {
            throw new VltException("Unable to load .vltignore file", e);
        }
    }

    private static Pattern createPatternFromGlob(String glob) {
        // only support * and ?
        glob = glob.replace(".", "\\.");
        glob = glob.replace("*", ".*");
        glob = glob.replace("?", ".+");
        return Pattern.compile(glob);
    }

    public boolean isVltIgnored(String name) {
        for (Pattern p: ignored) {
            if (p.matcher(name).matches()) {
                return true;
            }
        }
        return false;
    }

    private void scanEntries() throws VltException {
        for (VltEntry entry: entries.entries()) {
            if (!files.containsKey(entry.getName())) {
                VltFile file = new VltFile(dir, entry.getName(), entry);
                files.put(file.getName(), file);
            }
        }
    }

    private void scanDirectory(File localDir) throws VltException {
        File[] localFiles = localDir.listFiles();
        if (localFiles != null) {
            for (File localFile : localFiles) {
                if (!isIgnored(localFile)) {
                    String name = localFile.getName();
                    VltEntry entry = entries.getEntry(name);
                    VltFile file = new VltFile(dir, name, entry);
                    files.put(name, file);
                }
            }
        }
    }

    public boolean isIgnored(File file) {
        // currently only check for extension for conflict files
        String name = file.getName();
        return name.equals(VltDirectory.META_DIR_NAME)
                || globalIgnores.contains(name) || isVltIgnored(name);
    }

    public VltFile getFile(String name) {
        return files.get(name);
    }

    public boolean hasFile(String name) {
        return files.containsKey(name);
    }

    public Collection<VltFile> getFiles() {
        return files.values();
    }

    public Set<String> getFileNames() {
        return new HashSet<String>(files.keySet());
    }

    public void addFile(VltFile file) {
        files.put(file.getName(), file);
    }
}
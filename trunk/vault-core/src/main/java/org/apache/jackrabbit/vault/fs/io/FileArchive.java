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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.jackrabbit.vault.fs.api.VaultInputSource;
import org.apache.jackrabbit.vault.fs.config.ExportRoot;
import org.apache.jackrabbit.vault.fs.config.MetaInf;
import org.apache.jackrabbit.vault.util.Constants;
import org.apache.jackrabbit.vault.util.FileInputSource;

/**
 * Implements an archived based on the file system
 */
public class FileArchive extends AbstractArchive {

    /**
     * the root directory
     */
    private final File rootDirectory;

    private ExportRoot eRoot;

    private OsEntry jcrRoot;

    public FileArchive(File rootDirectory) {
        this.rootDirectory = rootDirectory;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void open(boolean strict) throws IOException {
        if (jcrRoot != null) {
            return;
        }
        eRoot = ExportRoot.findRoot(rootDirectory);
        if (!eRoot.isValid()) {
            throw new IOException("No " + Constants.ROOT_DIR + " found.");
        }
        jcrRoot = new OsEntry(eRoot.getJcrRoot());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        eRoot = null;
        jcrRoot = null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Entry getJcrRoot() {
        return jcrRoot;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Entry getRoot() throws IOException {
        return new OsEntry(eRoot.getRoot());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MetaInf getMetaInf() {
        return eRoot.getMetaInf();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public InputStream openInputStream(Entry entry) throws IOException {
        File file = entry == null ? null : ((OsEntry) entry).file;
        if (file == null || !file.isFile() || !file.canRead()) {
            return null;
        }
        return FileUtils.openInputStream(file);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public VaultInputSource getInputSource(Entry entry) throws IOException {
        File file = entry == null ? null : ((OsEntry) entry).file;
        if (file == null || !file.isFile() || !file.canRead()) {
            return null;
        }
        return new FileInputSource(file);
    }

    private static class OsEntry implements Entry {

        private final File file;

        private OsEntry(File file) {
            this.file = file;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getName() {
            return file.getName();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isDirectory() {
            return file.isDirectory();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Collection<Entry> getChildren() {
            File[] files = file.listFiles();
            if (files == null || files.length == 0) {
                return Collections.emptyList();
            }
            List<Entry> ret = new ArrayList<Entry>(files.length);
            for (File file: files) {
                ret.add(new OsEntry(file));
            }
            return ret;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Entry getChild(String name) {
            File child = new File(file, name);
            return child.exists() ? new OsEntry(child) : null;
        }
    }
    
}
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
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.apache.jackrabbit.vault.fs.api.VaultInputSource;
import org.apache.jackrabbit.vault.fs.config.MetaInf;
import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.util.Text;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static org.apache.jackrabbit.vault.packaging.impl.JcrPackageManagerImpl.ARCHIVE_PACKAGE_ROOT_PATH;

/**
 * Archive filter that suppresses the sub packages via the normal tree and allows to extract them individually.
 * Note that it doesn't alter the workspace filter, which is not a problem when /etc/packages is never traversed.
 */
public class SubPackageFilterArchive implements Archive {

    private final Archive base;

    public SubPackageFilterArchive(Archive base) {
        this.base = base;
    }

    @Override
    public void open(boolean strict) throws IOException {
        base.open(strict);
    }

    @Override
    @Nullable
    public InputStream openInputStream(@Nullable Entry entry) throws IOException {
        return base.openInputStream(entry);
    }

    @Override
    @Nullable
    public VaultInputSource getInputSource(@Nullable Entry entry) throws IOException {
        return base.getInputSource(entry);
    }

    @Override
    @Nullable
    public Entry getJcrRoot() throws IOException {
        return new FilterEntry(base.getJcrRoot(), 1);
    }

    @Override
    @NotNull
    public Entry getRoot() throws IOException {
        return new FilterEntry(base.getRoot(), 0);
    }

    @Override
    @NotNull
    public MetaInf getMetaInf() {
        return base.getMetaInf();
    }

    @Override
    @Nullable
    public Entry getEntry(@NotNull String path) throws IOException {
        if (path.length() == 0 || "/".equals(path)) {
            return getRoot();
        }
        if ("/jcr_root".equals(path)) {
            return getJcrRoot();
        }
        if ("/jcr_root/etc".equals(path)) {
            return new FilterEntry(base.getEntry(path), 2);
        }
        if (Text.isDescendantOrEqual(ARCHIVE_PACKAGE_ROOT_PATH, path)) {
            return null;
        }
        return base.getEntry(path);
    }

    @Override
    @Nullable
    public Archive getSubArchive(@NotNull String root, boolean asJcrRoot) throws IOException {
        return base.getSubArchive(root, asJcrRoot);
    }

    @Override
    public void close() {
        base.close();
    }

    /**
     * Returns a list of sub package entries.
     * @return the list of sub package entries.
     * @throws IOException if an error occurrs.
     */
    public List<Entry> getSubPackageEntries() throws IOException {
        List<Archive.Entry> entries = new LinkedList<>();
        Entry folder = base.getEntry(ARCHIVE_PACKAGE_ROOT_PATH);
        if (folder != null) {
            findSubPackageEntries(entries, folder);
        }
        return entries;
    }

    private void findSubPackageEntries(@NotNull List<Entry> entries, @NotNull Entry folder) {
        for (Archive.Entry e: folder.getChildren()) {
            final String name = e.getName();
            if (e.isDirectory()) {
                if (!".snapshot".equals(name)) {
                    findSubPackageEntries(entries, e);
                }
            } else {
                // only process files with .zip extension
                if (name.endsWith(".zip")) {
                    entries.add(e);
                }
            }
        }
    }


    /**
     * Special entry that filters out /jcr_root/etc/packages
     */
    private final class FilterEntry implements Entry {

        private final Entry base;

        private final long level;

        private FilterEntry(Entry base, long level) {
            this.base = base;
            this.level = level;
        }

        @Override
        @NotNull
        public String getName() {
            return base.getName();
        }

        @Override
        public boolean isDirectory() {
            return base.isDirectory();
        }

        private Entry filterChild(Entry e) {
            if (level == 0 && "jcr_root".equals(e.getName())) {
                return new FilterEntry(e, 1);
            } else if (level == 1 && "etc".equals(e.getName())) {
                return new FilterEntry(e, 2);
            } else if (level == 2 && "packages".equals(e.getName())) {
                return null;
            } else {
                return e;
            }
        }

        @Override
        @NotNull
        public Collection<? extends Entry> getChildren() {
            Collection<? extends Entry> children =  base.getChildren();
            List<Entry> ret = new ArrayList<>(children.size());
            for (Entry e: children) {
                e = filterChild(e);
                if (e != null) {
                    ret.add(e);
                }
            }
            return ret;
        }

        @Override
        @Nullable
        public Entry getChild(@NotNull String name) {
            return filterChild(base.getChild(name));
        }
    }


}

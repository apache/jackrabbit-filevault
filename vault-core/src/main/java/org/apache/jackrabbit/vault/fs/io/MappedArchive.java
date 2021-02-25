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

import org.apache.jackrabbit.vault.fs.api.PathMapping;
import org.apache.jackrabbit.vault.fs.api.VaultInputSource;
import org.apache.jackrabbit.vault.fs.config.MetaInf;
import org.apache.jackrabbit.util.Text;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
/**
 * Implements an archive that remaps the entries of an underlying archive using a {@link PathMapping}.
 */
public class MappedArchive extends AbstractArchive {

    /**
     * default logger
     */
    private static final Logger log = LoggerFactory.getLogger(MappedArchive.class);

    private final Archive base;

    private final PathMapping mapping;

    private final VirtualEntry root = new VirtualEntry();

    private VirtualEntry jcrRoot;

    public MappedArchive(Archive base, PathMapping mapping) {
        this.base = base;
        this.mapping = mapping;
    }

    @Override
    public void open(boolean strict) throws IOException {
        base.open(strict);
        applyMapping(base.getRoot(), root);
    }

    /**
     * Decorates the non jcr files without applying the mapping. this can be done with parallel traversal.
     * @param src source entry parent
     * @param dst destination entry parent
     */
    private void applyMapping(@NotNull Entry src, @NotNull VirtualEntry dst) {
        for (Entry child: src.getChildren()) {
            VirtualEntry dstChild = dst.add(child.getName(), child);
            if ("/jcr_root".equals(dstChild.getPath())) {
                jcrRoot = dstChild;
                applyMapping(child, "");
            } else {
                applyMapping(child, dstChild);
            }
        }
    }

    /**
     * Decorates the jcr entries while applying the path mapping. this cannot be done with parallel traversal, because
     * the remapping could create holes in the entry tree.
     *
     * @param src the source entry
     * @param jcrPath the jcr path of the source entry
     */
    private void applyMapping(@NotNull Entry src, @NotNull String jcrPath) {
        for (Entry child: src.getChildren()) {
            String path = jcrPath + "/" + child.getName();
            String mappedPath = mapping.map(path);

            // add entry to tree
            String[] segments = Text.explode(mappedPath, '/');
            VirtualEntry entry = jcrRoot;
            for (String seg: segments) {
                entry = entry.add(seg, null);
            }
            if (entry.baseEntry != null) {
                log.warn("Path mapping maps multiple paths to the same destination: {} -> {}. ignoring this source.", path, mappedPath);
            } else {
                entry.baseEntry = child;
            }

            applyMapping(child, path);
        }
    }

    @Override
    @Nullable
    public InputStream openInputStream(@Nullable Entry entry) throws IOException {
        if (entry == null) {
            return null;
        }
        return base.openInputStream(((VirtualEntry) entry).baseEntry);
    }

    @Override
    @Nullable
    public VaultInputSource getInputSource(@Nullable Entry entry) throws IOException {
        if (entry == null) {
            return null;
        }
        return base.getInputSource(((VirtualEntry) entry).baseEntry);
    }

    @Override
    @NotNull
    public Entry getRoot() throws IOException {
        return root;
    }

    @Override
    public Entry getJcrRoot() throws IOException {
        return jcrRoot;
    }

    @Override
    @NotNull
    public MetaInf getMetaInf() {
        return base.getMetaInf();
    }

    @Override
    public void close() {
        base.close();
    }

    /**
     * Implements a entry for this archive
     */
    private static class VirtualEntry implements Entry {

        @Nullable
        private final VirtualEntry parent;

        @NotNull
        private final String name;

        @Nullable
        private Archive.Entry baseEntry;

        @Nullable
        private Map<String, VirtualEntry> children;

        private VirtualEntry() {
            this.parent = null;
            this.name = "";
            this.baseEntry = null;
        }

        private VirtualEntry(@NotNull VirtualEntry parent, @NotNull String name, @Nullable Archive.Entry baseEntry) {
            this.parent = parent;
            this.name = name;
            this.baseEntry = baseEntry;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        @NotNull
        public String getName() {
            return name;
        }

        @NotNull
        public String getPath() {
            return getPath(new StringBuilder()).toString();
        }

        @NotNull
        private StringBuilder getPath(@NotNull StringBuilder sb) {
            return parent == null ? sb : parent.getPath(sb).append('/').append(name);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isDirectory() {
            return baseEntry == null || baseEntry.isDirectory();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        @NotNull
        public Collection<? extends Entry> getChildren() {
            return children == null ? Collections.<Entry>emptyList() : children.values();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        @Nullable
        public Entry getChild(String name) {
            return children == null ? null : children.get(name);
        }

        /**
         * Adds a new child entry.
         * @param name name
         * @param baseEntry the base archive's entry or
         * @return the new entry
         */
        @NotNull
        public VirtualEntry add(@NotNull String name, @Nullable Archive.Entry baseEntry) {
            if (children != null) {
                VirtualEntry ret = children.get(name);
                if (ret != null) {
                    return ret;
                }
            }
            VirtualEntry ve = new VirtualEntry(this, name, baseEntry);
            if (children == null) {
                children = new LinkedHashMap<String, VirtualEntry>();
            }
            children.put(name, ve);
            return ve;
        }
    }
}

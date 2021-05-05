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

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.io.IOUtils;
import org.apache.jackrabbit.util.Text;
import org.apache.jackrabbit.vault.fs.api.VaultInputSource;
import org.apache.jackrabbit.vault.fs.config.DefaultMetaInf;
import org.apache.jackrabbit.vault.fs.config.MetaInf;
import org.apache.jackrabbit.vault.fs.config.VaultSettings;
import org.apache.jackrabbit.vault.util.Constants;
import org.apache.jackrabbit.vault.util.InputStreamPump;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements an {@link org.apache.jackrabbit.vault.util.InputStreamPump.Pump} that extracts the relevant parts from the input stream into memory.
 * The memory archive is initialized via the {@link #run(InputStream)} being called from {@link InputStreamPump}.
 */
public class MemoryArchive extends AbstractArchive implements InputStreamPump.Pump {

    /**
     * default logger
     */
    private static final Logger log = LoggerFactory.getLogger(MemoryArchive.class);

    private final VirtualEntry root;

    private final DefaultMetaInf inf;

    private boolean cacheMetaOnly = false;

    /**
     * Creates new memory archive.
     * @param metaOnly if {@code true} only the meta info content is cached.
     * @throws IOException if an I/O error occurrs
     */
    public MemoryArchive(boolean metaOnly) throws IOException {
        this.cacheMetaOnly = metaOnly;
        root = new VirtualEntry(null, "", 0, null);
        inf = new DefaultMetaInf();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run(InputStream in) throws Exception {
        // scan the zip and copy data to temporary file
        ZipInputStream zin = new ZipInputStream(
                new BufferedInputStream(in)
        );
        ZipEntry entry;
        boolean hasRoot = false;
        while ((entry = zin.getNextEntry()) != null) {
            String name = entry.getName();
            boolean isMeta = name.startsWith(Constants.META_DIR + "/");
            if (!hasRoot && name.startsWith(Constants.ROOT_DIR + "/")) {
                hasRoot = true;
            }
            if (isMeta || !cacheMetaOnly) {
                String[] names = Text.explode(name, '/');
                byte[] data = entry.isDirectory() ? null : IOUtils.toByteArray(zin);
                if (names.length > 0) {
                    VirtualEntry je = root;
                    for (int i=0; i<names.length; i++) {
                        if (i == names.length -1 && !entry.isDirectory()) {
                            je = je.add(names[i], safeGetTime(entry), data);
                        } else {
                            je = je.add(names[i], 0, null);
                        }
                    }
                    if (log.isDebugEnabled()) {
                        log.debug("scanning jar: {}", name);
                    }
                }
                if (isMeta) {
                    inf.load(data == null ? null : new ByteArrayInputStream(data), "inputstream://" + name);
                }
            }
        }
        // ensure that root directory is present, even if we are not caching non-meta-inf stuff
        if (hasRoot && !root.children.containsKey(Constants.ROOT_DIR)) {
            root.add(Constants.ROOT_DIR, 0, null);
        }
        if (inf.getSettings() == null) {
            VaultSettings settings = new VaultSettings();
            settings.getIgnoredNames().add(".svn");
            inf.setSettings(settings);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void open(boolean strict) throws IOException {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public InputStream openInputStream(Entry entry) throws IOException {
        VirtualEntry ve = (VirtualEntry) entry;
        if (ve == null || ve.data == null) {
            return null;
        }
        return new ByteArrayInputStream(ve.data);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public VaultInputSource getInputSource(Entry entry) throws IOException {
        final VirtualEntry ve = (VirtualEntry) entry;
        if (ve == null) {
            return null;
        }
        return new VaultInputSource() {

            @Override
            public String getSystemId() {
                String systemId = super.getSystemId();
                if (systemId == null) {
                    systemId = ve.getPath();
                    setSystemId(systemId);
                }
                return systemId;
            }

            public InputStream getByteStream() {
                return ve.data == null ? null : new ByteArrayInputStream(ve.data);
            }

            /**
             * {@inheritDoc}
             */
            public long getContentLength() {
                return ve.data == null ? -1 : ve.data.length;
            }

            /**
             * {@inheritDoc}
             */
            public long getLastModified() {
                return ve.time;
            }

        };
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Entry getRoot() throws IOException {
        return root;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MetaInf getMetaInf() {
        return inf;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
    }

    /**
     * Implements a entry for this archive
     */
    private static class VirtualEntry implements Entry {

        private final VirtualEntry parent;

        private final String name;

        private final long time;

        private final byte[] data;

        private Map<String, VirtualEntry> children;

        private VirtualEntry(VirtualEntry parent, String name, long time, byte[] data) {
            this.parent = parent;
            this.name = name;
            this.time = time;
            this.data = data;
        }

        /**
         * {@inheritDoc}
         */
        @Override
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
            return data == null;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Collection<? extends Entry> getChildren() {
            return children == null ? Collections.<Entry>emptyList() : children.values();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Entry getChild(String name) {
            return children == null ? null : children.get(name);
        }

        /**
         * Adds a new child entry.
         * @param name name
         * @param time timestamp
         * @param data data or {@code null}
         * @return the new entry
         */
        @NotNull
        public VirtualEntry add(@NotNull String name, long time, byte[] data) {
            if (children != null) {
                VirtualEntry ret = children.get(name);
                if (ret != null) {
                    return ret;
                }
            }
            VirtualEntry ve = new VirtualEntry(this, name, time, data);
            if (children == null) {
                children = new LinkedHashMap<String, VirtualEntry>();
            }
            children.put(name, ve);
            return ve;
        }
    }

    /**
     * Safely returns the modification time of the zip entry or 0, if reading the time would
     * result in an error. for example due to http://bugs.java.com/view_bug.do?bug_id=JDK-8184940
     *
     * @param e the zip entry
     * @return the modification time
     */
    private static long safeGetTime(ZipEntry e) {
        try {
            return e.getTime();
        } catch (Exception e1) {
            return 0;
        }
    }
}

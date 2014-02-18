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
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.io.IOUtils;
import org.apache.jackrabbit.vault.fs.api.VaultInputSource;
import org.apache.jackrabbit.vault.fs.config.DefaultMetaInf;
import org.apache.jackrabbit.vault.fs.config.MetaInf;
import org.apache.jackrabbit.vault.fs.config.VaultSettings;
import org.apache.jackrabbit.vault.fs.spi.CNDReader;
import org.apache.jackrabbit.vault.fs.spi.ServiceProviderFactory;
import org.apache.jackrabbit.vault.util.Constants;
import org.apache.jackrabbit.vault.util.InputStreamPump;
import org.apache.jackrabbit.vault.util.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@code MemoryArchive}...
 */
public class MemoryArchive extends AbstractArchive implements InputStreamPump.Pump {

    /**
     * default logger
     */
    private static final Logger log = LoggerFactory.getLogger(MemoryArchive.class);

    private final VirtualEntry root;

    private final DefaultMetaInf inf;

    private boolean cacheMetaOnly = false;

    public MemoryArchive(boolean metaOnly) throws IOException {
        this.cacheMetaOnly = metaOnly;
        root = new VirtualEntry("", 0, null);
        inf = new DefaultMetaInf();
    }

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
                            je = je.add(names[i], entry.getTime(), data);
                        } else {
                            je = je.add(names[i], 0, null);
                        }
                    }
                    if (log.isDebugEnabled()) {
                        log.debug("scanning jar: {}", name);
                    }
                }
                if (isMeta) {
                    String path = "InputStream:" + name;
                    name = name.substring((Constants.META_DIR + "/").length());
                    if (name.equals(Constants.FILTER_XML)) {
                        // load filter
                        inf.loadFilter(new ByteArrayInputStream(data), path);
                    } else if (name.equals(Constants.CONFIG_XML)) {
                        // load config
                        inf.loadConfig(new ByteArrayInputStream(data), path);
                    } else if (name.equals(Constants.SETTINGS_XML)) {
                        // load settings
                        inf.loadSettings(new ByteArrayInputStream(data), path);
                    } else if (name.equals(Constants.PROPERTIES_XML)) {
                        // load properties
                        inf.loadProperties(new ByteArrayInputStream(data), path);
                    } else if (name.equals(Constants.PRIVILEGES_XML)) {
                        // load privileges
                        inf.loadPrivileges(new ByteArrayInputStream(data), path);
                    } else if (name.equals(Constants.PACKAGE_DEFINITION_XML)) {
                        inf.setHasDefinition(true);
                        log.debug("Contains package definition {}.", path);
                    } else if (name.endsWith(".cnd")) {
                        try {
                            Reader r = new InputStreamReader(new ByteArrayInputStream(data), "utf8");
                            CNDReader reader = ServiceProviderFactory.getProvider().getCNDReader();
                            reader.read(r, entry.getName(), null);
                            inf.getNodeTypes().add(reader);
                            log.debug("Loaded nodetypes from {}.", path);
                        } catch (IOException e1) {
                            log.error("Error while reading CND: {}", e1.toString());
                        }
                    }
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

    public void open(boolean strict) throws IOException {
    }

    public InputStream openInputStream(Entry entry) throws IOException {
        VirtualEntry ve = (VirtualEntry) entry;
        if (ve == null || ve.data == null) {
            return null;
        }
        return new ByteArrayInputStream(ve.data);
    }

    public VaultInputSource getInputSource(Entry entry) throws IOException {
        final VirtualEntry ve = (VirtualEntry) entry;
        if (ve == null) {
            return null;
        }
        return new VaultInputSource() {

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

    public Entry getRoot() throws IOException {
        return root;
    }

    public MetaInf getMetaInf() {
        return inf;
    }

    public void close() {
    }

    private static class VirtualEntry implements Entry {

        private final String name;

        private final long time;

        private final byte[] data;

        private Map<String, VirtualEntry> children;

        private VirtualEntry(String name, long time, byte[] data) {
            this.name = name;
            this.time = time;
            this.data = data;
        }

        public String getName() {
            return name;
        }

        public boolean isDirectory() {
            return data == null;
        }

        public Collection<? extends Entry> getChildren() {
            return children.values();
        }

        public Entry getChild(String name) {
            return children.get(name);
        }

        public VirtualEntry add(String name, long time, byte[] data) {
            if (children != null) {
                VirtualEntry ret = children.get(name);
                if (ret != null) {
                    return ret;
                }
            }
            VirtualEntry ve = new VirtualEntry(name, time, data);
            if (children == null) {
                children = new LinkedHashMap<String, VirtualEntry>();
            }
            children.put(name, ve);
            return ve;
        }
    }
}
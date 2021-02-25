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
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import org.apache.commons.io.FileUtils;
import org.apache.jackrabbit.vault.fs.api.VaultInputSource;
import org.apache.jackrabbit.vault.fs.config.ConfigurationException;
import org.apache.jackrabbit.vault.fs.config.DefaultMetaInf;
import org.apache.jackrabbit.vault.fs.config.MetaInf;
import org.apache.jackrabbit.vault.fs.config.VaultSettings;
import org.apache.jackrabbit.vault.util.Constants;
import org.apache.jackrabbit.util.Text;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements an archive that is based on a zip file.
 */
public class ZipArchive extends AbstractArchive {

    /**
     * default logger
     */
    private static final Logger log = LoggerFactory.getLogger(ZipArchive.class);

    /**
     * the zip file
     */
    private final File file;

    /**
     * {@code true} if file is temporary and can be deleted after this archive is closed.
     */
    private final boolean isTempFile;

    /**
     * The (loaded) meta info
     */
    private DefaultMetaInf inf;

    /**
     * the jar file that is created upon {@link #open(boolean)}
     */
    private JarFile jar;

    /**
     * the root entry of this archive
     */
    private EntryImpl root;

    /**
     * Creates a new archive that is based on the given zip file.
     * @param zipFile the zip file
     */
    public ZipArchive(@NotNull File zipFile) {
        this(zipFile, false);
    }

    /**
     * Creates a new archive that is based on the given zip file.
     * @param zipFile the zip file
     * @param isTempFile if {@code true} if the file is considered temporary and can be deleted after this archive is closed.
     */
    public ZipArchive(@NotNull File zipFile, boolean isTempFile) {
        this.file = zipFile;
        this.isTempFile = isTempFile;
    }

    @Override
    public void open(boolean strict) throws IOException {
        if (jar != null) {
            return;
        }
        jar = new JarFile(file);
        root = new EntryImpl("", true);
        inf = new DefaultMetaInf();

        Enumeration<JarEntry> e = jar.entries();
        while (e.hasMoreElements()) {
            ZipEntry entry = e.nextElement();
            String path = entry.getName();
            // check for meta inf
            if (path.startsWith(Constants.META_DIR + "/")) {
                try (InputStream input = jar.getInputStream(entry)) {
                    inf.load(input, file.getPath() + ":" + path);
                } catch (ConfigurationException e1) {
                    throw new IOException(e1);
                }
            }
            String[] names = Text.explode(path, '/');
            if (names.length > 0) {
                EntryImpl je = root;
                for (int i=0; i<names.length; i++) {
                    if (i == names.length -1) {
                        je = je.add(names[i], entry.isDirectory());
                    } else {
                        je = je.add(names[i], true);
                    }
                }
                je.zipEntryName = entry.getName();
                log.debug("scanning jar: {}", je.zipEntryName);
            }
        }
        if (inf.getFilter() == null) {
            log.debug("Zip {} does not contain filter definition.", file.getPath());
        }
        if (inf.getConfig() == null) {
            log.debug("Zip {} does not contain vault config.", file.getPath());
        }
        if (inf.getSettings() == null) {
            log.debug("Zip {} does not contain vault settings. using default.", file.getPath());
            VaultSettings settings = new VaultSettings();
            settings.getIgnoredNames().add(".svn");
            inf.setSettings(settings);
        }
        if (inf.getProperties() == null) {
            log.debug("Zip {} does not contain properties.", file.getPath());
        }
        if (inf.getNodeTypes().isEmpty()) {
            log.debug("Zip {} does not contain nodetypes.", file.getPath());
        }
    }

    @Override
    @Nullable
    public InputStream openInputStream(@Nullable Entry entry) throws IOException {
        EntryImpl e = (EntryImpl) entry;
        if (e == null || e.zipEntryName == null) {
            return null;
        }
        ZipEntry ze = jar.getEntry(e.zipEntryName);
        if (ze == null) {
            throw new IOException("ZipEntry could not be found: " + e.zipEntryName);
        }
        return jar.getInputStream(ze);
    }

    @Override
    @Nullable
    public VaultInputSource getInputSource(@Nullable Entry entry) throws IOException {
        EntryImpl e = (EntryImpl) entry;
        if (e == null || e.zipEntryName == null) {
            return null;
        }
        final ZipEntry ze = jar.getEntry(e.zipEntryName);
        if (ze == null) {
            throw new IOException("ZipEntry could not be found: " + e.zipEntryName);
        }
        return new VaultInputSource() {

            {
                setSystemId(ze.getName());
            }

            public InputStream getByteStream() {
                try {
                    return jar.getInputStream(ze);
                } catch (IOException e1) {
                    return null;
                }
            }

            /**
             * {@inheritDoc}
             */
            public long getContentLength() {
                return ze.getSize();
            }

            /**
             * {@inheritDoc}
             */
            public long getLastModified() {
                try {
                    return ze.getTime();
                } catch (Exception e1) {
                    // see: http://bugs.java.com/view_bug.do?bug_id=JDK-8184940
                    return 0;
                }
            }

        };
    }

    @Override
    public void close() {
        try {
            if (jar != null) {
                jar.close();
                jar = null;
            }
            if (file != null && isTempFile) {
                FileUtils.deleteQuietly(file);
            }
        } catch (IOException e) {
            log.warn("Error during close.", e);
        }
    }

    @Override
    @NotNull
    public Entry getRoot() throws IOException {
        return root;
    }

    @Override
    @NotNull
    public MetaInf getMetaInf() {
        if (inf == null) {
            throw new IllegalStateException("Archive not open.");
        }
        return inf;
    }

    /**
     * Returns the underlying file or {@code null} if it does not exist.
     * @return the file or null.
     */
    @Nullable
    public File getFile() {
        return file.exists() ? file : null;
    }

    /**
     * Returns the size of the underlying file or -1 if it does not exist.
     * @return the file size
     */
    public long getFileSize() {
        return file.length();
    }

    @Override
    public String toString() {
        return file.getPath();
    }

    /**
     * Implements the entry for this archive
     */
    private static class EntryImpl implements Entry {

        private final String name;

        private String zipEntryName;

        private final boolean isDirectory;

        private Map<String, EntryImpl> children;

        private EntryImpl(@NotNull String name, boolean directory) {
            this.name = name;
            isDirectory = directory;
        }

        @NotNull
        private EntryImpl add(@NotNull EntryImpl e) {
            if (children == null) {
                children = new LinkedHashMap<String, EntryImpl>();
            }
            children.put(e.getName(), e);
            return e;
        }

        @NotNull
        public EntryImpl add(@NotNull String name, boolean isDirectory) {
            if (children != null) {
                EntryImpl ret = children.get(name);
                if (ret != null) {
                    return ret;
                }
            }
            return add(new EntryImpl(name, isDirectory));
        }

        @Override
        @NotNull
        public String getName() {
            return name;
        }

        @Override
        public boolean isDirectory() {
            return isDirectory;
        }

        @Override
        @NotNull
        public Collection<? extends Entry> getChildren() {
            return children == null
                    ? Collections.<EntryImpl>emptyList()
                    : children.values();
        }

        @Override
        @Nullable
        public Entry getChild(@NotNull String name) {
            return children == null ? null : children.get(name);
        }

    }
}

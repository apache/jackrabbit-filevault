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

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.jackrabbit.vault.fs.api.VaultInputSource;
import org.apache.jackrabbit.vault.fs.config.MetaInf;
import org.apache.jackrabbit.vault.util.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements an archive that is based on a zip file.
 */
class ZipFileArchive extends AbstractArchive {

    /**
     * default logger
     */
    private static final Logger log = LoggerFactory.getLogger(ZipFileArchive.class);

    private ZipFile zip;

    private JarEntry root;

    public ZipFileArchive(ZipFile zip) {
        this.zip = zip;
    }

    public void open(boolean strict) throws IOException {
        root = new JarEntry("", true);
        Enumeration e = zip.entries();
        while (e.hasMoreElements()) {
            ZipEntry entry = (ZipEntry) e.nextElement();
            String path = entry.getName();
            String[] names = Text.explode(path, '/');
            if (names.length > 0) {
                JarEntry je = root;
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
    }


    public InputStream openInputStream(Entry entry) throws IOException {
        JarEntry e = (JarEntry) entry;
        if (e == null || e.zipEntryName == null) {
            return null;
        }
        ZipEntry ze = zip.getEntry(e.zipEntryName);
        if (ze == null) {
            throw new IOException("ZipEntry could not be found: " + e.zipEntryName);
        }
        return zip.getInputStream(ze);
    }

    public VaultInputSource getInputSource(Entry entry) throws IOException {
        JarEntry e = (JarEntry) entry;
        if (e == null || e.zipEntryName == null) {
            return null;
        }
        final ZipEntry ze = zip.getEntry(e.zipEntryName);
        if (ze == null) {
            throw new IOException("ZipEntry could not be found: " + e.zipEntryName);
        }
        return new VaultInputSource() {

            {
                setSystemId(ze.getName());
            }

            public InputStream getByteStream() {
                try {
                    return zip.getInputStream(ze);
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
                return ze.getTime();
            }

        };
    }

    public void close() {
        try {
            if (zip != null) {
                zip.close();
                zip = null;
            }
        } catch (IOException e) {
            log.warn("Error during close.", e);
        }
    }

    public Entry getRoot() throws IOException {
        return root;
    }

    public MetaInf getMetaInf() {
        throw new IllegalStateException("getMetaInf() should not be called directly.");
    }

    private static class JarEntry implements Entry {

        public final String name;

        private String zipEntryName;

        public final boolean isDirectory;

        public Map<String, JarEntry> children;

        public JarEntry(String name, boolean directory) {
            this.name = name;
            isDirectory = directory;
        }

        public JarEntry add(String name, boolean isDirectory) {
            if (children != null) {
                JarEntry ret = children.get(name);
                if (ret != null) {
                    return ret;
                }
            }
            return add(new JarEntry(name, isDirectory));
        }

        public String getName() {
            return name;
        }

        public boolean isDirectory() {
            return isDirectory;
        }

        public JarEntry add(JarEntry e) {
            if (children == null) {
                children = new LinkedHashMap<String, JarEntry>();
            }
            children.put(e.getName(), e);
            return e;
        }

        public Collection<? extends Entry> getChildren() {
            return children == null
                    ? Collections.<JarEntry>emptyList()
                    : children.values();
        }

        public Entry getChild(String name) {
            return children == null ? null : children.get(name);
        }

    }
}
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
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.jackrabbit.vault.fs.api.VaultInputSource;
import org.apache.jackrabbit.vault.fs.config.MetaInf;
import org.apache.jackrabbit.vault.util.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements an archive based on a zip file, but deflates the entries first
 * in a tmp file. this is only used to circumvent a bug in ZipFile of jdk1.5,
 * that has problems with large zip files.
 */
class ZipStreamArchive extends AbstractArchive {

    /**
     * default logger
     */
    private static final Logger log = LoggerFactory.getLogger(ZipStreamArchive.class);

    private final File zipFile;

    private File tmpFile;

    private RandomAccessFile raf;

    private JarEntry root;

    public ZipStreamArchive(File zipFile) {
        this.zipFile = zipFile;
    }

    public void open(boolean strict) throws IOException {
        if (raf != null) {
            throw new IllegalStateException("already open");
        }

        tmpFile = File.createTempFile("__vlttmpbuffer", ".dat");
        raf = new RandomAccessFile(tmpFile, "rw");

        root = new JarEntry("");

        // scan the zip and copy data to temporary file
        ZipInputStream zin = new ZipInputStream(
                new BufferedInputStream(
                        new FileInputStream(zipFile)
                )
        );

        try {
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                String name = entry.getName();
                String[] names = Text.explode(name, '/');
                if (names.length > 0) {
                    JarEntry je = root;
                    for (int i=0; i<names.length; i++) {
                        if (i == names.length -1 && !entry.isDirectory()) {
                            // copy stream
                            long pos = raf.getFilePointer();
                            long len = copy(zin);
                            je = je.add(new JarEntry(names[i], entry.getTime(), pos, len));
                        } else {
                            je = je.add(names[i]);
                        }
                    }
                    if (log.isDebugEnabled()) {
                        log.debug("scanning jar: {}", name);
                    }
                }
            }
        } finally {
            IOUtils.closeQuietly(zin);
        }
    }

    private long copy(InputStream in) throws IOException {
        byte[] buffer = new byte[8192];
        int read;
        int total = 0;
        while ((read = in.read(buffer)) > 0) {
            raf.write(buffer, 0, read);
            total += read;
        }
        return total;
    }

    public InputStream openInputStream(Entry entry) throws IOException {
        return new RafInputStream((JarEntry) entry);
    }

    public VaultInputSource getInputSource(Entry entry) throws IOException {
        return new RafInputSource((JarEntry) entry);
    }

    public MetaInf getMetaInf() {
        throw new IllegalStateException("getMetaInf() should not be called directly.");
    }

    public void close() {
        if (raf != null) {
            try {
                raf.close();
            } catch (IOException e) {
                // ignore
            }
            raf = null;
        }
        if (tmpFile != null) {
            FileUtils.deleteQuietly(tmpFile);
            tmpFile = null;
        }
    }

    public Entry getRoot() throws IOException {
        return root;
    }

    private class RafInputSource extends VaultInputSource {

        private final JarEntry entry;

        private RafInputSource(JarEntry entry) {
            this.entry = entry;
        }

        @Override
        public InputStream getByteStream() {
            return new RafInputStream(entry);
        }

        public long getContentLength() {
            return entry.len;
        }

        public long getLastModified() {
            return entry.date;
        }
    }

    private class RafInputStream extends InputStream {

        private long pos;

        private long end;

        private long mark;

        private RafInputStream(JarEntry entry) {
            pos = entry.pos;
            end = pos + entry.len;
        }

        public int read() throws IOException {
            if (pos < end) {
                raf.seek(pos++);
                return raf.read();
            } else {
                return -1;
            }
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (pos >= end) {
                return -1;
            }
            len = Math.min(len, (int) (end-pos));
            raf.seek(pos);
            int read = raf.read(b, off, len);
            if (read < 0) {
                return -1;
            }
            pos += read;
            return read;
        }

        @Override
        public int read(byte[] b) throws IOException {
            return read(b, 0, b.length);
        }

        @Override
        public long skip(long n) throws IOException {
            if (pos >= end) {
                return -1;
            }
            n = Math.min(n, end - pos);
            pos+= n;
            return n;
        }

        @Override
        public int available() throws IOException {
            return (int) (end - pos);
        }

        @Override
        public void close() throws IOException {
            // ignore
        }

        @Override
        public void mark(int readlimit) {
            mark = pos;
        }

        @Override
        public void reset() throws IOException {
            pos = mark;
        }

        @Override
        public boolean markSupported() {
            return true;
        }
    }

    private static class JarEntry implements Entry {

        public final String name;

        public final long date;

        public final long pos;

        public final long len;

        public Map<String, JarEntry> children;

        private JarEntry(String name) {
            this.name = name;
            this.date = 0;
            pos = -1;
            len = 0;
        }

        private JarEntry(String name, long date, long pos, long len) {
            this.name = name;
            this.date = date;
            this.pos = pos;
            this.len = len;
        }

        public String getName() {
            return name;
        }

        public boolean isDirectory() {
            return pos < 0;
        }

        public JarEntry add(JarEntry e) {
            if (children == null) {
                children = new LinkedHashMap<String, JarEntry>();
            }
            children.put(e.getName(), e);
            return e;
        }

        public JarEntry add(String name) {
            JarEntry e;
            if (children == null) {
                children = new LinkedHashMap<String, JarEntry>();
            } else {
                e = children.get(name);
                if (e != null) {
                    return e;
                }
            }
            e = new JarEntry(name);
            children.put(name, e);
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
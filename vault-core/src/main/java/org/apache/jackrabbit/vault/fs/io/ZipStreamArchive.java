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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.annotation.Nonnull;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.CloseShieldInputStream;
import org.apache.jackrabbit.vault.fs.api.VaultInputSource;
import org.apache.jackrabbit.vault.fs.config.ConfigurationException;
import org.apache.jackrabbit.vault.fs.config.DefaultMetaInf;
import org.apache.jackrabbit.vault.fs.config.MetaInf;
import org.apache.jackrabbit.vault.fs.config.VaultSettings;
import org.apache.jackrabbit.vault.util.Constants;
import org.apache.jackrabbit.vault.util.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements an archive based on a zip stream, but deflates the entries first into a buffer and later into a temporary
 * file, if the content length exceeds the buffer size.
 */
public class ZipStreamArchive extends AbstractArchive {

    /**
     * default logger
     */
    private static final Logger log = LoggerFactory.getLogger(ZipStreamArchive.class);

    /**
     * max allowed package size for using a memory archive
     */
    private static final int DEFAULT_BUFFER_SIZE = 1024*1024;

    /**
     * the input stream that is consumed in this archive
     */
    private InputStream in;

    /**
     * the temporary file if the stream needs to be copied to disk.
     */
    private File tmpFile;

    /**
     * A random access file of the temp file
     */
    private RandomAccessFile raf;

    /**
     * the decompressed data of the stream if the contents are small.
     */
    private byte[] decompressed;

    /**
     * the maximum buffer size
     */
    private final int maxBufferSize;

    /**
     * the current write position into the decompressed buffer
     */
    private int pos;

    /**
     * the root entry of this archive
     */
    private EntryImpl root;

    /**
     * the meta info that is loaded in this archive
     */
    private DefaultMetaInf inf;

    /**
     * internal buffer used for copying.
     */
    private final byte[] buffer = new byte[0x10000];

    /**
     * Creates a new zip stream archive on the given input stream.
     * @param in the input stream to read from.
     */
    public ZipStreamArchive(@Nonnull InputStream in) {
        this(in, DEFAULT_BUFFER_SIZE);
    }

    /**
     * Creates an ew zip stream archive on the given input stream.
     * @param in the input stream to read from.
     * @param maxBufferSize size of buffer to keep content in memory.
     */
    public ZipStreamArchive(@Nonnull InputStream in, int maxBufferSize) {
        this.in = in;
        this.maxBufferSize = maxBufferSize;
    }

    @Override
    public void open(boolean strict) throws IOException {
        if (raf != null || decompressed != null) {
            return;
        }

        decompressed = new byte[maxBufferSize];
        pos = 0;
        root = new EntryImpl("");
        inf = new DefaultMetaInf();

        // scan the zip and copy data to temporary file
        try (ZipInputStream zin = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                String name = entry.getName();
                String[] names = Text.explode(name, '/');
                EntryImpl je = root;
                if (names.length > 0) {
                    for (int i=0; i<names.length; i++) {
                        if (i == names.length -1 && !entry.isDirectory()) {
                            // copy stream
                            long pos = getPosition();
                            long len = copy(zin);
                            je = je.add(new EntryImpl(names[i], safeGetTime(entry), pos, len));
                        } else {
                            je = je.add(names[i]);
                        }
                    }
                    if (log.isDebugEnabled()) {
                        log.debug("scanning jar: {}", name);
                    }
                }
                // extract meta information
                if (name.startsWith(Constants.META_DIR + "/")) {
                    try (InputStream input = createInputStream(je)) {
                        // load from previous entryImpl
                        inf.load(input, "inputstream:" + name);
                    } catch (ConfigurationException e) {
                        throw new IOException(e);
                    }
                }
            }
            if (inf.getFilter() == null) {
                log.debug("Zip stream does not contain filter definition.");
            }
            if (inf.getConfig() == null) {
                log.debug("Zip stream does not contain vault config.");
            }
            if (inf.getSettings() == null) {
                log.debug("Zip stream does not contain vault settings. using default.");
                VaultSettings settings = new VaultSettings();
                settings.getIgnoredNames().add(".svn");
                inf.setSettings(settings);
            }
            if (inf.getProperties() == null) {
                log.debug("Zip stream does not contain properties.");
            }
            if (inf.getNodeTypes().isEmpty()) {
                log.debug("Zip stream does not contain nodetypes.");
            }
        }
    }

    /**
     * Gets the write position of either the random access file or the memory buffer.
     * @return the write position.
     * @throws IOException if an I/O error occurrs.
     */
    private long getPosition() throws IOException {
        if (raf != null) {
            return raf.getFilePointer();
        }
        return pos;
    }

    /**
     * Copies the input stream either into the random access file or the memory buffer.
     * @param in the input stream to copy
     * @return the number of bytes written to the destination.
     * @throws IOException if an I/O error occurrs.
     */
    private long copy(@Nonnull InputStream in) throws IOException {
        if (raf != null) {
            return copyToRaf(in);
        }
        return copyToBuffer(in);
    }

    /**
     * Copies the input stream to the buffer but check for overflow. If the buffer size is exceeded, the entire buffer
     * is copied to a random access file and the rest of the input stream is appended there.
     * @param in the input stream to copy
     * @return the number of bytes written to the destination.
     * @throws IOException if an I/O error occurrs.
     */
    private long copyToBuffer(@Nonnull InputStream in) throws IOException {
        int read;
        int total = 0;
        while ((read = in.read(decompressed, pos, decompressed.length - pos)) > 0) {
            total += read;
            pos += read;
            if (pos == decompressed.length) {
                // switch to raf
                tmpFile = File.createTempFile("__vlttmpbuffer", ".dat");
                raf = new RandomAccessFile(tmpFile, "rw");
                raf.write(decompressed);
                decompressed = null;
                return total + copyToRaf(in);
            }
        }
        return total;
    }

    /**
     * copies the input stream into the random access file
     * @param in the input stream
     * @return the total number of bytes copied
     * @throws IOException if an error occurrs.
     */
    private long copyToRaf(@Nonnull InputStream in) throws IOException {
        int read;
        int total = 0;
        while ((read = in.read(buffer)) > 0) {
            raf.write(buffer, 0, read);
            total += read;
        }
        return total;
    }

    @Override
    public InputStream openInputStream(Entry entry) throws IOException {
        return createInputStream((EntryImpl) entry);
    }

    @Override
    public VaultInputSource getInputSource(Entry entry) throws IOException {
        return new RafInputSource((EntryImpl) entry);
    }

    @Override
    public MetaInf getMetaInf() {
        return inf;
    }

    @Override
    public void close() {
        if (in != null) {
            IOUtils.closeQuietly(in);
        }
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
        if (decompressed != null) {
            // keep array so isBuffered works after closing
            decompressed = new byte[0];
        }
    }

    @Override
    public Entry getRoot() throws IOException {
        return root;
    }

    /**
     * Checks if this archive is currently buffered (and not using a temporary file).
     * @return {@code true} if buffered.
     */
    public boolean isBuffered() {
        return decompressed != null;
    }

    /**
     * creates an input stream that either read from the buffer or the random access file.
     * @param entry the archive entry
     * @return the input stream
     */
    private InputStream createInputStream(@Nonnull EntryImpl entry) {
        if (raf == null) {
            return new ByteArrayInputStream(decompressed, (int) entry.pos, (int) entry.len);
        }
        return new RafInputStream(entry);
    }

    /**
     * internal input source implementation that is based on entries of this archive.
     */
    private class RafInputSource extends VaultInputSource {

        private final EntryImpl entry;

        private RafInputSource(EntryImpl entry) {
            this.entry = entry;
        }

        @Override
        public InputStream getByteStream() {
            return createInputStream(entry);
        }

        public long getContentLength() {
            return entry.len;
        }

        public long getLastModified() {
            return entry.time;
        }
    }

    /**
     * internal input stream implementation that read from the random access file.
     */
    private class RafInputStream extends InputStream {

        private long pos;

        private long end;

        private long mark;

        private RafInputStream(EntryImpl entry) {
            pos = entry.pos;
            end = pos + entry.len;
        }

        @Override
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

    /**
     * archive entry implementation
     */
    private static class EntryImpl implements Entry {

        public final String name;

        public final long time;

        public final long pos;

        public final long len;

        public Map<String, EntryImpl> children;

        private EntryImpl(String name) {
            this.name = name;
            this.time = 0;
            pos = -1;
            len = 0;
        }

        private EntryImpl(String name, long time, long pos, long len) {
            this.name = name;
            this.time = time;
            this.pos = pos;
            this.len = len;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public boolean isDirectory() {
            return pos < 0;
        }

        public EntryImpl add(EntryImpl e) {
            if (children == null) {
                children = new LinkedHashMap<String, EntryImpl>();
            }
            children.put(e.getName(), e);
            return e;
        }

        public EntryImpl add(String name) {
            EntryImpl e;
            if (children == null) {
                children = new LinkedHashMap<String, EntryImpl>();
            } else {
                e = children.get(name);
                if (e != null) {
                    return e;
                }
            }
            e = new EntryImpl(name);
            children.put(name, e);
            return e;
        }

        @Override
        public Collection<? extends Entry> getChildren() {
            return children == null
                    ? Collections.<EntryImpl>emptyList()
                    : children.values();
        }

        @Override
        public Entry getChild(String name) {
            return children == null ? null : children.get(name);
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
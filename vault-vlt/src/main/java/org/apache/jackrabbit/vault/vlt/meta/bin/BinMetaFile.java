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

package org.apache.jackrabbit.vault.vlt.meta.bin;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.jackrabbit.vault.fs.api.DumpContext;
import org.apache.jackrabbit.vault.fs.api.Dumpable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <code>MetaFile</code>...
 */
public class BinMetaFile implements Dumpable {

    /**
     * default logger
     */
    private static final Logger log = LoggerFactory.getLogger(BinMetaFile.class);

    private final File file;

    private RandomAccessFile raf = null;

    private HeaderBlock header = null;

    private Map<Long, Block> blocks = new TreeMap<Long, Block>();

    private Map<Long, Link> links = new TreeMap<Long, Link>();

    private List<Block> added = new LinkedList<Block>();

    public BinMetaFile(File file) {
        this.file = file;
    }

    public boolean exists() {
        return file.exists();
    }

    public File getFile() {
        return file;
    }

    public HeaderBlock open() throws IOException {
        if (header == null) {
            if (!file.exists()) {
                file.createNewFile();
            }
            openRaf();
            header = new HeaderBlock();
            if (raf.length() == 0) {
                header.write(raf);
            } else {
                raf.seek(0);
                header.readData(raf);
            }
            blocks.put(0L, header);
        }
        return header;
    }

    public Block getBlock(long offset) throws IOException {
        open();
        Block block = blocks.get(offset);
        if (block == null) {
            if (offset >= raf.length()) {
                return null;
            }
            raf.seek(offset);
            block = Block.read(raf);
            blocks.put(offset, block);
        }
        return block;
    }

    public EntryBlock getEntryBlock(String name) {
        Iterator<Block> iter = blocks();
        while (iter.hasNext()) {
            Block block = iter.next();
            if (block instanceof EntryBlock) {
                EntryBlock e = (EntryBlock) block;
                if (e.getName().equals(name)) {
                    return e;
                }
            }
        }
        return null;
    }

    public Iterator<Block> blocks() {
        return new Iterator<Block>() {

            private Block next = header;
            public boolean hasNext() {
                return next != null;
            }

            public Block next() {
                Block ret = next;
                try {
                    next = getBlock(ret.next());
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
                return ret;
            }

            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    public DataBlock createDataBlock(long size) throws IOException {
        DataBlock d = new DataBlock(size);
        raf.seek(raf.length());
        d.offset = raf.getFilePointer();
        d.writeHeader(raf);
        if (size > 0) {
            raf.seek(raf.getFilePointer() + size - 1);
            raf.write(0);
        }
        blocks.put(d.offset, d);
        return d;
    }

    public DataBlock createDataBlock(byte[] buffer) throws IOException {
        DataBlock d = new DataBlock(buffer.length);
        raf.seek(raf.length());
        d.offset = raf.getFilePointer();
        d.writeHeader(raf);
        raf.write(buffer);
        blocks.put(d.offset, d);
        return d;
    }

    public DataBlock createDataBlock(InputStream in) throws IOException {
        DataBlock d = new DataBlock(0);
        raf.seek(raf.length());
        d.offset = raf.getFilePointer();
        byte[] buffer = new byte[4096];
        int rd;
        while ((rd = in.read(buffer)) > 0) {
            raf.write(buffer, 0, rd);
            d.length += rd;
        }
        raf.seek(d.offset);
        d.writeHeader(raf);
        blocks.put(d.offset, d);
        return d;
    }

    public byte[] getBytes(DataBlock blk) throws IOException {
        byte[] buf = new byte[(int)blk.length - 8];
        raf.seek(blk.offset + 8);
        raf.read(buf);
        return buf;
    }

    public InputStream getInputStream(DataBlock blk) throws IOException {
        raf.seek(blk.offset + 8);
        final long end = blk.offset + blk.length;
        return new InputStream() {

            public int read() throws IOException {
                if (raf.getFilePointer() < end) {
                    return raf.read();
                } else {
                    return -1;
                }
            }

            public int read(byte b[]) throws IOException {
                return read(b, 0, b.length);
            }

            public int read(byte b[], int off, int len) throws IOException {
                if (end - raf.getFilePointer() < len) {
                    len = (int) (end - raf.getFilePointer());
                }
                if (len == 0) {
                    return -1;
                }
                return raf.read(b, off, len);
            }
        };
    }

    public void delete(Block blk) throws IOException {
        if (blk != null) {
            for (long o: blk.getOffsets()) {
                if (o > 0) {
                    delete(getBlock(o));
                }
            }
            invalidate(blk);
        }
    }
    
    private void invalidate(Block blk) {
        InvalidBlock ib = new InvalidBlock(blk.offset, blk.length);
        ib.modified = true;
        blocks.put(ib.offset, ib);
    }

    public void linkBlock(Block block, int id, Block target) throws IOException {
        Link link = links.get(block.offset + id);
        if (link == null) {
            long offset = block.getLinkOffset(id);
            if (offset != 0) {
                invalidate(getBlock(offset));
            }
            link = new Link(block, target, id);
        } else {
            invalidate(link.target);
        }
        links.put(link.getKey(), link);
        block.linkModified(target.offset, id);
    }
    
    @SuppressWarnings("unchecked")
    public <T extends Block> T getLinkedBlock(Block block, int id, Class<T> type, boolean create)
            throws IOException {
        Link link = links.get(block.offset + id);
        if (link == null) {
            long offset = block.getLinkOffset(id);
            T blk;
            if (offset == 0) {
                if (!create) {
                    return null;
                }
                try {
                    blk = type.newInstance();
                } catch (Exception e) {
                    throw new InternalError(e.toString());
                }
            } else {
                blk = (T) getBlock(offset);
            }
            link = new Link(block, blk, id);
            links.put(link.getKey(), link);
        }
        return (T) link.target;
    }

    public void add(Block block) {
        added.add(block);
    }

    public void dump(DumpContext ctx, boolean isLast) {
        try {
            open();
            raf.seek(header.length());
            Block block = header;
            while (block != null) {
                block.dump(ctx, isLast);
                block = Block.read(raf);
            }
        } catch (IOException e) {
            log.error("Error while accessing file", e);
        }
    }

    public void sync() throws IOException {
        raf.seek(raf.length());

        // first check the linked blocks
        for (Link link: links.values()) {
            Block block = link.target;
            if (block.needsRelocate()) {
                if (block.offset > 0) {
                    // invalidate old block
                    InvalidBlock ib = new InvalidBlock(block.offset, block.length);
                    ib.modified = true;
                    blocks.put(block.offset, ib);
                }
                block.write(raf);
                blocks.put(block.offset, block);
                link.block.linkModified(block.offset, link.id);
            }
        }

        // write new blocks
        for (Block block: added) {
            block.write(raf);
            blocks.put(block.offset, block);
        }
        added.clear();

        // then write modified
        for (Block block: blocks.values()) {
            if (block.isModified()) {
                raf.seek(block.offset);
                block.write(raf);
            }
        }
    }

    protected RandomAccessFile openRaf() throws FileNotFoundException {
        if (raf == null) {
            raf = new RandomAccessFile(file, "rw");
        }
        return raf;
    }

    public void close() throws IOException {
        if (raf != null) {
            sync();
            try {
                raf.close();
            } catch (IOException e) {
                // ignore
            }
            raf = null;
        }
        header = null;
    }

    public static long getLong(byte[] b, int offs) {
        return ((long)  (b[offs] & 0xFF) << 56) +
                ((long) (b[1 + offs] & 0xFF) << 48) +
                ((long) (b[2 + offs] & 0xFF) << 40) +
                ((long) (b[3 + offs] & 0xFF) << 32) +
                ((long) (b[4 + offs] & 0xFF) << 24) +
                ((long) (b[5 + offs] & 0xFF) << 16) +
                ((long) (b[6 + offs] & 0xFF) << 8) +
                ((long) (b[7 + offs] & 0xFF));
    }

    public static void setLong(byte[] b, int offs, long v) {
        b[offs]   = (byte) ((v >>> 56) & 0xFF);
        b[offs+1] = (byte) ((v >>> 48) & 0xFF);
        b[offs+2] = (byte) ((v >>> 40) & 0xFF);
        b[offs+3] = (byte) ((v >>> 32) & 0xFF);
        b[offs+4] = (byte) ((v >>> 24) & 0xFF);
        b[offs+5] = (byte) ((v >>> 16) & 0xFF);
        b[offs+6] = (byte) ((v >>>  8) & 0xFF);
        b[offs+7] = (byte) ((v >>>  0) & 0xFF);
    }

}
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

import java.io.IOException;
import java.io.RandomAccessFile;

import org.apache.jackrabbit.vault.fs.api.DumpContext;
import org.apache.jackrabbit.vault.fs.api.Dumpable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <code>Block</code>...
 */
public abstract class Block implements Dumpable {

    /**
     * default logger
     */
    private static final Logger log = LoggerFactory.getLogger(Block.class);

    private static final long[] EMPTY_LONG_ARRAY = new long[0];

    public static final byte TYPE_INVALID = 0;

    public static final byte TYPE_HEADER = 1;

    public static final byte TYPE_DATA = 2;

    public static final byte TYPE_INFO = 3;

    public static final byte TYPE_ENTRY = 4;

    public static final byte TYPE_PROPS = 5;

    protected long length;

    protected long offset;

    protected boolean modified;

    protected boolean relocate;

    protected Block() {
        relocate = true;
        length = 8;
    }

    public Block(long offset, long length) {
        this.offset = offset;
        this.length = length;
    }

    public long length() {
        return length;
    }

    public long offset() {
        return offset;
    }

    public long next() {
        return offset + length;
    }

    public static Block read(RandomAccessFile raf) throws IOException {
        long offset = raf.getFilePointer();
        if (offset >= raf.length()) {
            return null;
        }
        byte[] tlen = new byte[8];
        raf.read(tlen);
        byte type = tlen[0];
        tlen[0] = 0;
        long length = BinMetaFile.getLong(tlen, 0);
        Block blk;
        switch (type) {
            case TYPE_INVALID:
                blk = new InvalidBlock(offset, length);
                break;
            case TYPE_DATA:
                blk = new DataBlock(offset, length);
                break;
            case TYPE_INFO:
                blk = new InfoBlock(offset, length);
                break;
            case TYPE_ENTRY:
                blk = new EntryBlock(offset, length);
                break;
            case TYPE_PROPS:
                blk = new PropertiesBlock(offset, length);
                break;
            default:
                throw new InternalError("invalid block type " + type);
        }
        blk.readData(raf);
        raf.seek(blk.next());
        return blk;
    }

    protected void writeHeader(RandomAccessFile raf) throws IOException {
        byte[] tlen = new byte[8];
        BinMetaFile.setLong(tlen, 0, length);
        tlen[0] = getType();
        raf.write(tlen);
    }

    public void write(RandomAccessFile raf) throws IOException {
        offset = raf.getFilePointer();
        // write (old) header
        writeHeader(raf);
        writeData(raf);
        long length = raf.getFilePointer() - offset;

        // write new header if length is different
        if (length != this.length) {
            this.length = length;
            raf.seek(offset);
            writeHeader(raf);
            raf.seek(next());
        }
        modified = false;
        relocate = false;
    }

    public boolean isModified() {
        return modified;
    }

    public boolean needsRelocate() {
        return relocate;
    }

    public long getLinkOffset(int id) {
        return getOffsets()[id];
    }

    public void linkModified(long targetOffset, int id) {
        final long[] offsets = getOffsets();
        if (targetOffset != offsets[id]) {
            offsets[id] = targetOffset;
            modified = true;
        }
    }


    public abstract byte getType();

    public long[] getOffsets() {
        return EMPTY_LONG_ARRAY;
    }

    public void readData(RandomAccessFile raf) throws IOException {
        // do nothing
    }

    public void writeData(RandomAccessFile raf) throws IOException {
        // just skip
        raf.seek(offset + length);
    }

    public void dump(DumpContext ctx, boolean isLast) {
        ctx.printf(isLast, "%08x %04x %s%n", offset, length, getClass().getSimpleName());
    }

}
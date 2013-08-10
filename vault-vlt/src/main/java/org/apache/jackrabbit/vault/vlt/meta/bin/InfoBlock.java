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
import org.apache.jackrabbit.vault.util.MD5;

/**
 * <code>InfoBlock</code>...
 */
public class InfoBlock extends Block {

    public static final int ID_DATA = 0;

    public static final int ID_PROPS = 1;

    private long size;

    private long date;

    private long[] offsets = new long[ID_PROPS + 1];

    private long md5Msb;

    private long md5Lsb;

    private String contentType = "";

    public InfoBlock() {
    }

    public InfoBlock(long offset, long length) {
        super(offset, length);
    }

    public byte getType() {
        return Block.TYPE_INFO;
    }

    public void readData(RandomAccessFile raf) throws IOException {
        size = raf.readLong();
        date = raf.readLong();
        for (int i=0; i<offsets.length; i++) {
            offsets[i] = raf.readLong();
        }
        md5Msb = raf.readLong();
        md5Lsb = raf.readLong();
        contentType = raf.readUTF();
    }

    public void writeData(RandomAccessFile raf) throws IOException {
        raf.writeLong(size);
        raf.writeLong(date);
        for (long off : offsets) {
            raf.writeLong(off);
        }
        raf.writeLong(md5Msb);
        raf.writeLong(md5Lsb);
        raf.writeUTF(contentType);
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        if (size != this.size) {
            this.size = size;
            modified = true;
        }
    }

    public long getDate() {
        return date;
    }

    public void setDate(long date) {
        if (this.date != date) {
            this.date = date;
            modified = true;
        }
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        if (!contentType.equals(this.contentType)) {
            this.contentType = contentType;
            relocate = true;
        }
    }

    public MD5 getMd5() {
        return new MD5(md5Msb, md5Lsb);
    }

    public void setMd5(MD5 md5) {
        if (md5Msb != md5.getMsb() || md5Lsb != md5.getLsb()) {
            md5Msb = md5.getMsb();
            md5Lsb = md5.getLsb();
            modified = true;
        }
    }

    public long[] getOffsets() {
        return offsets;
    }

    @Override
    public void dump(DumpContext ctx, boolean isLast) {
        super.dump(ctx, isLast);
        ctx.printf(false, "  size=%d%n", size);
        ctx.printf(false, "  date=%d%n", date);
        ctx.printf(false, "  data=%08x%n", offsets[ID_DATA]);
        ctx.printf(false, "   md5=%s%n", getMd5().toString());
        ctx.printf(false, "  type=%s%n", contentType);
        ctx.printf(true, "  props=%08x%n", offsets[ID_PROPS]);
    }
    
}
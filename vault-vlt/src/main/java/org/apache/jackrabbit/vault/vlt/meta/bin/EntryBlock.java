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

/**
 * <code>EntryBlock</code>...
 */
public class EntryBlock extends Block {

    public static final int ID_WORK = 0;
    public static final int ID_BASE = 1;
    public static final int ID_MINE = 2;
    public static final int ID_THEIRS = 3;
    public static final int ID_TMP = 4;

    private String name = "";

    private String relPath = "";

    private String aggPath = "";

    private long[] offsets = new long[ID_TMP + 1];

    public EntryBlock(String name, String aggPath, String relPath) {
        this.name = name;
        this.aggPath = aggPath;
        this.relPath = relPath;
    }

    public EntryBlock(long offset, long length) {
        super(offset, length);
    }

    public byte getType() {
        return Block.TYPE_ENTRY;
    }

    public void readData(RandomAccessFile raf) throws IOException {
        name = raf.readUTF();
        aggPath = raf.readUTF();
        relPath = raf.readUTF();
        for (int i=0; i<offsets.length; i++) {
            offsets[i] = raf.readLong();
        }
    }

    public void writeData(RandomAccessFile raf) throws IOException {
        raf.writeUTF(name);
        raf.writeUTF(aggPath);
        raf.writeUTF(relPath);
        for (long off : offsets) {
            raf.writeLong(off);
        }
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        relocate = true;
    }

    public String getRelPath() {
        return relPath;
    }

    public void setRelPath(String relPath) {
        this.relPath = relPath;
        relocate = true;
    }

    public String getAggregatePath() {
        return aggPath;
    }

    public void setAggregatePath(String aggPath) {
        this.aggPath = aggPath;
        relocate = true;
    }

    public long[] getOffsets() {
        return offsets;
    }

    @Override
    public void dump(DumpContext ctx, boolean isLast) {
        ctx.printf(false, "  name=%s%n", name);
        ctx.printf(false, "  work=%08x%n", offsets[ID_WORK]);
        ctx.printf(false, "  base=%08x%n", offsets[ID_BASE]);
        ctx.printf(false, "  mine=%08x%n", offsets[ID_MINE]);
        ctx.printf(false, "  thrs=%08x%n", offsets[ID_THEIRS]);
        ctx.printf(true, "  temp=%08x%n", offsets[ID_TMP]);
    }

}
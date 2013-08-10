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
 * <code>HeaderBlock</code>
 */
public class HeaderBlock extends Block {

    private static final byte[] MAGIC = new byte[]{'V', 'L', 'T'};

    private static final byte VERSION_1 = 1;

    private static final int LENGTH = 4 + 8;

    private static final int OFFSET_VERSION = 3;

    private byte version = VERSION_1;

    private long[] offsets = new long[1];

    public static final int ID_PROPERTIES = 0;

    public HeaderBlock() {
        super(0, LENGTH);
    }

    public byte getVersion() {
        return version;
    }

    public void setVersion(byte version) {
        this.version = version;
        modified = true;
    }

    public long[] getOffsets() {
        return offsets;
    }

    public void readData(RandomAccessFile raf) throws IOException {
        byte[] data = new byte[4];
        raf.read(data);
        if (data[0] != MAGIC[0] || data[1] != MAGIC[1] || data[2] != MAGIC[2]) {
            throw new IllegalArgumentException("invalid magic " + new String(data, 0, 3));
        }
        version = data[OFFSET_VERSION];
        if (version > VERSION_1) {
            throw new IllegalArgumentException("unsupported version " + version);
        }
        offsets[0] = raf.readLong();
    }

    public void write(RandomAccessFile raf) throws IOException {
        raf.seek(0);
        raf.write(MAGIC);
        raf.writeByte(version);
        raf.writeLong(offsets[0]);
        modified = false;
    }

    public byte getType() {
        return Block.TYPE_HEADER;
    }

    @Override
    public void dump(DumpContext ctx, boolean isLast) {
        ctx.printf(false, "HeaderBlock%n");
        ctx.printf(false, "  version=%d%n", version);
        ctx.printf(false, "  propOff=%08x%n", offsets[0]);
    }
}
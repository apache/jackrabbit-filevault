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
import java.util.HashMap;
import java.util.Map;

import org.apache.jackrabbit.vault.fs.api.DumpContext;

/**
 * <code>InfoBlock</code>...
 */
public class PropertiesBlock extends Block {

    private Map<String, String> props = new HashMap<String, String>();

    public PropertiesBlock() {
    }

    public PropertiesBlock(long offset, long length) {
        super(offset, length);
    }

    public byte getType() {
        return Block.TYPE_PROPS;
    }

    public void readData(RandomAccessFile raf) throws IOException {
        int size = raf.readInt();
        for (int i=0; i<size; i++) {
            String name = raf.readUTF();
            String v = raf.readUTF();
            props.put(name, v);
        }
    }

    public void writeData(RandomAccessFile raf) throws IOException {
        raf.writeInt(props.size());
        for (Map.Entry<String, String> e: props.entrySet()) {
            raf.writeUTF(e.getKey());
            raf.writeUTF(e.getValue());
        }
    }

    public String getProperty(String name) {
        return props.get(name);
    }

    public void setProperty(String name, String value) {
        if (value == null) {
            relocate = props.remove(name) != null;
        } else {
            relocate = !value.equals(props.put(name, value));
        }
    }

    @Override
    public void dump(DumpContext ctx, boolean isLast) {
        super.dump(ctx, isLast);
        for (Map.Entry<String, String> e: props.entrySet()) {
            ctx.printf(false, "  %s=%s%n", e.getKey(), e.getValue());
        }
    }
}
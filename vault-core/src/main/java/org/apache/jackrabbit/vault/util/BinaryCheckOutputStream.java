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

package org.apache.jackrabbit.vault.util;

import java.io.IOException;
import java.io.OutputStream;

/**
 * <code>BinaryCheckOutputStream</code>...
 */
public class BinaryCheckOutputStream extends OutputStream {

    private final OutputStream out;

    private boolean binary;

    private static final boolean[] binaries = new boolean[256];
    static {
        for (int i=0; i<32; i++) {
            binaries[i] = true;
        }
        binaries['\r'] = false;
        binaries['\n'] = false;
        binaries['\t'] = false;
        binaries['\b'] = false;
        binaries['\f'] = false;
    }

    public BinaryCheckOutputStream(OutputStream out) {
        this.out = out;
    }

    public boolean isBinary() {
        return binary;
    }

    public void write(int b) throws IOException {
        if (!binary) {
            binary = binaries[b & 0xff];
        }
        out.write(b);
    }

    public void write(byte[] b) throws IOException {
        for (int i=0; i < b.length && !binary; i++) {
            binary = binaries[b[i] & 0xff];
        }
        out.write(b);
    }

    public void write(byte[] b, int off, int len) throws IOException {
        for (int i=0; i < len && !binary; i++) {
            binary = binaries[b[i+off] & 0xff];
        }
        out.write(b, off, len);
    }

    public void flush() throws IOException {
        out.flush();
    }

    public void close() throws IOException {
        out.close();
    }

}
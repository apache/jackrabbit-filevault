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
 * Provides an output stream wrapper that detects line feed sequences and
 * replaces them by new ones.
 *
 */
public class LineOutputStream extends OutputStream {

    public static final byte[] LS_BINARY = null;

    public static final byte[] LS_UNIX = new byte[]{0x0a};

    public static final byte[] LS_WINDOWS = new byte[]{0x0d, 0x0a};

    public static final byte[] LS_NATIVE = System.getProperty("line.separator").getBytes();

    private byte[] buffer = new byte[8192];

    private byte[] lineFeed = LS_NATIVE;

    private int pos = 0;

    private static final char STATE_INIT = ' ';
    private static final char STATE_CR = 'c';
    private static final char STATE_LF = 'l';
    private static final char STATE_CRLF = 'f';

    private char state = STATE_INIT;

    private final OutputStream out;

    public LineOutputStream(OutputStream out, byte[] ls) {
        this.out = out;
        if (ls != null) {
            this.lineFeed = ls;
        }
    }

    public void write(int b) throws IOException {
        if (b == 0x0a) {
            switch (state) {
                case STATE_INIT:
                    state = STATE_LF;
                    break;
                case STATE_CR:
                    state = STATE_CRLF;
                    break;
                case STATE_LF:
                    flush(true);
                    state = STATE_LF;
                    break;
                case STATE_CRLF:
                    flush(true);
                    state = STATE_LF;
            }
        } else if (b == 0x0d) {
            switch (state) {
                case STATE_INIT:
                    state = STATE_CR;
                    break;
                case STATE_LF:
                    state = STATE_CRLF;
                    break;
                case STATE_CR:
                    flush(true);
                    state = STATE_CR;
                    break;
                case STATE_CRLF:
                    flush(true);
                    state = STATE_CR;
            }
        } else {
            if (state != STATE_INIT) {
                flush(true);
                state = STATE_INIT;
            }
            if (pos == buffer.length) {
                flush();
            }
            buffer[pos++] = (byte) (b & 0xff);
        }
    }

    public void flush(boolean addLF) throws IOException {
        flush();
        if (addLF) {
            out.write(lineFeed);
        }
        out.flush();
    }

    public void flush() throws IOException {
        out.write(buffer, 0, pos);
        pos = 0;
        out.flush();
    }

    public void close() throws IOException {
        // check for pending lfs
        flush(state != STATE_INIT);
        out.close();
    }

}
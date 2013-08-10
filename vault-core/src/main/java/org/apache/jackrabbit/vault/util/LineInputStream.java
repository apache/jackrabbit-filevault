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
import java.io.InputStream;

/**
 * Provides an input stream wrapper that detects line feed sequences and 
 * replaces them by new ones.
 *
 */
public class LineInputStream extends InputStream {

    public static final byte[] LS_UNIX = new byte[]{0x0a};

    public static final byte[] LS_WINDOWS = new byte[]{0x0d, 0x0a};

    public static final byte[] LS_NATIVE = System.getProperty("line.separator").getBytes();

    private byte[] buffer = new byte[8192];

    private byte[] lineFeed = LS_NATIVE;

    private byte[] lineSpool;

    private int pos = 0;

    private int end = 0;

    private static final char STATE_INIT = ' ';
    private static final char STATE_CR = 'c';
    private static final char STATE_LF = 'l';
    private static final char STATE_CRLF = 'f';

    private char state = STATE_INIT;

    boolean isEof = false;

    private byte[] spool;

    private int spoolPos = 0;

    private final InputStream in;

    public LineInputStream(InputStream in, byte[] ls) {
        this.in = in;
        if (ls != null) {
            lineFeed = ls;
        }
        lineSpool = new byte[lineFeed.length + 1];
        System.arraycopy(lineFeed, 0, lineSpool, 0, lineFeed.length);
    }

    private int fillBuffer() throws IOException {
        int ret = in.read(buffer, end, buffer.length - end);
        if (ret >= 0) {
            end += ret;
        } else {
            isEof = true;
        }
        return ret;
    }

    public int read() throws IOException {
        final byte[] one = new byte[1];
        if (read(one) == -1) {
            return -1;
        }
        return one[0];
    }

    public int read(byte b[], int off, int len) throws IOException {
        if (isEof) {
            if (spool == null) {
                if (state != STATE_INIT) {
                    spool = lineFeed;
                    state = STATE_INIT;
                } else {
                    return -1;
                }
            }
        }
        int total = 0;
        while (total < len) {
            if (spool != null) {
                b[off+(total++)] = spool[spoolPos++];
                if (spoolPos == spool.length) {
                    spool = null;
                    spoolPos = 0;
                }
            } else {
                if (pos == end) {
                    int ret = fillBuffer();
                    if (ret == 0 && pos == end) {
                        // in this case we didn't get more, so flush
                        pos = end = 0;
                        continue;
                    } else if (ret == -1) {
                        break;
                    }
                }
                byte c = buffer[pos++];
                if (c == 0x0a) {
                    switch (state) {
                        case STATE_INIT:
                            state = STATE_LF;
                            break;
                        case STATE_CR:
                            state = STATE_CRLF;
                            break;
                        case STATE_LF:
                            spool = lineFeed;
                            break;
                        case STATE_CRLF:
                            spool = lineFeed;
                            state = STATE_LF;
                    }
                } else if (c == 0x0d) {
                    switch (state) {
                        case STATE_INIT:
                            state = STATE_CR;
                            break;
                        case STATE_LF:
                            state = STATE_CRLF;
                            break;
                        case STATE_CR:
                            spool = lineFeed;
                            break;
                        case STATE_CRLF:
                            spool = lineFeed;
                            state = STATE_CR;
                    }
                } else {
                    if (state != STATE_INIT) {
                        spool = lineSpool;
                        lineSpool[lineSpool.length - 1] = c;
                        state = STATE_INIT;
                    } else {
                        b[off + (total++)] = c;
                    }
                }
            }
        }
        return total;
    }

    public void close() throws IOException {
        in.close();
    }

}
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
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

import org.apache.commons.io.input.CloseShieldInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@code InputStreamPump}...
 */
public class InputStreamPump extends InputStream {

    /**
     * default logger
     */
    private static final Logger log = LoggerFactory.getLogger(InputStreamPump.class);

    private final InputStream source;

    private final PipedOutputStream out;

    private final PipedInputStream in;

    private Thread pumpThread;

    private Exception error;

    public InputStreamPump(InputStream source, final Pump pump) throws IOException {
        this.source = source;

        out = new PipedOutputStream();
        in = new PipedInputStream(out, 8192);

        pumpThread = new Thread(new Runnable() {
            public void run() {
                try {
                    pump.run(new CloseShieldInputStream(in));
                    // ensure that input stream is pumping in case it didn't read to the end
                    byte[] buffer = new byte[8192];
                    while (in.read(buffer) >= 0);
                } catch (Exception e) {
                    error = e;
                    log.error("Error while processing input stream", e);
                }
            }
        });
        pumpThread.start();
    }

    public interface Pump {
        void run(InputStream in) throws Exception;
    }

    public Exception getError() {
        return error;
    }

    @Override
    public int read() throws IOException {
        int b = source.read();
        if (b >= 0) {
            out.write(b);
        }
        return b;
    }

    @Override
    public int read(byte[] b) throws IOException {
        int len = source.read(b);
        if (len > 0) {
            out.write(b, 0, len);
        }
        return len;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int read = source.read(b, off, len);
        if (read > 0) {
            out.write(b, off, read);
        }
        return read;
    }

    @Override
    public long skip(long n) throws IOException {
        return source.skip(n);
    }

    @Override
    public int available() throws IOException {
        return source.available();
    }

    @Override
    public void close() throws IOException {
        source.close();
        out.flush();
        try {
            out.close();
            pumpThread.join();
            in.close();
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
    }

    @Override
    public void mark(int readlimit) {
    }

    @Override
    public void reset() throws IOException {
    }

    @Override
    public boolean markSupported() {
        return false;
    }
}
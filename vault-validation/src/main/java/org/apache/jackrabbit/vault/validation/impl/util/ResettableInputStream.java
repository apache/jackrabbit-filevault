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
package org.apache.jackrabbit.vault.validation.impl.util;

import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.TeeInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An input stream wrapper which is resettable, i.e. allows {{@link #mark(int)} and {{@link #reset()} to be called.
 * It uses a temporary file for buffering the given input stream, except for the case where it is already a {@link FileInputStream}
 * in which case it uses the seeking methods of that.
 */
public class ResettableInputStream extends InputStream {

    private final Path tmpFile;
    private final OutputStream tmpOutputStream ;
    private InputStream currentInput; // might already point to a buffered one
    private boolean isAtStart;

    /**
     * the default logger
     */
    private static final Logger log = LoggerFactory.getLogger(ResettableInputStream.class);
    
    public ResettableInputStream(InputStream in) throws IOException {
        InputStream unwrappedInput = EnhancedBufferedInputStream.tryUnwrap(in);
        if (!(unwrappedInput instanceof FileInputStream)) {
            tmpFile = Files.createTempFile("vlt_tmp", null);
            tmpOutputStream = new BufferedOutputStream(Files.newOutputStream(tmpFile));
            log.debug("Caching input stream in temp file '{}' for later evaluation by another validator", tmpFile);
            currentInput = new TeeInputStream(in, tmpOutputStream);
        } else {
            tmpFile = null;
            tmpOutputStream = null;
            currentInput = in;
        }
    }

    @Override
    public synchronized void reset() throws IOException {
        InputStream unwrappedInput = EnhancedBufferedInputStream.tryUnwrap(currentInput);
        if (unwrappedInput instanceof FileInputStream) {
            FileInputStream fis = FileInputStream.class.cast(unwrappedInput);
            fis.getChannel().position(0);
            currentInput = new EnhancedBufferedInputStream(unwrappedInput);
        } else {
            if (tmpOutputStream == null) {
                throw new IllegalStateException("No output stream which buffers to a temp file has been created");
            }
            // spool all bytes to the temp file
            IOUtils.skip(currentInput, Long.MAX_VALUE);
            tmpOutputStream.close();
            currentInput = new EnhancedBufferedInputStream(Files.newInputStream(tmpFile));
        }
    }

    @Override
    public void close() throws IOException {
        // don't close underlying input stream
        if (tmpOutputStream != null) {
            tmpOutputStream.close();
        }
        if (tmpFile != null) {
            Files.delete(tmpFile);
        }
        super.close();
    }

    
    @Override
    public int read() throws IOException {
        if (isAtStart) {
            isAtStart = false;
        }
        return currentInput.read();
    }

    @Override
    public int read(byte[] b) throws IOException {
        return currentInput.read(b);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        return currentInput.read(b, off, len);
    }

    @Override
    public synchronized void mark(int readlimit) {
        if (!isAtStart) {
            throw new IllegalStateException("Currently only marking at the beginning of the input stream is supported");
        }
    }

    @Override
    public boolean markSupported() {
        return true;
    }
}

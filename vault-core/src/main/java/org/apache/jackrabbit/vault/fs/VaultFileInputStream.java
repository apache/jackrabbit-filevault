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

package org.apache.jackrabbit.vault.fs;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.jcr.RepositoryException;

import org.apache.jackrabbit.vault.fs.api.AccessType;
import org.apache.jackrabbit.vault.fs.api.Artifact;
import org.apache.jackrabbit.vault.fs.api.VaultFile;
import org.apache.jackrabbit.vault.fs.impl.VaultFileImpl;

/**
 * Implements an input stream on a jcr file file. It accesses the artifact of the
 * platform file an wraps either it's input stream or provides one using a tmp
 * file.
 *
 * If possible, use the {@link VaultFileImpl#getArtifact()} directly.
 *
 */
public class VaultFileInputStream extends InputStream {

    /**
     * The base input stream
     */
    private final InputStream base;

    /**
     * Temp file for spooling
     */
    private Path tmpFile;

    /**
     * Creates a new input stream on the given file. If the file is a
     * directory an IOException is thrown.
     *
     * @param file the file
     * @throws IOException if an I/O error occurs.
     */
    public VaultFileInputStream(VaultFile file) throws IOException {
        Artifact a = file.getArtifact();
        if (a == null || a.getPreferredAccess() == AccessType.NONE) {
            throw new IOException("invalid access.");
        }
        try {
            if (a.getPreferredAccess() == AccessType.STREAM) {
                base = a.getInputStream();
            } else {
                tmpFile = Files.createTempFile("vltfs", ".spool");
                try (OutputStream out = Files.newOutputStream(tmpFile)) {
                    a.spool(out);
                }
                base = Files.newInputStream(tmpFile);
            }
        } catch (RepositoryException e) {
            throw new IOException(e.toString());
        }
    }

    @Override
    public int read() throws IOException {
        return base.read();
    }

    @Override
    public int read(byte[] b) throws IOException {
        return base.read(b);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        return base.read(b, off, len);
    }

    @Override
    public long skip(long n) throws IOException {
        return base.skip(n);
    }

    @Override
    public int available() throws IOException {
        return base.available();
    }

    @Override
    public void close() throws IOException {
        base.close();
        if (tmpFile != null) {
            Files.delete(tmpFile);;
            tmpFile = null;
        }
    }

    @Override
    public synchronized void mark(int readlimit) {
        base.mark(readlimit);
    }

    @Override
    public synchronized void reset() throws IOException {
        base.reset();
    }

    @Override
    public boolean markSupported() {
        return base.markSupported();
    }
}
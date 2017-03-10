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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

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
    private File tmpFile;

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
                tmpFile = File.createTempFile("vltfs", ".spool");
                FileOutputStream out = new FileOutputStream(tmpFile);
                a.spool(out);
                out.close();
                base = new FileInputStream(tmpFile);
            }
        } catch (RepositoryException e) {
            throw new IOException(e.toString());
        }
    }

    /**
     * {@inheritDoc}
     */
    public int read() throws IOException {
        return base.read();
    }

    /**
     * {@inheritDoc}
     */
    public int read(byte[] b) throws IOException {
        return base.read(b);
    }

    /**
     * {@inheritDoc}
     */
    public int read(byte[] b, int off, int len) throws IOException {
        return base.read(b, off, len);
    }

    /**
     * {@inheritDoc}
     */
    public long skip(long n) throws IOException {
        return base.skip(n);
    }

    /**
     * {@inheritDoc}
     */
    public int available() throws IOException {
        return base.available();
    }

    /**
     * {@inheritDoc}
     */
    public void close() throws IOException {
        base.close();
        if (tmpFile != null) {
            tmpFile.delete();
            tmpFile.deleteOnExit();
            tmpFile = null;
        }
    }

    /**
     * {@inheritDoc}
     */
    public void mark(int readlimit) {
        base.mark(readlimit);
    }

    /**
     * {@inheritDoc}
     */
    public void reset() throws IOException {
        base.reset();
    }

    /**
     * {@inheritDoc}
     */
    public boolean markSupported() {
        return base.markSupported();
    }
}
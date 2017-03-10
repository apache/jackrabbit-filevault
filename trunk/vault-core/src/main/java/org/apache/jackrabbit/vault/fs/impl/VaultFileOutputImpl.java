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

package org.apache.jackrabbit.vault.fs.impl;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import javax.jcr.RepositoryException;

import org.apache.jackrabbit.vault.fs.api.VaultFileOutput;
import org.apache.jackrabbit.vault.fs.api.VaultInputSource;
import org.apache.jackrabbit.vault.util.FileInputSource;

/**
 * Provides methods for writing jcr files. This can either be done by providing
 * an input source or by fetching an output stream. this output stream can be
 * acquired via a {@link TransactionImpl}.
 *
 */
public class VaultFileOutputImpl implements VaultFileOutput {

    private final TransactionImpl.Change tx;

    private OutputStream out;

    private File tmpFile;

    private VaultInputSource is;

    protected VaultFileOutputImpl(TransactionImpl.Change tx) {
        this.tx = tx;
    }

    protected VaultFileOutputImpl(TransactionImpl.Change tx, VaultInputSource input) {
        this.tx = tx;
        this.is = input;
    }

    public OutputStream getOutputStream() throws IOException {
        if (out != null) {
            throw new IOException("Output stream already obtained.");
        }
        tmpFile = File.createTempFile("vltfs", ".tmp");
        tmpFile.deleteOnExit();
        out = new FileOutputStream(tmpFile);
        return out;
    }

    /*
    public void setArtfiactType(ArtifactType artfiactType) {
        this.artfiactType = artfiactType;
    }
    */

    public void setContentType(String contentType) {
        tx.setContentType(contentType);
    }

    public void close() throws IOException, RepositoryException {
        if (out != null) {
            out.close();
            is = new FileInputSource(tmpFile);
            tx.setInputSource(is);
        }
    }

}
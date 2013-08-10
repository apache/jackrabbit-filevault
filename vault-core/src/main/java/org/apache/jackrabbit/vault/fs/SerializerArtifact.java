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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.jcr.RepositoryException;

import org.apache.commons.io.output.DeferredFileOutputStream;
import org.apache.jackrabbit.vault.fs.api.AccessType;
import org.apache.jackrabbit.vault.fs.api.Artifact;
import org.apache.jackrabbit.vault.fs.api.ArtifactType;
import org.apache.jackrabbit.vault.fs.api.ExportArtifact;
import org.apache.jackrabbit.vault.fs.api.SerializationType;
import org.apache.jackrabbit.vault.fs.api.VaultInputSource;
import org.apache.jackrabbit.vault.fs.impl.AbstractArtifact;
import org.apache.jackrabbit.vault.fs.io.Serializer;
import org.apache.jackrabbit.vault.util.TempFileInputStream;

/**
 * Implements an output artifact that is based on a serializer, i.e. the
 * preferred access method is {@link AccessType#SPOOL}
 *
 */
public class SerializerArtifact extends AbstractArtifact implements ExportArtifact {

    /** the serializer that is able to spool the content */
    private final Serializer serializer;

    private final long lastModified;

    /**
     * Constructs a new artifact that is based on a content serializer.
     *
     * @param parent the parent artifact
     * @param name the name of the artifact
     * @param ext the extension of the artifact
     * @param type the type of the artifact
     * @param serializer the serializer to use for the content
     * @param lastModified the last modified date
     *
     * @throws IllegalArgumentException if the type is not suitable.
     */
    public SerializerArtifact(Artifact parent, String name, String ext, ArtifactType type,
                              Serializer serializer, long lastModified) {
        super(parent, name, ext, type);
        if (type == ArtifactType.DIRECTORY) {
            throw new IllegalArgumentException("Illegal type 'TYPE_DIRECTORY' for a serialized artifact.");
        }
        this.serializer = serializer;
        this.lastModified = lastModified;
    }

    /**
     * {@inheritDoc}
     *
     * @return always {@link AccessType#SPOOL}
     */
    public AccessType getPreferredAccess() {
        return AccessType.SPOOL;
    }

    /**
     * {@inheritDoc}
     */
    public SerializationType getSerializationType() {
        return serializer.getType();
    }

    /**
     * {@inheritDoc}
     */
    public void spool(OutputStream out)
            throws IOException, RepositoryException {
        serializer.writeContent(out);
    }

    /**
     * {@inheritDoc}
     */
    public InputStream getInputStream() throws IOException, RepositoryException {
        DeferredFileOutputStream out = new DeferredFileOutputStream(8192, "vlttmp", ".tmp", null);
        spool(out);
        out.close();
        if (out.isInMemory()) {
            return new ByteArrayInputStream(out.getData());
        } else {
            return new TempFileInputStream(out.getFile());
        }
    }

    /**
     * {@inheritDoc}
     */
    public VaultInputSource getInputSource() throws IOException, RepositoryException {
        DeferredFileOutputStream out = new DeferredFileOutputStream(8192, "vlttmp", ".tmp", null);
        spool(out);
        out.close();
        final InputStream in;
        final long size;
        if (out.isInMemory()) {
            in = new ByteArrayInputStream(out.getData());
            size = out.getData().length;
        } else {
            in = new TempFileInputStream(out.getFile());
            size = out.getFile().length();
        }
        return new VaultInputSource() {

            @Override
            public String getSystemId() {
                return SerializerArtifact.this.getRelativePath();
            }

            @Override
            public InputStream getByteStream() {
                return in;
            }


            public long getContentLength() {
                return size;
            }

            public long getLastModified() {
                return lastModified;
            }
        };
    }

    /**
     * {@inheritDoc}
     */
    public String getContentType() {
        String ct = super.getContentType();
        if (ct == null) {
            ct = serializer.getType().getContentType();
        }
        return ct;
    }

    /**
     * {@inheritDoc}
     */
    public long getContentLength() {
        return -1;
    }

    /**
     * {@inheritDoc}
     */
    public long getLastModified() {
        return lastModified;
    }
}
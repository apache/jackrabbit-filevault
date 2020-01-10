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

import javax.jcr.RepositoryException;

import org.apache.jackrabbit.vault.fs.api.AccessType;
import org.apache.jackrabbit.vault.fs.api.ArtifactType;
import org.apache.jackrabbit.vault.fs.api.ExportArtifact;
import org.apache.jackrabbit.vault.fs.api.ImportArtifact;
import org.apache.jackrabbit.vault.fs.api.SerializationType;
import org.apache.jackrabbit.vault.fs.api.VaultInputSource;
import org.apache.jackrabbit.vault.fs.impl.AbstractArtifact;

/**
 * Implements a generic directory artifact.
 *
 */
public class DirectoryArtifact extends AbstractArtifact
        implements ExportArtifact, ImportArtifact {

    /**
     * Constructs a new directory type artifact with the given repository name.
     *
     * @param name the repository name for this artifact.
     */
    public DirectoryArtifact(String name) {
        super(null, name, "", ArtifactType.DIRECTORY);
    }

    /**
     * Constructs a new directory type artifact with the given repository name
     * and extension
     *
     * @param name the repository name for this artifact.
     * @param extension the extension for this artifact
     */
    public DirectoryArtifact(String name, String extension) {
        super(null, name, extension, ArtifactType.DIRECTORY);
    }

    /**
     * {@inheritDoc}
     */
    public SerializationType getSerializationType() {
        return SerializationType.NONE;
    }

    /**
     * {@inheritDoc}
     *
     * @return always {@link AccessType#NONE}
     */
    public AccessType getPreferredAccess() {
        return AccessType.NONE;
    }

    /**
     * {@inheritDoc}
     */
    public void spool(OutputStream out) throws IOException, RepositoryException {
        throw new UnsupportedOperationException("Illegall access method for " + this);
    }

    /**
     * {@inheritDoc}
     */
    public InputStream getInputStream() throws IOException, RepositoryException {
        throw new UnsupportedOperationException("Illegall access method for " + this);
    }

    /**
     * {@inheritDoc}
     */
    public VaultInputSource getInputSource() throws IOException, RepositoryException {
        throw new UnsupportedOperationException("Illegall access method for " + this);
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
        return 0;
    }
}
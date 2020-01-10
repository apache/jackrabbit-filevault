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

package org.apache.jackrabbit.vault.fs.impl.io;

import java.io.IOException;
import java.io.InputStream;

import javax.jcr.RepositoryException;

import org.apache.jackrabbit.vault.fs.api.AccessType;
import org.apache.jackrabbit.vault.fs.api.Artifact;
import org.apache.jackrabbit.vault.fs.api.ArtifactType;
import org.apache.jackrabbit.vault.fs.api.ImportArtifact;
import org.apache.jackrabbit.vault.fs.api.SerializationType;
import org.apache.jackrabbit.vault.fs.api.VaultInputSource;
import org.apache.jackrabbit.vault.fs.impl.AbstractArtifact;
import org.xml.sax.InputSource;

/**
 * Implements an artifact that is based on a {@link InputSource}.
 *
 */
public class InputSourceArtifact extends AbstractArtifact implements ImportArtifact {

    /**
     * the input source
     */
    private final VaultInputSource source;

    private final SerializationType serType;

    /**
     * Constructs a compatible artifact of the given base one.
     *
     * @param base the base artifact
     * @param source the input source
     */
    public InputSourceArtifact(Artifact base, VaultInputSource source) {
        super(base, base.getType());
        this.source = source;
        this.serType = base.getSerializationType();
        setContentType(base.getContentType());
    }

    /**
     * Constructs a new artifact for the given source. the parent artifact is
     * only used for generating the paths and is not linked to this artifact.
     * 
     * @param parent the parent artifact
     * @param repoName the repository name
     * @param extension the platform extension
     * @param type the type of the artifact
     * @param source the input source of the artifact
     * @param serType the serialization type
     */
    public InputSourceArtifact(Artifact parent, String repoName, String extension,
                               ArtifactType type, VaultInputSource source,
                               SerializationType serType) {
        super(parent, repoName, extension, type);
        this.source = source;
        this.serType = serType;
    }

    /**
     * Creates a new input source artifact based on a given one.
     * @param isa the base source artifact
     * @param type the new type.
     */
    public InputSourceArtifact(InputSourceArtifact isa, ArtifactType type) {
        super(isa, type);
        this.source = isa.source;
        this.serType = isa.serType;
    }

    /**
     * {@inheritDoc}
     */
    public SerializationType getSerializationType() {
        return serType;
    }

    /**
     * {@inheritDoc}
     *
     * @return {@link AccessType#STREAM} always.
     */
    public AccessType getPreferredAccess() {
        return AccessType.STREAM;
    }

    /**
     * {@inheritDoc}
     */
    public InputStream getInputStream()
            throws IOException, RepositoryException {
        return source.getByteStream();
    }

    /**
     * {@inheritDoc}
     *
     * @return the underlying source
     */
    public VaultInputSource getInputSource()
            throws IOException, RepositoryException {
        return source;
    }

    /**
     * {@inheritDoc}
     */
    public long getLastModified() {
        return source.getLastModified();
    }

    /**
     * {@inheritDoc}
     */
    public long getContentLength() {
        return source.getContentLength();
    }
}
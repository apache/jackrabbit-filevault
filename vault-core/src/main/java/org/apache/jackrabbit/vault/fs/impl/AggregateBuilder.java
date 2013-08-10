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

import java.io.IOException;

import javax.jcr.RepositoryException;

import org.apache.jackrabbit.vault.fs.api.Aggregate;
import org.apache.jackrabbit.vault.fs.api.Artifact;
import org.apache.jackrabbit.vault.fs.api.ArtifactSet;
import org.apache.jackrabbit.vault.fs.api.ImportInfo;

/**
 * Implements methods for creating a new {@link AggregateImpl}
 *
 */
public class AggregateBuilder {

    /**
     * the underlying node or it's parent for new nodes
     */
    private final AggregateImpl aggregate;

    /**
     * the name for the new node or <code>null</code>
     */
    private final String reposName;

    /**
     * The artifacts to write
     */
    private ArtifactSetImpl artifacts = new ArtifactSetImpl();

    /**
     * Creates a new artifact output for the given node
     * @param aggregate the node
     * @param artifacts the artifacts of the node
     */
    AggregateBuilder(AggregateImpl aggregate, ArtifactSet artifacts) {
        this.aggregate = aggregate;
        this.reposName = null;
        this.artifacts.addAll(artifacts);
        this.artifacts.setCoverage(artifacts.getCoverage());
    }

    /**
     * Creates a new artifact output for the given parent node
     * @param parent the parent aggregate
     * @param reposName the name for the new node
     */
    AggregateBuilder(AggregateImpl parent, String reposName) {
        this.aggregate = parent;
        this.reposName = reposName;
    }

    /**
     * Returns the artifact set of this output.
     * @return the artifact set of this output.
     */
    public ArtifactSetImpl getArtifacts() {
        assertOpen();
        return artifacts;
    }

    /**
     * Adds an artifact to the output
     * @param artifact the artifact to add
     */
    public void addArtifact(Artifact artifact) {
        assertOpen();
        artifacts.add(artifact);
    }

    /**
     * Adds an artifact set to the output
     * @param artifacts the artifact set
     */
    public void addArtifacts(ArtifactSetImpl artifacts) {
        assertOpen();
        this.artifacts.addAll(artifacts);
    }

    /**
     * Returns the node this output was created for
     * @return the artifacts node
     */
    public Aggregate getAggregate() {
        return aggregate;
    }

    /**
     * Returns the repository name this output was created for
     * @return the repository name.
     */
    public String getReposName() {
        return reposName;
    }

    /**
     * Closes this artifact builder and writes the artifacts back to the
     * repository.
     *
     * @return Infos about the modifications
     * @throws RepositoryException if an error occurs.
     * @throws IOException if an I/O error occurs.
     */
    public ImportInfo close() throws RepositoryException, IOException {
        assertOpen();
        try {
            return aggregate.writeArtifacts(artifacts, reposName);
        } finally {
            artifacts = null;
        }
    }

    /**
     * Checks if this output is not closed.
     *
     * @throws IllegalStateException if this output is closed.
     */
    private void assertOpen() {
        if (artifacts == null) {
            throw new IllegalStateException("Output closed.");
        }
    }
}
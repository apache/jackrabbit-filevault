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

package org.apache.jackrabbit.vault.fs.api;

import java.io.IOException;
import java.util.Collection;

import javax.jcr.RepositoryException;

import org.apache.jackrabbit.vault.util.PlatformNameFormat;

/**
 * Representation of a node serialization in {@link VaultFileSystem}.
 * All paths used have the platform format, except if explicitly mentioned otherwise.
 * @see PlatformNameFormat
 */
public interface VaultFile extends Dumpable {
    /**
     * Returns the os path
     * @return the os path.
     */
    String getPath();

    /**
     * 
     * @return the relative path of this artifact with respect to its parent node (repository format).
     */
    String getRepoRelPath();

    /**
     * 
     * @return the repository path of the artifact's root (repository format).
     */
    String getAggregatePath();

    /**
     * Returns the name of this file.
     * @return the name of this file.
     */
    String getName();

    /**
     * Returns the underlying artifact for this os file. If this file represents
     * the <em>Meta-Directory</em> {@code null} is returned.
     * @return the artifact or {@code null}.
     */
    Artifact getArtifact();

    /**
     * Checks if this file is a directory.
     * @return {@code true} if this file is a directory.
     */
    boolean isDirectory();

    /**
     * Checks if this file is transient. a file is transient if it's only used
     * as hierarchical node for a deeper 'real' file. i.e.
     * @return {@code true} if this file is transient
     */
    boolean isTransient();

    /**
     * Returns the parent file or {@code null} if this is the root file.
     * @return the parent file.
     * @throws IOException if an I/O error occurs.
     * @throws RepositoryException if a repository error occurs.
     */
    VaultFile getParent() throws IOException, RepositoryException;

    /**
     * Returns the artifacts node of this file or {@code null} if it's
     * transient
     * @return the artifacts node
     */
    Aggregate getAggregate();

    /**
     * Returns the aggregate that controls this file.
     * @return the artifacts node
     */
    Aggregate getControllingAggregate();

    /**
     * Returns the child with the given name or {@code null}
     * @param name the name of the child
     * @return the child or {@code null}
     * @throws RepositoryException if an error occurs
     */
    VaultFile getChild(String name) throws RepositoryException;

    /**
     * Returns a collection of the children
     * @return a collection of the children
     * @throws RepositoryException if an error occurs
     */
    Collection<? extends VaultFile> getChildren() throws RepositoryException;

    /**
     * Returns the os file set for this file. The set contains those are the
     * files that are generated from the same jcr file.
     *
     * @return the file set of related files
     * @throws RepositoryException if an error occurs.
     */
    Collection<? extends VaultFile> getRelated() throws RepositoryException;

    /**
     * Checks if this file can be read from (eg. if it's not a directory)
     * @return {@code true} if this file can be read from.
     */
    boolean canRead();

    /**
     * Returns the last modified date or {@code 0} if not known.
     * @return the last modified date or {@code 0}
     */
    long lastModified();

    /**
     * Returns the length of the serialized data if it's known without doing the
     * actual serialization.
     * @return the length or {@code -1} if the length cannot be determined.
     */
    long length();

    /**
     * Returns the content type of this file or {@code null} if the type
     * cannot be determined or if it's a directory.
     * @return the content type or {@code null}.
     */
    String getContentType();

    /**
     * Returns the underlying file system.
     * @return the Vault filesystem
     */
    VaultFileSystem getFileSystem();

    void invalidate() throws RepositoryException;

}
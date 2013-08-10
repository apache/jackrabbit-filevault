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

import java.util.Collection;

/**
 * <code>ArtifactSet</code>...
 */
public interface ArtifactSet extends Dumpable {
    
    /**
     * Returns the item filter set that defines the coverage of the items in
     * this artifact set.
     * @return the item filter set.
     */
    ItemFilterSet getCoverage();

    /**
     * Adds a collection of artifacts
     *
     * @param artifacts the artifacts collection
     */
    void addAll(Collection<? extends Artifact> artifacts);

    /**
     * Adds a set of artifacts
    *
    * @param artifacts the artifacts set
    */
    void addAll(ArtifactSet artifacts);

    /**
     * Adds an artifacts
     *
     * @param artifact the artifact to add
     * @throws IllegalArgumentException if more than 1 primary data artifact is added
     */
    void add(Artifact artifact);

    /**
     * Returns the primary data artifact or <code>null</code>.
     * @return the primary data artifact or <code>null</code>.
     */
    Artifact getPrimaryData();

    /**
     * Returns the directory artifact or <code>null</code>.
     * @return the directory artifact or <code>null</code>.
     */
    Artifact getDirectory();

    /**
     * Checks if this set is empty.
     * @return <code>true</code> if this set is empty.
     */
    boolean isEmpty();

    /**
     * Returns the number of artifacts in this set.
     * @return the number of artifacts in this set.
     */
    int size();

    /**
     * Returns a collection of all artifacts that have the given type.
     * @param type the type of the artifacts to return
     * @return the artifacts
     */
    Collection<Artifact> values(ArtifactType type);

    /**
     * Returns a collection of all artifacts
     * @return the artifacts
     */
    Collection<Artifact> values();

    /**
     * Returns the collection of removed artifacts
     * @return the removed artifacts
     */
    Collection<Artifact> removed();

}
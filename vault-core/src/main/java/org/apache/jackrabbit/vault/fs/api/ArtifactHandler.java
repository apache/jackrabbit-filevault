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

import javax.jcr.RepositoryException;
import javax.jcr.Session;

/**
 * <code>ArtifactHandler</code>s are used to import artifacts into the
 * jcr repository.
 *
 */
public interface ArtifactHandler extends Dumpable {

    /**
     * Imports an artifact set for the given node.
     *
     * @param session the session to use for import
     * @param aggregate the aggregate
     * @param artifacts the artifacts to import
     * @return the import info on successful import, <code>null</code> otherwise
     * @throws RepositoryException if an error occurs.
     * @throws IOException if a I/O error occurs
     */
    ImportInfo accept(Session session, Aggregate aggregate, ArtifactSet artifacts)
            throws RepositoryException, IOException;

    /**
     * Imports an artifact set as new child node for the given parent.
     *
     * @param session the session to use for import
     * @param parent the parent aggregate of the new file to import
     * @param name the name of the new node
     * @param artifacts the artifacts to import
     * @return the import info on successful import, <code>null</code> otherwise
     * @throws RepositoryException if an error occurs.
     * @throws IOException if a I/O error occurs
     */
    ImportInfo accept(Session session, Aggregate parent, String name,
                      ArtifactSet artifacts)
            throws RepositoryException, IOException;
}
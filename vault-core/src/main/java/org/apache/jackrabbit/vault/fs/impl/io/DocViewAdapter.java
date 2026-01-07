/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.jackrabbit.vault.fs.impl.io;

import javax.jcr.RepositoryException;

import java.io.IOException;
import java.util.List;

import org.apache.jackrabbit.vault.util.DocViewNode2;

/**
 * {@code DocViewAdapter} is used by the {@link org.apache.jackrabbit.vault.fs.impl.io.DocViewImporter} to handle
 * special content that is not importable using "normal" JCR calls. For example users and other protected content
 * needs to be imported using the {@link javax.jcr.Session#importXML(String, java.io.InputStream, int)} or similar.
 *
 */
public interface DocViewAdapter {

    /**
     * Start node is invoked when the importer descends into an element.
     * @param node the node
     * @throws RepositoryException if a import exception occurs.
     */
    void startNode(DocViewNode2 node) throws IOException, RepositoryException;

    /**
     * Ends node is invoked when the importer ascends from an element.
     * @throws RepositoryException if a import exception occurs.
     */
    void endNode() throws IOException, RepositoryException;

    /**
     * Is called by the importer if the adapter is no longer used and must finalize the import.
     * @throws RepositoryException if a import exception occurs.
     * @return The paths that were created.
     */
    List<String> close() throws IOException, RepositoryException;
}

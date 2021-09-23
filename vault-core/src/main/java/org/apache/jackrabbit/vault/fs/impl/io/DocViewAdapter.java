/*************************************************************************
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
 ************************************************************************/
package org.apache.jackrabbit.vault.fs.impl.io;

import java.util.List;

import javax.jcr.RepositoryException;

import org.apache.jackrabbit.vault.util.DocViewNode2;
import org.xml.sax.SAXException;

/**
 * {@code DocViewAdapter} is used by the {@link org.apache.jackrabbit.vault.fs.impl.io.DocViewSAXHandler} to handle
 * special content that is not importable using "normal" JCR calls. For example users and other protected content
 * needs to be imported using the {@link javax.jcr.Session#importXML(String, java.io.InputStream, int)} or similar.
 * 
 * TODO: check overlap with DocViewParserHandler
 */
public interface DocViewAdapter {

    /**
     * Start node is invoked when the importer descends into an element.
     * @param node the node
     * @throws SAXException if a parsing error occurs.
     * @throws RepositoryException if a import exception occurs.
     */
    void startNode(DocViewNode2 node) throws RepositoryException;

    /**
     * Ends node is invoked when the importer ascends from an element.
     * @throws SAXException if a parsing error occurs.
     * @throws RepositoryException if a import exception occurs.
     */
    void endNode() throws RepositoryException;

    /**
     * Is called by the importer if the adapter is no longer used and must finalize the import.
     * @throws SAXException if a parsing error occurs.
     * @throws RepositoryException if a import exception occurs.
     * @return The paths that were created.
     */
    List<String> close() throws RepositoryException;

}
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
import javax.jcr.Session;

import java.io.IOException;
import java.util.Optional;

import org.apache.jackrabbit.vault.fs.io.DocViewAnalyzerListener;
import org.apache.jackrabbit.vault.fs.io.DocViewParser;
import org.apache.jackrabbit.vault.fs.io.DocViewParser.XmlParseException;
import org.apache.jackrabbit.vault.fs.io.DocViewParserHandler;
import org.apache.jackrabbit.vault.util.DocViewNode2;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;

/**
 * Implements a docview analyzer that scans the XML for nodes.
 */
public class DocViewAnalyzer implements DocViewParserHandler {

    /**
     * the default logger
     */
    static final Logger log = LoggerFactory.getLogger(DocViewAnalyzer.class);

    /**
     * listener that receives node events
     */
    private final DocViewAnalyzerListener listener;

    /**
     * Analyzes the given source
     * @param listener listener that receives node events
     * @param session repository session for namespace mappings
     * @param rootPath path of the root node
     * @param source input source
     *
     * @throws IOException if an i/o error occurs
     */
    public static void analyze(DocViewAnalyzerListener listener, Session session, String rootPath, InputSource source)
            throws IOException {
        try {
            DocViewParser docViewParser = new DocViewParser(session);
            docViewParser.parse(rootPath, source, new DocViewAnalyzer(listener));
        } catch (XmlParseException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Creates a new analyzer that will receive SAX events and generates the list
     * of included created nodes.
     *
     * @param listener listener that receives node events
     */
    private DocViewAnalyzer(DocViewAnalyzerListener listener) {
        this.listener = listener;
    }

    @Override
    public void startDocViewNode(
            @NotNull String nodePath,
            @NotNull DocViewNode2 docViewNode,
            @NotNull Optional<DocViewNode2> parentDocViewNode,
            int line,
            int column)
            throws IOException, RepositoryException {
        if (docViewNode.getProperties().isEmpty()) {
            listener.onNode(nodePath, true, "");
        } else {
            listener.onNode(nodePath, false, docViewNode.getPrimaryType().orElse(""));
        }
    }

    @Override
    public void endDocViewNode(
            @NotNull String nodePath,
            @NotNull DocViewNode2 docViewNode,
            @NotNull Optional<DocViewNode2> parentDocViewNode,
            int line,
            int column)
            throws IOException, RepositoryException {}
}

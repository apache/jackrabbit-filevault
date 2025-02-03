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
package org.apache.jackrabbit.vault.fs.io;

import java.io.IOException;
import java.util.Optional;

import javax.jcr.RepositoryException;

import org.apache.jackrabbit.spi.commons.conversion.NameResolver;
import org.apache.jackrabbit.vault.util.DocViewNode2;
import org.jetbrains.annotations.NotNull;

/**
 * Handler to be used with {@link DocViewParser}.
 */
public interface DocViewParserHandler {
    /**
     * Called for each element in the docview xml representing a node
     * @param nodePath the absolute node path of the node, the last part of the path is the JCR qualified name of the {@code docViewNode}'s name.
     * @param docViewNode the deserialized node (incl. its properties)
     * @param parentDocViewNode the parent deserialized node (only present if {@code docViewNode} is not the root node in the docview xml)
     * @param line the current line number in the docview xml
     * @param column the current column number in the docview xml
     * @throws IOException
     * @throws RepositoryException
     */
    void startDocViewNode(@NotNull String nodePath, @NotNull DocViewNode2 docViewNode, @NotNull Optional<DocViewNode2> parentDocViewNode, int line, int column) throws IOException, RepositoryException;

    /**
     * Called at the end of each element in the docview xml representing a node.
     * At this point in time all child nodes have been processed.
     * @param nodePath the absolute node path of the node
     * @param docViewNode the deserialized node (incl. its properties)
     * @param parentDocViewNode the parent deserialized node (only present if {@code docViewNode} is not the root node in the docview xml)
     * @param line the current line number in the docview xml
     * @param column the current column number in the docview xml
     * @throws IOException
     * @throws RepositoryException
     */
    void endDocViewNode(@NotNull String nodePath, @NotNull DocViewNode2 docViewNode, @NotNull Optional<DocViewNode2> parentDocViewNode, int line, int column) throws IOException, RepositoryException;

    /**
     * Called once when the end of a docview file has been reached.
     * @throws RepositoryException
     * @throws IOException
     */
    default void endDocument() throws RepositoryException, IOException {};

    /**
     * Called when a namespace mapping is defined in the docview xml.
     * Rather use {@link #setNameResolver(NameResolver)} instead if you just need to resolve JCR names.
     * @param prefix the namespace prefix
     * @param uri the namespace uri
     */
    default void startPrefixMapping(@NotNull String prefix, @NotNull String uri) {};

    /**
     * Called when a namespace mapping end in the docview xml.
     * Rather use {@link #setNameResolver(NameResolver)} instead if you just need to resolve JCR names.
     * @param prefix the namespace prefix
     */
    default void endPrefixMapping(@NotNull String prefix) {};

    /**
     * Called before the first {@link DocViewParserHandler#startDocViewNode(String, DocViewNode2, Optional, int, int)} is called.
     * Provides a NameResolver which can be used to resolve JCR names to their qualified form.
     * Can be used instead of overwriting {@link #startPrefixMapping(String, String)} and {@link #endPrefixMapping(String)}.
     * @param nameResolver the resolver aware of all namespaces and their prefixes defined in the underlying XML document.
     */
    default void setNameResolver(@NotNull NameResolver nameResolver) {}

    /** 
     * Called when non-whitespace character data is encountered in the underlying XML document.
     * For DocView XML files character data is not considered, i.e. this method is only called for superfluous data.
     * 
     * @param value the character data
     * @param nodePath the absolute node path of the node in which the character data was found
     * @param line 
     * @param column 
     */
    default void afterCharacterData(@NotNull String value, @NotNull String nodePath, int line, int column) {}
}

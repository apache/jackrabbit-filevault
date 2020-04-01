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
package org.apache.jackrabbit.vault.validation.spi;

import java.nio.file.Path;
import java.util.Collection;

import org.apache.jackrabbit.spi.Name;
import org.apache.jackrabbit.spi.commons.name.NameConstants;
import org.apache.jackrabbit.vault.util.DocViewNode;
import org.apache.jackrabbit.vault.validation.spi.util.NameUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.annotation.versioning.ProviderType;

/**
 * Validator interface for (enhanced) Document View XML files within content packages.
 * 
 * @see <a href="https://jackrabbit.apache.org/filevault/docview.html">Filevault DocView</a> 
 *
 */
@ProviderType
public interface DocumentViewXmlValidator extends Validator {

    /**
     * Called for the beginning of each new JCR document view node.
     * Deserialization of the node information was already done when this method is called!
     * The node and attribute names have the string representation outlined in {@link Name} (i.e. including the namespace uri in the format <code>{namespaceURI}localPart</code>).
     * This is also referred to as <a href="https://docs.adobe.com/docs/en/spec/jcr/2.0/3_Repository_Model.html#3.2.5.1%20Expanded%20Form">JCR name expanded form</a>.
     * To construct such names either use {@link NameUtil} or use the constants from {@link NameConstants}.
     * 
     * The node's label refers to the XML element name specifying the node. There shouldn't be any checks derived from it, but only from the expanded name.
     * @param node the node which should be validated
     * @param nodePath the absolute repository path of the given node
     * @param filePath the relative file path of the docview file containing this node
     * @param isRoot {@code true} in case this is the root node of the docview file otherwise {@code false}
     * @return validation messages or {@code null}
     * @deprecated Use {@link #validate(DocViewNode, NodeContext, boolean)} instead
     */
    @Deprecated
    default @Nullable Collection<ValidationMessage> validate(@NotNull DocViewNode node, @NotNull String nodePath, @NotNull Path filePath, boolean isRoot) {
        throw new UnsupportedOperationException();
    }
    
    /**
     * Called for the beginning of each new JCR document view node.
     * Deserialization of the node information was already done when this method is called!
     * The node and attribute names have the string representation outlined in {@link Name} (i.e. including the namespace uri in the format <code>{namespaceURI}localPart</code>).
     * This is also referred to as <a href="https://docs.adobe.com/docs/en/spec/jcr/2.0/3_Repository_Model.html#3.2.5.1%20Expanded%20Form">JCR name expanded form</a>.
     * To construct such names either use {@link NameUtil} or use the constants from {@link NameConstants}.
     * 
     * The node's label refers to the XML element name specifying the node. There shouldn't be any checks derived from it, but only from the expanded name.
     * @param node the node which should be validated
     * @param nodeContext the information about the node context (like path)
     * @param isRoot {@code true} in case this is the root node of the docview file otherwise {@code false}
     * @return validation messages or {@code null}
     */
    default @Nullable Collection<ValidationMessage> validate(@NotNull DocViewNode node, @NotNull NodeContext nodeContext, boolean isRoot) {
        return validate(node, nodeContext.getNodePath(), nodeContext.getFilePath(), isRoot);
    }
    
    
    /**
     * Called for the end of each new JCR document view node.
     * Deserialization of the node information was already done when this method is called as well as all child nodes within the same docview file have been processed.
     * The node and attribute names have the string representation outlined in {@link Name} (i.e. including the namespace uri in the format <code>{namespaceURI}localPart</code>).
     * This is also referred to as <a href="https://docs.adobe.com/docs/en/spec/jcr/2.0/3_Repository_Model.html#3.2.5.1%20Expanded%20Form">JCR name expanded form</a>.
     * To construct such names either use {@link NameUtil} or use the constants from {@link NameConstants}.
     * 
     * The node's label refers to the XML element name specifying the node. There shouldn't be any checks derived from it, but only from the expanded name.
     * @param node the node which should be validated
     * @param nodeContext the information about the node context (like path)
     * @param isRoot {@code true} in case this is the root node of the docview file otherwise {@code false}
     * @return validation messages or {@code null}
     */
    default @Nullable Collection<ValidationMessage> validateEnd(@NotNull DocViewNode node, @NotNull NodeContext nodeContext, boolean isRoot) {
        return null;
    }
}

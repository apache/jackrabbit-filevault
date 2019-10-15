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

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import org.apache.jackrabbit.spi.Name;
import org.apache.jackrabbit.vault.util.DocViewNode;
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
     * The node and attribute names have the string representation outlined in {@link Name} (i.e. including the expanded namespace uri in the format <code>{namespaceURI}localPart</code>).
     * 
     * @param node the node which should be validated
     * @param nodePath the absolute repository path of the given node
     * @param filePath the relative file path of the docview file containing this node
     * @param isRoot {@code true} in case this is the root node of the docview file otherwise {@code false}
     * @return validation messages or {@code null}
     */
    @CheckForNull Collection<ValidationMessage> validate(@Nonnull DocViewNode node, @Nonnull String nodePath, @Nonnull Path filePath, boolean isRoot);
}

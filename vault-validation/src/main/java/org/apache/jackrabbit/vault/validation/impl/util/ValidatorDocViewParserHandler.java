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
package org.apache.jackrabbit.vault.validation.impl.util;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.jcr.RepositoryException;

import org.apache.jackrabbit.vault.fs.io.DocViewParserHandler;
import org.apache.jackrabbit.vault.util.DocViewNode2;
import org.apache.jackrabbit.vault.validation.ValidationViolation;
import org.apache.jackrabbit.vault.validation.spi.DocumentViewXmlValidator;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessage;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessageSeverity;
import org.apache.jackrabbit.vault.validation.spi.util.NodeContextImpl;
import org.jetbrains.annotations.NotNull;

public class ValidatorDocViewParserHandler implements DocViewParserHandler {

    private final @NotNull Map<@NotNull String, @NotNull Integer> nodePathsAndLineNumbers;
    private final @NotNull Path filePath;
    private final @NotNull Path basePath;
    private final Map<String, DocumentViewXmlValidator> validators;
    private final @NotNull List<ValidationViolation> violations;

    public ValidatorDocViewParserHandler(Map<String, DocumentViewXmlValidator> docViewValidators, @NotNull Path filePath, @NotNull Path basePath) {
        this.nodePathsAndLineNumbers = new HashMap<>();
        this.filePath = filePath;
        this.basePath = basePath;
        this.validators = docViewValidators;
        violations = new LinkedList<>();
    }

    @Override
    public void startDocViewNode(@NotNull String nodePath, @NotNull DocViewNode2 docViewNode,
            @NotNull Optional<DocViewNode2> parentDocViewNode, int lineNumber, int columnNumber) throws IOException, RepositoryException {
        callValidators(true, nodePath, docViewNode, parentDocViewNode, lineNumber, columnNumber);
        if (!docViewNode.getProperties().isEmpty()) {
            nodePathsAndLineNumbers.put(nodePath, lineNumber);
        }
    }

    @Override
    public void endDocViewNode(@NotNull String nodePath, @NotNull DocViewNode2 docViewNode,
            @NotNull Optional<DocViewNode2> parentDocViewNode, int lineNumber, int columnNumber) throws IOException, RepositoryException {
        callValidators(false, nodePath, docViewNode, parentDocViewNode, lineNumber, columnNumber);
    }

    /** @return a Map of absolute node paths (i.e. starting with "/") with "/" as path delimiter and the line number in which they were found in the docview file */
    public @NotNull Map<String, Integer> getNodePaths() {
        return nodePathsAndLineNumbers;
    }

    public @NotNull List<ValidationViolation> getViolations() {
        return violations;
    }

    private void callValidators(boolean isStart, String nodePath, DocViewNode2 docViewNode, Optional<DocViewNode2> parentDocViewNode, int lineNumber,
            int columnNumber) {
        violations.add(new ValidationViolation(ValidationMessageSeverity.DEBUG, "Validate node '" + docViewNode + "' " + (isStart ? "start" : "end")));
        for (Map.Entry<String, DocumentViewXmlValidator> entry : validators.entrySet()) {
            try {
                final Collection<ValidationMessage> messages;
                if (isStart) {
                    messages = entry.getValue().validate(docViewNode, new NodeContextImpl(nodePath, filePath, basePath), !parentDocViewNode.isPresent());
                } else {
                    messages = entry.getValue().validateEnd(docViewNode, new NodeContextImpl(nodePath, filePath, basePath), !parentDocViewNode.isPresent());
                }
                if (messages != null && !messages.isEmpty()) {
                    violations.addAll(ValidationViolation.wrapMessages(entry.getKey(), messages, filePath, null, nodePath,
                            lineNumber, columnNumber));
                }
            } catch (RuntimeException e) {
                throw new ValidatorException(entry.getKey(), e, filePath, lineNumber, columnNumber, e);
            }
        }
    }
    
    
}

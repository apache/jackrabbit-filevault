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
package org.apache.jackrabbit.vault.validation.spi.impl;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.jcr.NamespaceException;

import org.apache.commons.io.input.CloseShieldInputStream;
import org.apache.jackrabbit.spi.commons.namespace.NamespaceResolver;
import org.apache.jackrabbit.vault.fs.io.DocViewParser;
import org.apache.jackrabbit.vault.fs.io.DocViewParser.XmlParseException;
import org.apache.jackrabbit.vault.validation.ValidationViolation;
import org.apache.jackrabbit.vault.validation.impl.util.EnhancedBufferedInputStream;
import org.apache.jackrabbit.vault.validation.impl.util.ValidatorDocViewParserHandler;
import org.apache.jackrabbit.vault.validation.spi.DocumentViewXmlValidator;
import org.apache.jackrabbit.vault.validation.spi.GenericJcrDataValidator;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessage;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessageSeverity;
import org.jetbrains.annotations.NotNull;
import org.xml.sax.InputSource;

public class DocumentViewParserValidator implements GenericJcrDataValidator {

    public static final String EXTENDED_FILE_AGGREGATE_FOLDER_SUFFIX = ".dir";
    private final Map<String, DocumentViewXmlValidator> docViewValidators;
    private final DocViewParser docViewParser;
    private final @NotNull ValidationMessageSeverity severity;
    private final @NotNull ValidationMessageSeverity severityForUnusedCharacterData;
    
    public DocumentViewParserValidator(@NotNull ValidationMessageSeverity severity, boolean allowUndeclaredPrefixInFileName, final @NotNull ValidationMessageSeverity severityForUnusedCharacterData) {
        super();
        this.docViewValidators = new HashMap<>();
        if (allowUndeclaredPrefixInFileName) {
            NamespaceResolver nsResolver = new NamespaceResolver() {

                @Override
                public String getURI(String prefix) throws NamespaceException {
                    return "http://undeclared.uri";
                }
                @Override
                public String getPrefix(String uri) throws NamespaceException {
                    return "undeclared prefix";
                }
            };
            this.docViewParser = new DocViewParser(nsResolver);
        } else {
            this.docViewParser = new DocViewParser();
        }
        this.severity = severity;
        this.severityForUnusedCharacterData = severityForUnusedCharacterData;
    }

    public void setDocumentViewXmlValidators(Map<String, DocumentViewXmlValidator> documentViewXmlValidators) {
        this.docViewValidators.putAll(documentViewXmlValidators);
    }

    @Override
    public Collection<ValidationMessage> done() {
        return null;
    }

    @Override
    public boolean shouldValidateJcrData(@NotNull Path filePath, @NotNull Path basePath) {
        // support upper case extensions?
        return filePath.toString().endsWith(".xml");
    }

    @Override
    public Collection<ValidationMessage> validateJcrData(@NotNull InputStream input, @NotNull Path filePath, @NotNull Path basePath, @NotNull Map<String, Integer> nodePathsAndLineNumbers) throws IOException {
        Collection<ValidationMessage> messages = new LinkedList<>();
        // TODO: support other formats like sysview xml or generic xml
        // (https://jackrabbit.apache.org/filevault/vaultfs.html#Deserialization)

        // wrap input stream as buffered input stream (to be able to reset it and for performance reasons)
        final EnhancedBufferedInputStream bufferedInput = new EnhancedBufferedInputStream(input);

        String documentViewXmlRootNodePath = DocViewParser.getDocumentViewXmlRootNodePath(bufferedInput, filePath);
        if (documentViewXmlRootNodePath != null) {
            messages.addAll(validateDocumentViewXml(bufferedInput, filePath, basePath, documentViewXmlRootNodePath,
                            nodePathsAndLineNumbers));
            
        } else {
            messages.add(new ValidationMessage(ValidationMessageSeverity.INFO, "This file is not detected as docview xml file and therefore treated as binary"));
        }
        
       return messages;
    }

    protected Collection<ValidationMessage> validateDocumentViewXml(InputStream input, @NotNull Path filePath, @NotNull Path basePath, String rootNodePath,
            Map<String, Integer> nodePathsAndLineNumbers) throws IOException {
        List<ValidationMessage> enrichedMessages = new LinkedList<>();
        enrichedMessages.add(new ValidationMessage(ValidationMessageSeverity.DEBUG, "Detected DocView..."));
        ValidatorDocViewParserHandler handler = new ValidatorDocViewParserHandler(severity, severityForUnusedCharacterData, docViewValidators, filePath, basePath);
        try {
            docViewParser.parse(rootNodePath, new InputSource(new CloseShieldInputStream(input)), handler);
            enrichedMessages.addAll(ValidationViolation.wrapMessages(null, handler.getViolations(), filePath, basePath, rootNodePath, 0, 0));
        } catch (XmlParseException e) {
            enrichedMessages.add(new ValidationViolation(DocumentViewParserValidatorFactory.ID, severity, "Could not parse FileVault Document View XML: " + e.getMessage(), filePath, basePath, e.getNodePath(), e.getLineNumber(), e.getColumnNumber(), e));
        }
        nodePathsAndLineNumbers.putAll(handler.getNodePaths());
        return enrichedMessages;
    }


}

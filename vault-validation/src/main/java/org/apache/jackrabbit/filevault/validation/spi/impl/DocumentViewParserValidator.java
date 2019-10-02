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
package org.apache.jackrabbit.filevault.validation.spi.impl;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.SAXParser;

import org.apache.commons.io.input.CloseShieldInputStream;
import org.apache.jackrabbit.filevault.validation.ValidationExecutor;
import org.apache.jackrabbit.filevault.validation.ValidationViolation;
import org.apache.jackrabbit.filevault.validation.impl.util.DocumentViewXmlContentHandler;
import org.apache.jackrabbit.filevault.validation.impl.util.EnhancedBufferedInputStream;
import org.apache.jackrabbit.filevault.validation.spi.DocumentViewXmlValidator;
import org.apache.jackrabbit.filevault.validation.spi.GenericJcrDataValidator;
import org.apache.jackrabbit.filevault.validation.spi.ValidationMessage;
import org.apache.jackrabbit.filevault.validation.spi.ValidationMessageSeverity;
import org.apache.jackrabbit.vault.fs.api.SerializationType;
import org.apache.jackrabbit.vault.fs.impl.io.XmlAnalyzer;
import org.apache.jackrabbit.vault.util.Constants;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

public class DocumentViewParserValidator implements GenericJcrDataValidator {

    private final Map<String, DocumentViewXmlValidator> docViewValidators;
    private final SAXParser saxParser;
    private final ValidationMessageSeverity severity;
    
    public DocumentViewParserValidator(SAXParser saxParser, ValidationMessageSeverity severity) {
        super();
        this.docViewValidators = new HashMap<>();
        this.saxParser = saxParser;
        this.severity = severity;
    }

    public void setDocumentViewXmlValidators(Map<String, DocumentViewXmlValidator> documentViewXmlValidators) {
        this.docViewValidators.putAll(documentViewXmlValidators);
    }
   
    @Override
    public Collection<ValidationMessage> done() {
        return null;
    }

    @Override
    public Collection<ValidationMessage> validateJcrData(InputStream input, Path filePath, Map<String, Integer> nodePathsAndLineNumbers) throws IOException {
        Collection<ValidationMessage> messages = new LinkedList<>();
        // TODO: support other formats like sysview xml or generic xml
        // (https://jackrabbit.apache.org/filevault/vaultfs.html#Deserialization)

        // wrap input stream as buffered input stream (to be able to reset it and for performance reasons)
        final EnhancedBufferedInputStream bufferedInput = new EnhancedBufferedInputStream(input);

        Path documentViewXmlRootPath = getDocumentViewXmlRootPath(bufferedInput, filePath);
        if (documentViewXmlRootPath != null) {
            try {
                messages.addAll(validateDocumentViewXml(bufferedInput, filePath, ValidationExecutor.filePathToNodePath(documentViewXmlRootPath),
                            nodePathsAndLineNumbers));
            } catch (SAXException e) {
                throw new IOException("Could not parse xml", e);
            }
        } else {
            messages.add(new ValidationMessage(ValidationMessageSeverity.INFO, "This file is not detected as docview xml file and therefore treated as binary"));
            nodePathsAndLineNumbers.put(ValidationExecutor.filePathToNodePath(filePath), 0);
        }
        
       return messages;
    }


    /** @param input, the given input stream must be reset later on
     * @param path
     * @return either the path of the root node of the given docview xml or {@code null} if no docview xml given
     * @throws IOException */
    private static Path getDocumentViewXmlRootPath(BufferedInputStream input, Path path) throws IOException {
        Path name = path.getFileName();
        Path rootPath = null;

        int nameCount = path.getNameCount();
        if (name.equals(Paths.get(Constants.DOT_CONTENT_XML))) {
            if (nameCount > 1) {
                rootPath = path.subpath(0, nameCount - 1);
            } else {
                rootPath = Paths.get("");
            }
            // correct suffix matching
        } else if (name.toString().endsWith(".xml")) {

            // we need to rely on a buffered input stream to be able to reset it later
            input.mark(1024);
            // analyze content
            // this closes the input source internally, therefore protect against closing
            // make sure to initialize the SLF4J logger appropriately (for the XmlAnalyzer)
            try {
                SerializationType type = XmlAnalyzer.analyze(new InputSource(new CloseShieldInputStream(input)));
                if (type == SerializationType.XML_DOCVIEW) {
                    //  remove .xml extension
                    String fileName = path.getFileName().toString();
                    fileName = fileName.substring(0, fileName.length() - ".xml".length());
                    if (nameCount > 1) {
                        rootPath = path.subpath(0, nameCount - 1).resolve(fileName);
                    } else {
                        rootPath = Paths.get(fileName);
                    }
                }
            } finally {
                input.reset();
            }
        }
        return rootPath;
    }

    protected Collection<ValidationMessage> validateDocumentViewXml(InputStream input, Path filePath, String rootNodePath,
            Map<String, Integer> nodePathsAndLineNumbers) throws IOException, SAXException {
        List<ValidationMessage> enrichedMessages = new LinkedList<>();
        XMLReader xr = saxParser.getXMLReader();
        final DocumentViewXmlContentHandler handler = new DocumentViewXmlContentHandler(filePath, rootNodePath,
                docViewValidators);
        enrichedMessages.add(new ValidationMessage(ValidationMessageSeverity.DEBUG, "Detected DocView..."));
        xr.setContentHandler(handler);
        try {
            xr.parse(new InputSource(new CloseShieldInputStream(input)));
            enrichedMessages.addAll(ValidationViolation.wrapMessages(null, handler.getViolations(), filePath, null, null, 0, 0));
        } catch (SAXException e) {
            enrichedMessages.add(new ValidationViolation(severity, "Invalid XML found: " + e.getMessage(), filePath, null, null, 0, 0, e));
        }
        nodePathsAndLineNumbers.putAll(handler.getNodePaths());
        return enrichedMessages;
    }

    // support upper case extensions?
    @Override
    public boolean shouldValidateJcrData(Path filePath) {
        return filePath.toString().endsWith(".xml");
    }


}

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

import java.nio.file.Path;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

import org.apache.jackrabbit.spi.Name;
import org.apache.jackrabbit.spi.commons.name.NameConstants;
import org.apache.jackrabbit.spi.commons.name.NameFactoryImpl;
import org.apache.jackrabbit.util.ISO9075;
import org.apache.jackrabbit.util.Text;
import org.apache.jackrabbit.vault.util.DocViewNode;
import org.apache.jackrabbit.vault.util.DocViewProperty;
import org.apache.jackrabbit.vault.validation.ValidationViolation;
import org.apache.jackrabbit.vault.validation.spi.DocumentViewXmlValidator;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessage;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessageSeverity;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/** TODO: reuse more logic from DocViewSAXImporter (https://issues.apache.org/jira/browse/JCRVLT-357) */
public class DocumentViewXmlContentHandler extends DefaultHandler {

    private final Map<String, Integer> nodePathsAndLineNumbers;
    private String rootNodeName;
    private String rootNodeParentPath; // must not end with "/"
    private final Path filePath;
    private Locator locator;
    private Deque<String> elementNameStack;
    private Deque<DocViewNode> nodeStack;
    private Deque<String> nodePathStack;
    private final Map<String, DocumentViewXmlValidator> validators;

    private List<ValidationViolation> violations;

    public @Nonnull List<ValidationViolation> getViolations() {
        return violations;
    }

    /** Pattern to be used with {@link String#format(String, Object...)} */
    public static final String PARSE_VIOLATION_MESSAGE_STRING = "Error while parsing element '%s': %s " +
            ". Check all attribute values. They must stick to the grammar " +
            "[ \"{\" type \"}\" ] ( value | \"[\" [ value { \",\" value } ] \"]\" " +
            "while type is one of: String, Binary, Long, Double, Date, Boolean, Name, Path, Reference, WeakReference, URI, Decimal";

    /**
     * 
     * @param filePath the relative file to the docview file (relative to the jcr_root folder)
     * @param rootNodePath the node path of the root node covered by this docview file
     * @param documentViewXmlValidators the validators to call for this docview file
     */
    public DocumentViewXmlContentHandler(Path filePath, String rootNodePath, Map<String, DocumentViewXmlValidator> documentViewXmlValidators) {
        this.filePath = filePath;
        if (rootNodePath.equals("/")) {
            rootNodePath = "";
        }
        if (rootNodePath.endsWith("/")) {
            throw new IllegalArgumentException("rootPath must not end with \"/\" but is " + rootNodePath);
        }
        if (rootNodePath.contains("\\")) {
            throw new IllegalArgumentException("rootPath must not contain backslashes, only forward slashes should be used as separator!");
        }
        nodePathsAndLineNumbers = new HashMap<>();
        rootNodeName = Text.getName(rootNodePath);
        rootNodeParentPath = Text.getRelativeParent(rootNodePath, 1);
        if (rootNodeParentPath.equals("/")) {
            rootNodeParentPath = "";
        }

        elementNameStack = new LinkedList<>();
        nodeStack = new LinkedList<>();
        nodePathStack = new LinkedList<>();
        this.validators = documentViewXmlValidators;
        violations = new LinkedList<>();
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        // convert to DocViewNode (mostly taken over from DocViewSAXImporter#startElement)
        String label = ISO9075.decode(qName);
        if (elementNameStack.isEmpty()) {
            if (localName.equals(NameConstants.JCR_ROOT.getLocalName())
                    && uri.equals(NameConstants.JCR_ROOT.getNamespaceURI())) {
                // take over node name from file name
                label = rootNodeName;
            } else {
                // element name takes precedence over file name
                rootNodeName = label;
            }
        }

        String name = label; // name is usually the same except for SNS nodes

        // in the case of SNS nodes the name contains an index in brackets as suffix
        int idx = name.lastIndexOf('[');
        if (idx > 0) {
            name = name.substring(0, idx);
        }
        elementNameStack.push(name);

        // add fully qualified name
        StringBuilder nodePath = new StringBuilder(rootNodeParentPath);
        Iterator<String> iterator = elementNameStack.descendingIterator();
        while (iterator.hasNext()) {
            nodePath.append("/").append(iterator.next());
        }
        nodePathsAndLineNumbers.put(nodePath.toString(), locator.getLineNumber());
        nodePathStack.push(nodePath.toString());
        try {
            DocViewNode node = getDocViewNode(name, label, attributes);
            nodeStack.push(node);
            for (Map.Entry<String, DocumentViewXmlValidator> entry : validators.entrySet()) {
                violations.add(new ValidationViolation(entry.getKey(), ValidationMessageSeverity.DEBUG, "Validate node '" + node + "' start"));
                Collection<ValidationMessage> messages = entry.getValue().validate(node, nodePath.toString(), filePath, elementNameStack.size() <= 1);
                if (messages != null && !messages.isEmpty()) {
                    violations.addAll(ValidationViolation.wrapMessages(entry.getKey(), messages, filePath, null, nodePath.toString(),
                            locator.getLineNumber(), locator.getColumnNumber()));
                }
            }
        } catch (IllegalArgumentException e) { // thrown from DocViewProperty.parse()
            violations.add(new ValidationViolation(ValidationMessageSeverity.ERROR, String.format(PARSE_VIOLATION_MESSAGE_STRING, qName, e.getMessage()), filePath, null, nodePath.toString(), locator.getLineNumber(), locator.getColumnNumber(), e));
        }
    }

    private DocViewNode getDocViewNode(String name, String label, Attributes attributes) {
        Map<String, DocViewProperty> propertyMap = new HashMap<>();
        
        String uuid = null;
        String primary = null;
        String[] mixins = null;
        for (int i=0; i<attributes.getLength(); i++) {
            Name pName = NameFactoryImpl.getInstance().create(
                    attributes.getURI(i),
                    ISO9075.decode(attributes.getLocalName(i)));
            DocViewProperty property = DocViewProperty.parse(pName.toString(), attributes.getValue(i));
            propertyMap.put(property.name, property);
            if (pName.equals(NameConstants.JCR_UUID)) {
                uuid = property.values[0];
            } else if (pName.equals(NameConstants.JCR_PRIMARYTYPE)) {
                primary = property.values[0];
            } else if (pName.equals(NameConstants.JCR_MIXINTYPES)) {
                mixins = property.values;
            }
        }
        
        return new DocViewNode(name, label, uuid, propertyMap, mixins, primary);
    }

    /** @return a Collection of absolute node paths (i.e. starting with "/") with "/" as path delimiter. */
    public @Nonnull Map<String, Integer> getNodePaths() {
        return nodePathsAndLineNumbers;
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {

        if (nodeStack.isEmpty()) {
            // may happen in case start element was not successfull (e.g. parse exception)
            return;
        }
        DocViewNode node = nodeStack.pop();
        elementNameStack.pop();
        String nodePath = nodePathStack.pop();
        if (node == null || nodePath == null) {
            throw new IllegalStateException("Seems that the XML is not well formed");
        }
    }

    @Override
    public void setDocumentLocator(Locator locator) {
        this.locator = locator;
    }
}

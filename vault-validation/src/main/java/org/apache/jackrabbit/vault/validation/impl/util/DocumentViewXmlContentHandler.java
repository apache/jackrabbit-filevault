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

import javax.jcr.NamespaceException;

import org.apache.jackrabbit.spi.Name;
import org.apache.jackrabbit.spi.commons.conversion.IllegalNameException;
import org.apache.jackrabbit.spi.commons.conversion.NameParser;
import org.apache.jackrabbit.spi.commons.name.NameConstants;
import org.apache.jackrabbit.spi.commons.name.NameFactoryImpl;
import org.apache.jackrabbit.spi.commons.namespace.NamespaceResolver;
import org.apache.jackrabbit.util.ISO9075;
import org.apache.jackrabbit.util.Text;
import org.apache.jackrabbit.vault.util.DocViewNode;
import org.apache.jackrabbit.vault.util.DocViewProperty;
import org.apache.jackrabbit.vault.validation.ValidationViolation;
import org.apache.jackrabbit.vault.validation.spi.DocumentViewXmlValidator;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessage;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessageSeverity;
import org.apache.jackrabbit.vault.validation.spi.util.NodeContextImpl;
import org.jetbrains.annotations.NotNull;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/** TODO: reuse more logic from DocViewSAXImporter (https://issues.apache.org/jira/browse/JCRVLT-357) */
public class DocumentViewXmlContentHandler extends DefaultHandler implements NamespaceResolver {

    private final @NotNull Map<@NotNull String, @NotNull Integer> nodePathsAndLineNumbers;
    private String rootNodeName;
    private String rootNodeParentPath; // must not end with "/"
    private final @NotNull Path filePath;
    private final @NotNull Path basePath;
    private Locator locator;
    private Deque<String> elementNameStack;
    private Deque<DocViewNode> nodeStack;
    private Deque<String> nodePathStack;
    private final Map<String, DocumentViewXmlValidator> validators;
    private final Map<String, String> namespaceRegistry;

    private @NotNull List<ValidationViolation> violations;

    public @NotNull List<ValidationViolation> getViolations() {
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
     * @param basePath the absolute file path of the the jcr_root folder (to which {@code filePath} is relative)
     * @param rootNodePath the node path of the root node covered by this docview file
     * @param documentViewXmlValidators the validators to call for this docview file
     */
    public DocumentViewXmlContentHandler(@NotNull Path filePath, @NotNull Path basePath, String rootNodePath, Map<String, DocumentViewXmlValidator> documentViewXmlValidators) {
        this.filePath = filePath;
        this.basePath = basePath;
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
        namespaceRegistry = new HashMap<>();
    }

    
    @Override
    public void startPrefixMapping(String prefix, String uri) throws SAXException {
        namespaceRegistry.put(prefix, uri);
    }


    @Override
    public void endPrefixMapping(String prefix) throws SAXException {
        namespaceRegistry.remove(prefix);
    }

    
    @Override
    public String getPrefix(String uri) throws NamespaceException {
        throw new UnsupportedOperationException("Only resolving from prefix to URI is supported, but not vice-versa");
    }


    @Override
    public String getURI(String prefix) throws NamespaceException {
        if (prefix.isEmpty()) {
            return Name.NS_DEFAULT_URI;
        }
        return namespaceRegistry.get(prefix);
    }


    private Name getExpandedName(String name) throws IllegalNameException, NamespaceException {
        return NameParser.parse(name, this, NameFactoryImpl.getInstance());
    }

    /**
     * Resolves the ISO-9075 encoding and removes a same-name sibling suffix from the name which is either a localName or qualified name
     * @param name
     * @return the normalized name
     */
    private String getNormalizedName(String name) {
        // in the case of SNS nodes the name contains an index in brackets as suffix
        name = ISO9075.decode(name);
        int idx = name.lastIndexOf('[');
        if (idx > 0) {
            name = name.substring(0, idx);
        }
        return name;
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        // convert to DocViewNode (mostly taken over from DocViewSAXImporter#startElement)
        String label = getNormalizedName(qName);
        Name name;
        if (elementNameStack.isEmpty() && localName.equals(NameConstants.JCR_ROOT.getLocalName())
                    && uri.equals(NameConstants.JCR_ROOT.getNamespaceURI())) {
                // take over node name from file name
                label = rootNodeName;
                try {
                    if (rootNodeName.equals("")) {
                        name = NameConstants.JCR_ROOT;
                    } else {
                        // how to get name of root node?
                        name = getExpandedName(rootNodeName);
                    }
                } catch (IllegalNameException|NamespaceException|IllegalArgumentException e) {
                    throw new SAXException("Given root node name '" + rootNodeName + "' (implicitly given via filename) cannot be resolved. The prefix used in the filename must be declared as XML namespace in the child docview XML as well!", e);
                }
        } else {
            name = NameFactoryImpl.getInstance().create(uri, getNormalizedName(localName));
        }

        // the path is being given via the qualified (prefixed) names
        elementNameStack.push(label);

        // add fully qualified name
        StringBuilder nodePath = new StringBuilder(rootNodeParentPath);
        Iterator<String> iterator = elementNameStack.descendingIterator();
        while (iterator.hasNext()) {
            nodePath.append("/").append(iterator.next());
        }
        nodePathStack.push(nodePath.toString());
        
        try {
            DocViewNode node = getDocViewNode(name, qName, attributes);
            nodeStack.push(node);
            violations.add(new ValidationViolation(ValidationMessageSeverity.DEBUG, "Validate node '" + node + "' start"));
            for (Map.Entry<String, DocumentViewXmlValidator> entry : validators.entrySet()) {
                try {
                    Collection<ValidationMessage> messages = entry.getValue().validate(node, new NodeContextImpl(nodePath.toString(), filePath, basePath), elementNameStack.size() <= 1);
                    if (messages != null && !messages.isEmpty()) {
                        violations.addAll(ValidationViolation.wrapMessages(entry.getKey(), messages, filePath, null, nodePath.toString(),
                                locator.getLineNumber(), locator.getColumnNumber()));
                    }
                } catch (RuntimeException e) {
                    throw new ValidatorException(entry.getKey(), e, filePath, locator.getLineNumber(), locator.getColumnNumber(), e);
                }
            }
        } catch (IllegalArgumentException e) { // thrown from DocViewProperty.parse()
            violations.add(new ValidationViolation(ValidationMessageSeverity.ERROR, String.format(PARSE_VIOLATION_MESSAGE_STRING, qName, e.getMessage()), filePath, null, nodePath.toString(), locator.getLineNumber(), locator.getColumnNumber(), e));
        }
        // do not collect node paths for empty elements (as they represent order only)
        // really?
        if (attributes.getLength() > 0) {
            nodePathsAndLineNumbers.put(nodePath.toString(), locator.getLineNumber());
        }
    }

    private @NotNull DocViewNode getDocViewNode(Name name, String label, Attributes attributes) {
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
        return new DocViewNode(name.toString(), label, uuid, propertyMap, mixins, primary);
    }

    /** @return a Collection of absolute node paths (i.e. starting with "/") with "/" as path delimiter. */
    public @NotNull Map<String, Integer> getNodePaths() {
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
        violations.add(new ValidationViolation(ValidationMessageSeverity.DEBUG, "Validate node '" + node + "' end"));
        for (Map.Entry<String, DocumentViewXmlValidator> entry : validators.entrySet()) {
            try {
                Collection<ValidationMessage> messages = entry.getValue().validateEnd(node, new NodeContextImpl(nodePath, filePath, basePath), elementNameStack.size() < 1);
                if (messages != null && !messages.isEmpty()) {
                    violations.addAll(ValidationViolation.wrapMessages(entry.getKey(), messages, filePath, null, nodePath.toString(),
                            locator.getLineNumber(), locator.getColumnNumber()));
                }
            } catch (RuntimeException e) {
                throw new ValidatorException(entry.getKey(), e, filePath, locator.getLineNumber(), locator.getColumnNumber(), e);
            }
        }
    }

    @Override
    public void setDocumentLocator(Locator locator) {
        this.locator = locator;
    }
}

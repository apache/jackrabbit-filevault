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

package org.apache.jackrabbit.vault.fs.impl.io;

import java.io.IOException;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import javax.jcr.NamespaceException;
import javax.jcr.RepositoryException;

import org.apache.jackrabbit.spi.Name;
import org.apache.jackrabbit.spi.NameFactory;
import org.apache.jackrabbit.spi.commons.conversion.IllegalNameException;
import org.apache.jackrabbit.spi.commons.conversion.NameResolver;
import org.apache.jackrabbit.spi.commons.conversion.ParsingNameResolver;
import org.apache.jackrabbit.spi.commons.name.NameConstants;
import org.apache.jackrabbit.spi.commons.name.NameFactoryImpl;
import org.apache.jackrabbit.spi.commons.namespace.NamespaceResolver;
import org.apache.jackrabbit.util.ISO9075;
import org.apache.jackrabbit.util.Text;
import org.apache.jackrabbit.vault.fs.io.DocViewParserHandler;
import org.apache.jackrabbit.vault.util.DocViewNode2;
import org.apache.jackrabbit.vault.util.DocViewProperty2;
import org.apache.jackrabbit.vault.util.PathUtil;
import org.apache.jackrabbit.vault.util.RejectingEntityDefaultHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.LocatorImpl;
import org.xml.sax.helpers.NamespaceSupport;

/**
 * Implements an importer that processes SAX events from a (modified) document
 * view. The behaviour for existing nodes works as follows:
 * TODO: better description
 * <pre>
 *
 * - extended docview always includes SNS indexes
 * - label is the last element of the path
 * - uuid "child" means uuid of a direct child node matches
 * - uuid "desc" means uuid of a descendant child node matches
 * - uuid "other" means uuid of a node outside of the node tree matches
 *
 * uuid  | label | nt  | result
 * -     | no    | -   | create
 * -     | yes   | no  | replace
 * -     | yes   | yes | reuse
 * no    | no    | -   | create
 * no    | yes   | no  | replace
 * no    | yes   | yes | reuse
 * child | no    | no  | replace
 * child | no    | yes | replace
 * child | yes   | no  | replace
 * child | yes   | yes | reuse
 * desc  | -     | -   | *error*
 * other | -     | -   | *error*
 * </pre>
 */
public class DocViewSAXHandler extends RejectingEntityDefaultHandler implements NamespaceResolver {


    /**
     * A representation of a namespace. One of these will be pushed on the namespace
     * stack for each element.
     */
    public static final class Namespace {

        /**
         * Next NameSpace element on the stack.
         */
        public Namespace next = null;

        /**
         * Prefix of this NameSpace element.
         */
        public String prefix;

        /**
         * Namespace URI of this NameSpace element.
         */
        public String uri; // if null, then Element namespace is empty.

        /**
         * Construct a namespace for placement on the result tree namespace stack.
         *
         * @param prefix Prefix of this element
         * @param uri    URI of this element
         */
        public Namespace(String prefix, String uri) {
            this.prefix = prefix;
            this.uri = uri;
        }
    }

    private static final NameFactory FACTORY = NameFactoryImpl.getInstance();

    /**
     * the default logger
     */
    static final Logger log = LoggerFactory.getLogger(DocViewSAXHandler.class);

    /**
     * empty attributes
     */
    static final Attributes EMPTY_ATTRIBUTES = new AttributesImpl();

    /**
     * the current namespace state
     */
    private final NamespaceSupport nsSupport;

    /**
     * the name path resolver to use for the filename (outside the docview), only relevant to resolve jcr:root element
     */
    private final NameResolver nameResolver;

    /**
     * Optional additional namespace resolver used for namespace lookup if not declared in XML
     */
    private final @Nullable NamespaceResolver nsResolver;

    private final DocViewParserHandler handler;
    private final String rootNodePath;

    private final Deque<DocViewNode2> nodeStack;
    /** absolute repository path of the node which is currently parsed */
    private String currentPath;
    
    private Locator locator;
    
    public DocViewSAXHandler(@NotNull DocViewParserHandler handler, @NotNull String rootNodePath, @Nullable NamespaceResolver nsResolver) {
        super();
        Objects.requireNonNull(handler, "handler must not be null");
        this.handler = handler;
        Objects.requireNonNull(rootNodePath, "rootNodePath must not be null");
        if (rootNodePath.isEmpty()) {
            throw new IllegalArgumentException("rootNodePath must not be empty");
        }
        this.rootNodePath = rootNodePath;
        
        nsSupport = new NamespaceSupport();
        nodeStack = new LinkedList<>();
        currentPath = null;
        locator = new LocatorImpl();
        this.nsResolver = nsResolver;
        this.nameResolver = new ParsingNameResolver(NameFactoryImpl.getInstance(), this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void startDocument() throws SAXException {
        this.handler.setNameResolver(nameResolver);
    }

    @Override
    public void setDocumentLocator(Locator locator) {
        this.locator = locator; 
    }

    public @NotNull Locator getDocumentLocator() {
        return this.locator;
    }

    /**
     * 
     * @return the node path which has been last processed
     */
    public @NotNull String getCurrentPath() {
        return currentPath != null ? currentPath : rootNodePath;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void endDocument() throws SAXException {
        try {
            handler.endDocument();
        } catch (RepositoryException | IOException e) {
            throw new SAXException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void characters(char ch[], int start, int length) throws SAXException {
        // can be ignored in docview
    }

    @Override
    public void startPrefixMapping(String prefix, String uri) throws SAXException {
        log.trace("-> prefixMapping for {}:{}", prefix, uri);
        nsSupport.pushContext();
        nsSupport.declarePrefix(prefix, uri);
        handler.startPrefixMapping(prefix, uri);
    }

    @Override
    public void endPrefixMapping(String prefix) throws SAXException {
        log.trace("<- prefixMapping for {}", prefix);
        nsSupport.popContext();
        handler.endPrefixMapping(prefix);
    }

    
    /** 
     * Extracts the index and the original item name from a name according to <a href="https://s.apache.org/jcr-2.0-spec/22_Same-Name_Siblings.html#22.2%20Addressing%20Same-Name%20Siblings%20by%20Path">JCR 2.0 22.2</a>.
     * @param name
     * @return
     */
    private Map.Entry<String, Integer> getNameAndIndex(String name) {
        int idx = name.lastIndexOf('[');
        int index = 0;
        if (idx > 0) {
            // extract index
            int idx2 = name.indexOf(']', idx);
            if (idx2 > 0) {
                try {
                    index = Integer.valueOf(name.substring(idx + 1, idx2));
                } catch (NumberFormatException e) {
                    // ignore
                }
            }
            name = name.substring(0, idx);
        }
        return new SimpleEntry<>(name, index);
    }

    /**
     * This considers both namespaces declared in the XML as well as namespaces from the underlying namespace resolver.
     */
    @Override
    public String getURI(String prefix) throws NamespaceException {
        if (prefix.equals(Name.NS_EMPTY_PREFIX)) {
            return Name.NS_DEFAULT_URI;
        }
        String uri = nsSupport.getURI(prefix);
        if (uri == null) {
            if (nsResolver != null) {
                return nsResolver.getURI(prefix);
            }
            throw new NamespaceException("Unknown prefix " + prefix);
        }
        return uri;
    }

    /**
     * This considers both namespaces declared in the XML as well as namespaces from the underlying namespace resolver.
     */
    @Override
    public String getPrefix(String uri) throws NamespaceException {
        String prefix = nsSupport.getPrefix(uri);
        if (prefix == null) {
            if (nsResolver != null) {
                return nsResolver.getPrefix(uri);
            }
            throw new NamespaceException("Unmapped URL " + uri);
        }
        return prefix;
    }

    @Override
    public void startElement(final String uri, final String localName, final String qName, final Attributes attributes) throws SAXException {
        // special handling for root node
        log.trace("-> element {}", qName);
        Name name;
        final int index;
        if (currentPath == null
                && localName.equals(NameConstants.JCR_ROOT.getLocalName())
                && uri.equals(NameConstants.JCR_ROOT.getNamespaceURI())) {
            // special case for root (https://issues.apache.org/jira/browse/JCR-4625)
            if (rootNodePath.equals("/")) {
                name = NameConstants.ROOT;
                index = 0;
            } else {
                Map.Entry<String, Integer> nameAndIndex = getNameAndIndex(Text.getName(rootNodePath));
                index = nameAndIndex.getValue();
                try {
                    name = nameResolver.getQName(nameAndIndex.getKey());
                } catch (NamespaceException e) {
                    throw new SAXException("Unknown namespace prefix used in file name '" + nameAndIndex.getKey() + "'", e);
                } catch (IllegalNameException e) {
                	throw new SAXException("Invalid name format used in file name '" + nameAndIndex.getKey() + "'", e);
                }
            }
            currentPath = rootNodePath;
        } else {
            Map.Entry<String, Integer> nameAndIndex = getNameAndIndex(ISO9075.decode(localName));
            index = nameAndIndex.getValue();
            try {
                name = FACTORY.create(uri, nameAndIndex.getKey());
                if (currentPath == null) {
                    // root node element name should take precedence of root node name derived from path
                    currentPath = Text.getRelativeParent(rootNodePath, 1);
                }
                currentPath = PathUtil.append(currentPath, ISO9075.decode(qName));
            } catch (IllegalArgumentException e) {
                throw new SAXException("Invalid name format used in node name '" + nameAndIndex.getKey() + "'", e);
            }
        }
        try {
            List<DocViewProperty2> props = new ArrayList<>(attributes.getLength());
            for (int i = 0; i < attributes.getLength(); i++) {
                // ignore non CDATA attributes
                if (!attributes.getType(i).equals(DocViewImporter.ATTRIBUTE_TYPE_CDATA)) {
                    continue;
                }
                Name pName = FACTORY.create(
                        attributes.getURI(i),
                        ISO9075.decode(attributes.getLocalName(i)));
                DocViewProperty2 property = DocViewProperty2.parse(
                        pName,
                        attributes.getValue(i));
                props.add(property);
            }
            DocViewNode2 ni = new DocViewNode2(name, index, props);
            handler.startDocViewNode(currentPath, ni, Optional.ofNullable(nodeStack.peek()), locator.getLineNumber(), locator.getColumnNumber());
            nodeStack.push(ni);
        }  catch (RepositoryException|IOException e) {
            throw new SAXException("Error while processing element " + qName, e);
        } 
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        log.trace("<- element {}", qName);
        try {
            handler.endDocViewNode(currentPath, nodeStack.pop(), Optional.ofNullable(nodeStack.peek()), locator.getLineNumber(), locator.getColumnNumber());
            currentPath = Text.getRelativeParent(currentPath, 1);
        } catch (RepositoryException|IOException e) {
            throw new SAXException(e);
        }
    }
}

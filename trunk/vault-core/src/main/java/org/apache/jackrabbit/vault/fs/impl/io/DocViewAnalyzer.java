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

import javax.jcr.NamespaceException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.jackrabbit.spi.commons.conversion.DefaultNamePathResolver;
import org.apache.jackrabbit.spi.commons.name.NameConstants;
import org.apache.jackrabbit.spi.commons.namespace.NamespaceResolver;
import org.apache.jackrabbit.util.ISO9075;
import org.apache.jackrabbit.vault.fs.io.DocViewAnalyzerListener;
import org.apache.jackrabbit.vault.util.RejectingEntityDefaultHandler;
import org.apache.jackrabbit.vault.util.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Implements a docview analyzer that scans the XML for nodes.
 */
public class DocViewAnalyzer extends RejectingEntityDefaultHandler implements NamespaceResolver {

    /**
     * the default logger
     */
    static final Logger log = LoggerFactory.getLogger(DocViewAnalyzer.class);

    /**
     * the importing session
     */
    private final Session session;

    /**
     * the name of the root node
     */
    private final String rootPath;

    /**
     * the current namespace state
     */
    private NameSpace nsStack = null;

    /**
     * current stack
     */
    private StackElement stack;

    /**
     * the default name path resolver
     */
    private final DefaultNamePathResolver npResolver = new DefaultNamePathResolver(this);

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
    public static void analyze(DocViewAnalyzerListener listener,
                               Session session, String rootPath,
                               InputSource source)
            throws IOException {
        try {
            DocViewAnalyzer handler = new DocViewAnalyzer(listener, session, rootPath);
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://xml.org/sax/features/namespace-prefixes", false);
            SAXParser parser = factory.newSAXParser();
            parser.parse(source, handler);
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException(e);
        } catch (SAXException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Creates a new analyzer that will receive SAX events and generates the list
     * of included created nodes.
     *
     * @param listener listener that receives node events
     * @param session repository session used for namespace mapping
     * @param rootPath name of the root node
     */
    private DocViewAnalyzer(DocViewAnalyzerListener listener, Session session, String rootPath) {
        this.listener = listener;
        this.session = session;
        this.rootPath = rootPath;
    }

    /**
     * {@inheritDoc}
     */
    public void startDocument() throws SAXException {
        stack = new StackElement(null, Text.getRelativeParent(rootPath,1));
    }

    /**
     * {@inheritDoc}
     */
    public void endDocument() throws SAXException {
        if (stack.parent != null) {
            throw new IllegalStateException("stack mismatch");
        }
    }

    /**
     * {@inheritDoc}
     */
    public void characters(char ch[], int start, int length) throws SAXException {
        // can be ignored in docview
    }

    /**
     * {@inheritDoc}
     *
     * Pushes the mapping to the stack and updates the namespace mapping in the
     * session.
     */
    public void startPrefixMapping(String prefix, String uri) throws SAXException {
        /*
        log.debug("-> prefixMapping for {}:{}", prefix, uri);
        NameSpace ns = new NameSpace(prefix, uri);
        // push on stack
        ns.next = nsStack;
        nsStack = ns;
        // check if uri is already registered
        String oldPrefix;
        try {
            oldPrefix = session.getNamespacePrefix(uri);
        } catch (NamespaceException e) {
            // assume uri never registered
            try {
                session.getWorkspace().getNamespaceRegistry().registerNamespace(prefix, uri);
            } catch (RepositoryException e1) {
                throw new SAXException(e);
            }
            oldPrefix = prefix;
        } catch (RepositoryException e) {
            throw new SAXException(e);
        }
        // update mapping
        if (!oldPrefix.equals(prefix)) {
            try {
                session.setNamespacePrefix(prefix, uri);
            } catch (RepositoryException e) {
                throw new SAXException(e);
            }
        }
        */
    }

    /**
     * {@inheritDoc}
     *
     * Pops the mapping from the stack and updates the namespace mapping in the
     * session if necessary.
     */
    public void endPrefixMapping(String prefix) throws SAXException {
        /*
        log.debug("<- prefixMapping for {}", prefix);
        NameSpace ns = nsStack;
        NameSpace prev = null;
        while (ns != null && !ns.prefix.equals(prefix)) {
            prev = ns;
            ns = ns.next;
        }
        if (ns == null) {
            throw new SAXException("Illegal state: prefix " + prefix + " never mapped.");
        }
        // remove from stack
        if (prev == null) {
            nsStack = ns.next;
        } else {
            prev.next = ns.next;
        }
        // find old prefix
        ns = ns.next;
        while (ns != null && !ns.prefix.equals(prefix)) {
            ns = ns.next;
        }
        // update mapping
        if (ns != null) {
            try {
                session.setNamespacePrefix(prefix, ns.uri);
            } catch (RepositoryException e) {
                throw new SAXException(e);
            }
            log.debug("   remapped: {}:{}", prefix, ns.uri);
        }
        */
    }

    /**
     * {@inheritDoc}
     */
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        // special handling for root node
        if (stack.parent == null) {
            if (localName.equals(NameConstants.JCR_ROOT.getLocalName())
                    && uri.equals(NameConstants.JCR_ROOT.getNamespaceURI())) {
                qName = Text.getName(rootPath);
            }
        }
        String label = ISO9075.decode(qName);
        String name = label;
        int idx = name.lastIndexOf('[');
        if (idx > 0) {
            name = name.substring(0, idx);
        }
        stack = stack.push(name);
        if (attributes.getLength() == 0) {
            listener.onNode(stack.getPath(), true, "");
        } else {
            // currently ignore namespace mappings in node type values
            // todo: fix
            String pt = attributes.getValue(NameConstants.JCR_PRIMARYTYPE.getNamespaceURI(), NameConstants.JCR_PRIMARYTYPE.getLocalName());
            listener.onNode(stack.getPath(), false, pt == null ? "" : pt);
        }
    }

    /**
     * {@inheritDoc}
     */
    public void endElement(String uri, String localName, String qName) throws SAXException {
        stack = stack.pop();
    }

    /**
     * {@inheritDoc}
     */
    public String getURI(String prefix) throws NamespaceException {
        try {
            return session.getNamespaceURI(prefix);
        } catch (RepositoryException e) {
            throw new NamespaceException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    public String getPrefix(String uri) throws NamespaceException {
        try {
            return session.getNamespacePrefix(uri);
        } catch (RepositoryException e) {
            throw new NamespaceException(e);
        }
    }

    private static class StackElement  {

        private final String path;

        final StackElement parent;

        public StackElement(StackElement parent, String name) {
            if (parent == null) {
                this.parent = null;
                this.path = name;
            } else {
                this.path = parent.path + "/" + name;
                this.parent = parent;
            }
        }

        public String getPath() {
            return path;
        }

        public StackElement push(String name) {
            return new StackElement(this, name);
        }

        public StackElement pop() {
            return parent;
        }

    }

}
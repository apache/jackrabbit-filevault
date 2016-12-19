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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.jcr.NamespaceException;
import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.jackrabbit.spi.Name;
import org.apache.jackrabbit.spi.commons.conversion.DefaultNamePathResolver;
import org.apache.jackrabbit.spi.commons.conversion.NameException;
import org.apache.jackrabbit.spi.commons.conversion.NameParser;
import org.apache.jackrabbit.spi.commons.name.NameConstants;
import org.apache.jackrabbit.spi.commons.name.NameFactoryImpl;
import org.apache.jackrabbit.spi.commons.namespace.NamespaceResolver;
import org.apache.jackrabbit.spi.commons.namespace.SessionNamespaceResolver;
import org.apache.jackrabbit.util.ISO9075;
import org.apache.jackrabbit.vault.fs.api.Aggregate;
import org.apache.jackrabbit.vault.fs.api.VaultFsConfig;
import org.apache.jackrabbit.vault.util.DocViewProperty;
import org.apache.jackrabbit.vault.util.ItemNameComparator;
import org.apache.jackrabbit.vault.util.JcrConstants;
import org.apache.jackrabbit.vault.util.Text;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * The docview sax formatter generates SAX events to a given ContentHandler based on the aggregate tree.
 */
public class DocViewSAXFormatter implements AggregateWalkListener {

    /**
     * The XML elements and attributes used in serialization
     */
    public static final String CDATA_TYPE = "CDATA";

    /**
     * the session to be used for resolving namespace mappings
     */
    protected final Session session;

    /**
     * the session's namespace resolver
     */
    protected final NamespaceResolver nsResolver;

    /**
     * the content handler to feed the SAX events to
     */
    protected final ContentHandler contentHandler;

    /**
     * The jcr:primaryType property name (allowed for session-local prefix mappings)
     */
    protected final String jcrPrimaryType;

    /**
     * The nt:unstructured name (allowed for session-local prefix mappings)
     */
    protected final String ntUnstructured;

    /**
     * The jcr:mixinTypes property name (allowed for session-local prefix mappings)
     */
    protected final String jcrMixinTypes;

    /**
     * The jcr:uuid property name (allowed for session-local prefix mappings)
     */
    protected final String jcrUUID;

    /**
     * The jcr:root node name (allowed for session-local prefix mappings)
     */
    protected final String jcrRoot;

    // used to temporarily store properties of a node
    private final List<Property> props = new ArrayList<Property>();

    /**
     * the export context
     */
    private final Aggregate aggregate;

    /**
     * flag indicating if binary references are to be used.
     */
    private final boolean useBinaryReferences;

    /**
     * internally ignored properties
     */
    private final Set<String> ignored = new HashSet<String>();

    protected DocViewSAXFormatter(Aggregate aggregate, ContentHandler contentHandler)
            throws RepositoryException {

        this.aggregate = aggregate;
        this.session = aggregate.getNode().getSession();
        nsResolver = new SessionNamespaceResolver(session);

        this.contentHandler = contentHandler;

        DefaultNamePathResolver npResolver = new DefaultNamePathResolver(nsResolver);

        // resolve the names of some well known properties
        // allowing for session-local prefix mappings
        try {
            jcrPrimaryType = npResolver.getJCRName(NameConstants.JCR_PRIMARYTYPE);
            jcrMixinTypes = npResolver.getJCRName(NameConstants.JCR_MIXINTYPES);
            jcrUUID = npResolver.getJCRName(NameConstants.JCR_UUID);
            jcrRoot = npResolver.getJCRName(NameConstants.JCR_ROOT);
            ntUnstructured = npResolver.getJCRName(NameConstants.NT_UNSTRUCTURED);
        } catch (NamespaceException e) {
            // should never get here...
            String msg = "internal error: failed to resolve namespace mappings";
            throw new RepositoryException(msg, e);
        }

        useBinaryReferences = "true".equals(aggregate.getManager().getConfig().getProperty(VaultFsConfig.NAME_USE_BINARY_REFERENCES));
    }

    /**
     * Starts namespace declarations
     *
     * @throws RepositoryException if a repository error occurs
     * @throws SAXException if the underlying content handler throws a sax exception
     */
    protected void startNamespaceDeclarations()
            throws RepositoryException, SAXException {
        // start namespace declarations
        for (String prefix: aggregate.getNamespacePrefixes()) {
            if (Name.NS_XML_PREFIX.equals(prefix)) {
                // skip 'xml' prefix as this would be an illegal namespace declaration
                continue;
            }
            contentHandler.startPrefixMapping(prefix, aggregate.getNamespaceURI(prefix));
        }
    }

    /**
     * Ends namespace declarations
     *
     * @throws RepositoryException if a repository error occurs
     * @throws SAXException if the underlying content handler throws a sax exception
      */
    protected void endNamespaceDeclarations()
            throws RepositoryException, SAXException {
        // end namespace declarations
        for (String prefix: aggregate.getNamespacePrefixes()) {
            if (Name.NS_XML_PREFIX.equals(prefix)) {
                // skip 'xml' prefix as this would be an illegal namespace declaration
                continue;
            }
            contentHandler.endPrefixMapping(prefix);
        }
    }

    private Name getQName(String rawName) throws RepositoryException {
        try {
            return NameParser.parse(rawName, nsResolver, NameFactoryImpl.getInstance());
        } catch (NameException e) {
            // should never get here...
            String msg = "internal error: failed to resolve namespace mappings";
            throw new RepositoryException(msg, e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onWalkBegin(Node root) throws RepositoryException {
        // init ignored protected properties
        ignored.clear();
        ignored.add(JcrConstants.JCR_CREATED);
        ignored.add(JcrConstants.JCR_CREATED_BY);
        ignored.add(JcrConstants.JCR_BASEVERSION);
        ignored.add(JcrConstants.JCR_VERSIONHISTORY);
        ignored.add(JcrConstants.JCR_PREDECESSORS);

        try {
            contentHandler.startDocument();
            startNamespaceDeclarations();
        } catch (SAXException e) {
            throw new RepositoryException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onWalkEnd(Node root) throws RepositoryException {
        try {
            // clear namespace declarations and end document
            endNamespaceDeclarations();
            contentHandler.endDocument();
        } catch (SAXException e) {
            throw new RepositoryException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onNodeBegin(Node node, boolean included, int level)
            throws RepositoryException{
        // register used node types
        aggregate.getManager().addNodeTypes(node);
        props.clear();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onChildren(Node node, int level) throws RepositoryException {
        String label = Text.getName(node.getPath());
        String elemName;
        if (level == 0 || node.getDepth() == 0) {
            // root node needs a name
            elemName = jcrRoot;
        } else {
            // encode node name to make sure it's a valid xml name
            elemName = ISO9075.encode(label);
        }

        // attributes (properties)
        AttributesImpl attrs = new AttributesImpl();
        Collections.sort(props, ItemNameComparator.INSTANCE);
        for (Property prop: props) {
            // attribute name (encode property name to make sure it's a valid xml name)
            String attrName = ISO9075.encode(prop.getName());
            Name qName = getQName(attrName);
            boolean sort = qName.equals(NameConstants.JCR_MIXINTYPES);
            attrs.addAttribute(qName.getNamespaceURI(), qName.getLocalName(),
                    attrName, CDATA_TYPE, DocViewProperty.format(prop, sort, useBinaryReferences));
        }

        // start element (node)
        Name qName = getQName(elemName);
        try {
            contentHandler.startElement(qName.getNamespaceURI(),
                    qName.getLocalName(), elemName, attrs);
        } catch (SAXException e) {
            throw new RepositoryException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onNodeEnd(Node node, boolean included, int level) throws RepositoryException {
        String label = Text.getName(node.getPath());
        String elemName;
        if (node.getDepth() == 0) {
            // root node needs a name
            elemName = jcrRoot;
        } else {
            // encode node name to make sure it's a valid xml name
            elemName = ISO9075.encode(label);
        }

        // end element (node)
        Name qName = getQName(elemName);
        try {
            contentHandler.endElement(qName.getNamespaceURI(), qName.getLocalName(), elemName);
        } catch (SAXException e) {
            throw new RepositoryException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onProperty(Property prop, int level) throws RepositoryException {
        if (ignored.contains(prop.getName()) && prop.getDefinition().isProtected()) {
            return;
        }

        props.add(prop);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onNodeIgnored(Node node, int depth) throws RepositoryException {
        // just add an empty node. used for ordering
        String label = Text.getName(node.getPath());
        String elemName = ISO9075.encode(label);
        Name qName = getQName(elemName);
        try {
            contentHandler.startElement(qName.getNamespaceURI(), qName.getLocalName(), elemName, null);
            contentHandler.endElement(qName.getNamespaceURI(), qName.getLocalName(), elemName);
        } catch (SAXException e) {
            throw new RepositoryException(e);
        }
    }
}
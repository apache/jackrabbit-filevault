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

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.RepositoryException;

import org.apache.jackrabbit.spi.Name;
import org.apache.jackrabbit.spi.commons.conversion.NameException;
import org.apache.jackrabbit.spi.commons.conversion.NameParser;
import org.apache.jackrabbit.spi.commons.name.NameConstants;
import org.apache.jackrabbit.spi.commons.name.NameFactoryImpl;
import org.apache.jackrabbit.util.ISO9075;
import org.apache.jackrabbit.vault.fs.api.Aggregate;
import org.apache.jackrabbit.vault.fs.api.VaultFsConfig;
import org.apache.jackrabbit.vault.fs.impl.AggregateManagerImpl;
import org.apache.jackrabbit.vault.util.DocViewProperty;
import org.apache.jackrabbit.vault.util.ItemNameComparator;
import org.apache.jackrabbit.vault.util.JcrConstants;
import org.apache.jackrabbit.vault.util.Text;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * <code>DocViewSAXFormatter</code>...
 *
 */
public class DocViewSAXFormatter extends AbstractSAXFormatter {

    public static final String CDATA_TYPE = "CDATA";

    // used to temporarily store properties of a node
    private final List<Property> props = new ArrayList<Property>();

    private boolean useJcrRoot;

    private boolean useBinaryReferences;

    private Set<String> ignored = new HashSet<String>();

    public DocViewSAXFormatter(Aggregate aggregate, ContentHandler contentHandler)
            throws RepositoryException {
        super(aggregate, contentHandler);

        useBinaryReferences = "true".equals(aggregate.getManager().getConfig().getProperty(VaultFsConfig.NAME_USE_BINARY_REFERENCES));
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
     * Controls if the jcr:root should be used as root element name.
     * @param useJcrRoot <code>true</code> if jcrRoot is to be used as
     * root element name.
     */
    public void setUseJcrRoot(boolean useJcrRoot) {
        this.useJcrRoot = useJcrRoot;
    }

    /**
     * {@inheritDoc}
     */
    public void onWalkBegin(Node root) throws RepositoryException {
        super.onWalkBegin(root);

        // init ignored protected properties
        ignored.clear();
        ignored.add(JcrConstants.JCR_CREATED);
        ignored.add(JcrConstants.JCR_CREATED_BY);
        ignored.add(JcrConstants.JCR_BASEVERSION);
        ignored.add(JcrConstants.JCR_VERSIONHISTORY);
        ignored.add(JcrConstants.JCR_PREDECESSORS);
    }

    /**
     * {@inheritDoc}
     */
    public void onNodeBegin(Node node, boolean included, int level)
            throws RepositoryException{
        // register used node types
        ((AggregateManagerImpl) aggregate.getManager()).addNodeTypes(node);
        props.clear();
    }

    /**
     * {@inheritDoc}
     */
    public void onChildren(Node node, int level) throws RepositoryException {
        String name = node.getName();
        String label = Text.getName(node.getPath());
        if (name.equals(jcrXMLText)) {
            // the node represents xml character data
            for (Property prop : props) {
                String propName = prop.getName();
                if (propName.equals(jcrXMLCharacters)) {
                    // assume jcr:xmlcharacters is single-valued
                    char[] chars = prop.getValue().getString().toCharArray();
                    try {
                        contentHandler.characters(chars, 0, chars.length);
                    } catch (SAXException e) {
                        throw new RepositoryException(e);
                    }
                }
            }
        } else {
            // regular node
            // element name
            String elemName;
            if ((level == 0 && useJcrRoot) || node.getDepth() == 0) {
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
    }

    /**
     * {@inheritDoc}
     */
    public void onNodeEnd(Node node, boolean included, int level) throws RepositoryException {
        String name = node.getName();
        String label = Text.getName(node.getPath());
        if (name.equals(jcrXMLText)) {
            // the node represents xml character data
            // (already processed in leavingProperties(NodeImpl, int)
            return;
        }
        // element name
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
            contentHandler.endElement(qName.getNamespaceURI(), qName.getLocalName(),
                    elemName);
        } catch (SAXException e) {
            throw new RepositoryException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    public void onProperty(Property prop, int level)
            throws RepositoryException {
        if (ignored.contains(prop.getName()) && prop.getDefinition().isProtected()) {
            return;
        }

        props.add(prop);
    }

    /**
     * {@inheritDoc}
     */
    public void onNodeIgnored(Node node, int depth) throws RepositoryException {
        // just add an empty node. used for ordering
        String label = Text.getName(node.getPath());
        String elemName = ISO9075.encode(label);
        Name qName = getQName(elemName);
        try {
            contentHandler.startElement(qName.getNamespaceURI(),
                qName.getLocalName(), elemName, null);
            contentHandler.endElement(qName.getNamespaceURI(), qName.getLocalName(),
                    elemName);
        } catch (SAXException e) {
            throw new RepositoryException(e);
        }
    }
}
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

import javax.jcr.NamespaceException;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.jackrabbit.spi.Name;
import org.apache.jackrabbit.spi.commons.conversion.DefaultNamePathResolver;
import org.apache.jackrabbit.spi.commons.name.NameConstants;
import org.apache.jackrabbit.spi.commons.namespace.NamespaceResolver;
import org.apache.jackrabbit.spi.commons.namespace.SessionNamespaceResolver;
import org.apache.jackrabbit.vault.fs.api.Aggregate;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * <code>AbstractSaxFormatter</code>...
 *
 */
public abstract class AbstractSAXFormatter implements AggregateWalkListener {

    /**
     * The XML elements and attributes used in serialization
     */
    public static final String NODE_ELEMENT = "node";
    public static final String PREFIXED_NODE_ELEMENT =
        Name.NS_SV_PREFIX + ":" + NODE_ELEMENT;

    public static final String PROPERTY_ELEMENT = "property";
    public static final String PREFIXED_PROPERTY_ELEMENT =
        Name.NS_SV_PREFIX + ":" + PROPERTY_ELEMENT;

    public static final String VALUE_ELEMENT = "value";
    public static final String PREFIXED_VALUE_ELEMENT =
        Name.NS_SV_PREFIX + ":" + VALUE_ELEMENT;

    public static final String NAME_ATTRIBUTE = "name";
    public static final String PREFIXED_NAME_ATTRIBUTE =
        Name.NS_SV_PREFIX + ":" + NAME_ATTRIBUTE;

    public static final String TYPE_ATTRIBUTE = "type";
    public static final String PREFIXED_TYPE_ATTRIBUTE =
        Name.NS_SV_PREFIX + ":" + TYPE_ATTRIBUTE;

    public static final String CDATA_TYPE = "CDATA";
    public static final String ENUMERATION_TYPE = "ENUMERATION";

    /**
     * indicates if binaries are to be excluded from the serialization
     */
    protected boolean skipBinary = true;

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

    /**
     * The jcr:xmltext node name (allowed for session-local prefix mappings)
     */
    protected final String jcrXMLText;

    /**
     * The jcr:xmlCharacters property name (allowed for session-local prefix mappings)
     */
    protected final String jcrXMLCharacters;

    /**
     * the export context
     */
    protected final Aggregate aggregate;

    protected AbstractSAXFormatter(Aggregate aggregate, ContentHandler contentHandler)
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
            jcrXMLText = npResolver.getJCRName(NameConstants.JCR_XMLTEXT);
            jcrXMLCharacters = npResolver.getJCRName(NameConstants.JCR_XMLCHARACTERS);
            ntUnstructured = npResolver.getJCRName(NameConstants.NT_UNSTRUCTURED);
        } catch (NamespaceException e) {
            // should never get here...
            String msg = "internal error: failed to resolve namespace mappings";
            throw new RepositoryException(msg, e);
        }
    }

    public void onWalkBegin(Node root) throws RepositoryException {
        try {
            contentHandler.startDocument();
            startNamespaceDeclarations();
        } catch (SAXException e) {
            throw new RepositoryException(e);
        }
    }

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
}
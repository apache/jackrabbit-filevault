/*************************************************************************
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
 ************************************************************************/
package org.apache.jackrabbit.vault.fs.impl.io;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.jcr.ImportUUIDBehavior;
import javax.jcr.InvalidSerializedDataException;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.jackrabbit.spi.Name;
import org.apache.jackrabbit.spi.commons.conversion.DefaultNamePathResolver;
import org.apache.jackrabbit.spi.commons.conversion.NamePathResolver;
import org.apache.jackrabbit.spi.commons.name.NameFactoryImpl;
import org.apache.jackrabbit.vault.fs.api.ImportMode;
import org.apache.jackrabbit.vault.util.DocViewNode2;
import org.apache.jackrabbit.vault.util.DocViewProperty2;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
* {@code JcrSysViewTransformer} transforms a docview importer hierarchy to a jcr sysview one by translating the
 * vault specific docview nodes and properties into SAX events for the JCR sysview import content handler.
 *
 * @see Session#getImportContentHandler(String, int)
*/
public class JcrSysViewTransformer implements DocViewAdapter {

    /**
     * default logger
     */
    private static final Logger log = LoggerFactory.getLogger(JcrSysViewTransformer.class);

    private static final Name NAME_REP_CACHE = NameFactoryImpl.getInstance().create(Name.NS_REP_URI, "cache");
    /**
     * sysview handler for special content
     */
    private ContentHandler handler;

    /**
     * temporary recovery helper when 'rescuing' the child nodes
     */
    private NodeStash recovery;

    private Name rootName;

    private Node parent;

    private final String existingPath;

    private final Set<Name> excludedNodeNames = new HashSet<>();
    
    private final @NotNull ImportMode importMode;

    private final NamePathResolver resolver;

    private long ignoreLevel = 0;

    public JcrSysViewTransformer(@NotNull Node node, @NotNull ImportMode importMode) throws RepositoryException {
        this(node, null, importMode);
    }

    JcrSysViewTransformer(@NotNull Node node, @Nullable String existingPath, @NotNull ImportMode importMode) throws RepositoryException {
        Session session = node.getSession();
        parent = node;
        handler = session.getImportContentHandler(
                node.getPath(),
                existingPath != null
                        ? ImportUUIDBehavior.IMPORT_UUID_COLLISION_REPLACE_EXISTING
                        : ImportUUIDBehavior.IMPORT_UUID_COLLISION_REMOVE_EXISTING
        );
        // first define the current namespaces
        String[] prefixes = session.getNamespacePrefixes();
        try {
            handler.startDocument();
            for (String prefix: prefixes) {
                handler.startPrefixMapping(prefix, session.getNamespaceURI(prefix));
            }
        } catch (SAXException e) {
            throw new RepositoryException("Can not use sysview handler", e);
        }

        this.existingPath = existingPath;
        if (existingPath != null) {
            // check if there is an existing node with the name
            recovery = new NodeStash(session, existingPath).excludeName("rep:cache");
            recovery.stash();
        }
        excludeNode(NAME_REP_CACHE);
        this.importMode = importMode;
        resolver = new DefaultNamePathResolver(session);
    }

    @Override
    public List<String> close() throws InvalidSerializedDataException {
        try {
            handler.endDocument();
        } catch (SAXException e) {
            throw new InvalidSerializedDataException("Invalid sysview", e);
        }

        // get list of created paths
        List<String> paths = new ArrayList<String>();
        try {
            if (existingPath != null && parent.getSession().nodeExists(existingPath)) {
                addPaths(paths, parent.getSession().getNode(existingPath));
            } else if (rootName != null && parent.hasNode(rootName.toString())) {
                addPaths(paths, parent.getNode(rootName.toString()));
            }
        } catch (RepositoryException e) {
            log.error("error while retrieving list of created nodes.");
        }

        // check for rescued child nodes
        if (recovery != null) {
            try {
                recovery.recover(importMode, null);
            } catch (RepositoryException e) {
                log.error("Error while processing rescued child nodes");
            } finally {
                recovery = null;
            }
        }

        return paths;
    }

    private void addPaths(List<String> paths, Node node) throws RepositoryException {
        paths.add(node.getPath());
        NodeIterator iter = node.getNodes();
        while (iter.hasNext()) {
            addPaths(paths, iter.nextNode());
        }
    }

    @Override
    public void startNode(DocViewNode2 ni) throws RepositoryException {
        if (ignoreLevel > 0) {
            DocViewSAXHandler.log.trace("ignoring child node of excluded node: {}", ni.getName());
            ignoreLevel++;
            return;
        }
        if (excludedNodeNames.contains(ni.getName())) {
            DocViewSAXHandler.log.trace("Ignoring excluded node {}", ni.getName());
            ignoreLevel = 1;
            return;
        }

        DocViewSAXHandler.log.trace("Transforming element to sysview {}", ni.getName());

        try {
            AttributesImpl attrs = new AttributesImpl();

            // use qualified name due to https://issues.apache.org/jira/browse/OAK-9586
            attrs.addAttribute(Name.NS_SV_URI, "name", "sv:name", "CDATA", resolver.getJCRName(ni.getName()));
            handler.startElement(Name.NS_SV_URI, "node", "sv:node", attrs);

            // add the properties
            for (DocViewProperty2 p: ni.getProperties()) {
                if (p.getStringValue() != null) {
                    attrs = new AttributesImpl();
                    // use qualified name due to https://issues.apache.org/jira/browse/OAK-9586
                    attrs.addAttribute(Name.NS_SV_URI, "name", "sv:name", "CDATA", resolver.getJCRName(p.getName()));
                    attrs.addAttribute(Name.NS_SV_URI, "type", "sv:type", "CDATA", PropertyType.nameFromValue(p.getType()));
                    if (p.isMultiValue()) {
                        attrs.addAttribute(Name.NS_SV_URI, "multiple", "sv:multiple", "CDATA", "true");
                    }

                    handler.startElement(Name.NS_SV_URI, "property", "sv:property", attrs);
                    for (String v: p.getStringValues()) {
                        handler.startElement(Name.NS_SV_URI, "value", "sv:value", DocViewSAXHandler.EMPTY_ATTRIBUTES);
                        handler.characters(v.toCharArray(), 0, v.length());
                        handler.endElement(Name.NS_SV_URI, "value", "sv:value");
                    }
                    handler.endElement(Name.NS_SV_URI, "property", "sv:property");
                }
            }
        } catch (SAXException e) {
            throw new InvalidSerializedDataException("Invalid sysview", e);
        }

        if (rootName == null) {
            rootName = ni.getName();
        }
    }

    @Override
    public void endNode() throws RepositoryException {
        if (ignoreLevel > 0) {
            ignoreLevel--;
            return;
        }
        try {
            handler.endElement(Name.NS_SV_URI, "node", "sv:node");
        } catch (SAXException e) {
            throw new InvalidSerializedDataException("Invalid sysview", e);
        }
    }

    public JcrSysViewTransformer excludeNode(Name name) {
        excludedNodeNames.add(name);
        return this;
    }
}
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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.jcr.NamespaceException;
import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.jackrabbit.spi.Name;
import org.apache.jackrabbit.spi.commons.conversion.DefaultNamePathResolver;
import org.apache.jackrabbit.spi.commons.conversion.IllegalNameException;
import org.apache.jackrabbit.spi.commons.name.NameConstants;
import org.apache.jackrabbit.spi.commons.namespace.NamespaceResolver;
import org.apache.jackrabbit.spi.commons.namespace.SessionNamespaceResolver;
import org.apache.jackrabbit.vault.fs.api.Aggregate;
import org.apache.jackrabbit.vault.fs.api.VaultFsConfig;
import org.apache.jackrabbit.vault.util.DocViewNode2;
import org.apache.jackrabbit.vault.util.JcrConstants;

/**
 * Writes the enhanced docview XML based on the aggregate tree to a given {@link XMLStreamWriter}.
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
     * the writer for outputting the xml
     */
    protected final XMLStreamWriter writer;

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
    private final List<Property> props = new ArrayList<>();

    /**
     * the export context
     */
    private final Aggregate aggregate;

    /**
     * flag indicating if binary references are to be used.
     */
    private final boolean useBinaryReferences;

    /**
     * Names of properties which should not be contained in the doc view serialization in case they are protected (in the underlying node definition).
     */
    private static final Set<String> IGNORED_POTENTIALLY_PROTECTED_PROPERTIES;
    static {
        Set<String> props = new HashSet<>();
        props.add(JcrConstants.JCR_CREATED);
        props.add(JcrConstants.JCR_CREATED_BY);
        props.add(JcrConstants.JCR_BASEVERSION);
        props.add(JcrConstants.JCR_VERSIONHISTORY);
        props.add(JcrConstants.JCR_PREDECESSORS);
        IGNORED_POTENTIALLY_PROTECTED_PROPERTIES = Collections.unmodifiableSet(props);
    }

    protected DocViewSAXFormatter(Aggregate aggregate, XMLStreamWriter writer)
            throws RepositoryException {

        this.aggregate = aggregate;
        this.session = aggregate.getNode().getSession();
        nsResolver = new SessionNamespaceResolver(session);
        this.writer = writer;

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
     * {@inheritDoc}
     */
    @Override
    public void onWalkBegin(Node root) throws RepositoryException {
        try {
            writer.writeStartDocument();
        } catch (XMLStreamException e) {
            throw new RepositoryException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onWalkEnd(Node root) throws RepositoryException {
        try {
            writer.writeEndDocument();
        } catch (XMLStreamException e) {
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
        try {
            DocViewNode2 docViewNode = DocViewNode2.fromNode(node, level == 0, props, useBinaryReferences);
            final Set<String> namespacePrefixes;
            if (level == 0) {
                namespacePrefixes = new LinkedHashSet<>();
                // always include jcr namespace (see JCRVLT-266)
                namespacePrefixes.add(Name.NS_JCR_PREFIX);
                namespacePrefixes.addAll(Arrays.asList(aggregate.getNamespacePrefixes()));
            } else {
                namespacePrefixes = Collections.emptySet();
            }
            docViewNode.writeStart(writer, nsResolver, namespacePrefixes);
        } catch (XMLStreamException e) {
            throw new RepositoryException(e);
        } catch (IllegalNameException e) {
            // augment exception message with path of node causing the problem
            throw new IllegalNameException(e.getMessage() + " (on path '" + node.getPath() + "')", e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onNodeEnd(Node node, boolean included, int level) throws RepositoryException {
        // end element (node)
        try {
            DocViewNode2.writeEnd(writer);
        } catch (XMLStreamException e) {
            throw new RepositoryException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onProperty(Property prop, int level) throws RepositoryException {
        if (IGNORED_POTENTIALLY_PROTECTED_PROPERTIES.contains(prop.getName()) && prop.getDefinition().isProtected()) {
            return;
        }

        props.add(prop);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onNodeIgnored(Node node, int depth) throws RepositoryException {
        try {
            // just add an empty node. used for ordering
            DocViewNode2 docViewNode = DocViewNode2.fromNode(node, false, Collections.emptyList(), useBinaryReferences);
            docViewNode.writeStart(writer, nsResolver, Collections.emptyList());
            DocViewNode2.writeEnd(writer);
        } catch (XMLStreamException e) {
            throw new RepositoryException(e);
        }
    }
}
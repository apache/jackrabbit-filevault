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
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.Optional;

import javax.jcr.ImportUUIDBehavior;
import javax.jcr.Item;
import javax.jcr.ItemNotFoundException;
import javax.jcr.NamespaceException;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.PropertyType;
import javax.jcr.ReferentialIntegrityException;
import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.jcr.ValueFactory;
import javax.jcr.nodetype.ConstraintViolationException;
import javax.jcr.nodetype.NodeDefinition;
import javax.jcr.nodetype.NodeType;
import javax.jcr.nodetype.PropertyDefinition;

import org.apache.jackrabbit.spi.Name;
import org.apache.jackrabbit.spi.commons.conversion.DefaultNamePathResolver;
import org.apache.jackrabbit.spi.commons.name.NameConstants;
import org.apache.jackrabbit.spi.commons.namespace.NamespaceResolver;
import org.apache.jackrabbit.util.ISO9075;
import org.apache.jackrabbit.util.Text;
import org.apache.jackrabbit.vault.fs.api.Artifact;
import org.apache.jackrabbit.vault.fs.api.ArtifactType;
import org.apache.jackrabbit.vault.fs.api.IdConflictPolicy;
import org.apache.jackrabbit.vault.fs.api.ImportMode;
import org.apache.jackrabbit.vault.fs.api.ItemFilterSet;
import org.apache.jackrabbit.vault.fs.api.NodeNameList;
import org.apache.jackrabbit.vault.fs.api.SerializationType;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.fs.impl.ArtifactSetImpl;
import org.apache.jackrabbit.vault.fs.impl.PropertyValueArtifact;
import org.apache.jackrabbit.vault.fs.io.AccessControlHandling;
import org.apache.jackrabbit.vault.fs.spi.ACLManagement;
import org.apache.jackrabbit.vault.fs.spi.ServiceProviderFactory;
import org.apache.jackrabbit.vault.fs.spi.UserManagement;
import org.apache.jackrabbit.vault.fs.spi.impl.jcr20.JcrNamespaceHelper;
import org.apache.jackrabbit.vault.util.DocViewNode;
import org.apache.jackrabbit.vault.util.DocViewProperty;
import org.apache.jackrabbit.vault.util.EffectiveNodeType;
import org.apache.jackrabbit.vault.util.JcrConstants;
import org.apache.jackrabbit.vault.util.MimeTypes;
import org.apache.jackrabbit.vault.util.RejectingEntityDefaultHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

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
public class DocViewSAXImporter extends RejectingEntityDefaultHandler implements NamespaceResolver {

    private static final String PROP_OAK_COUNTER = "oak:counter";

    /**
     * the default logger
     */
    static final Logger log = LoggerFactory.getLogger(DocViewSAXImporter.class);

    /**
     * empty attributes
     */
    static final Attributes EMPTY_ATTRIBUTES = new AttributesImpl();

    /**
     * these properties are protected but can be set nevertheless via system view xml import
     */
    static final Set<String> PROTECTED_PROPERTIES;

    static {
        Set<String> props = new HashSet<>();
        props.add(JcrConstants.JCR_PRIMARYTYPE);
        props.add(JcrConstants.JCR_MIXINTYPES);
        props.add(JcrConstants.JCR_UUID);
        props.add(JcrConstants.JCR_ISCHECKEDOUT);
        props.add(JcrConstants.JCR_BASEVERSION);
        props.add(JcrConstants.JCR_PREDECESSORS);
        props.add(JcrConstants.JCR_SUCCESSORS);
        props.add(JcrConstants.JCR_VERSIONHISTORY);
        PROTECTED_PROPERTIES = Collections.unmodifiableSet(props);
    }

    /**
     * the current namespace state
     */
    private NameSpace nsStack = null;

    /**
     * the importing session
     */
    private final Session session;

    /**
     * the root node of the import
     */
    private final Node parentNode;

    /**
     * the name of the root node
     */
    private final String rootNodeName;

    /**
     * the depth of the root node
     */
    private final int rootDepth;

    /**
     * current stack
     */
    private DocViewSAXImporter.StackElement stack;

    /**
     * Specified the filter that is used to check if child nodes are contained
     * in the import or not.
     */
    private final ItemFilterSet filter;

    /**
     * the workspace filter
     */
    private final WorkspaceFilter wspFilter;

    /**
     * a map of binaries (attachments)
     */
    private Map<String, Map<String, DocViewSAXImporter.BlobInfo>> binaries
            = new HashMap<>();

    /**
     * map of hint nodes in the same artifact set
     */
    private Set<String> hints = new HashSet<>();

    /**
     * properties that should not be deleted
     */
    private Set<String> preserveProperties = new HashSet<>();

    /**
     * the default name path resolver
     */
    private final DefaultNamePathResolver npResolver = new DefaultNamePathResolver(this);

    /**
     * final import information
     */
    private ImportInfoImpl importInfo = new ImportInfoImpl();

    /**
     * acl management
     */
    private final ACLManagement aclManagement;

    /**
     * user management
     */
    private final UserManagement userManagement;

    /**
     * the acl handling to apply
     */
    private AccessControlHandling aclHandling = AccessControlHandling.IGNORE;

    /**
     * Closed user group handling to apply by default (when set to <code>null</code>)
     * falls back to using aclHandling
     */
    private AccessControlHandling cugHandling = null;

    /**
     * flag indicating if SNS are supported by the underlying repository
     */
    private final boolean snsSupported;

    /**
     * helper for namespace registration
     */
    private final JcrNamespaceHelper nsHelper;

    private final IdConflictPolicy idConflictPolicy;

    /**
     * Creates a new importer that will receive SAX events and imports the
     * items below the given root.
     *
     * @param parentNode   the (parent) node of the import
     * @param rootNodeName name of the root node
     * @param artifacts    the artifact set that could contain attachments
     * @param wspFilter    workspace filter
     * @throws RepositoryException if an error occurs.
     */
    public DocViewSAXImporter(Node parentNode, String rootNodeName,
                              ArtifactSetImpl artifacts, WorkspaceFilter wspFilter, IdConflictPolicy idConflictPolicy)
            throws RepositoryException {
        this.filter = artifacts.getCoverage();
        this.wspFilter = wspFilter;
        this.parentNode = parentNode;
        this.rootDepth = parentNode.getDepth() + 1;
        this.session = parentNode.getSession();
        this.rootNodeName = rootNodeName;
        this.aclManagement = ServiceProviderFactory.getProvider().getACLManagement();
        this.userManagement = ServiceProviderFactory.getProvider().getUserManagement();
        this.snsSupported = session.getRepository().
                getDescriptorValue(Repository.NODE_TYPE_MANAGEMENT_SAME_NAME_SIBLINGS_SUPPORTED).getBoolean();
        this.nsHelper = new JcrNamespaceHelper(session, null);
        this.idConflictPolicy = idConflictPolicy;

        String rootPath = parentNode.getPath();
        if (!rootPath.equals("/")) {
            rootPath += "/";
        }
        for (Artifact a : artifacts.values(ArtifactType.BINARY)) {
            registerBinary(a, rootPath);
        }
        for (Artifact a : artifacts.values(ArtifactType.FILE)) {
            if (a.getSerializationType() != SerializationType.XML_DOCVIEW) {
                registerBinary(a, rootPath);
            }
        }
        for (Artifact a : artifacts.values(ArtifactType.HINT)) {
            hints.add(rootPath + a.getRelativePath());
        }
    }

    public AccessControlHandling getAclHandling() {
        return aclHandling;
    }

    public void setAclHandling(AccessControlHandling aclHandling) {
        this.aclHandling = aclHandling;
    }

    /**
     * returns closed user group handling
     * @return either current cugHandling value or <code>null</code>
     * if undefined and aclHandling is used instead
     */
    public AccessControlHandling getCugHandling() {
        return cugHandling;
    }

    /**
     * Sets closed user group handling for this importer
     * For backwards compatibility, if <code>null</code> is specified
     * then importer falls back to using aclHandling value instead.
     * @param cugHandling
     */
    public void setCugHandling(AccessControlHandling cugHandling) {
        this.cugHandling = cugHandling;
    }

    private void registerBinary(Artifact a, String rootPath)
            throws RepositoryException {
        String path = rootPath + a.getRelativePath();
        int idx = -1;
        int pos = path.indexOf('[', path.lastIndexOf('/'));
        if (pos > 0) {
            idx = Integer.parseInt(path.substring(pos + 1, path.length() - 1));
            path = path.substring(0, pos);
        }
        if (a.getType() == ArtifactType.FILE && a instanceof PropertyValueArtifact) {
            // hack, mark "file" properties just as present
            String parentPath = ((PropertyValueArtifact) a).getProperty().getParent().getPath();
            preserveProperties.add(parentPath + "/" + JcrConstants.JCR_DATA);
            preserveProperties.add(parentPath + "/" + JcrConstants.JCR_LASTMODIFIED);
        } else {
            preserveProperties.add(path);
            // hack, mark "file" properties just as present
            preserveProperties.add(path + "/jcr:content/jcr:data");
            preserveProperties.add(path + "/jcr:content/jcr:lastModified");
            preserveProperties.add(path + "/jcr:content/jcr:mimeType");
            String parentPath = Text.getRelativeParent(path, 1);
            String name = Text.getName(path);
            Map<String, DocViewSAXImporter.BlobInfo> infoSet = binaries.get(parentPath);
            if (infoSet == null) {
                infoSet = new HashMap<String, DocViewSAXImporter.BlobInfo>();
                binaries.put(parentPath, infoSet);
            }
            DocViewSAXImporter.BlobInfo info = infoSet.get(name);
            if (info == null) {
                info = new DocViewSAXImporter.BlobInfo(idx >= 0);
                infoSet.put(name, info);
            }
            if (idx >= 0) {
                info.add(idx, a);
            } else {
                info.add(a);
            }
        }
        log.trace("scheduling binary: {}{}", rootPath, a.getRelativePath() + a.getExtension());
    }

    private boolean isIncluded(Item item, int depth) throws RepositoryException {
        String path = importInfo.getRemapped().map(item.getPath());
        return wspFilter.contains(path) && (depth == 0 || filter.contains(item, path, depth));
    }

    public ImportInfoImpl getInfo() {
        return importInfo;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void startDocument() throws SAXException {
        try {
            stack = new StackElement(parentNode, parentNode.isNew());
        } catch (RepositoryException e) {
            throw new SAXException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void endDocument() throws SAXException {
        if (!stack.isRoot()) {
            throw new IllegalStateException("stack mismatch");
        }

        // process binaries
        for (String parentPath : binaries.keySet()) {
            Map<String, DocViewSAXImporter.BlobInfo> blobs = binaries.get(parentPath);
            // check for node
            log.trace("processing binaries at {}", parentPath);
            try {
                if (session.nodeExists(parentPath)) {
                    Node node = session.getNode(parentPath);
                    for (String propName : blobs.keySet()) {
                        DocViewSAXImporter.BlobInfo info = blobs.get(propName);
                        if (node.hasNode(propName)) {
                            handleBinNode(node.getNode(propName), info, true);
                        } else if (info.isFile()) {
                            // special case for not existing files
                            Node fNode = node.addNode(propName, JcrConstants.NT_FILE);
                            importInfo.onCreated(fNode.getPath());
                            handleBinNode(fNode, info, false);
                        } else {
                            if (info.isMulti) {
                                node.setProperty(propName, info.getValues(session));
                            } else {
                                node.setProperty(propName, info.getValue(session));
                            }
                            importInfo.onModified(node.getPath());
                        }
                    }

                } else {
                    log.warn("binaries parent path does not exist: {}", parentPath);
                    // assume below 'this' root.
                    Node node = null;
                    for (String propName : blobs.keySet()) {
                        DocViewSAXImporter.BlobInfo info = blobs.get(propName);
                        if (info.isFile()) {
                            if (node == null) {
                                node = createNodeDeep(parentPath);
                            }
                            Node fNode = node.addNode(propName, JcrConstants.NT_FILE);
                            importInfo.onCreated(fNode.getPath());
                            handleBinNode(fNode, info, false);
                        }
                    }
                }
            } catch (Exception e) {
                throw new SAXException(e);
            }
        }
    }

    private Node createNodeDeep(String path) throws RepositoryException {
        if (session.nodeExists(path)) {
            return session.getNode(path);
        }
        int idx = path.lastIndexOf('/');
        if (idx <= 0) {
            return session.getRootNode();
        }
        String parentPath = path.substring(0, idx);
        String name = path.substring(idx + 1);
        Node parentNode = createNodeDeep(parentPath);
        Node node;
        try {
            node = parentNode.addNode(name);
        } catch (RepositoryException e) {
            // try create with nt:folder
            node = parentNode.addNode(name, JcrConstants.NT_FOLDER);
        }
        importInfo.onCreated(node.getPath());
        return node;
    }

    private void handleBinNode(Node node, DocViewSAXImporter.BlobInfo info, boolean checkIfNtFileOk)
            throws RepositoryException, IOException {
        log.trace("handling binary file at {}", node.getPath());
        if (info.isMulti) {
            throw new IllegalStateException("unable to add MV binary to node " + node.getPath());
        }

        if (checkIfNtFileOk) {
            if (node.isNodeType(JcrConstants.NT_FILE)) {
                if (node.hasNode(JcrConstants.JCR_CONTENT)) {
                    node = node.getNode(JcrConstants.JCR_CONTENT);
                } else {
                    node = node.addNode(JcrConstants.JCR_CONTENT, JcrConstants.NT_RESOURCE);
                }
            }
        } else {
            node = node.addNode(JcrConstants.JCR_CONTENT, JcrConstants.NT_RESOURCE);
        }

        Artifact a = info.artifacts.get(0);
        // Keep track of whether this file got modified
        boolean modified = false;
        // Set the jcr:data property
        ValueFactory factory = node.getSession().getValueFactory();
        try (InputStream input = a.getInputStream()) {
            Value value = factory.createValue(input);
            if (node.hasProperty(JcrConstants.JCR_DATA)) {
                Property data = node.getProperty(JcrConstants.JCR_DATA);
                if (!value.equals(data.getValue())) {
                    data.setValue(value);
                    // mark jcr:data as modified.
                    importInfo.onModified(data.getPath());
                    modified = true;
                }
            } else {
                Property data = node.setProperty(JcrConstants.JCR_DATA, value);
                // mark jcr:data as created
                importInfo.onCreated(data.getPath());
                modified = true;
            }
        }
        // always update last modified if binary was modified (bug #22969)
        if (!node.hasProperty(JcrConstants.JCR_LASTMODIFIED) || modified) {
            Calendar lastModified = Calendar.getInstance();
            node.setProperty(JcrConstants.JCR_LASTMODIFIED, lastModified);
            modified = true;
        }
        // do not overwrite mimetype
        if (!node.hasProperty(JcrConstants.JCR_MIMETYPE)) {
            String mimeType = a.getContentType();
            if (mimeType == null) {
                mimeType = Text.getName(a.getRelativePath(), '.');
                mimeType = MimeTypes.getMimeType(mimeType, MimeTypes.APPLICATION_OCTET_STREAM);
            }
            node.setProperty(JcrConstants.JCR_MIMETYPE, mimeType);
            modified = true;
        }
        if (node.isNew()) {
            importInfo.onCreated(node.getPath());
        } else if (modified) {
            importInfo.onModified(node.getPath());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void characters(char ch[], int start, int length) throws SAXException {
        // can be ignored in docview
    }

    /**
     * {@inheritDoc}
     * <p>
     * Pushes the mapping to the stack and updates the namespace mapping in the
     * session.
     */
    @Override
    public void startPrefixMapping(String prefix, String uri) throws SAXException {
        log.trace("-> prefixMapping for {}:{}", prefix, uri);
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
                oldPrefix = nsHelper.registerNamespace(prefix, uri);
            } catch (RepositoryException e1) {
                throw new SAXException(e);
            }
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
    }

    /**
     * {@inheritDoc}
     * <p>
     * Pops the mapping from the stack and updates the namespace mapping in the
     * session if necessary.
     */
    @Override
    public void endPrefixMapping(String prefix) throws SAXException {
        log.trace("<- prefixMapping for {}", prefix);
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
            log.trace("   remapped: {}:{}", prefix, ns.uri);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void startElement(final String uri, final String localName, final String qName, final Attributes attributes) throws SAXException {
        // special handling for root node
        String label = ISO9075.decode(qName);
        if (stack.isRoot()
                && localName.equals(NameConstants.JCR_ROOT.getLocalName())
                && uri.equals(NameConstants.JCR_ROOT.getNamespaceURI())) {
                label = rootNodeName;
        }
        String name = label;
        log.trace("-> element {}", label);
        boolean snsNode = false;
        int idx = name.lastIndexOf('[');
        if (idx > 0) {
            if (!snsSupported) {
                int idx2 = name.indexOf(']', idx);
                if (idx2 > 0) {
                    try {
                        if (Integer.valueOf(name.substring(idx + 1, idx2)) > 1) {
                            snsNode = true;
                        }
                    } catch (NumberFormatException e) {
                        // ignore
                    }
                }
            }
            name = name.substring(0, idx);
        }
        try {
            stack.addName(label);
            Node node = stack.getNode();
            if (node == null) {
                stack = stack.push();
                DocViewAdapter xform = stack.getAdapter();
                if (xform != null) {
                    DocViewNode ni = new DocViewNode(name, label, attributes, npResolver);
                    xform.startNode(ni);
                } else {
                    log.trace("Skipping ignored element {}", name);
                }
            } else {
                final String path = (!node.getPath().equals("/") ? node.getPath() : "")+ "/" + name;
                if (attributes.getLength() == 0) {
                    // only ordering node. skip
                    log.trace("Skipping empty node {}", path);
                    stack = stack.push();
                } else if (snsNode) {
                    // skip SNS nodes with index > 1
                    log.warn("Skipping unsupported SNS node with index > 1. Some content will be missing after import: {}", path);
                    stack = stack.push();
                } else {
                    try {
                        DocViewNode ni = new DocViewNode(name, label, attributes, npResolver);
                        // is policy node?
                        if (aclManagement.isACLNodeType(ni.primary)) {
                            AccessControlHandling acHandling = getAcHandling(label);
                            if (acHandling != AccessControlHandling.CLEAR && acHandling != AccessControlHandling.IGNORE) {
                                log.trace("Access control policy element detected. starting special transformation {}/{}", node.getPath(), name);
                                if (aclManagement.ensureAccessControllable(node, ni.primary)) {
                                    log.debug("Adding access control policy element to non access-controllable parent - adding mixin: {}", node.getPath());
                                }
                                stack = stack.push();
                                if ("rep:repoPolicy".equals(name)) {
                                    if (node.getDepth() == 0) {
                                        stack.adapter = new JackrabbitACLImporter(session, acHandling);
                                        stack.adapter.startNode(ni);
                                    } else {
                                        log.debug("ignoring invalid location for repository level ACL: {}", node.getPath());
                                    }
                                } else {
                                    stack.adapter = new JackrabbitACLImporter(node, acHandling);
                                    stack.adapter.startNode(ni);
                                }
                            } else {
                                stack = stack.push();
                            }
                        } else if (userManagement != null && userManagement.isAuthorizableNodeType(ni.primary)) {
                            // is authorizable node?
                            handleAuthorizable(node, ni);
                        } else {
                            // regular node
                            stack = stack.push(addNode(ni));
                        }
                    } catch (RepositoryException | IOException e) {
                        if (e instanceof ConstraintViolationException && wspFilter.getImportMode(path) != ImportMode.REPLACE) {
                            // only warn in case of constraint violations for mode != replace (as best effort is used in that case)
                            log.warn("Error during processing of {}: {}, skip node due to import mode {}", path, e.toString(), wspFilter.getImportMode(path));
                            importInfo.onNop(path);
                        } else {
                            log.error("Error during processing of {}: {}", path, e.toString());
                            importInfo.onError(path, e);
                        }
                        stack = stack.push();
                    }
                }
            }
        }  catch (RepositoryException e) {
            throw new SAXException("Fatal exception while parsing", e);
        } 
    }

    /**
     * Handle an authorizable node
     *
     * @param node the parent node
     * @param ni   doc view node of the authorizable
     * @throws RepositoryException if an error accessing the repository occurrs.
     * @throws SAXException        if an XML parsing error occurrs.
     */
    private void handleAuthorizable(Node node, DocViewNode ni) throws RepositoryException, SAXException {
        String id = userManagement.getAuthorizableId(ni);
        String newPath = node.getPath() + "/" + ni.name;
        boolean isIncluded = wspFilter.contains(newPath);
        String oldPath = userManagement.getAuthorizablePath(this.session, id);
        if (oldPath == null) {
            if (!isIncluded) {
                log.trace("auto-creating authorizable node not in filter {}", newPath);
            }

            // just import the authorizable node
            log.trace("Authorizable element detected. starting sysview transformation {}", newPath);
            stack = stack.push();
            stack.adapter = new JcrSysViewTransformer(node, wspFilter.getImportMode(newPath));
            stack.adapter.startNode(ni);
            importInfo.onCreated(newPath);
            return;
        }

        Node authNode = session.getNode(oldPath);
        ImportMode mode = wspFilter.getImportMode(newPath);

        // if existing path is not the same as this, we need to register this so that further
        // nodes down the line (i.e. profiles, policies) are imported at the correct location
        // we only follow existing authorizables for non-REPLACE mode and if ignoring this authorizable node
        // todo: check if this also works cross-aggregates
        if (mode != ImportMode.REPLACE || !isIncluded) {
            importInfo.onRemapped(oldPath, newPath);
        }

        if (!isIncluded) {
            // skip authorizable handling - always follow existing authorizable - regardless of mode
            // todo: we also need to check any rep:Memberlist subnodes. see JCRVLT-69
            stack = stack.push(new StackElement(authNode, false));
            importInfo.onNop(oldPath);
            return;
        }

        switch (mode) {
            case MERGE:
            case MERGE_PROPERTIES:
                // remember desired memberships.
                // todo: how to deal with multi-node memberships? see JCRVLT-69
                DocViewProperty prop = ni.props.get("rep:members");
                if (prop != null) {
                    importInfo.registerMemberships(id, prop.values);
                }

                log.debug("Skipping import of existing authorizable '{}' due to MERGE import mode.", id);
                stack = stack.push(new StackElement(authNode, false));
                importInfo.onNop(newPath);
                break;

            case REPLACE:
                // just replace the entire subtree for now.
                log.trace("Authorizable element detected. starting sysview transformation {}", newPath);
                stack = stack.push();
                stack.adapter = new JcrSysViewTransformer(node, mode);
                stack.adapter.startNode(ni);
                importInfo.onReplaced(newPath);
                break;

            case UPDATE:
            case UPDATE_PROPERTIES:
                log.trace("Authorizable element detected. starting sysview transformation {}", newPath);
                stack = stack.push();
                stack.adapter = new JcrSysViewTransformer(node, oldPath, mode);
                // we need to tweak the ni.name so that the sysview import does not
                // rename the authorizable node name
                String newName = Text.getName(oldPath);
                DocViewNode mapped = new DocViewNode(
                        newName,
                        newName,
                        ni.uuid,
                        ni.props,
                        ni.mixins,
                        ni.primary
                );
                // but we need to augment with a potential rep:authorizableId
                if (authNode.hasProperty("rep:authorizableId")) {
                    DocViewProperty authId = new DocViewProperty(
                            "rep:authorizableId",
                            new String[]{authNode.getProperty("rep:authorizableId").getString()},
                            false,
                            PropertyType.STRING
                    );
                    mapped.props.put(authId.name, authId);
                }
                stack.adapter.startNode(mapped);
                importInfo.onReplaced(newPath);
                break;
        }
    }

    private StackElement addNode(DocViewNode ni) throws RepositoryException, IOException {
        final Node currentNode = stack.getNode();

        Node existingNode = null;
        if ("".equals(ni.label)) {
            // special case for root node update
            existingNode = currentNode;
        } else {
            if (stack.checkForNode() && currentNode.hasNode(ni.label)) {
                existingNode = currentNode.getNode(ni.label);
            }
            if (ni.uuid != null && idConflictPolicy == IdConflictPolicy.FAIL) {
                try {
                    // does uuid already exist in the repo?
                    Node sameIdNode = session.getNodeByIdentifier(ni.uuid);
                    // edge-case: same node path -> uuid is kept
                    if (existingNode != null && existingNode.getPath().equals(sameIdNode.getPath())) {
                        log.debug("Node with existing identifier {} at {} is being updated without modifying its uuid", ni.uuid, existingNode.getPath());
                    } else {
                        // uuid found in path covered by filter
                        if (isIncluded(sameIdNode, 0)) {
                            log.warn("Node identifier {} for to-be imported node {}/{} already taken by {}, trying to release it.", ni.uuid, currentNode.getPath(), ni.label, sameIdNode.getPath());
                            removeReferences(sameIdNode);
                            session.removeItem(sameIdNode.getPath());
                            existingNode = null;
                        } else {
                            // uuid found in path not-covered by filter
                            throw new ReferentialIntegrityException("UUID " + ni.uuid + " already taken by node " + sameIdNode.getPath());
                        }
                    }
                } catch (ItemNotFoundException e) {
                    // ignore
                }
            }
        }

        // check if new node needs to be checked in
        DocViewProperty coProp = ni.props.remove(JcrConstants.JCR_ISCHECKEDOUT);
        boolean isCheckedIn = coProp != null && "false".equals(coProp.values[0]);

        // create or update node
        boolean isNew = existingNode == null;
        if (isNew) {
            // workaround for bug in jcr2spi if mixins are empty
            if (!ni.props.containsKey(JcrConstants.JCR_MIXINTYPES)) {
                ni.props.put(JcrConstants.JCR_MIXINTYPES,
                        new DocViewProperty(JcrConstants.JCR_MIXINTYPES, new String[0], true, PropertyType.NAME));
            }

            stack.ensureCheckedOut();
            existingNode = createNewNode(currentNode, ni);
            if (existingNode.getDefinition() == null) {
                throw new RepositoryException("Child node not allowed.");
            }
            if (existingNode.isNodeType(JcrConstants.NT_RESOURCE)) {
                if (!existingNode.hasProperty(JcrConstants.JCR_DATA)) {
                    importInfo.onMissing(existingNode.getPath() + "/" + JcrConstants.JCR_DATA);
                }
            } else if (isCheckedIn) {
                // don't rely on isVersionable here, since SPI might not have this info yet
                importInfo.registerToVersion(existingNode.getPath());
            }
            importInfo.onCreated(existingNode.getPath());

        } else if (isIncluded(existingNode, existingNode.getDepth() - rootDepth)) {
            if (isCheckedIn) {
                // don't rely on isVersionable here, since SPI might not have this info yet
                importInfo.registerToVersion(existingNode.getPath());
            }
            ImportMode importMode = wspFilter.getImportMode(existingNode.getPath());
            Node updatedNode = updateExistingNode(existingNode, ni, importMode);
            if (updatedNode != null) {
                if (updatedNode.isNodeType(JcrConstants.NT_RESOURCE) && !updatedNode.hasProperty(JcrConstants.JCR_DATA)) {
                    importInfo.onMissing(existingNode.getPath() + "/" + JcrConstants.JCR_DATA);
                }
                importInfo.onModified(updatedNode.getPath());
                existingNode = updatedNode;
            } else {
                importInfo.onNop(existingNode.getPath());
            }
        } else {
            // remove registered binaries outside of the filter (JCR-126)
            binaries.remove(existingNode.getPath());
        }
        return new StackElement(existingNode, isNew);
    }

    /**
     * Tries to remove references to the given node but only in case they are included in the filters.
     * @param node the referenced node
     * @throws ReferentialIntegrityException in case some references can not be removed (outside filters)
     * @throws RepositoryException in case some other error occurs
     */
    public void removeReferences(@NotNull Node node) throws ReferentialIntegrityException, RepositoryException {
        Collection<String> removableReferencePaths = new ArrayList<>();
        PropertyIterator pIter = node.getReferences();
        while (pIter.hasNext()) {
            Property referenceProperty = pIter.nextProperty();
            if (isIncluded(referenceProperty, 0) || idConflictPolicy == IdConflictPolicy.FORCE_REMOVE_CONFLICTING_ID) {
                removableReferencePaths.add(referenceProperty.getPath());
            } else {
                throw new ReferentialIntegrityException("Found non-removable reference for conflicting UUID " + node.getIdentifier() + " (" + node.getPath() + ") at " + referenceProperty.getPath());
            }
        }
        for (String referencePath : removableReferencePaths) {
            log.info("Remove reference towards {} at {}", node.getIdentifier(), referencePath);
            session.removeItem(referencePath);
        }
    }

    private @Nullable Node updateExistingNode(@NotNull Node node, @NotNull DocViewNode ni, @NotNull ImportMode importMode) throws RepositoryException {
        VersioningState vs = new VersioningState(stack, node);
        Node updatedNode = null;
        // try to set uuid via sysview import if it differs from existing one
        if (ni.uuid != null && !node.getIdentifier().equals(ni.uuid) && !"rep:root".equals(ni.primary)) {
            NodeStash stash = new NodeStash(session, node.getPath());
            stash.stash();
            Node parent = node.getParent();
            removeReferences(node);
            node.remove();
            updatedNode = createNewNode(parent, ni);
            stash.recover(importMode, importInfo);
        } else {
            // TODO: is this faster than using sysview import?
            // set new primary type (but never set rep:root)
            if (importMode == ImportMode.REPLACE && !"rep:root".equals(ni.primary)) {
                if (!node.getPrimaryNodeType().getName().equals(ni.primary)) {
                    vs.ensureCheckedOut();
                    node.setPrimaryType(ni.primary);
                    updatedNode = node;
                }
            }
            // calculate mixins to be added
            Set<String> newMixins = new HashSet<>();
            AccessControlHandling acHandling = getAcHandling(ni.name);
            if (ni.mixins != null) {
                for (String mixin : ni.mixins) {
                    // omit name if mix:AccessControllable and CLEAR
                    if (!aclManagement.isAccessControllableMixin(mixin)
                            || acHandling != AccessControlHandling.CLEAR) {
                        newMixins.add(mixin);
                    }
                }
            }
            // remove mixins not in package (only for mode = replace)
            if (importMode == ImportMode.REPLACE) {
                for (NodeType mix : node.getMixinNodeTypes()) {
                    String name = mix.getName();
                    if (!newMixins.remove(name)) {
                        // special check for mix:AccessControllable
                        if (!aclManagement.isAccessControllableMixin(name)
                                || acHandling == AccessControlHandling.CLEAR
                                || acHandling == AccessControlHandling.OVERWRITE) {
                            vs.ensureCheckedOut();
                            node.removeMixin(name);
                            updatedNode = node;
                        }
                    }
                }
            }
            // add remaining mixins (for all import modes)
            for (String mixin : newMixins) {
                vs.ensureCheckedOut();
                node.addMixin(mixin);
                updatedNode = node;
            }
    
            // remove unprotected properties not in package (only for mode = replace)
            if (importMode == ImportMode.REPLACE) {
                PropertyIterator pIter = node.getProperties();
                while (pIter.hasNext()) {
                    Property p = pIter.nextProperty();
                    String propName = p.getName();
                    if (!p.getDefinition().isProtected()
                            && !ni.props.containsKey(propName)
                            && !preserveProperties.contains(p.getPath())
                            && wspFilter.includesProperty(p.getPath())) {
                        vs.ensureCheckedOut();
                        p.remove();
                        updatedNode = node;
                    }
                }
            }

            // add/modify properties contained in package
            if (setUnprotectedProperties(node, ni, importMode == ImportMode.REPLACE|| importMode == ImportMode.UPDATE || importMode == ImportMode.UPDATE_PROPERTIES, vs)) {
                updatedNode = node;
            }
        }
        return updatedNode;
    }

    /**
     * Creates a new node via system view XML and {@link Session#importXML(String, InputStream, int)} to be able to set protected properties. 
     * Afterwards uses regular JCR API to set unprotected properties (on a best-effort basis as this depends on the repo implementation).
     * @param parentNode the parent node below which the new node should be created
     * @param ni the information about the new node to be created
     * @return the newly created node
     * @throws RepositoryException
     */
    private @NotNull Node createNewNode(Node parentNode, DocViewNode ni)
            throws RepositoryException {
        final int importUuidBehavior;
        switch(idConflictPolicy) {
            case CREATE_NEW_ID:
                // what happens to references?
                importUuidBehavior = ImportUUIDBehavior.IMPORT_UUID_CREATE_NEW;
                break;
            case FORCE_REMOVE_CONFLICTING_ID:
                importUuidBehavior = ImportUUIDBehavior.IMPORT_UUID_COLLISION_REMOVE_EXISTING;
                break;
            default:
                importUuidBehavior = ImportUUIDBehavior.IMPORT_UUID_COLLISION_THROW;
                break;
        }
        try {
            String parentPath = parentNode.getPath();
            final ContentHandler handler = session.getImportContentHandler(
                    parentPath,
                    importUuidBehavior);
            // first define the current namespaces
            String[] prefixes = session.getNamespacePrefixes();
            handler.startDocument();
            for (String prefix : prefixes) {
                handler.startPrefixMapping(prefix, session.getNamespaceURI(prefix));
            }
            AttributesImpl attrs = new AttributesImpl();
            attrs.addAttribute(Name.NS_SV_URI, "name", "sv:name", "CDATA", ni.name);
            handler.startElement(Name.NS_SV_URI, "node", "sv:node", attrs);

            // check if SNS and a helper uuid if needed
            boolean addMixRef = false;
            if (!ni.label.equals(ni.name) && ni.uuid == null) {
                ni.uuid = UUID.randomUUID().toString();
                ni.props.put(JcrConstants.JCR_UUID, new DocViewProperty(
                        JcrConstants.JCR_UUID, new String[]{ni.uuid}, false, PropertyType.STRING));
                // check mixins
                DocViewProperty mix = ni.props.get(JcrConstants.JCR_MIXINTYPES);
                addMixRef = true;
                if (mix == null) {
                    mix = new DocViewProperty(JcrConstants.JCR_MIXINTYPES, new String[]{JcrConstants.MIX_REFERENCEABLE}, true, PropertyType.NAME);
                    ni.props.put(mix.name, mix);
                } else {
                    for (String v : mix.values) {
                        if (v.equals(JcrConstants.MIX_REFERENCEABLE)) {
                            addMixRef = false;
                            break;
                        }
                    }
                    if (addMixRef) {
                        String[] vs = new String[mix.values.length + 1];
                        System.arraycopy(mix.values, 0, vs, 0, mix.values.length);
                        vs[mix.values.length] = JcrConstants.MIX_REFERENCEABLE;
                        mix = new DocViewProperty(JcrConstants.JCR_MIXINTYPES, vs, true, PropertyType.NAME);
                        ni.props.put(mix.name, mix);
                    }
                }
            }
            // add the properties
            for (DocViewProperty p : ni.props.values()) {
                if (p != null && p.values != null) {
                    // only pass 'protected' properties to the import
                    if (PROTECTED_PROPERTIES.contains(p.name)) {
                        attrs = new AttributesImpl();
                        attrs.addAttribute(Name.NS_SV_URI, "name", "sv:name", "CDATA", p.name);
                        attrs.addAttribute(Name.NS_SV_URI, "type", "sv:type", "CDATA", PropertyType.nameFromValue(p.type));
                        handler.startElement(Name.NS_SV_URI, "property", "sv:property", attrs);
                        for (String v : p.values) {
                            handler.startElement(Name.NS_SV_URI, "value", "sv:value", EMPTY_ATTRIBUTES);
                            handler.characters(v.toCharArray(), 0, v.length());
                            handler.endElement(Name.NS_SV_URI, "value", "sv:value");
                        }
                        handler.endElement(Name.NS_SV_URI, "property", "sv:property");
                    }
                }
            }
            handler.endElement(Name.NS_SV_URI, "node", "sv:node");
            handler.endDocument();

            // retrieve newly created node either by uuid, label or name
            Node node = getNodeByUUIDLabelOrName(parentNode, ni, importUuidBehavior == ImportUUIDBehavior.IMPORT_UUID_CREATE_NEW);
            setUnprotectedProperties(node, ni, true, null);
            // remove mix referenceable if it was temporarily added
            if (addMixRef) {
                node.removeMixin(JcrConstants.MIX_REFERENCEABLE);
            }
            return node;
        } catch (SAXException e) {
            Exception root = e.getException();
            if (root instanceof RepositoryException) {
                if (root instanceof ConstraintViolationException) {
                    // potentially rollback changes in the transient space (only relevant for Oak, https://issues.apache.org/jira/browse/OAK-9436), as otherwise the same exception is thrown again at Session.save()
                    try {
                        Node node = getNodeByUUIDLabelOrName(parentNode, ni, importUuidBehavior == ImportUUIDBehavior.IMPORT_UUID_CREATE_NEW);
                        node.remove();
                    } catch (RepositoryException re) {
                        // ignore as no node found when the transient space is clean already
                    }
                }
                throw (RepositoryException) root;
            } else if (root instanceof RuntimeException) {
                throw (RuntimeException) root;
            } else {
                throw new RepositoryException("Error while creating node", root);
            }
        }
    }

    /**
     * Determines if a given property is protected according to the node type.
    * 
     * @param effectiveNodeType the effective node type
     * @param docViewProperty the property
     * @return{@code true} in case the property is protected, {@code false} otherwise
     * @throws RepositoryException 
     */
    private static boolean isPropertyProtected(@NotNull EffectiveNodeType effectiveNodeType, @NotNull DocViewProperty docViewProperty) throws RepositoryException {
        return effectiveNodeType.getApplicablePropertyDefinition(docViewProperty.name, docViewProperty.isMulti, docViewProperty.type).map(PropertyDefinition::isProtected).orElse(false);
    }

    private Node getNodeByUUIDLabelOrName(@NotNull Node currentNode, @NotNull DocViewNode ni, boolean isUuidNewlyAssigned) throws RepositoryException {
        Node node = null;
        if (ni.uuid != null && !isUuidNewlyAssigned) {
            try {
                node = currentNode.getSession().getNodeByUUID(ni.uuid);
            } catch (RepositoryException e) {
                log.warn("Newly created node not found by uuid {}: {}", currentNode.getPath() + "/" + ni.name, e.toString());
            }
        }
        if (node == null) {
            try {
                node = currentNode.getNode(ni.label);
            } catch (RepositoryException e) {
                log.warn("Newly created node not found by label {}: {}", currentNode.getPath() + "/" + ni.name, e.toString());
            }
        }
        if (node == null) {
            try {
                node = currentNode.getNode(ni.name);
            } catch (RepositoryException e) {
                log.warn("Newly created node not found by name {}: {}", currentNode.getPath() + "/" + ni.name, e.toString());
                throw e;
            }
        }
        return node;
    }

    private boolean setUnprotectedProperties(@NotNull Node node, @NotNull DocViewNode ni, boolean overwriteExistingProperties, @Nullable VersioningState vs) throws RepositoryException {
        boolean isAtomicCounter = false;
        if (ni.mixins != null) {
            for (String mixin : ni.mixins) {
                if ("mix:atomicCounter".equals(mixin)) {
                    isAtomicCounter = true;
                }
            }
        }
        EffectiveNodeType effectiveNodeType = EffectiveNodeType.ofNode(node);
        boolean modified = false;
        // add properties
        for (DocViewProperty prop : ni.props.values()) {
            if (prop != null && !isPropertyProtected(effectiveNodeType, prop) && (overwriteExistingProperties || !node.hasProperty(prop.name)) && wspFilter.includesProperty(node.getPath() + "/" + prop.name)) {
                // check if property is allowed
                try {
                    modified |= prop.apply(node);
                } catch (RepositoryException e) {
                    try {
                        if (vs == null) {
                            throw e;
                        }
                        // try again with checked out node
                        vs.ensureCheckedOut();
                        modified |= prop.apply(node);
                    } catch (RepositoryException e1) {
                        // be lenient in case of mode != replace
                        if (wspFilter.getImportMode(node.getPath()) != ImportMode.REPLACE) {
                            log.warn("Error while setting property {} (ignore due to mode {}): {}", prop.name, wspFilter.getImportMode(node.getPath()), e1);
                        } else {
                            throw e;
                        }
                    }
                }
            }
        }

        // adjust oak atomic counter
        if (isAtomicCounter && wspFilter.includesProperty(node.getPath() + "/" + PROP_OAK_COUNTER)) {
            long previous = 0;
            if (node.hasProperty(PROP_OAK_COUNTER)) {
                previous = node.getProperty(PROP_OAK_COUNTER).getLong();
            }
            long counter = 0;
            try {
                counter = Long.valueOf(ni.getValue(PROP_OAK_COUNTER));
            } catch (NumberFormatException e) {
                // ignore
            }
            if (counter != previous) {
                node.setProperty("oak:increment", counter - previous);
                modified = true;
            }
        }
        return modified;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        log.trace("<- element {}", qName);
        try {
            // currentNode's import is finished, check if any child nodes
            // need to be removed
            NodeNameList childNames = stack.getChildNames();
            Node node = stack.getNode();
            int numChildren = 0;
            if (node == null) {
                DocViewAdapter adapter = stack.getAdapter();
                if (adapter != null) {
                    adapter.endNode();
                }
                // close transformer if last in stack
                if (stack.adapter != null) {
                    List<String> createdPaths = stack.adapter.close();
                    for (String createdPath : createdPaths) {
                        importInfo.onCreated(createdPath);
                    }
                    stack.adapter = null;
                    log.trace("Sysview transformation complete.");
                }
            } else {
                NodeIterator iter = node.getNodes();
                while (iter.hasNext()) {
                    numChildren++;
                    Node child = iter.nextNode();
                    String path = child.getPath();
                    String label = Text.getName(path);
                    AccessControlHandling acHandling = getAcHandling(child.getName());
                    if (!childNames.contains(label)
                            && !hints.contains(path)
                            && isIncluded(child, child.getDepth() - rootDepth)) {
                        // if the child is in the filter, it belongs to
                        // this aggregate and needs to be removed
                        if (aclManagement.isACLNode(child)) {
                            if (acHandling == AccessControlHandling.OVERWRITE
                                    || acHandling == AccessControlHandling.CLEAR) {
                                importInfo.onDeleted(path);
                                aclManagement.clearACL(node);
                            }
                        } else {
                            if (wspFilter.getImportMode(path) == ImportMode.REPLACE) {

                                // check if child is not protected
                                if (child.getDefinition().isProtected()) {
                                    log.debug("Refuse to delete protected child node: {}", path);
                                } else if (child.getDefinition().isMandatory() 
                                        && !hasSiblingWithSameType(child.getParent(), child)) {
                                    log.debug("Refuse to delete mandatory child node: {}", path);
                                } else {
                                    importInfo.onDeleted(path);
                                    child.remove();
                                }
                            }
                        }
                    } else if (acHandling == AccessControlHandling.CLEAR
                            && aclManagement.isACLNode(child)
                            && isIncluded(child, child.getDepth() - rootDepth)) {
                        importInfo.onDeleted(path);
                        aclManagement.clearACL(node);
                    }
                }
                if (isIncluded(node, node.getDepth() - rootDepth)) {
                    // ensure order
                    stack.restoreOrder();
                }
            }
            stack = stack.pop();
            if (node != null && (numChildren == 0 && !childNames.isEmpty() || stack.isRoot())) {
                importInfo.addNameList(node.getPath(), childNames);
            }
        } catch (RepositoryException e) {
            throw new SAXException(e);
        }
    }

    private boolean hasSiblingWithSameType(Node parent, Node child) throws RepositoryException {

        try {
            EffectiveNodeType ent = EffectiveNodeType.ofNode(parent);
            String typeName = ent.getApplicableChildNodeDefinition(child.getName(), child.getPrimaryNodeType()).get().getName();

            NodeIterator iter = parent.getNodes();
            while (iter.hasNext()) {
                Node sibling = iter.nextNode();
                if (!sibling.isSame(child)) {
                    Optional<NodeDefinition> childDef = ent.getApplicableChildNodeDefinition(sibling.getName(),
                            sibling.getPrimaryNodeType());
                    try {
                        if (typeName.equals(childDef.get().getName())) {
                            return true;
                        }
                    } catch (NoSuchElementException ignored) {
                    }
                }
            }
        } catch (NoSuchElementException ignored) {
        }
        return false;
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

    /**
     * Returns proper access control handling value based on the node
     * name.
     * @param nodeName name of the access control node
     * @return cugHandling for CUG related nodes, aclHandling for
     * everything else
     */
    @NotNull
    private AccessControlHandling getAcHandling(@NotNull String nodeName) {
        if (cugHandling != null && "rep:cugPolicy".equals(nodeName)) {
            return cugHandling;
        } else {
            return aclHandling;
        }
    }

    /**
     * Helper class that stores information about attachments
     */
    private static class BlobInfo {

        private final boolean isMulti;

        private final List<Artifact> artifacts = new ArrayList<Artifact>();

        public BlobInfo(boolean multi) {
            isMulti = multi;
        }

        public boolean isFile() {
            return artifacts.size() > 0 && artifacts.get(0).getType() == ArtifactType.FILE;
        }

        public void add(Artifact a) {
            assert artifacts.isEmpty();
            artifacts.add(a);
        }

        public void add(int idx, Artifact a) {
            while (idx >= artifacts.size()) {
                artifacts.add(null);
            }
            artifacts.set(idx, a);
        }

        public Value[] getValues(Session session)
                throws RepositoryException, IOException {
            Value[] values = new Value[artifacts.size()];
            for (int i = 0; i < values.length; i++) {
                Artifact a = artifacts.get(i);
                try (InputStream input = a.getInputStream()) {
                    values[i] = session.getValueFactory().createValue(input);
                }
            }
            return values;
        }

        public Value getValue(Session session)
                throws RepositoryException, IOException {
            Artifact a = artifacts.get(0);
            try (InputStream input = a.getInputStream()) {
                return session.getValueFactory().createValue(input);
            }
        }

        public void detach() {
            for (Artifact a : artifacts) {
                if (a instanceof PropertyValueArtifact) {
                    try {
                        ((PropertyValueArtifact) a).detach();
                    } catch (IOException e) {
                        log.warn("error while detaching property artifact", e);
                    } catch (RepositoryException e) {
                        log.warn("error while detaching property artifact", e);
                    }
                }
            }
        }
    }

    private class VersioningState {

        private final StackElement stack;

        private final Node node;

        private boolean isCheckedOut;

        private boolean isParentCheckedOut;

        private VersioningState(StackElement stack, Node node) throws RepositoryException {
            this.stack = stack;
            this.node = node;
            isCheckedOut = node == null || !node.isNodeType(JcrConstants.MIX_VERSIONABLE) || node.isCheckedOut();
            isParentCheckedOut = stack.isCheckedOut();
        }

        public void ensureCheckedOut() throws RepositoryException {
            if (!isCheckedOut) {
                importInfo.registerToVersion(node.getPath());
                try {
                    node.checkout();
                } catch (RepositoryException e) {
                    log.warn("error while checkout node (ignored)", e);
                }
                isCheckedOut = true;
            }
            if (!isParentCheckedOut) {
                stack.ensureCheckedOut();
                isParentCheckedOut = true;
            }
        }
    }

    private class StackElement {

        private final Node node;

        private DocViewSAXImporter.StackElement parent;

        private final NodeNameList childNames = new NodeNameList();

        private boolean isCheckedOut;

        private boolean isNew;

        /**
         * adapter for special content
         */
        private DocViewAdapter adapter;

        public StackElement(Node node, boolean isNew) throws RepositoryException {
            this.node = node;
            this.isNew = isNew;
            isCheckedOut = node == null || !node.isNodeType(JcrConstants.MIX_VERSIONABLE) || node.isCheckedOut();
        }

        public Node getNode() {
            return node;
        }

        public boolean isCheckedOut() {
            return isCheckedOut && (parent == null || parent.isCheckedOut());
        }

        public void ensureCheckedOut() throws RepositoryException {
            if (!isCheckedOut) {
                importInfo.registerToVersion(node.getPath());
                try {
                    node.checkout();
                } catch (RepositoryException e) {
                    log.warn("error while checkout node (ignored)", e);
                }
                isCheckedOut = true;
            }
            if (parent != null) {
                parent.ensureCheckedOut();
            }
        }

        public boolean isRoot() {
            return parent == null;
        }

        public boolean checkForNode() {
            // we should check if child node exist if stack is not new or if it's a root node
            return !isNew || parent == null;
        }

        public void addName(String name) {
            childNames.addName(name);
        }

        public NodeNameList getChildNames() {
            return childNames;
        }

        public void restoreOrder() throws RepositoryException {
            if (checkForNode() && childNames.needsReorder(node)) {
                ensureCheckedOut();
                childNames.restoreOrder(node);
            }
        }

        public StackElement push() throws RepositoryException {
            return push(new StackElement(null, false));
        }

        public StackElement push(StackElement elem) throws RepositoryException {
            elem.parent = this;
            return elem;
        }


        public StackElement pop() {
            return parent;
        }

        public DocViewAdapter getAdapter() {
            if (adapter != null) {
                return adapter;
            }
            return parent == null ? null : parent.getAdapter();
        }

    }

}


/**
 * A representation of a namespace.  One of these will
 * be pushed on the namespace stack for each
 * element.
 */
class NameSpace {

    /**
     * Next NameSpace element on the stack.
     */
    public NameSpace next = null;

    /**
     * Prefix of this NameSpace element.
     */
    public String prefix;

    /**
     * Namespace URI of this NameSpace element.
     */
    public String uri;  // if null, then Element namespace is empty.

    /**
     * Construct a namespace for placement on the
     * result tree namespace stack.
     *
     * @param prefix Prefix of this element
     * @param uri    URI of  this element
     */
    public NameSpace(String prefix, String uri) {
        this.prefix = prefix;
        this.uri = uri;
    }
}

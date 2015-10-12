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
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.jcr.ImportUUIDBehavior;
import javax.jcr.Item;
import javax.jcr.ItemNotFoundException;
import javax.jcr.NamespaceException;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.PropertyType;
import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.jcr.ValueFactory;
import javax.jcr.nodetype.NodeType;

import org.apache.jackrabbit.spi.Name;
import org.apache.jackrabbit.spi.commons.conversion.DefaultNamePathResolver;
import org.apache.jackrabbit.spi.commons.name.NameConstants;
import org.apache.jackrabbit.spi.commons.namespace.NamespaceResolver;
import org.apache.jackrabbit.util.ISO9075;
import org.apache.jackrabbit.vault.fs.PropertyValueArtifact;
import org.apache.jackrabbit.vault.fs.api.Artifact;
import org.apache.jackrabbit.vault.fs.api.ArtifactType;
import org.apache.jackrabbit.vault.fs.api.ImportMode;
import org.apache.jackrabbit.vault.fs.api.ItemFilterSet;
import org.apache.jackrabbit.vault.fs.api.NodeNameList;
import org.apache.jackrabbit.vault.fs.api.SerializationType;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.fs.impl.ArtifactSetImpl;
import org.apache.jackrabbit.vault.fs.io.AccessControlHandling;
import org.apache.jackrabbit.vault.fs.spi.ACLManagement;
import org.apache.jackrabbit.vault.fs.spi.ServiceProviderFactory;
import org.apache.jackrabbit.vault.fs.spi.UserManagement;
import org.apache.jackrabbit.vault.util.DocViewNode;
import org.apache.jackrabbit.vault.util.DocViewProperty;
import org.apache.jackrabbit.vault.util.JcrConstants;
import org.apache.jackrabbit.vault.util.MimeTypes;
import org.apache.jackrabbit.vault.util.RejectingEntityDefaultHandler;
import org.apache.jackrabbit.vault.util.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Implements an importer that processes SAX events from a (modified) document
 * view. The behaviour for existing nodes works as follows:
 * <xmp>
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
 * </xmp>
 *
 */
public class DocViewSAXImporter extends RejectingEntityDefaultHandler implements NamespaceResolver {

    /**
     * the default logger
     */
    static final Logger log = LoggerFactory.getLogger(DocViewSAXImporter.class);

    /**
     * empty attributes
     */
    static final Attributes EMPTY_ATTRIBUTES = new AttributesImpl();

    static final Set<String> PROTECTED_PROPERTIES;
    static {
        Set<String> props = new HashSet<String>();
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
            = new HashMap<String, Map<String, DocViewSAXImporter.BlobInfo>>();

    /**
     * map of hint nodes in the same artifact set
     */
    private Set<String> hints = new HashSet<String>();

    /**
     * properties that should not be deleted
     */
    private Set<String> saveProperties = new HashSet<String>();

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
     * flag indicating if SNS are supported by the underlying repository
     */
    private final boolean snsSupported;

    /**
     * Creates a new importer that will receive SAX events and imports the
     * items below the given root.
     *
     * @param parentNode the (parent) node of the import
     * @param rootNodeName name of the root node
     * @param artifacts the artifact set that could contain attachments
     * @param wspFilter workspace filter
     * @throws RepositoryException if an error occurs.
     */
    public DocViewSAXImporter(Node parentNode, String rootNodeName,
                              ArtifactSetImpl artifacts, WorkspaceFilter wspFilter)
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

        String rootPath = parentNode.getPath();
        if (!rootPath.equals("/")) {
            rootPath += "/";
        }
        for (Artifact a: artifacts.values(ArtifactType.BINARY)) {
            registerBinary(a, rootPath);
        }
        for (Artifact a: artifacts.values(ArtifactType.FILE)) {
            if (a.getSerializationType() != SerializationType.XML_DOCVIEW) {
                registerBinary(a, rootPath);
            }
        }
        for (Artifact a: artifacts.values(ArtifactType.HINT)) {
            hints.add(rootPath + a.getRelativePath());
        }
    }

    public AccessControlHandling getAclHandling() {
        return aclHandling;
    }

    public void setAclHandling(AccessControlHandling aclHandling) {
        this.aclHandling = aclHandling;
    }

    private void registerBinary(Artifact a, String rootPath)
            throws RepositoryException {
        String path = rootPath + a.getRelativePath();
        int idx = -1;
        int pos = path.indexOf('[', path.lastIndexOf('/'));
        if (pos > 0) {
            idx = Integer.parseInt(path.substring(pos + 1, path.length() -1));
            path = path.substring(0, pos);
        }
        if (a.getType() == ArtifactType.FILE && a instanceof PropertyValueArtifact) {
            // hack, mark "file" properties just as present
            String parentPath = ((PropertyValueArtifact) a).getProperty().getParent().getPath();
            saveProperties.add(parentPath + "/" + JcrConstants.JCR_DATA);
            saveProperties.add(parentPath + "/" + JcrConstants.JCR_LASTMODIFIED);
        } else {
            saveProperties.add(path);
            // hack, mark "file" properties just as present
            saveProperties.add(path + "/jcr:content/jcr:data");
            saveProperties.add(path + "/jcr:content/jcr:lastModified");
            saveProperties.add(path + "/jcr:content/jcr:mimeType");
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
        log.debug("scheduling binary: {}{}", rootPath, a.getRelativePath() + a.getExtension());
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
        for (String parentPath: binaries.keySet()) {
            Map<String, DocViewSAXImporter.BlobInfo> blobs = binaries.get(parentPath);
            // check for node
            log.debug("processing binaries at {}", parentPath);
            try {
                if (session.nodeExists(parentPath)) {
                    Node node = session.getNode(parentPath);
                    for (String propName: blobs.keySet()) {
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
                    for (String propName: blobs.keySet()) {
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
        log.debug("handling binary file at {}", node.getPath());
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
        Value value = factory.createValue(a.getInputStream());
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
     *
     * Pushes the mapping to the stack and updates the namespace mapping in the
     * session.
     */
    @Override
    public void startPrefixMapping(String prefix, String uri) throws SAXException {
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
    }

    /**
     * {@inheritDoc}
     *
     * Pops the mapping from the stack and updates the namespace mapping in the
     * session if necessary.
     */
    @Override
    public void endPrefixMapping(String prefix) throws SAXException {
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
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        // special handling for root node
        if (stack.isRoot()) {
            if (localName.equals(NameConstants.JCR_ROOT.getLocalName())
                    && uri.equals(NameConstants.JCR_ROOT.getNamespaceURI())) {
                qName = rootNodeName;
            }
        }
        String label = ISO9075.decode(qName);
        String name = label;
        log.debug("-> element {}", label);
        boolean snsNode = false;
        int idx = name.lastIndexOf('[');
        if (idx > 0) {
            if (!snsSupported) {
                int idx2 = name.indexOf(']', idx);
                if (idx2 > 0) {
                    try {
                        if (Integer.valueOf(name.substring(idx+1, idx2)) > 1) {
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
                    log.debug("Skipping ignored element {}", name);
                }
            } else {
                if (attributes.getLength() == 0) {
                    // only ordering node. skip
                    log.debug("Skipping empty node {}", node.getPath() + "/" + name);
                    stack = stack.push();
                } else if (snsNode) {
                    // skip SNS nodes with index > 1
                    log.warn("Skipping unsupported SNS node with index > 1. Some content will be missing after import: {}", node.getPath() + "/" + label);
                    stack = stack.push();
                } else {
                    try {
                        DocViewNode ni = new DocViewNode(name, label, attributes, npResolver);
                        if (aclManagement.isACLNodeType(ni.primary)) {
                            if (aclHandling != AccessControlHandling.CLEAR && aclHandling != AccessControlHandling.IGNORE) {
                                log.debug("ACL element detected. starting special transformation {}/{}", node.getPath(), name);
                                if (aclManagement.ensureAccessControllable(node)) {
                                    log.info("Adding ACL element to non ACL parent - adding mixin: {}", node.getPath());
                                }
                                stack = stack.push();
                                if ("rep:repoPolicy".equals(name)) {
                                    if (node.getDepth() == 0) {
                                        stack.adapter = new JackrabbitACLImporter(session, aclHandling);
                                        stack.adapter.startNode(ni);
                                    } else {
                                        log.info("ignoring invalid location for repository level ACL: {}", node.getPath());
                                    }
                                } else {
                                    stack.adapter = new JackrabbitACLImporter(node, aclHandling);
                                    stack.adapter.startNode(ni);
                                }
                            } else {
                                stack = stack.push();
                            }
                        } else if (userManagement != null && userManagement.isAuthorizableNodeType(ni.primary)) {
                            handleAuthorizable(node, ni);
                        } else {
                            stack = stack.push(addNode(ni));
                        }
                    } catch (RepositoryException e) {
                        String errPath = node.getPath();
                        if (errPath.length() > 1) {
                            errPath += "/";
                        }
                        errPath += name;
                        log.error("Error during processing of {}: {}", errPath, e.toString());
                        importInfo.onError(errPath, e);
                        stack = stack.push();
                    }
                }
            }
        } catch (Exception e) {
            throw new SAXException(e);
        }
    }

    /**
     * Handle an authorizable node
     * @param node the parent node
     * @param ni doc view node of the authorizable
     * @throws RepositoryException
     * @throws SAXException
     */
    private void handleAuthorizable(Node node, DocViewNode ni) throws RepositoryException, SAXException {
        String id = userManagement.getAuthorizableId(ni);
        String newPath = node.getPath() + "/" + ni.name;
        boolean isIncluded = wspFilter.contains(newPath);
        String oldPath = userManagement.getAuthorizablePath(this.session, id);
        if (oldPath == null) {
            if (!isIncluded) {
                log.debug("auto-creating authorizable node not in filter {}", newPath);
            }

            // just import the authorizable node
            log.debug("Authorizable element detected. starting sysview transformation {}", newPath);
            stack = stack.push();
            stack.adapter = new JcrSysViewTransformer(node);
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
                // remember desired memberships.
                // todo: how to deal with multi-node memberships? see JCRVLT-69
                DocViewProperty prop = ni.props.get("rep:members");
                if (prop != null) {
                    importInfo.registerMemberships(id, prop.values);
                }

                log.info("Skipping import of existing authorizable '{}' due to MERGE import mode.", id);
                stack = stack.push(new StackElement(authNode, false));
                importInfo.onNop(newPath);
                break;

            case REPLACE:
                // just replace the entire subtree for now.
                log.debug("Authorizable element detected. starting sysview transformation {}", newPath);
                stack = stack.push();
                stack.adapter = new JcrSysViewTransformer(node);
                stack.adapter.startNode(ni);
                importInfo.onReplaced(newPath);
                break;

            case UPDATE:
                log.debug("Authorizable element detected. starting sysview transformation {}", newPath);
                stack = stack.push();
                stack.adapter = new JcrSysViewTransformer(node, oldPath);
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
                // but we need to be augment with a potential rep:authorizableId
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

        // find old node
        Node oldNode = null;
        Node node = null;
        if (ni.label.equals("")) {
            // special case for root node update
            node = currentNode;
        } else if (ni.uuid == null) {
            if (stack.checkForNode() && currentNode.hasNode(ni.label)) {
                node = currentNode.getNode(ni.label);
                if (ni.primary != null && !node.getPrimaryNodeType().getName().equals(ni.primary)) {
                    // if node type mismatches => replace
                    oldNode = node;
                    node = null;
                }
            }
        } else {
            try {
                node = session.getNodeByUUID(ni.uuid);
                if (!node.getParent().isSame(currentNode)) {
                    log.warn("Packaged node at {} is referenceable and collides with existing node at {}. Will create new UUID.",
                            currentNode.getPath() + "/" + ni.label,
                            node.getPath());
                    ni.uuid = null;
                    ni.props.remove(JcrConstants.JCR_UUID);
                    ni.props.remove(JcrConstants.JCR_BASEVERSION);
                    ni.props.remove(JcrConstants.JCR_PREDECESSORS);
                    ni.props.remove(JcrConstants.JCR_SUCCESSORS);
                    ni.props.remove(JcrConstants.JCR_VERSIONHISTORY);
                    node = null;
                }
            } catch (ItemNotFoundException e) {
                // ignore
            }
            if (node == null) {
                if (stack.checkForNode() && currentNode.hasNode(ni.label)) {
                    node = currentNode.getNode(ni.label);
                    if (ni.primary != null && !node.getPrimaryNodeType().getName().equals(ni.primary)) {
                        // if node type mismatches => replace
                        oldNode = node;
                        node = null;
                    }
                }
            } else {
                if (node.getName().equals(ni.name)) {
                    if (ni.primary != null && !node.getPrimaryNodeType().getName().equals(ni.primary)) {
                        // if node type mismatches => replace
                        oldNode = node;
                        node = null;
                    }
                } else {
                    // if names mismatches => replace
                    oldNode = node;
                    node = null;
                }

            }
        }
        // if old node is not included in the package, ignore rewrite
        if (oldNode != null && !isIncluded(oldNode, oldNode.getDepth() - rootDepth)) {
            node = oldNode;
            oldNode = null;
        }

        if (oldNode != null) {
            // check versionable
            new VersioningState(stack, oldNode).ensureCheckedOut();

            ChildNodeStash recovery = new ChildNodeStash(session);
            recovery.stashChildren(oldNode);

            // ensure that existing binaries are not sourced from a property
            // that is about to be removed
            Map<String, DocViewSAXImporter.BlobInfo> blobs = binaries.get(oldNode.getPath());
            if (blobs != null) {
                for (DocViewSAXImporter.BlobInfo info: blobs.values()) {
                    info.detach();
                }
            }

            oldNode.remove();
            // now create the new node
            node = createNode(currentNode, ni);

            // move the children back
            recovery.recoverChildren(node, importInfo);

            importInfo.onReplaced(node.getPath());
            return new StackElement(node, false);
        }

        // check if new node needs to be checked in
        DocViewProperty coProp = ni.props.remove(JcrConstants.JCR_ISCHECKEDOUT);
        boolean isCheckedIn = coProp != null && coProp.values[0].equals("false");

        // create or update node
        boolean isNew = false;
        if (node == null) {
            // workaround for bug in jcr2spi if mixins are empty
            if (!ni.props.containsKey(JcrConstants.JCR_MIXINTYPES)) {
                ni.props.put(JcrConstants.JCR_MIXINTYPES,
                        new DocViewProperty(JcrConstants.JCR_MIXINTYPES, new String[0], true, PropertyType.NAME));
            }

            stack.ensureCheckedOut();
            node = createNode(currentNode, ni);
            if (node.isNodeType(JcrConstants.NT_RESOURCE)) {
                if (!node.hasProperty(JcrConstants.JCR_DATA)) {
                    importInfo.onMissing(node.getPath() + "/" + JcrConstants.JCR_DATA);
                }
            } else if (isCheckedIn) {
                // don't rely on isVersionable here, since SPI might not have this info yet
                importInfo.registerToVersion(node.getPath());
            }
            importInfo.onCreated(node.getPath());
            isNew = true;

        } else if (isIncluded(node, node.getDepth() - rootDepth)){
            boolean modified = false;

            if (isCheckedIn) {
                // don't rely on isVersionable here, since SPI might not have this info yet
                importInfo.registerToVersion(node.getPath());
            }
            VersioningState vs = new VersioningState(stack, node);

            // remove the 'system' properties from the set
            ni.props.remove(JcrConstants.JCR_PRIMARYTYPE);
            ni.props.remove(JcrConstants.JCR_MIXINTYPES);
            ni.props.remove(JcrConstants.JCR_UUID);
            ni.props.remove(JcrConstants.JCR_BASEVERSION);
            ni.props.remove(JcrConstants.JCR_PREDECESSORS);
            ni.props.remove(JcrConstants.JCR_SUCCESSORS);
            ni.props.remove(JcrConstants.JCR_VERSIONHISTORY);

            // adjust mixins
            Set<String> newMixins = new HashSet<String>();
            if (ni.mixins != null) {
                for (String mixin: ni.mixins) {
                    // omit name if mix:AccessControllable and CLEAR
                    if (!aclManagement.isAccessControllableMixin(mixin)
                            || aclHandling != AccessControlHandling.CLEAR) {
                        newMixins.add(mixin);
                    }
                }
            }
            // remove mixin not in package
            for (NodeType mix: node.getMixinNodeTypes()) {
                String name = mix.getName();
                if (!newMixins.remove(name)) {
                    // special check for mix:AccessControllable
                    if (!aclManagement.isAccessControllableMixin(name)
                        || aclHandling == AccessControlHandling.CLEAR
                        || aclHandling == AccessControlHandling.OVERWRITE) {
                        vs.ensureCheckedOut();
                        node.removeMixin(name);
                        modified = true;
                    }
                }
            }

            // add remaining mixins
            for (String mixin: newMixins) {
                vs.ensureCheckedOut();
                node.addMixin(mixin);
                modified = true;
            }

            // remove properties not in the set
            PropertyIterator pIter = node.getProperties();
            while (pIter.hasNext()) {
                Property p = pIter.nextProperty();
                String propName = p.getName();
                if (!PROTECTED_PROPERTIES.contains(propName)
                        && !ni.props.containsKey(propName)
                        && !saveProperties.contains(p.getPath())) {
                    try {
                        vs.ensureCheckedOut();
                        p.remove();
                        modified = true;
                    } catch (RepositoryException e) {
                        // ignore
                    }
                }
            }
            // add properties
            for (DocViewProperty prop : ni.props.values()) {
                if (prop != null && !PROTECTED_PROPERTIES.contains(prop.name)) {
                    try {
                        modified |= prop.apply(node);
                    } catch (RepositoryException e) {
                        try {
                            // try again with checked out node
                            vs.ensureCheckedOut();
                            modified |= prop.apply(node);
                        } catch (RepositoryException e1) {
                            log.warn("Error while setting property (ignore): " + e1);
                        }
                    }
                }
            }
            if (modified) {
                if (node.isNodeType(JcrConstants.NT_RESOURCE)) {
                    if (!node.hasProperty(JcrConstants.JCR_DATA)) {
                        importInfo.onMissing(node.getPath() + "/" + JcrConstants.JCR_DATA);
                    }
                }
                importInfo.onModified(node.getPath());
            } else {
                importInfo.onNop(node.getPath());
            }
        }
        return new StackElement(node, isNew);
    }

    private Node createNode(Node currentNode, DocViewNode ni)
            throws RepositoryException {
        try {
            String parentPath = currentNode.getPath();
            final ContentHandler handler = session.getImportContentHandler(
                    parentPath,
                    ImportUUIDBehavior.IMPORT_UUID_COLLISION_REMOVE_EXISTING);
            // first define the current namespaces
            String[] prefixes = session.getNamespacePrefixes();
            handler.startDocument();
            for (String prefix: prefixes) {
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
                    for (String v: mix.values) {
                        if (v.equals(JcrConstants.MIX_REFERENCEABLE)) {
                            addMixRef = false;
                            break;
                        }
                    }
                    if (addMixRef) {
                        String[] vs = new String[mix.values.length+1];
                        System.arraycopy(mix.values, 0, vs, 0, mix.values.length);
                        vs[mix.values.length] = JcrConstants.MIX_REFERENCEABLE;
                        mix = new DocViewProperty(JcrConstants.JCR_MIXINTYPES, vs, true, PropertyType.NAME);
                        ni.props.put(mix.name, mix);
                    }
                }
            }
            // add the properties
            for (DocViewProperty p: ni.props.values()) {
                if (p != null && p.values != null) {
                    // only pass 'protected' properties to the import
                    if (PROTECTED_PROPERTIES.contains(p.name)) {
                        attrs = new AttributesImpl();
                        attrs.addAttribute(Name.NS_SV_URI, "name", "sv:name", "CDATA", p.name);
                        attrs.addAttribute(Name.NS_SV_URI, "type", "sv:type", "CDATA", PropertyType.nameFromValue(p.type));
                        handler.startElement(Name.NS_SV_URI, "property", "sv:property", attrs);
                        for (String v: p.values) {
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
            Node node = null;
            if (ni.uuid != null) {
                try {
                    node = currentNode.getSession().getNodeByUUID(ni.uuid);
                } catch (RepositoryException e) {
                    log.warn("Newly created node not found by uuid {}: {}", parentPath + "/" + ni.name, e.toString());
                }
            }
            if (node == null) {
                try {
                    node = currentNode.getNode(ni.label);
                } catch (RepositoryException e) {
                    log.warn("Newly created node not found by label {}: {}", parentPath + "/" + ni.name, e.toString());
                }
            }
            if (node == null) {
                try {
                    node = currentNode.getNode(ni.name);
                } catch (RepositoryException e) {
                    log.warn("Newly created node not found by name {}: {}", parentPath + "/" + ni.name, e.toString());
                    throw e;
                }
            }
            // handle non protected properties
            for (DocViewProperty p: ni.props.values()) {
                if (p != null && p.values != null) {
                    if (!PROTECTED_PROPERTIES.contains(p.name)) {
                        try {
                            p.apply(node);
                        } catch (RepositoryException e) {
                            log.warn("Error while setting property (ignore): " + e);
                        }
                    }
                }
            }
            // remove mix referenceable if it was temporarily added
            if (addMixRef) {
                node.removeMixin(JcrConstants.MIX_REFERENCEABLE);
            }
            return node;

        } catch (SAXException e) {
            Exception root = e.getException();
            if (root instanceof RepositoryException) {
                throw (RepositoryException) root;
            } else if (root instanceof RuntimeException) {
                throw (RuntimeException) root;
            } else {
                throw new RepositoryException("Error while creating node", root);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        log.debug("<- element {}", qName);
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
                    for (String createdPath: createdPaths) {
                        importInfo.onCreated(createdPath);
                    }
                    stack.adapter = null;
                    log.debug("Sysview transformation complete.");
                }
            } else {
                NodeIterator iter = node.getNodes();
                while (iter.hasNext()) {
                    numChildren++;
                    Node child = iter.nextNode();
                    String path = child.getPath();
                    String label = Text.getName(path);
                    if (!childNames.contains(label)
                            && !hints.contains(path)
                            && isIncluded(child, child.getDepth() - rootDepth)) {
                        // if the child is in the filter, it belongs to
                        // this aggregate and needs to be removed
                        if (aclManagement.isACLNode(child)) {
                            if (aclHandling == AccessControlHandling.OVERWRITE
                                    || aclHandling == AccessControlHandling.CLEAR) {
                                importInfo.onDeleted(path);
                                aclManagement.clearACL(node);
                            }
                        } else {
                            if (wspFilter.getImportMode(path) == ImportMode.REPLACE) {
                                importInfo.onDeleted(path);
                                child.remove();
                            }
                        }
                    } else if (aclHandling == AccessControlHandling.CLEAR
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
            for (int i=0; i<values.length; i++) {
                Artifact a = artifacts.get(i);
                values[i] = session.getValueFactory().createValue(a.getInputStream());
            }
            return values;
        }

        public Value getValue(Session session)
                throws RepositoryException, IOException {
            Artifact a = artifacts.get(0);
            return session.getValueFactory().createValue(a.getInputStream());
        }

        public void detach() {
            for (Artifact a: artifacts) {
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

    private class StackElement  {

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
   * @param uri URI of  this element
   */
  public NameSpace(String prefix, String uri) {
    this.prefix = prefix;
    this.uri = uri;
  }
}
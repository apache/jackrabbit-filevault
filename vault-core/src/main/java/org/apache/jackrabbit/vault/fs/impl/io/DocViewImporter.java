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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.jcr.ImportUUIDBehavior;
import javax.jcr.Item;
import javax.jcr.ItemExistsException;
import javax.jcr.ItemNotFoundException;
import javax.jcr.NamespaceException;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.PathNotFoundException;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.PropertyType;
import javax.jcr.ReferentialIntegrityException;
import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.jcr.ValueFactory;
import javax.jcr.lock.LockException;
import javax.jcr.nodetype.ConstraintViolationException;
import javax.jcr.nodetype.NoSuchNodeTypeException;
import javax.jcr.nodetype.NodeDefinition;
import javax.jcr.nodetype.NodeType;
import javax.jcr.nodetype.PropertyDefinition;
import javax.jcr.version.VersionException;

import org.apache.jackrabbit.spi.Name;
import org.apache.jackrabbit.spi.commons.conversion.DefaultNamePathResolver;
import org.apache.jackrabbit.spi.commons.conversion.NamePathResolver;
import org.apache.jackrabbit.spi.commons.name.NameConstants;
import org.apache.jackrabbit.spi.commons.name.NameFactoryImpl;
import org.apache.jackrabbit.util.Text;
import org.apache.jackrabbit.vault.fs.api.Artifact;
import org.apache.jackrabbit.vault.fs.api.ArtifactType;
import org.apache.jackrabbit.vault.fs.api.IdConflictPolicy;
import org.apache.jackrabbit.vault.fs.api.ImportInfo.Info;
import org.apache.jackrabbit.vault.fs.api.ImportInfo.Type;
import org.apache.jackrabbit.vault.fs.api.ImportMode;
import org.apache.jackrabbit.vault.fs.api.ItemFilterSet;
import org.apache.jackrabbit.vault.fs.api.NodeNameList;
import org.apache.jackrabbit.vault.fs.api.SerializationType;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.fs.impl.ArtifactSetImpl;
import org.apache.jackrabbit.vault.fs.impl.PropertyValueArtifact;
import org.apache.jackrabbit.vault.fs.io.AccessControlHandling;
import org.apache.jackrabbit.vault.fs.io.DocViewParserHandler;
import org.apache.jackrabbit.vault.fs.spi.ACLManagement;
import org.apache.jackrabbit.vault.fs.spi.ServiceProviderFactory;
import org.apache.jackrabbit.vault.fs.spi.UserManagement;
import org.apache.jackrabbit.vault.fs.spi.impl.jcr20.JackrabbitUserManagement;
import org.apache.jackrabbit.vault.fs.spi.impl.jcr20.JcrNamespaceHelper;
import org.apache.jackrabbit.vault.util.DocViewNode2;
import org.apache.jackrabbit.vault.util.DocViewProperty2;
import org.apache.jackrabbit.vault.util.EffectiveNodeType;
import org.apache.jackrabbit.vault.util.JcrConstants;
import org.apache.jackrabbit.vault.util.MimeTypes;
import org.apache.jackrabbit.vault.util.PathUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Imports nodes represented by {@link DocViewNode2} into the repository.
 */
public class DocViewImporter implements DocViewParserHandler {

    public static final String ATTRIBUTE_TYPE_CDATA = "CDATA";
    private static final Name NAME_REP_CUG_POLICY = NameFactoryImpl.getInstance().create(Name.NS_REP_URI, "cugPolicy");
    private static final Name NAME_REP_MEMBERS = NameFactoryImpl.getInstance().create(Name.NS_REP_URI, "members");

    private static final String NAMESPACE_OAK = "http://jackrabbit.apache.org/oak/ns/1.0";
    private static final Name NAME_OAK_COUNTER = NameFactoryImpl.getInstance().create(NAMESPACE_OAK, "counter");

    /**
     * the default logger
     */
    static final Logger log = LoggerFactory.getLogger(DocViewImporter.class);

    /**
     * these properties are protected but are set for new nodes nevertheless via system view xml import
     */
    static final Set<Name> PROTECTED_PROPERTIES_CONSIDERED_FOR_NEW_NODES;

    /**
     * these properties are protected but are set for updated nodes via special JCR methods
     */
    static final Set<Name> PROTECTED_PROPERTIES_CONSIDERED_FOR_UPDATED_NODES;

    static {
        Set<Name> props = new HashSet<>();
        props.add(NameConstants.JCR_PRIMARYTYPE);
        props.add(NameConstants.JCR_MIXINTYPES);
        props.add(NameConstants.JCR_UUID);
        PROTECTED_PROPERTIES_CONSIDERED_FOR_UPDATED_NODES = Collections.unmodifiableSet(props);
        props.add(NameConstants.JCR_ISCHECKEDOUT);
        props.add(NameConstants.JCR_BASEVERSION);
        props.add(NameConstants.JCR_PREDECESSORS);
        props.add(NameConstants.JCR_SUCCESSORS);
        props.add(NameConstants.JCR_VERSIONHISTORY);
        PROTECTED_PROPERTIES_CONSIDERED_FOR_NEW_NODES = Collections.unmodifiableSet(props);
    }


    /**
     * the importing session
     */
    private final Session session;

    /**
     * import information for the nodes touched through this DocView (initially empty)
     */
    private ImportInfoImpl importInfo = new ImportInfoImpl();

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
    private Map<String, Map<String, BlobInfo>> binaries
            = new HashMap<>();

    /**
     * map of hint nodes in the same artifact set
     */
    private Set<String> hints = new HashSet<>();

    /**
     * properties that should not be deleted on existing nodes in the repository
     */
    private Set<String> preserveProperties = new HashSet<>();

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
    private final AccessControlHandling aclHandling;

    /**
     * Closed user group handling to apply by default (when set to <code>null</code>)
     * falls back to using aclHandling
     */
    private final @Nullable AccessControlHandling cugHandling;

    /**
     * helper for namespace registration
     */
    private final JcrNamespaceHelper nsHelper;

    private final IdConflictPolicy idConflictPolicy;

    /**
     * current stack
     */
    private StackElement stack;

    /**
     * the current namespace state
     */
    private DocViewSAXHandler.Namespace nsStack = null;

    private int rootDepth;

    private final NamePathResolver npResolver;
    /**
     * {@code true} in case the repository supports same-name siblings
     */
    private final boolean isSnsSupported;

    /**
     * Creates a new importer that will imports the
     * items below the given root.
     *
     * @param parentNode   the (parent) node of the import
     * @param rootNodeName name of the root node
     * @param artifacts    the artifact set that could contain attachments
     * @param wspFilter    workspace filter
     * @throws RepositoryException if an error occurs.
     */
    public DocViewImporter(Node parentNode, String rootNodeName,
                              ArtifactSetImpl artifacts, WorkspaceFilter wspFilter, IdConflictPolicy idConflictPolicy) throws RepositoryException {
        this(parentNode, rootNodeName, artifacts, wspFilter, idConflictPolicy, AccessControlHandling.IGNORE, null);
    }

    public DocViewImporter(Node parentNode, String rootNodeName,
            ArtifactSetImpl artifacts, WorkspaceFilter wspFilter, IdConflictPolicy idConflictPolicy, AccessControlHandling aclHandling, AccessControlHandling cugHandling) throws RepositoryException {
        this.filter = artifacts.getCoverage();
        this.wspFilter = wspFilter;
        this.rootDepth = parentNode.getDepth() + 1;
        this.session = parentNode.getSession();
        this.aclManagement = ServiceProviderFactory.getProvider().getACLManagement();
        this.userManagement = ServiceProviderFactory.getProvider().getUserManagement();
        this.nsHelper = new JcrNamespaceHelper(session, null);
        this.idConflictPolicy = idConflictPolicy;
        this.aclHandling = aclHandling;
        this.cugHandling = cugHandling;
        this.isSnsSupported = session.getRepository().
                getDescriptorValue(Repository.NODE_TYPE_MANAGEMENT_SAME_NAME_SIBLINGS_SUPPORTED).getBoolean();
    
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
        
        stack = new StackElement(parentNode, parentNode.isNew());
        npResolver = new DefaultNamePathResolver(parentNode.getSession());
    }

    /**
     * {@inheritDoc}
     * <p>
     * Pushes the mapping to the stack and updates the namespace mapping in the
     * session.
     */
    @Override
    public void startPrefixMapping(String prefix, String uri) {
        // for backwards compatibility unknown namespaces in the repository need to be registered because some API can only deal with qualified/prefixed names
        log.trace("-> prefixMapping for {}:{}", prefix, uri);
        DocViewSAXHandler.Namespace ns = new DocViewSAXHandler.Namespace(prefix, uri);
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
                throw new IllegalStateException(e1);
            }
        } catch (RepositoryException e) {
            throw new IllegalStateException(e);
        }
        // update mapping
        if (!oldPrefix.equals(prefix)) {
            try {
                session.setNamespacePrefix(prefix, uri);
            } catch (RepositoryException e) {
                throw new IllegalStateException(e);
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
    public void endPrefixMapping(String prefix) {
        log.trace("<- prefixMapping for {}", prefix);
        DocViewSAXHandler.Namespace ns = nsStack;
        DocViewSAXHandler.Namespace prev = null;
        while (ns != null && !ns.prefix.equals(prefix)) {
            prev = ns;
            ns = ns.next;
        }
        if (ns == null) {
            throw new IllegalStateException("Illegal state: prefix " + prefix + " never mapped.");
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
                throw new IllegalStateException(e);
            }
            log.trace("   remapped: {}:{}", prefix, ns.uri);
        }
    }

    @Override
    public void startDocViewNode(@NotNull String nodePath, @NotNull DocViewNode2 docViewNode, @NotNull Optional<DocViewNode2> parentDocViewNode, int line, int column) throws IOException, RepositoryException {
        stack.addName(docViewNode.getSnsAwareName());
        Node node = stack.getNode();
        if (node == null) {
            stack = stack.push();
            DocViewAdapter xform = stack.getAdapter();
            if (xform != null) {
                xform.startNode(docViewNode);
            } else {
                log.trace("Skipping ignored node {}", docViewNode); // TODO: clarify what this means
            }
        } else {
            if (docViewNode.getProperties().isEmpty()) {
                // only ordering node. skip
                log.trace("Skipping empty node {}", nodePath);
                stack = stack.push();
                return;
            } else if (docViewNode.getIndex() > 1 && !isSnsSupported) {
                //skip SNS nodes with index > 1
                log.warn("Skipping unsupported SNS node with index > 1. Some content will be missing after import: {}", nodePath);
                stack = stack.push();
                return;
            }
            try {
                // is policy node?
                if (docViewNode.getPrimaryType().filter(aclManagement::isACLNodeType).isPresent()) {
                    AccessControlHandling acHandling = getAcHandling(docViewNode.getName());
                    if (acHandling != AccessControlHandling.CLEAR && acHandling != AccessControlHandling.IGNORE) {
                        log.trace("Access control policy element detected. starting special transformation {}/{}", node.getPath(), docViewNode.getName());
                        if (aclManagement.ensureAccessControllable(node, npResolver.getJCRName(docViewNode.getName()))) {
                            log.debug("Adding access control policy element to non access-controllable parent - adding mixin: {}", node.getPath());
                        }
                        stack = stack.push();
                        if (NameConstants.REP_REPO_POLICY.equals(docViewNode.getName())) {
                            if (node.getDepth() == 0) {
                                stack.adapter = new JackrabbitACLImporter(session, acHandling);
                                stack.adapter.startNode(docViewNode);
                            } else {
                                log.debug("ignoring invalid location for repository level ACL: {}", node.getPath());
                            }
                        } else {
                            
                            stack.adapter = new JackrabbitACLImporter(node, acHandling);
                            stack.adapter.startNode(docViewNode);
                        }
                    } else {
                        stack = stack.push();
                    }
                } else if (userManagement != null && docViewNode.getPrimaryType().filter(userManagement::isAuthorizableNodeType).isPresent()) {
                    // is authorizable node?
                    handleAuthorizable(node, docViewNode);
                } else {
                    // regular node
                    stack = stack.push(addNode(docViewNode));
                }
            } catch (RepositoryException | IOException e) {
                if (e instanceof ConstraintViolationException && wspFilter.getImportMode(nodePath) != ImportMode.REPLACE) {
                    // only warn in case of constraint violations for mode != replace (as best effort is used in that case)
                    log.warn("Error during processing of {}: {}, skip node due to import mode {}", nodePath, e.toString(), wspFilter.getImportMode(nodePath));
                    importInfo.onNop(nodePath);
                } else {
                    log.error("Error during processing of {}: {}", nodePath, e.toString());
                    importInfo.onError(nodePath, e);
                }
                stack = stack.push();
            }
        }
    }


    @Override
    public void endDocViewNode(@NotNull String nodePath, @NotNull DocViewNode2 docViewNode, @NotNull Optional<DocViewNode2> parentDocViewNode, int line, int column) throws IOException, RepositoryException {
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
                AccessControlHandling acHandling = getAcHandling(npResolver.getQName(child.getName()));
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
                            boolean shouldRemoveChild = true;
                            // check if child is not protected
                            if (child.getDefinition().isProtected()) {
                                log.warn("Refuse to delete protected child node: {}", path);
                                shouldRemoveChild = false;
                                // check if child is mandatory (and not residual, https://s.apache.org/jcr-2.0-spec/2.0/3_Repository_Model.html#3.7.2.4%20Mandatory)
                            } else if (child.getDefinition().isMandatory() && !child.getDefinition().getName().equals("*")) {
                                // get relevant child node definition from parent's effective node type
                                EffectiveNodeType ent = EffectiveNodeType.ofNode(child.getParent());
                                Optional<NodeDefinition> childNodeDefinition = ent.getApplicableChildNodeDefinition(child.getName(), child.getPrimaryNodeType());
                                if (!childNodeDefinition.isPresent()) {
                                    // this should never happen as then child.getDefinition().isMandatory() would have returned false in the first place...
                                    throw new IllegalStateException("Could not find applicable child node definition for mandatory child node " + child.getPath());
                                } else {
                                    if (!hasSiblingWithPrimaryTypesAndName(child, childNodeDefinition.get().getRequiredPrimaryTypes(), childNodeDefinition.get().getName())) {
                                        log.warn("Refuse to delete mandatory non-residual child node: {} with no other matching siblings", path);
                                        shouldRemoveChild = false;
                                    }
                                }
                            } 
                            if (shouldRemoveChild) {
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
    }

    private boolean hasSiblingWithPrimaryTypesAndName(Node node, NodeType[] requiredPrimaryNodeTypes, String requiredName) throws RepositoryException {
        NodeIterator iter = node.getParent().getNodes();
        while (iter.hasNext()) {
            Node sibling = iter.nextNode();
            if (!sibling.isSame(node)) {
                boolean allTypesMatch = true;
                // check type: due to inheritance multiple primary node types need to be checked
                for (NodeType requiredPrimaryNodeType : requiredPrimaryNodeTypes) {
                    allTypesMatch &= sibling.isNodeType(requiredPrimaryNodeType.getName());
                }
                // check name
                if (allTypesMatch && (requiredName.equals("*") || requiredName.equals(node.getName()))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * {@inheritDoc}
     * @throws RepositoryException 
     * @throws IOException 
     * @throws ConstraintViolationException 
     * @throws VersionException 
     * @throws LockException 
     * @throws NoSuchNodeTypeException 
     * @throws PathNotFoundException 
     * @throws ItemExistsException 
     */
    @Override
    public void endDocument() throws RepositoryException, IOException {
        if (!stack.isRoot()) {
            throw new IllegalStateException("stack mismatch");
        }

        // process binaries
        for (String parentPath : binaries.keySet()) {
            Map<String, BlobInfo> blobs = binaries.get(parentPath);
            // check for node
            log.trace("processing binaries at {}", parentPath);
            if (session.nodeExists(parentPath)) {
                Node node = session.getNode(parentPath);
                for (String propName : blobs.keySet()) {
                    BlobInfo info = blobs.get(propName);
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
                    BlobInfo info = blobs.get(propName);
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
        }
    }

    private void registerBinary(Artifact a, String rootPath)
            throws RepositoryException {
        String path = rootPath + a.getRelativePath();
        final int idx;
        int pos = path.indexOf('[', path.lastIndexOf('/'));
        if (pos > 0) {
            idx = Integer.parseInt(path.substring(pos + 1, path.length() - 1));
            path = path.substring(0, pos);
        } else {
            idx = -1;
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
            Map<String, BlobInfo> infoSet = binaries.computeIfAbsent(parentPath, (p) ->  new HashMap<>());
            BlobInfo info = infoSet.computeIfAbsent(name, (n) -> new BlobInfo(idx >= 0));
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

    private void handleBinNode(Node node, BlobInfo info, boolean checkIfNtFileOk)
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
     * Handle an authorizable node
     *
     * @param node the parent node
     * @param docViewNode   doc view node of the authorizable
     * @throws RepositoryException if an error accessing the repository occurrs.
     * @throws SAXException        if an XML parsing error occurrs.
     */
    private void handleAuthorizable(Node node, DocViewNode2 docViewNode) throws RepositoryException {
        String id = userManagement.getAuthorizableId(docViewNode);
        String newPath = node.getPath() + "/" + npResolver.getJCRName(docViewNode.getName());
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
            stack.adapter.startNode(docViewNode);
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
                Optional<DocViewProperty2> prop = docViewNode.getProperty(NAME_REP_MEMBERS);
                if (prop.isPresent()) {
                    importInfo.registerMemberships(id, prop.get().getStringValues().toArray(new String[0]));
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
                stack.adapter.startNode(docViewNode);
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
                Collection<DocViewProperty2> properties = new LinkedList<>(docViewNode.getProperties());
                // but we need to augment with a potential rep:authorizableId
                if (authNode.hasProperty("rep:authorizableId")) {
                    DocViewProperty2 authId = new DocViewProperty2(
                            JackrabbitUserManagement.NAME_REP_AUTHORIZABLE_ID,
                            authNode.getProperty("rep:authorizableId").getString(),
                            PropertyType.STRING
                    );
                    properties.removeIf((p) -> p.getName().equals(JackrabbitUserManagement.NAME_REP_AUTHORIZABLE_ID));
                    properties.add(authId);
                }
                
                DocViewNode2 mapped = new DocViewNode2(
                        npResolver.getQName(newName),
                        properties
                );
                
                stack.adapter.startNode(mapped);
                importInfo.onReplaced(newPath);
                break;
        }
    }

    private StackElement addNode(DocViewNode2 docViewNode) throws RepositoryException, IOException {
        final Node currentNode = stack.getNode();

        Collection<DocViewProperty2> preprocessedProperties = new LinkedList<>(docViewNode.getProperties());
        Node existingNode = null;
        if (NameConstants.ROOT.equals(docViewNode.getName())) {
            // special case for root node update
            existingNode = currentNode;
        } else {
            if (stack.checkForNode() && currentNode.hasNode(docViewNode.getName().toString())) {
                existingNode = currentNode.getNode(docViewNode.getName().toString());
            }
            Optional<String> identifier = docViewNode.getIdentifier();
            if (identifier.isPresent()) {
                try {
                    // does uuid already exist in the repo?
                    Node sameIdNode = session.getNodeByIdentifier(identifier.get());
                    String newNodePath = currentNode.getPath() + "/" + npResolver.getJCRName(docViewNode.getName());
                    // edge-case: same node path -> uuid is kept
                    if (existingNode != null && existingNode.getPath().equals(sameIdNode.getPath())) {
                        log.debug("Node at {} with existing identifier {} is being updated without modifying its identifier", existingNode.getPath(), docViewNode.getIdentifier());
                    } else {
                        log.warn("Node Collision: To-be imported node {} uses a node identifier {} which is already taken by {}, trying to resolve conflict according to policy {}", 
                                newNodePath, docViewNode.getIdentifier(), sameIdNode.getPath(), idConflictPolicy.name());
                        if (idConflictPolicy == IdConflictPolicy.FAIL) {
                            // uuid found in path covered by filter
                            if (isIncluded(sameIdNode, 0)) {
                                Info sameIdNodeInfo = importInfo.getInfo(sameIdNode.getPath());
                                // is the conflicting node part of the package (i.e. the package contained duplicate uuids)
                                if (sameIdNodeInfo != null && sameIdNodeInfo.getType() != Type.DEL) {
                                    throw new ReferentialIntegrityException("Node identifier " + docViewNode.getIdentifier() + " already taken by node " + sameIdNode.getPath() + " from the same package");
                                } else {
                                    log.warn("Trying to remove existing conflicting node {} (and all its references)", sameIdNode.getPath());
                                    removeReferences(sameIdNode);
                                    String sameIdNodePath = sameIdNode.getPath();
                                    session.removeItem(sameIdNodePath);
                                    log.warn("Node {} and its references removed", sameIdNodePath);
                                }
                                existingNode = null;
                            } else {
                                // uuid found in path not-covered by filter
                                throw new ReferentialIntegrityException("Node identifier " + docViewNode.getIdentifier() + " already taken by node " + sameIdNode.getPath());
                            }
                        } else if (idConflictPolicy == IdConflictPolicy.LEGACY) {
                            // is the conflicting node a sibling
                            if (sameIdNode.getParent().isSame(currentNode)) {
                                String sameIdNodePath = sameIdNode.getPath();
                                if (isIncluded(sameIdNode, 0)) {
                                    log.warn("Existing conflicting node {} has same parent as to-be imported one and is contained in the filter, trying to remove it.", sameIdNodePath);
                                    session.removeItem(sameIdNodePath); // references point to new node afterwards
                                    importInfo.onDeleted(sameIdNodePath);
                                } else {
                                    log.warn("Existing conflicting node {} has same parent as to-be imported one and is not contained in the filter, ignoring new node but continue with children below existing conflicting node", sameIdNodePath);
                                    importInfo.onRemapped(newNodePath, sameIdNodePath);
                                    existingNode = sameIdNode;
                                }
                            } else {
                                log.warn("To-be imported node and existing conflicting node have different parents. Will create new identifier for the former. ({})",
                                        newNodePath);
                                preprocessedProperties.removeIf(p -> p.getName().equals(NameConstants.JCR_UUID) 
                                        || p.getName().equals(NameConstants.JCR_BASEVERSION) 
                                        || p.getName().equals(NameConstants.JCR_PREDECESSORS)
                                        || p.getName().equals(NameConstants.JCR_SUCCESSORS)
                                        || p.getName().equals(NameConstants.JCR_VERSIONHISTORY));
                            }
                        }
                    }
                } catch (ItemNotFoundException e) {
                    // ignore
                }
            }
        }

        // check if new node needs to be checked in
        preprocessedProperties.removeIf(p -> p.getName().equals(NameConstants.JCR_ISCHECKEDOUT));
        boolean isCheckedIn = "false".equals(docViewNode.getPropertyValue(NameConstants.JCR_ISCHECKEDOUT).orElse("true"));

        // create or update node
        boolean isNew = existingNode == null;
        if (isNew) {
            // workaround for bug in jcr2spi if mixins are empty
            if (!docViewNode.hasProperty(NameConstants.JCR_MIXINTYPES)) {
                preprocessedProperties.add(new DocViewProperty2(NameConstants.JCR_MIXINTYPES, Collections.emptyList(), PropertyType.NAME));
            }

            stack.ensureCheckedOut();
            existingNode = createNewNode(currentNode, docViewNode.cloneWithDifferentProperties(preprocessedProperties));
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
            Node updatedNode = updateExistingNode(existingNode, docViewNode.cloneWithDifferentProperties(preprocessedProperties), importMode);
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
    private void removeReferences(@NotNull Node node) throws ReferentialIntegrityException, RepositoryException {
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

    private @Nullable Node updateExistingNode(@NotNull Node node, @NotNull DocViewNode2 ni, @NotNull ImportMode importMode) throws RepositoryException {
        VersioningState vs = new VersioningState(stack, node);
        Node updatedNode = null;
        Optional<String> identifier = ni.getIdentifier();
        // try to set uuid via sysview import if it differs from existing one
        if (identifier.isPresent() && !node.getIdentifier().equals(identifier.get()) && !"rep:root".equals(ni.getPrimaryType().orElse(""))) {
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
            String primaryType = ni.getPrimaryType().orElseThrow(() -> new IllegalStateException("Mandatory property 'jcr:primaryType' missing from " + ni));
            if (importMode == ImportMode.REPLACE && !"rep:root".equals(primaryType) && wspFilter.includesProperty(PathUtil.append(node.getPath(), JcrConstants.JCR_PRIMARYTYPE))) {
                if (!node.getPrimaryNodeType().getName().equals(primaryType)) {
                    vs.ensureCheckedOut();
                    log.trace("Setting primary node type {} for {}", primaryType, node.getPath());
                    node.setPrimaryType(primaryType);
                    updatedNode = node;
                }
            }
            // calculate mixins to be added
            Set<String> newMixins = new HashSet<>();
            if (wspFilter.includesProperty(PathUtil.append(node.getPath(), JcrConstants.JCR_MIXINTYPES))) {
                AccessControlHandling acHandling = getAcHandling(ni.getName());
                for (String mixin : ni.getMixinTypes()) {
                    // omit if mix:AccessControllable and CLEAR
                    if (!aclManagement.isAccessControllableMixin(mixin)
                            || acHandling != AccessControlHandling.CLEAR) {
                        newMixins.add(mixin);
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
            }
            // remove unprotected properties not in package (only for mode = replace)
            if (importMode == ImportMode.REPLACE) {
                PropertyIterator pIter = node.getProperties();
                while (pIter.hasNext()) {
                    Property p = pIter.nextProperty();
                    String propName = p.getName();
                    if (!p.getDefinition().isProtected()
                            && !ni.hasProperty(npResolver.getQName(propName))
                            && !preserveProperties.contains(p.getPath())
                            && wspFilter.includesProperty(p.getPath())) {
                        vs.ensureCheckedOut();
                        p.remove();
                        updatedNode = node;
                    }
                }
            }
            EffectiveNodeType effectiveNodeType = EffectiveNodeType.ofNode(node);
            // logging for uncovered protected properties
            logIgnoredProtectedProperties(effectiveNodeType, node.getPath(), ni.getProperties(), PROTECTED_PROPERTIES_CONSIDERED_FOR_UPDATED_NODES);
            
            // add/modify properties contained in package
            if (setUnprotectedProperties(effectiveNodeType, node, ni, importMode == ImportMode.REPLACE|| importMode == ImportMode.UPDATE || importMode == ImportMode.UPDATE_PROPERTIES, vs)) {
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
    private @NotNull Node createNewNode(Node parentNode, DocViewNode2 ni)
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
            attrs.addAttribute(Name.NS_SV_URI, "name", "sv:name", ATTRIBUTE_TYPE_CDATA, npResolver.getJCRName(ni.getName()));
            handler.startElement(Name.NS_SV_URI, "node", "sv:node", attrs);
    
            // check if SNS and a helper uuid if needed
            boolean addMixRef = false;
            
            if (ni.getIndex() > 0 && !ni.getIdentifier().isPresent()) {
                Collection<DocViewProperty2> preprocessedProperties = new LinkedList<>(ni.getProperties());
                preprocessedProperties.add(new DocViewProperty2( NameConstants.JCR_UUID, UUID.randomUUID().toString(), PropertyType.STRING));
                // check mixins
                DocViewProperty2 mix = ni.getProperty(NameConstants.JCR_MIXINTYPES).orElse(null);
                addMixRef = true;
                if (mix == null) {
                    mix = new DocViewProperty2(NameConstants.JCR_MIXINTYPES, Collections.singletonList(JcrConstants.MIX_REFERENCEABLE), PropertyType.NAME);
                    preprocessedProperties.add(mix);
                } else {
                    for (String v : mix.getStringValues()) {
                        if (v.equals(JcrConstants.MIX_REFERENCEABLE)) {
                            addMixRef = false;
                            break;
                        }
                    }
                    if (addMixRef) {
                        List<String> mixinValues = new LinkedList<>(mix.getStringValues());
                        mixinValues.add(JcrConstants.MIX_REFERENCEABLE);
                        preprocessedProperties.remove(mix);
                        mix = new DocViewProperty2(NameConstants.JCR_MIXINTYPES, mixinValues, PropertyType.NAME);
                        preprocessedProperties.add(mix);
                    }
                }
                ni = ni.cloneWithDifferentProperties(preprocessedProperties);
            }

            String nodePath = PathUtil.append(parentPath, npResolver.getJCRName(ni.getName()));
            // add the protected properties
            for (DocViewProperty2 p : ni.getProperties()) {
                String qualifiedPropertyName = npResolver.getJCRName(p.getName());
                if (p.getStringValue().isPresent() && PROTECTED_PROPERTIES_CONSIDERED_FOR_NEW_NODES.contains(p.getName()) && wspFilter.includesProperty(nodePath + "/" + qualifiedPropertyName)) {
                    attrs = new AttributesImpl();
                    attrs.addAttribute(Name.NS_SV_URI, "name", "sv:name", ATTRIBUTE_TYPE_CDATA, qualifiedPropertyName);
                    attrs.addAttribute(Name.NS_SV_URI, "type", "sv:type", ATTRIBUTE_TYPE_CDATA, PropertyType.nameFromValue(p.getType()));
                    handler.startElement(Name.NS_SV_URI, "property", "sv:property", attrs);
                    for (String v : p.getStringValues()) {
                        handler.startElement(Name.NS_SV_URI, "value", "sv:value", DocViewSAXHandler.EMPTY_ATTRIBUTES);
                        handler.characters(v.toCharArray(), 0, v.length());
                        handler.endElement(Name.NS_SV_URI, "value", "sv:value");
                    }
                    handler.endElement(Name.NS_SV_URI, "property", "sv:property");
                }
            }
            handler.endElement(Name.NS_SV_URI, "node", "sv:node");
            handler.endDocument();

            // retrieve newly created node either by uuid, label or name
            Node node = getNodeByIdOrName(parentNode, ni, importUuidBehavior == ImportUUIDBehavior.IMPORT_UUID_CREATE_NEW);
            EffectiveNodeType effectiveNodeType = EffectiveNodeType.ofNode(node);

            // logging for uncovered protected properties
            logIgnoredProtectedProperties(effectiveNodeType, node.getPath(), ni.getProperties(), PROTECTED_PROPERTIES_CONSIDERED_FOR_NEW_NODES);
            setUnprotectedProperties(effectiveNodeType, node, ni, true, null);
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
                        Node node = getNodeByIdOrName(parentNode, ni, importUuidBehavior == ImportUUIDBehavior.IMPORT_UUID_CREATE_NEW);
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

    private void logIgnoredProtectedProperties(EffectiveNodeType effectiveNodeType, String nodePath, Collection<DocViewProperty2> properties, Set<Name> importedProtectedProperties) {
        // logging for protected properties which are not considered during import
        properties.stream()
            .filter(p -> p.getStringValue().isPresent() 
                    && !importedProtectedProperties.contains(p.getName()))
            .forEach(p -> {
                try {
                    if (isPropertyProtected(effectiveNodeType, p)) {
                       log.warn("Ignore protected property '{}' on node '{}'", npResolver.getJCRName(p.getName()), nodePath);
                    }
                } catch (RepositoryException e) {
                    throw new IllegalStateException("Error retrieving protected status of properties", e);
                }
            });
    }

    /**
     * Determines if a given property is protected according to the node type.
     * 
     * @param effectiveNodeType the effective node type
     * @param docViewProperty the property
     * @return{@code true} in case the property is protected, {@code false} otherwise
     * @throws RepositoryException 
     */
    private boolean isPropertyProtected(@NotNull EffectiveNodeType effectiveNodeType, @NotNull DocViewProperty2 docViewProperty) throws RepositoryException {
        return effectiveNodeType.getApplicablePropertyDefinition(npResolver.getJCRName(docViewProperty.getName()), docViewProperty.isMultiValue(), docViewProperty.getType()).map(PropertyDefinition::isProtected).orElse(false);
    }

    private Node getNodeByIdOrName(@NotNull Node currentNode, @NotNull DocViewNode2 ni, boolean isIdNewlyAssigned) throws RepositoryException {
        Node node = null;
        Optional<String> id = ni.getIdentifier();
        String name = npResolver.getJCRName(ni.getName());
        if (id.isPresent() && !isIdNewlyAssigned) {
            try {
                node = currentNode.getSession().getNodeByIdentifier(id.get());
            } catch (RepositoryException e) {
                log.warn("Newly created node not found by uuid {}: {}", currentNode.getPath() + "/" + name, e.toString());
            }
        }
        if (node == null) {
            String snsName = npResolver.getJCRName(ni.getSnsAwareName());
            try {
                node = currentNode.getNode(snsName);
            } catch (RepositoryException e) {
                log.warn("Newly created node not found by SNS aware name {}: {}", currentNode.getPath() + "/" + snsName, e.toString());
            }
        }
        if (node == null) {
            try {
                node = currentNode.getNode(name);
            } catch (RepositoryException e) {
                log.debug("Newly created node not found by name {}: {}", currentNode.getPath() + "/" + name, e.toString());
                throw e;
            }
        }
        return node;
    }

    private boolean setUnprotectedProperties(@NotNull EffectiveNodeType effectiveNodeType, @NotNull Node node, @NotNull DocViewNode2 ni, boolean overwriteExistingProperties, @Nullable VersioningState vs) throws RepositoryException {
        boolean isAtomicCounter = false;
        for (String mixin : ni.getMixinTypes()) {
            if ("mix:atomicCounter".equals(mixin)) {
                isAtomicCounter = true;
            }
        }

        boolean modified = false;
        // add properties
        for (DocViewProperty2 prop : ni.getProperties()) {
            String name = npResolver.getJCRName(prop.getName());
            if (prop != null && !isPropertyProtected(effectiveNodeType, prop) && (overwriteExistingProperties || !node.hasProperty(name)) && wspFilter.includesProperty(node.getPath() + "/" + npResolver.getJCRName(prop.getName()))) {
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
                            log.warn("Error while setting property {} (ignore due to mode {}): {}", prop.getName(), wspFilter.getImportMode(node.getPath()), e1);
                        } else {
                            throw e;
                        }
                    }
                }
            }
        }

        // adjust oak atomic counter
        if (isAtomicCounter && wspFilter.includesProperty(node.getPath() + "/" + npResolver.getJCRName(NAME_OAK_COUNTER))) {
            long previous = 0;
            if (node.hasProperty(NAME_OAK_COUNTER.toString())) {
                previous = node.getProperty(NAME_OAK_COUNTER.toString()).getLong();
            }
            long counter = 0;
            try {
                counter = ni.getPropertyValue(NAME_OAK_COUNTER).map(Long::valueOf).orElse(0L);
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
     * Returns proper access control handling value based on the node
     * name.
     * @param nodeName name of the access control node
     * @return cugHandling for CUG related nodes, aclHandling for
     * everything else
     */
    @NotNull
    private AccessControlHandling getAcHandling(@NotNull Name nodeName) {
        if (cugHandling != null && NAME_REP_CUG_POLICY.equals(nodeName)) {
            return cugHandling;
        } else {
            return aclHandling;
        }
    }


    /**
     * Encapsulates information about the node which has been imported last
     * TODO: minimize as some stack is managed by the oarser already (i.e. the full jcr paths)
     */
    private class StackElement {

        private final Node node;

        private StackElement parent;

        private final NodeNameList childNames = new NodeNameList();

        private boolean isCheckedOut;

        private boolean isNew;

        /**
         * adapter for special content
         */
        private DocViewAdapter adapter;

        public StackElement(@Nullable Node node, boolean isNew) throws RepositoryException {
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

        public void addName(Name name) throws NamespaceException {
            childNames.addName(npResolver.getJCRName(name));
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

    /**
     * Helper class that stores information about attachments
     */
    private static class BlobInfo {

        private final boolean isMulti;

        private final List<Artifact> artifacts = new ArrayList<>();

        public BlobInfo(boolean multi) {
            isMulti = multi;
        }

        public boolean isFile() {
            return !artifacts.isEmpty() && artifacts.get(0).getType() == ArtifactType.FILE;
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
                    } catch (IOException|RepositoryException e) {
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

}

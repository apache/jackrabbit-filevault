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

package org.apache.jackrabbit.vault.fs.impl;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.jcr.Binary;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Value;

import org.apache.jackrabbit.api.ReferenceBinary;
import org.apache.jackrabbit.vault.fs.api.Aggregate;
import org.apache.jackrabbit.vault.fs.api.Aggregator;
import org.apache.jackrabbit.vault.fs.api.Artifact;
import org.apache.jackrabbit.vault.fs.api.ArtifactSet;
import org.apache.jackrabbit.vault.fs.api.ArtifactType;
import org.apache.jackrabbit.vault.fs.api.DumpContext;
import org.apache.jackrabbit.vault.fs.api.ImportInfo;
import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.api.RepositoryAddress;
import org.apache.jackrabbit.vault.fs.api.VaultFsConfig;
import org.apache.jackrabbit.vault.fs.impl.io.AggregateWalkListener;
import org.apache.jackrabbit.vault.util.NodeNameComparator;
import org.apache.jackrabbit.vault.util.PathUtil;
import org.apache.jackrabbit.vault.util.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Collects the items that form an aggregate. The aggregates form a tree on top
 * of the repository one by invoking the respective aggregators. The aggregates
 * are controlled via the {@link AggregateManagerImpl} and are loaded dynamically
 * when traversing through the tree.
 * <p/>
 * The aggregates can then later be used by the aggregators to provide the
 * artifacts of this aggregate.
 *
 */
public class AggregateImpl implements Aggregate {

    private static final char STATE_INITIAL = 'i';
    private static final char STATE_PREPARED = 'p';
    private static final char STATE_COLLECTED = 'c';

    /**
     * The default logger
     */
    protected static final Logger log = LoggerFactory.getLogger(AggregateImpl.class);

    private final AggregateImpl parent;

    private final String path;

    private String relPath;

    private final Aggregator aggregator;

    private final AggregateManagerImpl mgr;

    private final boolean useBinaryReferences;

    private ArtifactSetImpl artifacts;

    /**
     * rel paths of included items (including a leading slash)
     */
    private Set<String> includes;

    private Collection<Property> binaries;

    private List<AggregateImpl> leaves;

    private String[] namespacePrefixes;

    private char state = STATE_INITIAL;

    private WeakReference<Node> nodeRef;

    /**
     * workaround to filter out non directory artifacts for relative
     * path includes (ACL export case)
     */
    private boolean filterArtifacts;

    /**
     * Creates a new root aggregate
     * @param mgr Aggregate manager
     * @param path the path of the aggregate
     * @param aggregator aggregator
     * @throws RepositoryException if a error occurs
     */
    protected AggregateImpl(AggregateManagerImpl mgr, String path, Aggregator aggregator)
            throws RepositoryException{
        log.debug("Create Root Aggregate {}", path);
        this.mgr = mgr;
        this.parent = null;
        this.path = path.equals("/") ? "" : path;
        this.aggregator = aggregator;
        this.useBinaryReferences = "true".equals(mgr.getConfig().getProperty(VaultFsConfig.NAME_USE_BINARY_REFERENCES));
    }

    /**
     * Creates a new sub aggregate
     * @param parent parent aggregate
     * @param path path of aggregate
     * @param aggregator aggregator
     * @throws RepositoryException if a error occurs
     */
    protected AggregateImpl(AggregateImpl parent, String path, Aggregator aggregator)
            throws RepositoryException{
        log.debug("Create Aggregate {}", path);
        this.mgr = parent.mgr;
        this.parent = parent;
        this.path = path;
        this.aggregator = aggregator;
        this.useBinaryReferences = "true".equals(mgr.getConfig().getProperty(VaultFsConfig.NAME_USE_BINARY_REFERENCES));
        // if we have a full coverage aggregator, consider this already collected
        mgr.onAggregateCreated();
        if (aggregator.hasFullCoverage()) {
            this.state = STATE_COLLECTED;
            mgr.onAggregateCollected();
        }
    }

    public Node getNode() throws RepositoryException {
        if (path.length() == 0) {
            return mgr.getSession().getRootNode();
        } else {
            Node node = nodeRef == null ? null : nodeRef.get();
            if (node == null) {
                node = mgr.getSession().getNode(path);
                nodeRef = new WeakReference<Node>(node);
            }
            return node;
        }
    }

    public boolean hasNode() throws RepositoryException {
        return nodeRef != null && nodeRef.get() != null
                || path.length() == 0 || mgr.getSession().nodeExists(path);
    }

    public void invalidate() {
        log.debug("invalidating aggregate {}", getPath());
        artifacts = null;
        includes = null;
        binaries = null;
        leaves = null;
        namespacePrefixes = null;
        nodeRef = null;
        relPath = null;
        state = STATE_INITIAL;
    }

    public Aggregate getParent() {
        return parent;
    }

    public String getPath() {
        return path;
    }

    public RepositoryAddress getRepositoryAddress() throws RepositoryException {
        //assertAttached();
        return mgr.getMountpoint().resolve(getPath());
    }

    public boolean allowsChildren() {
        return aggregator == null || !aggregator.hasFullCoverage() ;
    }

    public String getRelPath() {
        if (relPath == null) {
            relPath = parent == null
                    ? path.substring(path.lastIndexOf('/') + 1)
                    : path.substring(parent.getPath().length()+1);
        }
        return relPath;
    }

    public String getName() {
        return Text.getName(getRelPath());
    }

    public List<? extends Aggregate> getLeaves() throws RepositoryException {
        load();
        return leaves;
    }

    public Aggregate getAggregate(String relPath) throws RepositoryException {
        String[] pathElems = PathUtil.makePath((String[]) null, relPath);
        if (pathElems == null) {
            return this;
        }
        return getAggregate(pathElems, 0);
    }

    private Aggregate getAggregate(String[] pathElems, int pos)
            throws RepositoryException {
        if (pos < pathElems.length) {
            String elem = pathElems[pos];
            if (elem.equals("..")) {
                return parent == null ? null : parent.getAggregate(pathElems, pos + 1);
            }
            // find suitable leaf
            load();
            if (leaves != null && !leaves.isEmpty()) {
                for (AggregateImpl a: leaves) {
                    String[] le = Text.explode(a.getRelPath(), '/');
                    int i=0;
                    while (i<le.length && i+pos < pathElems.length) {
                        if (!le[i].equals(pathElems[i+pos])) {
                            break;
                        }
                        i++;
                    }
                    if (i==le.length) {
                        return a.getAggregate(pathElems, i+pos);
                    }
                }
            }
            return null;
        }
        return this;
    }

    public ArtifactSet getArtifacts() throws RepositoryException {
        if (artifacts == null) {
            assertAttached();
            load();
            artifacts = (ArtifactSetImpl) aggregator.createArtifacts(this);

            if (filterArtifacts) {
                // filter out all non-directory and non .content.xml artifacts
                ArtifactSetImpl na = new ArtifactSetImpl();
                na.addAll(artifacts);
                for (Artifact a: na.values()) {
                    if (a.getType() != ArtifactType.DIRECTORY) {
                        if (!Text.getName(a.getPlatformPath()).equals(".content.xml")) {
                            artifacts.remove(a);
                        }
                    }
                }
            }
        }
        return artifacts;
    }

    /**
     * Returns an artifact output for this node that allows writing the artifacts.
     *
     * @return an artifact output.
     * @throws RepositoryException if this file is not attached to the fs, yet.
     */
    public AggregateBuilder getBuilder() throws RepositoryException {
        assertAttached();
        return new AggregateBuilder(this, getArtifacts());
    }

    /**
     * Creates a new child artifact node with the given name.
     * Please note, that the returned node is not attached to the tree.
     * <p/>
     * If this artifact node does not allow children a RepositoryException is
     * thrown.
     *
     * @param reposName the (repository) name for the new node
     * @return a new child node.
     * @throws RepositoryException if an error occurs.
     */
    public AggregateBuilder create(String reposName) throws RepositoryException {
        assertAttached();
        if (!allowsChildren()) {
            throw new RepositoryException("Unable to create artifact node below a non-folder.");
        }
        return new AggregateBuilder(this, reposName);
    }

    /**
     * Removes this artifact node from the tree. If this artifact node has
     * directory and non-directory artifacts only the non-directory artifacts
     * are removed unless <code>recursive</code> is specified.
     *
     * @param recursive specifies if directories are removed as well.
     * @return infos about the modifications
     * @throws RepositoryException if an error occurs.
     */
    public ImportInfo remove(boolean recursive) throws RepositoryException {
        assertAttached();
        Node node = getNode();
        ImportInfo info = aggregator.remove(node, recursive, true);
        if (parent != null) {
            parent.invalidate();
        }
        return info;
    }

    public AggregateManagerImpl getManager() {
        return mgr;
    }

    /**
     * Writes the artifacts back to the repository.
     *
     * @param artifacts the artifacts to write
     * @param reposName the name of a new child node or <code>null</code>
     * @return infos about the modifications
     * @throws RepositoryException if an error occurs.
     * @throws IOException if an I/O error occurs.
     */
    ImportInfo writeArtifacts(ArtifactSetImpl artifacts, String reposName)
            throws RepositoryException, IOException {
        try {
            return mgr.writeAggregate(this, reposName, artifacts);
        } catch (RepositoryException e) {
            log.error("Error while writing artifacts of {}: {}", getPath(), e.toString());
            throw e;
        } catch (IOException e) {
            log.error("Error while writing artifacts of {}: {}", getPath(), e.toString());
            throw e;
        }
    }

    /**
     * Checks if this aggregate has an aggregator and its node exists.
     * @throws RepositoryException if no aggregator is set.
     */
    private void assertAttached() throws RepositoryException {
        if (aggregator == null || !hasNode()) {
            throw new RepositoryException("aggregate not attached anymore");
        }
    }

    public boolean isAttached() throws RepositoryException {
        return aggregator != null && hasNode();
    }

    /**
     * {@inheritDoc}
     */
    public void dump(DumpContext ctx, boolean isLast) {
        ctx.println(isLast, "Aggregate");
        ctx.indent(isLast);
        ctx.printf(false, "path: %s", getPath());
        ctx.printf(false, "name: %s", getName());
        ctx.printf(false, "relPath: %s", getRelPath());
        try {
            getArtifacts().dump(ctx, false);
        } catch (RepositoryException e) {
            ctx.printf(false, "no artifacts: %s", e.toString());
        }
        ctx.println(false, "Namespaces");
        ctx.indent(false);
        for (String pfx: getNamespacePrefixes()) {
            String uri = "invalid";
            try {
                uri = getNamespaceURI(pfx);
            } catch (RepositoryException e) {
                log.error("Error while resolving namespace uri", e);
            }
            ctx.printf(false, "%s = %s", pfx, uri);
        }
        ctx.outdent();
        if (aggregator != null) {
            aggregator.dump(ctx, true);
        } else {
            ctx.println(true, "no aggregator");
        }
        ctx.outdent();
    }

    public String[] getNamespacePrefixes() {
        if (namespacePrefixes == null) {
            loadNamespaces();
        }
        return namespacePrefixes;
    }

    public String getNamespaceURI(String prefix) throws RepositoryException {
        return mgr.getNamespaceURI(prefix);
    }

    public Collection<Property> getBinaries() {
        return binaries;
    }

    /**
     * Walks the node tree and invokes the callbacks in the listener according
     * to the configured filters.
     *
     * For each tree there are the following events generated:
     * events := OnWalkBegin { nodeEvent } OnWalkEnd;
     * nodeEvent := OnNodeBegin { propEvent } OnChildBegin { nodeEvent } OnNodeEnd;
     * propEvent := OnProperty;
     *
     * @param aggregateWalkListener the listener that receives the events
     * @throws RepositoryException if an repository error occurs.
     */
    public void walk(AggregateWalkListener aggregateWalkListener)
            throws RepositoryException {
        Node node = getNode();
        aggregateWalkListener.onWalkBegin(node);
        walk(aggregateWalkListener, "", node, 0);
        aggregateWalkListener.onWalkEnd(node);
    }

    /**
     * Walks the tree.
     *
     * @param aggregateWalkListener the listener
     * @param relPath rel path of node
     * @param node the current node
     * @param depth the depth of the node
     * @throws RepositoryException if an error occurs.
     */
    private void walk(AggregateWalkListener aggregateWalkListener, String relPath, Node node, int depth)
            throws RepositoryException {
        if (node != null) {
            boolean included = includes(relPath);
            aggregateWalkListener.onNodeBegin(node, included, depth);
            PropertyIterator piter = node.getProperties();
            while (piter.hasNext()) {
                Property prop = piter.nextProperty();
                if (includes(relPath + "/" + prop.getName())) {
                    aggregateWalkListener.onProperty(prop, depth + 1);
                }
            }
            aggregateWalkListener.onChildren(node, depth);

            // copy nodes to list
            NodeIterator niter = node.getNodes();
            long size = niter.getSize();
            List<Node> nodes = new ArrayList<Node>(size > 0 ? (int) size : 16);
            while (niter.hasNext()) {
                nodes.add(niter.nextNode());
            }

            // if node is not orderable, sort them alphabetically
            boolean hasOrderableChildNodes = node.getPrimaryNodeType().hasOrderableChildNodes();
            if (!hasOrderableChildNodes) {
                Collections.sort(nodes, NodeNameComparator.INSTANCE);
            }
            for (Node child: nodes) {
                String p = relPath + "/" + Text.getName(child.getPath());
                if (includes(p)) {
                    walk(aggregateWalkListener, p, child, depth + 1);
                } else {
                    // only inform if node is orderable
                    if (hasOrderableChildNodes) {
                        aggregateWalkListener.onNodeIgnored(child, depth+1);
                    }
                }
            }
            aggregateWalkListener.onNodeEnd(node, included, depth);
        }
    }

    private boolean includes(String relPath) throws RepositoryException {
        // if we have a full coverage aggregator, all items below our root are
        // included.. for now just include all
        return aggregator.hasFullCoverage() ||
                includes != null && includes.contains(relPath);
    }

    private void include(Node node, String nodePath) throws RepositoryException {
        if (nodePath == null) {
            nodePath = node.getPath();
        }

        String relPath = nodePath.substring(path.length());
        if (includes == null || !includes.contains(relPath)) {
            if (log.isDebugEnabled()) {
                log.debug("including {} -> {}", path, nodePath);
            }
            if (includes == null) {
                includes = new HashSet<String>();
            }
            includes.add(mgr.cacheString(relPath));
            if (!node.isSame(getNode())) {
                // ensure that parent nodes are included
                include(node.getParent(), null);
            }
        }
    }

    private void addNamespace(Set<String> prefixes, Property prop) throws RepositoryException {
        String propName = prop.getName();
        addNamespace(prefixes, propName);
        switch (prop.getType()) {
            case PropertyType.NAME:
                if (propName.equals("jcr:mixinTypes") || prop.getDefinition().isMultiple()) {
                    Value[] values = prop.getValues();
                    for (Value value: values) {
                        addNamespace(prefixes, value.getString());
                    }
                } else {
                    addNamespace(prefixes, prop.getValue().getString());
                }
                break;
            case PropertyType.PATH:
                if (prop.getDefinition().isMultiple()) {
                    Value[] values = prop.getValues();
                    for (Value value: values) {
                        addNamespacePath(prefixes, value.getString());
                    }
                } else {
                    addNamespacePath(prefixes, prop.getValue().getString());
                }
                break;
        }
    }

    private void include(Node parent, Property prop, String propPath)
            throws RepositoryException {
        String relPath = propPath.substring(path.length());
        if (includes == null || !includes.contains(relPath)) {
            if (log.isDebugEnabled()) {
                log.debug("including {} -> {}", path, propPath);
            }
            // ensure that parent node is included as well
            include(parent, null);
            includes.add(mgr.cacheString(relPath));
            if (prop.getType() == PropertyType.BINARY) {
                boolean includeBinary = true;
                if (useBinaryReferences) {
                    Binary bin = prop.getBinary();
                    if (bin != null && bin instanceof ReferenceBinary) {
                        String binaryReference = ((ReferenceBinary) bin).getReference();

                        // do not create a separate binary file if there is a reference
                        if (binaryReference != null) {
                            includeBinary = false;
                        }
                    }
                }

                if (includeBinary) {
                    if (binaries == null) {
                        binaries = new LinkedList<Property>();
                    }
                    binaries.add(prop);
                }
            }
        }
    }

    private void addNamespace(Set<String> prefixes, String name) throws RepositoryException {
        int idx = name.indexOf(':');
        if (idx > 0) {
            String pfx = name.substring(0, idx);
            if (!prefixes.contains(pfx)) {
                prefixes.add(mgr.cacheString(pfx));
            }
        }
    }

    private void addNamespacePath(Set<String> prefixes, String path) throws RepositoryException {
        String[] names = path.split("/");
        for (String name: names) {
            addNamespace(prefixes, name);
        }
    }

    private void loadNamespaces() {
        if (namespacePrefixes == null) {
            if (log.isDebugEnabled()) {
                log.debug("loading namespaces of aggregate {}", path);
            }
            try {
                load();
                Set<String> prefixes = new HashSet<String>();
                // need to traverse the nodes to get all namespaces
                loadNamespaces(prefixes, "", getNode());
                namespacePrefixes = prefixes.toArray(new String[prefixes.size()]);
            } catch (RepositoryException e) {
                throw new IllegalStateException("Internal error while loading namespaces", e);
            }
        }
    }

    private void loadNamespaces(Set<String> prefixes, String parentPath, Node node) throws RepositoryException {
        String name = node.getName();
        addNamespace(prefixes, name);
        for (PropertyIterator iter = node.getProperties(); iter.hasNext();) {
            Property p = iter.nextProperty();
            String relPath = parentPath + "/" + p.getName();
            if (includes(relPath)) {
                addNamespace(prefixes, p);
            }
        }
        for (NodeIterator iter = node.getNodes(); iter.hasNext();) {
            Node c = iter.nextNode();
            String relPath = parentPath + "/" + c.getName();
            if (includes(relPath)) {
                loadNamespaces(prefixes, relPath, c);
            } else if (node.getPrimaryNodeType().hasOrderableChildNodes()) {
                addNamespace(prefixes, c.getName());
            }
        }
    }

    private void load() throws RepositoryException {
        long now = System.currentTimeMillis();
        if (state == STATE_INITIAL) {
            log.debug("Collect + Preparing {}", getPath());
            prepare(getNode(), true);
            state = STATE_PREPARED;
            long end = System.currentTimeMillis();
            log.debug("Collect + Preparing {} in {}ms", getPath(), (end-now));
            mgr.onAggregateCollected();
            mgr.onAggregatePrepared();
        } else if (state == STATE_COLLECTED) {
            log.debug("Preparing {}", getPath());
            // in this state we were traversed once and all parent items where
            // resolved. now we need to collect the items of our non-collected
            // leafs
            if (leaves != null && !leaves.isEmpty()) {
                for (AggregateImpl leaf: leaves) {
                    leaf.collect();
                }
            }
            state = STATE_PREPARED;
            long end = System.currentTimeMillis();
            log.debug("Preparing {} in {}ms", getPath(), (end-now));
            mgr.onAggregatePrepared();
        }
    }

    private void collect() throws RepositoryException {
        if (state == STATE_INITIAL) {
            long now = System.currentTimeMillis();
            log.debug("Collecting {}", getPath());
            prepare(getNode(), false);
            state = STATE_COLLECTED;
            long end = System.currentTimeMillis();
            log.debug("Collecting  {} in {}ms", getPath(), (end-now));
            mgr.onAggregateCollected();
        }
    }

    private void prepare(Node node, boolean descend)
            throws RepositoryException {
        if (log.isDebugEnabled()) {
            log.debug("descending into {} (descend={})", node.getPath(), descend);
        }
        // add "our" properties to the include set
        PropertyIterator pIter = node.getProperties();
        while (pIter.hasNext()) {
            Property p = pIter.nextProperty();
            String path = p.getPath();
            if (aggregator.includes(getNode(), node, p, path)) {
                include(node, p, path);
            }
        }
        // include "our" nodes to the include set and delegate the others to the
        // respective aggregator building sub aggregates
        NodeIterator nIter = node.getNodes();
        while (nIter.hasNext()) {
            Node n = nIter.nextNode();
            String path = n.getPath();
            PathFilterSet coverSet = mgr.getWorkspaceFilter().getCoveringFilterSet(path);
            boolean isAncestor = mgr.getWorkspaceFilter().isAncestor(path);
            boolean isIncluded = mgr.getWorkspaceFilter().contains(path);
            if (coverSet == null && !isAncestor) {
                continue;
            }
            // check if another aggregator can handle this node
            Aggregator a = mgr.getAggregator(n, path);
            // - if the aggregator is null
            // - or the aggregator is the same as ours or the default
            // - and if we include the content as well
            // - then don't use the matched aggregator
            if ((a == null)
                    || ((a == aggregator || a.isDefault())
                        && (aggregator.includes(getNode(), n, path)))) {
                // if workspace does not include this node, ignore it
                if (!isIncluded && !isAncestor) {
                    continue;
                }
                include(n, path);
                prepare(n, true);
            } else {
                // otherwise create sub node and collect items if needed
                // but only if the node is either an ancestor or is included
                // or if the workspace filter set contains relative pattern (ACL export case).
                boolean onlyRelativePatterns = coverSet !=null && coverSet.hasOnlyRelativePatterns();
                if (isAncestor || isIncluded || onlyRelativePatterns) {
                    AggregateImpl sub = new AggregateImpl(this, path, a);
                    sub.filterArtifacts = !isIncluded && onlyRelativePatterns;
                    if (leaves == null) {
                        leaves = new LinkedList<AggregateImpl>();
                    }
                    if (descend) {
                        try {
                            sub.collect();
                        } catch (RepositoryException e) {
                            // in some weird cases, the jcr2spi layer reports
                            // wrong nodes. in this case, just remove it again
                            // as leave
                            log.warn("Alleged node is gone: {}", path);
                            sub.invalidate();
                            sub = null;
                        }
                    } else {
                        log.debug("adding pending leaf {}", path);
                    }
                    if (sub != null) {
                        leaves.add(sub);
                    }
                }
            }
        }
    }

}
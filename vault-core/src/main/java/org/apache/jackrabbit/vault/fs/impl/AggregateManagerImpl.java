/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.jackrabbit.vault.fs.impl;

import javax.jcr.Credentials;
import javax.jcr.LoginException;
import javax.jcr.Node;
import javax.jcr.PropertyType;
import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.nodetype.NodeDefinition;
import javax.jcr.nodetype.NodeType;
import javax.jcr.nodetype.PropertyDefinition;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.jackrabbit.vault.fs.api.AggregateManager;
import org.apache.jackrabbit.vault.fs.api.Aggregator;
import org.apache.jackrabbit.vault.fs.api.ArtifactHandler;
import org.apache.jackrabbit.vault.fs.api.DumpContext;
import org.apache.jackrabbit.vault.fs.api.ImportInfo;
import org.apache.jackrabbit.vault.fs.api.ProgressTrackerListener;
import org.apache.jackrabbit.vault.fs.api.RepositoryAddress;
import org.apache.jackrabbit.vault.fs.api.VaultFsConfig;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.fs.config.AbstractVaultFsConfig;
import org.apache.jackrabbit.vault.fs.config.ConfigurationException;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.fs.impl.aggregator.RootAggregator;
import org.apache.jackrabbit.vault.fs.spi.CNDReader;
import org.apache.jackrabbit.vault.fs.spi.NodeTypeInstaller;
import org.apache.jackrabbit.vault.fs.spi.ServiceProviderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The artifact manager exposes an artifact node tree using the configured
 * aggregators and serializers.
 */
public class AggregateManagerImpl implements AggregateManager {

    /**
     * the name of the (internal) default config
     */
    private static final String DEFAULT_CONFIG = "org/apache/jackrabbit/vault/fs/config/defaultConfig-1.1.xml";

    /**
     * the name of the (internal) default config
     */
    private static final String DEFAULT_BINARY_REFERENCES_CONFIG =
            "org/apache/jackrabbit/vault/fs/config/defaultConfig-1.1-binaryless.xml";

    /**
     * the name of the (internal) default workspace filter
     */
    private static final String DEFAULT_WSP_FILTER = "" + "org/apache/jackrabbit/vault/fs/config/defaultFilter-1.0.xml";

    /**
     * name of node types resource
     */
    private static final String DEFAULT_NODETYPES = "" + "org/apache/jackrabbit/vault/fs/config/nodetypes.cnd";

    /**
     * default logger
     */
    private static final Logger log = LoggerFactory.getLogger(AggregateManagerImpl.class);

    /**
     * the repository session for this manager
     */
    private Session session;

    /**
     * indicates if this manager owns the session and is allowed to close
     * it in {@link #unmount()}
     */
    private final boolean ownSession;

    /**
     * The repository address of the mountpoint;
     */
    private final RepositoryAddress mountpoint;

    /**
     * provider that selects the respective aggregator for a repository node
     */
    private final AggregatorProvider aggregatorProvider;

    /**
     * list of artifact handlers
     */
    private final List<ArtifactHandler> artifactHandlers;

    /**
     * filter to general includes/excludes
     */
    private final WorkspaceFilter workspaceFilter;

    private AggregatorTracker tracker;

    /**
     * Set of node types used in the aggregates. this is cumulated when building
     * the aggregates
     */
    private final Set<String> nodeTypes = new HashSet<String>();

    /**
     * Cache of namespace prefixes to URIs. This cache is shared across all aggregates
     * to avoid expensive JCR tree traversals for namespace discovery.
     */
    private final ConcurrentHashMap<String, String> namespacePrefixCache = new ConcurrentHashMap<>();

    /**
     * Cache of namespace prefixes per aggregate path. This cache stores the discovered prefixes
     * for each aggregate path to avoid re-walking the same subtrees.
     */
    private final ConcurrentHashMap<String, String[]> aggregateNamespaceCache = new ConcurrentHashMap<>();

    /**
     * config
     */
    private final VaultFsConfig config;

    /**
     * the root aggregate
     */
    private final AggregateImpl root;

    /**
     * Creates a new artifact manager that is rooted at the given node.
     *
     * @param config fs config
     * @param wspFilter the workspace filter
     * @param mountpoint the address of the mountpoint
     * @param session the repository session
     * @return an artifact manager
     * @throws RepositoryException if an error occurs.
     */
    public static AggregateManager mount(
            VaultFsConfig config, WorkspaceFilter wspFilter, RepositoryAddress mountpoint, Session session)
            throws RepositoryException {
        if (config == null) {
            config = getDefaultConfig();
        }
        if (wspFilter == null) {
            wspFilter = getDefaultWorkspaceFilter();
        }
        Node rootNode = session.getNode(mountpoint.getPath());
        return new AggregateManagerImpl(config, wspFilter, mountpoint, rootNode, false);
    }

    /**
     * Creates a new artifact manager that is rooted at the given path using
     * the provided repository, credentials and workspace to create the
     * session.
     *
     * @param config fs config
     * @param wspFilter the workspace filter
     * @param rep the jcr repository
     * @param credentials the credentials
     * @param mountpoint the address of the mountpoint
     * @return an artifact manager
     * @throws RepositoryException if an error occurs.
     */
    public static AggregateManager mount(
            VaultFsConfig config,
            WorkspaceFilter wspFilter,
            Repository rep,
            Credentials credentials,
            RepositoryAddress mountpoint)
            throws RepositoryException {
        if (config == null) {
            config = getDefaultConfig();
        }
        if (wspFilter == null) {
            wspFilter = getDefaultWorkspaceFilter();
        }
        Node rootNode;
        String wspName = mountpoint.getWorkspace();
        try {
            rootNode = rep.login(credentials, wspName).getNode(mountpoint.getPath());
        } catch (LoginException e) {
            if (wspName == null) {
                // try again with default workspace
                // todo: make configurable
                rootNode = rep.login(credentials, "crx.default").getNode(mountpoint.getPath());
            } else {
                throw e;
            }
        }
        return new AggregateManagerImpl(config, wspFilter, mountpoint, rootNode, true);
    }

    /**
     * Returns the default config
     * @return the default config
     */
    public static VaultFsConfig getDefaultConfig() {
        try (InputStream in = AggregateManagerImpl.class.getClassLoader().getResourceAsStream(DEFAULT_CONFIG)) {
            if (in == null) {
                throw new InternalError("Default config not in classpath: " + DEFAULT_CONFIG);
            }
            return AbstractVaultFsConfig.load(in, DEFAULT_CONFIG);
        } catch (ConfigurationException e) {
            throw new IllegalArgumentException("Internal error while parsing config.", e);
        } catch (IOException e) {
            throw new IllegalArgumentException("Internal error while parsing config.", e);
        }
    }

    /**
     * Returns the default config
     * @return the default config
     */
    public static VaultFsConfig getDefaultBinaryReferencesConfig() {
        try (InputStream in =
                AggregateManagerImpl.class.getClassLoader().getResourceAsStream(DEFAULT_BINARY_REFERENCES_CONFIG)) {
            if (in == null) {
                throw new InternalError("Default config not in classpath: " + DEFAULT_BINARY_REFERENCES_CONFIG);
            }
            return AbstractVaultFsConfig.load(in, DEFAULT_BINARY_REFERENCES_CONFIG);
        } catch (ConfigurationException e) {
            throw new IllegalArgumentException("Internal error while parsing config.", e);
        } catch (IOException e) {
            throw new IllegalArgumentException("Internal error while parsing config.", e);
        }
    }

    /**
     * Returns the default workspace filter
     * @return the default workspace filter
     */
    public static DefaultWorkspaceFilter getDefaultWorkspaceFilter() {
        try (InputStream in = AggregateManagerImpl.class.getClassLoader().getResourceAsStream(DEFAULT_WSP_FILTER)) {
            if (in == null) {
                throw new InternalError("Default filter not in classpath: " + DEFAULT_WSP_FILTER);
            }
            DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
            filter.load(in);
            return filter;
        } catch (ConfigurationException e) {
            throw new IllegalArgumentException("Internal error while parsing config.", e);
        } catch (IOException e) {
            throw new IllegalArgumentException("Internal error while parsing config.", e);
        }
    }

    public void unmount() throws RepositoryException {
        assertMounted();
        if (ownSession) {
            session.logout();
        }
        session = null;
    }

    public AggregateImpl getRoot() throws RepositoryException {
        assertMounted();
        return root;
    }

    public RepositoryAddress getMountpoint() {
        return mountpoint;
    }

    /**
     * Constructs the artifact manager.
     *
     * @param config the configuration
     * @param wspFilter the workspace filter
     * @param mountpoint the repository address of the mountpoint
     * @param rootNode the root node
     * @param ownSession indicates if the session can be logged out in unmount.
     * @throws RepositoryException if an error occurs.
     */
    private AggregateManagerImpl(
            VaultFsConfig config,
            WorkspaceFilter wspFilter,
            RepositoryAddress mountpoint,
            Node rootNode,
            boolean ownSession)
            throws RepositoryException {
        session = rootNode.getSession();
        this.mountpoint = mountpoint;
        this.ownSession = ownSession;
        this.config = config;
        workspaceFilter = wspFilter;
        aggregatorProvider = new AggregatorProvider(config.getAggregators());
        artifactHandlers = Collections.unmodifiableList(config.getHandlers());

        // init root node
        Aggregator rootAggregator = rootNode.getDepth() == 0 ? new RootAggregator() : getAggregator(rootNode, null);
        root = new AggregateImpl(this, rootNode.getPath(), rootAggregator);

        // setup node types
        initNodeTypes();

        // pre-populate namespace cache with standard JCR namespaces
        initNamespaceCache();
    }

    public Set<String> getNodeTypes() {
        return nodeTypes;
    }

    /**
     * Add the primary and mixin node types of that node to the internal set
     * of used node types.
     * @param node the node
     * @throws RepositoryException if an error occurs
     */
    public void addNodeTypes(Node node) throws RepositoryException {
        internalAddNodeType(node.getPrimaryNodeType());
        for (NodeType nt : node.getMixinNodeTypes()) {
            internalAddNodeType(nt);
        }
    }

    public String getNamespaceURI(String prefix) throws RepositoryException {
        return session.getNamespaceURI(prefix);
    }

    /**
     * Gets a namespace URI from the cache or from the session if not cached.
     * This method caches the prefix-to-URI mapping to avoid repeated JCR lookups.
     *
     * @param prefix the namespace prefix
     * @return the namespace URI
     * @throws RepositoryException if an error occurs
     */
    public String getCachedNamespaceURI(String prefix) throws RepositoryException {
        return namespacePrefixCache.computeIfAbsent(prefix, p -> {
            try {
                return session.getNamespaceURI(p);
            } catch (RepositoryException e) {
                throw new RuntimeException("Failed to get namespace URI for prefix: " + p, e);
            }
        });
    }

    /**
     * Adds a namespace prefix to the cache.
     *
     * @param prefix the namespace prefix to cache
     */
    public void cacheNamespacePrefix(String prefix) {
        if (prefix != null && !prefix.isEmpty() && !namespacePrefixCache.containsKey(prefix)) {
            try {
                String uri = session.getNamespaceURI(prefix);
                namespacePrefixCache.put(prefix, uri);
            } catch (RepositoryException e) {
                // Log but don't fail - the prefix might be checked later
                log.debug("Could not resolve namespace URI for prefix: {}", prefix, e);
            }
        }
    }

    /**
     * Gets the cached namespace prefixes.
     *
     * @return a set of all cached namespace prefixes
     */
    public Set<String> getCachedNamespacePrefixes() {
        return namespacePrefixCache.keySet();
    }

    /**
     * Gets cached namespace prefixes for a specific aggregate path.
     *
     * @param path the aggregate path
     * @return the cached prefixes, or null if not cached
     */
    public String[] getCachedAggregatePrefixes(String path) {
        return aggregateNamespaceCache.get(path);
    }

    /**
     * Caches namespace prefixes for a specific aggregate path.
     *
     * @param path the aggregate path
     * @param prefixes the namespace prefixes to cache
     */
    public void cacheAggregatePrefixes(String path, String[] prefixes) {
        if (path != null && prefixes != null) {
            aggregateNamespaceCache.put(path, prefixes);
        }
    }

    /**
     * Invalidates the namespace caches. This should be called if namespace
     * definitions are added or modified in the repository.
     */
    public void invalidateNamespaceCaches() {
        log.info(
                "Invalidating namespace caches ({} prefix mappings, {} aggregate caches)",
                namespacePrefixCache.size(),
                aggregateNamespaceCache.size());
        namespacePrefixCache.clear();
        aggregateNamespaceCache.clear();
        // Re-initialize the prefix cache with current repository namespaces
        initNamespaceCache();
    }

    /**
     * Invalidates the aggregate namespace cache for a specific path.
     * This should be called when content at that path is modified.
     *
     * @param path the aggregate path to invalidate
     */
    public void invalidateAggregatePrefixes(String path) {
        if (path != null) {
            aggregateNamespaceCache.remove(path);
            log.debug("Invalidated namespace cache for path: {}", path);
        }
    }

    public void startTracking(ProgressTrackerListener pTracker) {
        tracker = new AggregatorTracker(pTracker);
    }

    public void stopTracking() {
        if (tracker != null) {
            tracker.log(true);
            tracker = null;
        }
    }

    public void onAggregateCreated() {
        if (tracker != null) {
            tracker.onCreated();
        }
    }

    public void onAggregateCollected() {
        if (tracker != null) {
            tracker.onCollected();
        }
    }

    public void onAggregatePrepared() {
        if (tracker != null) {
            tracker.onPrepared();
        }
    }

    /**
     * internally add the node type and all transitive ones to the set of
     * used node types.
     * @param nodeType to add
     */
    private void internalAddNodeType(NodeType nodeType) {
        if (nodeType != null && !nodeTypes.contains(nodeType.getName())) {
            nodeTypes.add(nodeType.getName());
            NodeType[] superTypes = nodeType.getSupertypes();
            for (NodeType superType : superTypes) {
                nodeTypes.add(superType.getName());
            }
            NodeDefinition[] nodeDefs = nodeType.getChildNodeDefinitions();
            if (nodeDefs != null) {
                for (NodeDefinition nodeDef : nodeDefs) {
                    internalAddNodeType(nodeDef.getDefaultPrimaryType());
                    NodeType[] reqs = nodeDef.getRequiredPrimaryTypes();
                    if (reqs != null) {
                        for (NodeType req : reqs) {
                            internalAddNodeType(req);
                        }
                    }
                }
            }

            // check reference constraints, too (bug #33367)
            PropertyDefinition[] propDefs = nodeType.getPropertyDefinitions();
            if (propDefs != null) {
                for (PropertyDefinition propDef : propDefs) {
                    if (propDef.getRequiredType() == PropertyType.REFERENCE
                            || propDef.getRequiredType() == PropertyType.WEAKREFERENCE) {
                        String[] vcs = propDef.getValueConstraints();
                        if (vcs != null) {
                            for (String vc : vcs) {
                                try {
                                    internalAddNodeType(session.getWorkspace()
                                            .getNodeTypeManager()
                                            .getNodeType(vc));
                                } catch (RepositoryException e) {
                                    // ignore
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Initializes vlt node types (might not be the correct location)
     * @throws RepositoryException if an error occurs
     */
    private void initNodeTypes() throws RepositoryException {
        // check if node types are registered
        try {
            session.getWorkspace().getNodeTypeManager().getNodeType("vlt:HierarchyNode");
            session.getWorkspace().getNodeTypeManager().getNodeType("vlt:FullCoverage");
            return;
        } catch (RepositoryException e) {
            // ignore
        }

        try (InputStream in = getClass().getClassLoader().getResourceAsStream(DEFAULT_NODETYPES)) {
            NodeTypeInstaller installer = ServiceProviderFactory.getProvider().getDefaultNodeTypeInstaller(session);
            CNDReader types = ServiceProviderFactory.getProvider().getCNDReader();
            types.read(new InputStreamReader(in, "utf8"), DEFAULT_NODETYPES, null);
            installer.install(null, types);
        } catch (Exception e) {
            throw new RepositoryException("Error while importing nodetypes.", e);
        }
    }

    /**
     * Pre-populates the namespace cache with all namespaces registered in the repository.
     * This optimization reduces expensive JCR tree traversals during namespace discovery.
     */
    private void initNamespaceCache() {
        try {
            String[] prefixes = session.getNamespacePrefixes();
            for (String prefix : prefixes) {
                try {
                    String uri = session.getNamespaceURI(prefix);
                    namespacePrefixCache.put(prefix, uri);
                } catch (RepositoryException e) {
                    log.debug("Could not cache namespace prefix '{}': {}", prefix, e.getMessage());
                }
            }
            log.info("Initialized namespace cache with {} prefixes", namespacePrefixCache.size());
        } catch (RepositoryException e) {
            log.warn("Could not initialize namespace cache", e);
        }
    }

    public Aggregator getAggregator(Node node, String path) throws RepositoryException {
        return aggregatorProvider.getAggregator(node, path);
    }

    public WorkspaceFilter getWorkspaceFilter() {
        return workspaceFilter;
    }

    /**
     * Writes the artifact set back to the repository.
     *
     * @param node the artifact node to write
     * @param reposName the name of the new node or {@code null}
     * @param artifacts the artifact to write
     * @return infos about the modifications
     * @throws RepositoryException if an error occurs.
     * @throws IOException if an I/O error occurs.
     */
    public ImportInfo writeAggregate(AggregateImpl node, String reposName, ArtifactSetImpl artifacts)
            throws RepositoryException, IOException {
        assertMounted();
        if (reposName == null) {
            ImportInfo info;
            for (ArtifactHandler artifactHandler : artifactHandlers) {
                info = artifactHandler.accept(session, node, artifacts);
                if (info != null) {
                    node.invalidate();
                    return info;
                }
            }
        } else {
            ImportInfo info;
            for (ArtifactHandler artifactHandler : artifactHandlers) {
                info = artifactHandler.accept(session, node, reposName, artifacts);
                if (info != null) {
                    node.invalidate();
                    return info;
                }
            }
        }
        throw new IllegalStateException("No handler accepted artifacts " + artifacts);
    }

    /**
     * Checks if this tree is still mounted and if the attached session
     * is still live.
     *
     * @throws RepositoryException if not mounted or not live.
     */
    private void assertMounted() throws RepositoryException {
        if (!isMounted()) {
            throw new RepositoryException("JcrFS is not mounted anymore.");
        }
    }

    public boolean isMounted() {
        return session != null && session.isLive();
    }

    public String getUserId() throws RepositoryException {
        assertMounted();
        return session.getUserID();
    }

    public String getWorkspace() throws RepositoryException {
        assertMounted();
        return session.getWorkspace().getName();
    }

    public Session getSession() {
        return session;
    }

    public void dumpConfig(PrintWriter out) throws IOException {
        DumpContext ctx = new DumpContext(out);
        ctx.println(false, "workspace filter");
        ctx.indent(false);
        workspaceFilter.dump(ctx, true);
        ctx.outdent();
        aggregatorProvider.dump(ctx, false);
        ctx.println(true, "handlers");
        ctx.indent(true);
        for (Iterator<ArtifactHandler> iter = artifactHandlers.iterator(); iter.hasNext(); ) {
            ArtifactHandler h = iter.next();
            h.dump(ctx, !iter.hasNext());
        }
        ctx.outdent();

        ctx.flush();
    }

    public VaultFsConfig getConfig() {
        return config;
    }

    private static class AggregatorTracker {

        /**
         * default logger
         */
        private static final Logger log = LoggerFactory.getLogger(AggregatorTracker.class);

        private ProgressTrackerListener tracker;

        int numCreated;

        int numCollected;

        int numPrepared;

        long lastLogged;

        private AggregatorTracker(ProgressTrackerListener tracker) {
            this.tracker = tracker;
        }

        public void onCreated() {
            numCreated++;
            log(false);
        }

        public void onCollected() {
            numCollected++;
            log(false);
        }

        public void onPrepared() {
            numPrepared++;
            log(false);
        }

        public void log(boolean flush) {
            if (tracker == null && !log.isInfoEnabled()) {
                return;
            }
            long now = System.currentTimeMillis();
            if (lastLogged == 0) {
                lastLogged = now;

                // updated each 5 seconds
            } else if (now - lastLogged > 5000 || flush) {
                lastLogged = now;
                String str = new StringBuilder("Aggregation status: ")
                        .append(numPrepared)
                        .append(" of ")
                        .append(numCreated)
                        .append(" prepared, ")
                        .append(numCollected)
                        .append(" collected")
                        .toString();
                log.trace("- {}", str);
                if (tracker != null) {
                    tracker.onMessage(ProgressTrackerListener.Mode.TEXT, "-", str);
                }
            }
        }
    }
}

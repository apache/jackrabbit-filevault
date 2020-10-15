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
package org.apache.jackrabbit.vault.validation.spi.impl.nodetype;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import javax.jcr.AccessDeniedException;
import javax.jcr.NamespaceException;
import javax.jcr.NamespaceRegistry;
import javax.jcr.RepositoryException;
import javax.jcr.UnsupportedRepositoryOperationException;
import javax.jcr.ValueFactory;
import javax.jcr.nodetype.InvalidNodeTypeDefinitionException;
import javax.jcr.nodetype.NodeTypeExistsException;
import javax.jcr.nodetype.NodeTypeManager;

import org.apache.jackrabbit.commons.cnd.CndImporter;
import org.apache.jackrabbit.commons.cnd.ParseException;
import org.apache.jackrabbit.jcr2spi.ManagerProvider;
import org.apache.jackrabbit.jcr2spi.NamespaceRegistryImpl;
import org.apache.jackrabbit.jcr2spi.NamespaceStorage;
import org.apache.jackrabbit.jcr2spi.hierarchy.HierarchyManager;
import org.apache.jackrabbit.jcr2spi.lock.LockStateManager;
import org.apache.jackrabbit.jcr2spi.nodetype.EffectiveNodeTypeProvider;
import org.apache.jackrabbit.jcr2spi.nodetype.ItemDefinitionProvider;
import org.apache.jackrabbit.jcr2spi.nodetype.ItemDefinitionProviderImpl;
import org.apache.jackrabbit.jcr2spi.nodetype.NodeTypeDefinitionProvider;
import org.apache.jackrabbit.jcr2spi.nodetype.NodeTypeManagerImpl;
import org.apache.jackrabbit.jcr2spi.nodetype.NodeTypeRegistryImpl;
import org.apache.jackrabbit.jcr2spi.security.AccessManager;
import org.apache.jackrabbit.jcr2spi.security.authorization.AccessControlProvider;
import org.apache.jackrabbit.jcr2spi.version.VersionManager;
import org.apache.jackrabbit.spi.QValueFactory;
import org.apache.jackrabbit.spi.commons.conversion.DefaultNamePathResolver;
import org.apache.jackrabbit.spi.commons.conversion.NamePathResolver;
import org.apache.jackrabbit.spi.commons.conversion.NameResolver;
import org.apache.jackrabbit.spi.commons.conversion.PathResolver;
import org.apache.jackrabbit.spi.commons.namespace.NamespaceMapping;
import org.apache.jackrabbit.spi.commons.namespace.NamespaceResolver;
import org.apache.jackrabbit.spi.commons.namespace.RegistryNamespaceResolver;
import org.apache.jackrabbit.spi.commons.nodetype.NodeTypeStorage;
import org.apache.jackrabbit.spi.commons.nodetype.NodeTypeStorageImpl;
import org.apache.jackrabbit.spi.commons.value.QValueFactoryImpl;
import org.apache.jackrabbit.value.ValueFactoryImpl;
import org.jetbrains.annotations.NotNull;

public class NodeTypeManagerProvider implements ManagerProvider, NamespaceStorage {

    // namespace related helpers
    private final @NotNull NamespaceMapping namespaceMapping;
    private final @NotNull NamespaceRegistry namespaceRegistry;
    private final @NotNull NamespaceResolver namespaceResolver;
    private final @NotNull NamePathResolver npResolver;
    
    // nodetype related helpers
    private final @NotNull NodeTypeStorage nodeTypeStorage;
    private final @NotNull NodeTypeRegistryImpl nodeTypeRegistry;
    private final @NotNull NodeTypeManagerImpl nodeTypeManager;
    
    private final @NotNull ItemDefinitionProvider itemDefinitionProvider;

    public NodeTypeManagerProvider() throws IOException, RepositoryException, ParseException {
        namespaceMapping = new NamespaceMapping();
        // add default mapping, the rest comes from the CDN provided via the reader
        namespaceMapping.setMapping(NamespaceRegistry.PREFIX_EMPTY, NamespaceRegistry.NAMESPACE_EMPTY);
        namespaceRegistry = new NamespaceRegistryImpl(this);
        namespaceResolver = new RegistryNamespaceResolver(namespaceRegistry);
        npResolver = new DefaultNamePathResolver(namespaceResolver);
        nodeTypeStorage = new NodeTypeStorageImpl();
        nodeTypeRegistry = NodeTypeRegistryImpl.create(nodeTypeStorage, namespaceRegistry);
        nodeTypeManager = new NodeTypeManagerImpl(nodeTypeRegistry, this);
        itemDefinitionProvider = new ItemDefinitionProviderImpl(nodeTypeRegistry, null, null);
        // always provide default nodetypes
        try (Reader reader = new InputStreamReader(
                this.getClass().getResourceAsStream("/default-nodetypes.cnd"),
                StandardCharsets.US_ASCII)) {
            registerNodeTypes(reader);
        }
    }

    public void registerNodeTypes(Reader reader) throws InvalidNodeTypeDefinitionException, NodeTypeExistsException, UnsupportedRepositoryOperationException, ParseException, RepositoryException, IOException {
        CndImporter.registerNodeTypes(reader, null, nodeTypeManager, namespaceRegistry, getJcrValueFactory(), false);
    }

    @Override
    public @NotNull NamePathResolver getNamePathResolver() {
        return npResolver;
    }

    @Override
    public @NotNull NameResolver getNameResolver() {
        return npResolver;
    }

    @Override
    public @NotNull PathResolver getPathResolver() {
        return npResolver;
    }

    @Override
    public @NotNull NamespaceResolver getNamespaceResolver() {
        return namespaceResolver;
    }

    public @NotNull NodeTypeManager getNodeTypeManager() {
        return nodeTypeManager;
    }

    @Override
    public HierarchyManager getHierarchyManager() {
        throw new UnsupportedOperationException();
    }

    @Override
    public AccessManager getAccessManager() {
        throw new UnsupportedOperationException();
    }

    @Override
    public LockStateManager getLockStateManager() {
        throw new UnsupportedOperationException();
    }

    @Override
    public VersionManager getVersionStateManager() {
        throw new UnsupportedOperationException();
    }

    @Override
    public @NotNull ItemDefinitionProvider getItemDefinitionProvider() {
        return itemDefinitionProvider;
    }

    @Override
    public @NotNull NodeTypeDefinitionProvider getNodeTypeDefinitionProvider() {
        return nodeTypeManager;
    }

    @Override
    public @NotNull EffectiveNodeTypeProvider getEffectiveNodeTypeProvider() {
        return nodeTypeRegistry;
    }

    @Override
    public @NotNull ValueFactory getJcrValueFactory() throws RepositoryException {
        return ValueFactoryImpl.getInstance();
    }

    @Override
    public @NotNull QValueFactory getQValueFactory() throws RepositoryException {
        return QValueFactoryImpl.getInstance();
    }

    @Override
    public AccessControlProvider getAccessControlProvider() throws RepositoryException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Map<String, String> getRegisteredNamespaces() throws RepositoryException {
        return namespaceMapping.getPrefixToURIMapping();
    }

    @Override
    public String getPrefix(String uri) throws NamespaceException, RepositoryException {
        return namespaceMapping.getPrefix(uri);
    }

    @Override
    public String getURI(String prefix) throws NamespaceException, RepositoryException {
        return namespaceMapping.getURI(prefix);
    }

    @Override
    public void registerNamespace(String prefix, String uri)
            throws NamespaceException, UnsupportedRepositoryOperationException, AccessDeniedException, RepositoryException {
        namespaceMapping.setMapping(prefix, uri);
    }

    @Override
    public void unregisterNamespace(String uri)
            throws NamespaceException, UnsupportedRepositoryOperationException, AccessDeniedException, RepositoryException {
        namespaceMapping.removeMapping(uri);
    }
}

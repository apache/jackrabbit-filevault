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
package org.apache.jackrabbit.vault.packaging.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.jackrabbit.vault.packaging.JcrPackage;
import org.apache.jackrabbit.vault.packaging.JcrPackageDefinition;
import org.apache.jackrabbit.vault.packaging.JcrPackageManager;
import org.apache.jackrabbit.vault.packaging.PackageManager;
import org.apache.jackrabbit.vault.packaging.Packaging;
import org.apache.jackrabbit.vault.packaging.events.impl.PackageEventDispatcher;
import org.apache.jackrabbit.vault.packaging.registry.PackageRegistry;
import org.apache.jackrabbit.vault.packaging.registry.impl.AbstractPackageRegistry;
import org.apache.jackrabbit.vault.packaging.registry.impl.CompositePackageRegistry;
import org.apache.jackrabbit.vault.packaging.registry.impl.JcrPackageRegistry;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@code PackagingImpl}...
 */
@Component(
        service = Packaging.class,
        immediate = true,
        property = {"service.vendor=The Apache Software Foundation"}
)
@Designate(ocd = PackagingImpl.Config.class)
public class PackagingImpl implements Packaging {

    /**
     * default logger
     */
    private static final Logger log = LoggerFactory.getLogger(PackagingImpl.class);

    @Reference
    private PackageEventDispatcher eventDispatcher;
    
    // In case a PackageRegistry is exposed as OSGi Service the first one will be considered
    // as base registry to fall back for dependency checks - currently only FSPackageRegistry is exposed as such
    @Reference (cardinality = ReferenceCardinality.MULTIPLE,
            policy = ReferencePolicy.DYNAMIC,
            policyOption = ReferencePolicyOption.GREEDY)
    volatile List<PackageRegistry> registries;

    /**
     * package manager is a singleton
     */
    private final PackageManagerImpl pkgManager = new PackageManagerImpl();

    Config config;

    public PackagingImpl() {
        
    }

    @ObjectClassDefinition(
            name = "Apache Jackrabbit FileVault Packaging Service (Package Manager Configuration)"
    )
    @interface Config {

        /**
         * Defines the package roots of the package manager
         */
        @AttributeDefinition(description = "The locations in the repository which are used by the package manager")
        String[] packageRoots() default {"/etc/packages"};
        
        @AttributeDefinition(description = "The authorizable ids which are allowed to execute hooks (in addition to 'admin', 'administrators' and 'system'")
        String[] authIdsForHookExecution();
        
        @AttributeDefinition(description = "The authorizable ids which are allowed to install packages with the 'requireRoot' flag (in addition to 'admin', 'administrators' and 'system'")
        String[] authIdsForRootInstallation();
        
        @AttributeDefinition(description = "The default value for strict imports (i.e. whether it just logs certain errors or always throws exceptions")
        boolean isStrict() default true;
    }

    @Activate
    private void activate(Config config) {
        this.config = config;
        pkgManager.setDispatcher(eventDispatcher);
        log.info("Jackrabbit Filevault Packaging initialized with config {}", config);
    }

    /**
     * {@inheritDoc}
     */
    public PackageManager getPackageManager() {
        return pkgManager;
    }

    /**
     * {@inheritDoc}
     */
    public JcrPackageManager getPackageManager(Session session) {
        JcrPackageManagerImpl mgr = new JcrPackageManagerImpl(session, config.packageRoots(), config.authIdsForHookExecution(), config.authIdsForRootInstallation(), config.isStrict());
        mgr.setDispatcher(eventDispatcher);
        setBaseRegistry(mgr.getInternalRegistry(), registries);
        return mgr;
    }

    private static boolean setBaseRegistry(JcrPackageRegistry jcrPackageRegistry, List<PackageRegistry> otherRegistries) {
        if (!otherRegistries.isEmpty()) {
            jcrPackageRegistry.setBaseRegistry(otherRegistries.get(0));
            return true;
        } else {
            return false;
        }
    }
    
    /**
     * {@inheritDoc}
     */
    public JcrPackageDefinition createPackageDefinition(Node defNode) {
        return new JcrPackageDefinitionImpl(defNode);
    }

    /**
     * {@inheritDoc}
     */
    public JcrPackage open(Node node, boolean allowInvalid) throws RepositoryException {
        JcrPackageManager pMgr = getPackageManager(node.getSession());
        return pMgr.open(node, allowInvalid);
    }

    @Override
    public PackageRegistry getCompositePackageRegistry(Session session, boolean useJcrRegistryAsPrimaryRegistry) throws IOException {
        List<PackageRegistry> allRegistries = new ArrayList<>(registries);
        JcrPackageRegistry jcrPackageRegistry = getJcrPackageRegistry(session, false);
        if (useJcrRegistryAsPrimaryRegistry) {
            allRegistries.add(0, jcrPackageRegistry);
        } else {
            allRegistries.add(jcrPackageRegistry);
        }
        return new CompositePackageRegistry(allRegistries);
    }

    @Override
    public JcrPackageRegistry getJcrPackageRegistry(Session session) {
        return getJcrPackageRegistry(session, true);
    }

    private JcrPackageRegistry getJcrPackageRegistry(Session session, boolean useBaseRegistry) {
        JcrPackageRegistry registry = new JcrPackageRegistry(session, new AbstractPackageRegistry.SecurityConfig(config.authIdsForHookExecution(), config.authIdsForRootInstallation()), config.isStrict(), config.packageRoots());
        registry.setDispatcher(eventDispatcher);
        if (useBaseRegistry) {
            setBaseRegistry(registry, registries);
        }
        return registry;
    }
}
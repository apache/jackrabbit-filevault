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

package org.apache.jackrabbit.vault.packaging;

import java.io.IOException;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.jackrabbit.vault.packaging.registry.PackageRegistry;
import org.apache.jackrabbit.vault.packaging.registry.impl.JcrPackageRegistry;
import org.osgi.annotation.versioning.ProviderType;

/**
 * Default access point to package managers.
 * @since 2.4.0
 */
@ProviderType
public interface Packaging {

    /**
     * Returns a non-repository based package manager.
     * @return the package manager
     */
    PackageManager getPackageManager();

    /**
     * Returns a repository based package manager.
     * @param session repository session
     * @return the package manager
     */
    JcrPackageManager getPackageManager(Session session);

    /**
     * Creates a new jcr package definition based on the given node.
     * @param defNode the node
     * @return the definition
     */
    JcrPackageDefinition createPackageDefinition(Node defNode);

    /**
     * Opens a package that is based on the given node. If {@code allowInvalid}
     * is {@code true} also invalid packages are returned, but only if the
     * node is file like (i.e. is nt:hierarchyNode and has a
     * jcr:content/jcr:data property).
     *
     * This is a shortcut version of {@link org.apache.jackrabbit.vault.packaging.JcrPackageManager#open(javax.jcr.Node, boolean)}
     * which does not create a package manager instance.
     *
     * @param node the underlying node
     * @param allowInvalid if {@code true} invalid packages are opened, too.
     * @return the new package or {@code null} it the package is not
     *         valid unless {@code allowInvalid} is {@code true}.
     * @throws javax.jcr.RepositoryException if an error occurs
     * 
     * @since 2.3.0
     */
    JcrPackage open(Node node, boolean allowInvalid) throws RepositoryException;
    
    /**
     * Returns a new composite package registry which acts on all currently registered package registries and 
     * a JCR-based registry for the current configuration and the given session.
     * All operations creating new packages will act on the primary registry which is determined by argument
     * {@code useJcrRegistryAsPrimaryRegistry}.
     * Due to the dynamic nature of package registries the return value should not be persisted.
     * 
     * @param session the JCR session to use for the JCR-based registry
     * @param useJcrRegistryAsPrimaryRegistry if {@code true} the JCR-based registry will be used as primary
     * registry, otherwise the first registered package registry is used and the JCR-based registry will be inserted as last registry. 
     * @return the composite package registry
     * @throws IOException 
     */
    PackageRegistry getCompositePackageRegistry(Session session, boolean useJcrRegistryAsPrimaryRegistry) throws IOException;

    /**
     * Returns a JCR-based package registry using the given session.
     * @param session the JCR session to use for reading/writing nodes in the repository
     * @return the JCR-based package registry
     */
    JcrPackageRegistry getJcrPackageRegistry(Session session);
}
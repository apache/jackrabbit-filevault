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

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.jackrabbit.vault.packaging.impl.JcrPackageDefinitionImpl;
import org.apache.jackrabbit.vault.packaging.impl.JcrPackageManagerImpl;
import org.apache.jackrabbit.vault.packaging.impl.PackageManagerImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default access point to package managers for non OSGi clients.
 * 
 * @since 2.0
 */
public class PackagingService {

    /**
     * default logger
     */
    private static final Logger log = LoggerFactory.getLogger(PackagingService.class);

    /**
     * Returns a non-repository based package manager.
     * @return the package manager
     */
    public static PackageManager getPackageManager() {
        return new PackageManagerImpl();
    }

    /**
     * Returns a repository based package manager.
     * @param session repository session
     * @return the package manager
     */
    public static JcrPackageManager getPackageManager(Session session) {
        try {
            throw new IllegalStateException();
        } catch (IllegalStateException e) {
            log.info("JcrPackageManager acquired w/o service! Alternate package roots will not be respected.", e);
        }

        // todo: should somehow pass the package roots
        return new JcrPackageManagerImpl(session, new String[0]);
    }

    /**
     * Creates a new jcr package definition based on the given node.
     * @param defNode the node
     * @return the definition
     * @since 2.2.14
     */
    public static JcrPackageDefinition createPackageDefinition(Node defNode) {
        return new JcrPackageDefinitionImpl(defNode);
    }

    /**
     * Opens a package that is based on the given node. If {@code allowInvalid}
     * is {@code true} also invalid packages are returned, but only if the
     * node is file like (i.e. is nt:hierarchyNode and has a
     * jcr:content/jcr:data property).
     *
     * This is a shortcut version of {@link JcrPackageManager#open(Node, boolean)}
     * which does not create a package manager instance.
     *
     * @param node the underlying node
     * @param allowInvalid if {@code true} invalid packages are openend, too.
     * @return the new package or {@code null} it the package is not
     *         valid unless {@code allowInvalid} is {@code true}.
     * @throws RepositoryException if an error occurs
     * 
     * @since 2.3.0
     */
    public static JcrPackage open(Node node, boolean allowInvalid)
            throws RepositoryException {
        JcrPackageManager pMgr = getPackageManager(node.getSession());
        return pMgr.open(node, allowInvalid);
    }
}
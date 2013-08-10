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

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Service;
import org.apache.jackrabbit.vault.packaging.JcrPackage;
import org.apache.jackrabbit.vault.packaging.JcrPackageDefinition;
import org.apache.jackrabbit.vault.packaging.JcrPackageManager;
import org.apache.jackrabbit.vault.packaging.PackageManager;
import org.apache.jackrabbit.vault.packaging.Packaging;
import org.apache.jackrabbit.vault.util.JcrConstants;

/**
 * <code>PackagingImpl</code>...
 */
@Component(metatype = false, immediate = true)
@Service(value = Packaging.class)
public class PackagingImpl implements Packaging {

    /**
     * package manager is a singleton
     */
    private final PackageManager pkgManager = new PackageManagerImpl();

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
        return new JcrPackageManagerImpl(session);
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
        JcrPackage pack = new JcrPackageImpl(node);
        if (pack.isValid()) {
            return pack;
        } else if (allowInvalid
                && node.isNodeType(JcrConstants.NT_HIERARCHYNODE)
                && node.hasProperty(JcrConstants.JCR_CONTENT + "/" + JcrConstants.JCR_DATA)) {
            return pack;
        } else {
            return null;
        }
    }
}
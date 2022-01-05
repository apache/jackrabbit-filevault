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

import java.util.Properties;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.apache.jackrabbit.vault.fs.config.DefaultMetaInf;
import org.apache.jackrabbit.vault.packaging.PackageProperties;

/**
 * Only limited meta information like package properties and filters are exposed which are available
 * from the underlying package definition node.
 * Raw properties are exposed on a best effort basis, because the storage format is different from
 * the one in {@code properties.xml}.
 * Therefore it is recommended to use the high-level API exposed via {@link PackageProperties}.
 */
public class JcrPackageDefinitionMetaInf extends DefaultMetaInf  {

    private final PackageProperties packageProperties;
    private final Properties legacyProperties;

    public JcrPackageDefinitionMetaInf(Node node, PackageProperties packageProperties, Properties legacyProperties) throws RepositoryException {
        setFilter(JcrWorkspaceFilter.loadFilter(node));
        this.packageProperties = packageProperties;
        this.legacyProperties = legacyProperties;
    }

    @Override
    public PackageProperties getPackageProperties() {
        return packageProperties;
    }

    @Override
    public Properties getProperties() {
        return legacyProperties;
    }
}

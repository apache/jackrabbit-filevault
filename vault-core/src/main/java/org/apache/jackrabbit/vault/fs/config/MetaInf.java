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

package org.apache.jackrabbit.vault.fs.config;

import java.util.Collection;
import java.util.Properties;

import org.apache.jackrabbit.vault.fs.api.VaultFsConfig;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.fs.spi.NodeTypeSet;
import org.apache.jackrabbit.vault.fs.spi.PrivilegeDefinitions;

/**
 * Abstracts the way of accessing the vault specific meta-info of a checkout.
 */
public interface MetaInf {

    /**
     * Format Version 1. Used for content assembled until Vault 1.2.8
     * @since 2.0
     */
    public static int FORMAT_VERSION_1 = 1;

    /**
     * Format Version 2. Used for content assembled since Vault 1.2.9
     * @since 2.0
     */
    public static int FORMAT_VERSION_2 = 2;

    /**
     * Name of the package format version
     * @since 2.0
     */
    public static String PACKAGE_FORMAT_VERSION = "packageFormatVersion";

    /**
     * Name of the 'created' property
     */
    public static String CREATED = "created";

    /**
     * Name of the 'created by' property
     */
    public static String CREATED_BY = "createdBy";
    
    /**
     * Returns the package format version of this package. If the package
     * lacks this information, {@link #FORMAT_VERSION_2} is returned, since this
     * feature was implemented recently.
     *
     * @return the package format version
     * @since 2.0
     */
    int getPackageFormatVersion();

    /**
     * Returns the vault settings.
     * @return the vault settings.
     */
    VaultSettings getSettings();

    /**
     * Returns the workspace filter.
     * @return the workspace filter.
     */
    WorkspaceFilter getFilter();

    /**
     * Returns the vault config
     * @return the vault config
     */
    VaultFsConfig getConfig();

    /**
     * Returns the properties
     * @return the properties
     */
    Properties getProperties();

    /**
     * Returns the node types
     * @return the node types
     */
    Collection<NodeTypeSet> getNodeTypes();

    /**
     * Returns custom privileges defined in the meta inf.
     * @return a collection of custom privileges.
     * @since 3.0
     */
    PrivilegeDefinitions getPrivileges();
    
    /**
     * Checks if the meta-inf contains a serialized definition.
     * @return <code>true</code> if it contains a serialized definition.
     */
    boolean hasDefinition();

}
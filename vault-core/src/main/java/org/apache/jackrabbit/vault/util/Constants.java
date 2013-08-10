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

package org.apache.jackrabbit.vault.util;

/**
 * Defines some (file) name constants.
 *
 */
public interface Constants {

    /**
     * the namespace uri of the 'vlt' namespace
     */
    String VAULT_NS_URI = "http://www.day.com/jcr/vault/1.0";

    /**
     * name of the {@value} directory
     */
    String META_INF = "META-INF";

    /**
     * name of the {@value} directory inside the {@value #META_INF}.
     */
    String VAULT_DIR = "vault";

    /**
     * default encoding for strings
     */
    String ENCODING = "utf-8";

    /**
     * path of the meta directory
     */
    String META_DIR = META_INF + "/" + VAULT_DIR;

    /**
     * name of the "hooks" directory
     */
    String HOOKS_DIR = "hooks";

    /**
     * name of the root directory under which all content goes.
     */
    String ROOT_DIR = "jcr_root";

    /**
     * name of the vault fs config file.
     */
    String CONFIG_XML = "config.xml";

    /**
     * name of the filter file.
     */
    String FILTER_XML = "filter.xml";

    /**
     * name of the filter file.
     */
    String FILTER_VLT_XML = "filter-vlt.xml";

    /**
     * name of the global settings file.
     */
    String SETTINGS_XML = "settings.xml";

    /**
     * name of the package definition directory
     */
    String PACKAGE_DEFINITION_XML = "definition/.content.xml";
    
    /**
     * name of the auth config
     */
    String AUTH_XML = "auth.xml";

    /**
     * name of the node types file.
     */
    String NODETYPES_CND = "nodetypes.cnd";

    /**
     * name of the custom export properties file
     */
    String PROPERTIES_XML = "properties.xml";

    /**
     * name of the custom privileges file
     * @since 3.0
     */
    String PRIVILEGES_XML = "privileges.xml";

    /**
     * name of the file for generic serializations
     */
    String DOT_CONTENT_XML = ".content.xml";

    /**
     * empty string array
     */
    String[] EMPTY_STRING_ARRAY = new String[0];

    /**
     * the native file separator char
     */
    String FS_NATIVE = System.getProperty("file.separator");


}
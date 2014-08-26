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

package org.apache.jackrabbit.vault.fs.spi;

import java.util.HashSet;
import java.util.Set;

/**
 * <code>DefaultNodeTypes</code>...
 */
public final class DefaultNodeTypes {

    public static final Set<String> JSR170_NODE_TYPES = new HashSet<String>();

    static {
        JSR170_NODE_TYPES.add("nt:base");
        JSR170_NODE_TYPES.add("nt:unstructured");
        JSR170_NODE_TYPES.add("mix:referenceable");
        JSR170_NODE_TYPES.add("mix:lockable");
        JSR170_NODE_TYPES.add("mix:versionable");
        JSR170_NODE_TYPES.add("nt:versionHistory");
        JSR170_NODE_TYPES.add("nt:versionLabels");
        JSR170_NODE_TYPES.add("nt:version");
        JSR170_NODE_TYPES.add("nt:frozenNode");
        JSR170_NODE_TYPES.add("nt:versionedChild");
        JSR170_NODE_TYPES.add("nt:nodeType");
        JSR170_NODE_TYPES.add("nt:propertyDefinition");
        JSR170_NODE_TYPES.add("nt:childNodeDefinition");
        JSR170_NODE_TYPES.add("nt:hierarchyNode");
        JSR170_NODE_TYPES.add("nt:folder");
        JSR170_NODE_TYPES.add("nt:file");
        JSR170_NODE_TYPES.add("nt:linkedFile");
        JSR170_NODE_TYPES.add("nt:resource");
        JSR170_NODE_TYPES.add("nt:query");
    }

    public static final Set<String> JSR283_NODE_TYPES = new HashSet<String>(JSR170_NODE_TYPES);

    static {
        JSR283_NODE_TYPES.add("mix:created");
        JSR283_NODE_TYPES.add("mix:lastModified");
        JSR283_NODE_TYPES.add("mix:etag");
        JSR283_NODE_TYPES.add("mix:title");
        JSR283_NODE_TYPES.add("mix:language");
        JSR283_NODE_TYPES.add("mix:mimeType");
        JSR283_NODE_TYPES.add("mix:shareable");
        JSR283_NODE_TYPES.add("mix:simpleVersionable");
        JSR283_NODE_TYPES.add("mix:lifecycle");
        JSR283_NODE_TYPES.add("nt:address");
        JSR283_NODE_TYPES.add("nt:activity");
        JSR283_NODE_TYPES.add("nt:configuration");
    }

    public static final Set<String> JACKRABBIT_1X_NODE_TYPES = new HashSet<String>(JSR170_NODE_TYPES);

    static {
        JACKRABBIT_1X_NODE_TYPES.add("rep:nodeTypes");
        JACKRABBIT_1X_NODE_TYPES.add("rep:root");
        JACKRABBIT_1X_NODE_TYPES.add("rep:system");
        JACKRABBIT_1X_NODE_TYPES.add("rep:versionStorage");
    }

    public static final Set<String> JACKRABBIT_2X_NODE_TYPES = new HashSet<String>(JSR283_NODE_TYPES);

    static {
        JACKRABBIT_2X_NODE_TYPES.add("rep:nodeTypes");
        JACKRABBIT_2X_NODE_TYPES.add("rep:root");
        JACKRABBIT_2X_NODE_TYPES.add("rep:system");
        JACKRABBIT_2X_NODE_TYPES.add("rep:versionStorage");
        // since 2.0
        JACKRABBIT_2X_NODE_TYPES.add("rep:Activities");
        JACKRABBIT_2X_NODE_TYPES.add("rep:Configurations");
        JACKRABBIT_2X_NODE_TYPES.add("rep:VersionReference");
        JACKRABBIT_2X_NODE_TYPES.add("rep:AccessControllable");
        JACKRABBIT_2X_NODE_TYPES.add("rep:Policy");
        JACKRABBIT_2X_NODE_TYPES.add("rep:ACL");
        JACKRABBIT_2X_NODE_TYPES.add("rep:ACE");
        JACKRABBIT_2X_NODE_TYPES.add("rep:GrantACE");
        JACKRABBIT_2X_NODE_TYPES.add("rep:DenyACE");
        JACKRABBIT_2X_NODE_TYPES.add("rep:AccessControl");
        JACKRABBIT_2X_NODE_TYPES.add("rep:PrincipalAccessControl");
        JACKRABBIT_2X_NODE_TYPES.add("rep:Authorizable");
        JACKRABBIT_2X_NODE_TYPES.add("rep:Impersonatable");
        JACKRABBIT_2X_NODE_TYPES.add("rep:User");
        JACKRABBIT_2X_NODE_TYPES.add("rep:Group");
        JACKRABBIT_2X_NODE_TYPES.add("rep:AuthorizableFolder");
        JACKRABBIT_2X_NODE_TYPES.add("rep:RetentionManageable");
    }

    public static final Set<String> OAK_1X_NODE_TYPES = new HashSet<String>(JACKRABBIT_2X_NODE_TYPES);
    static {
        OAK_1X_NODE_TYPES.add("rep:SystemUser");
        OAK_1X_NODE_TYPES.add("rep:MemberReferences");
        OAK_1X_NODE_TYPES.add("rep:MemberReferencesList");
        OAK_1X_NODE_TYPES.add("rep:Privileges");
        OAK_1X_NODE_TYPES.add("rep:Privilege");
        OAK_1X_NODE_TYPES.add("rep:Token");
        OAK_1X_NODE_TYPES.add("rep:MergeConflict");
        OAK_1X_NODE_TYPES.add("rep:PermissionStore");
        OAK_1X_NODE_TYPES.add("rep:Permissions");
        OAK_1X_NODE_TYPES.add("rep:Restrictions");
        OAK_1X_NODE_TYPES.add("rep:NodeType");
        OAK_1X_NODE_TYPES.add("rep:VersionablePaths");
        OAK_1X_NODE_TYPES.add("rep:Unstructured");
        OAK_1X_NODE_TYPES.add("oak:QueryIndexDefinition");
        OAK_1X_NODE_TYPES.add("oak:Unstructured");
    }

    public static final Set<String> CRX_1X_NODE_TYPES = new HashSet<String>(JACKRABBIT_1X_NODE_TYPES);

    static {
        CRX_1X_NODE_TYPES.add("crx:XmlNode");
        CRX_1X_NODE_TYPES.add("crx:XmlCharacterData");
        CRX_1X_NODE_TYPES.add("crx:XmlElement");
        CRX_1X_NODE_TYPES.add("crx:XmlDocument");
        CRX_1X_NODE_TYPES.add("crx:XmlProcessingInstruction");
        CRX_1X_NODE_TYPES.add("crx:Package");
        CRX_1X_NODE_TYPES.add("crx:ItemFilter");
        CRX_1X_NODE_TYPES.add("crx:HierarchyFilter");
        CRX_1X_NODE_TYPES.add("crx:OPVValueFilter");
        CRX_1X_NODE_TYPES.add("crx:DeclaredTypeFilter");
        CRX_1X_NODE_TYPES.add("crx:NodeTypeFilter");
        CRX_1X_NODE_TYPES.add("crx:XPathFilter");
        CRX_1X_NODE_TYPES.add("rep:AccessControllable");
        CRX_1X_NODE_TYPES.add("rep:AccessControl");
        CRX_1X_NODE_TYPES.add("rep:Permission");
        CRX_1X_NODE_TYPES.add("rep:GrantPermission");
        CRX_1X_NODE_TYPES.add("rep:DenyPermission");
        CRX_1X_NODE_TYPES.add("rep:Principal");
        CRX_1X_NODE_TYPES.add("rep:Impersonateable");
        CRX_1X_NODE_TYPES.add("rep:User");
        CRX_1X_NODE_TYPES.add("rep:Group");
        CRX_1X_NODE_TYPES.add("rep:PrincipalFolder");
        CRX_1X_NODE_TYPES.add("rep:ExternalPrincipal");
        CRX_1X_NODE_TYPES.add("rep:Sudoers");
        CRX_1X_NODE_TYPES.add("rep:WorkspaceAccess");
        CRX_1X_NODE_TYPES.add("rep:Workspace");
        CRX_1X_NODE_TYPES.add("crx:ResourceBundle");
        CRX_1X_NODE_TYPES.add("crx:RequestMapping");
        CRX_1X_NODE_TYPES.add("crx:NodeTypeRequestMapping");
        CRX_1X_NODE_TYPES.add("crx:PathRequestMapping");
    }

    public static final Set<String> CRX_2X_NODE_TYPES = new HashSet<String>(JACKRABBIT_2X_NODE_TYPES);

    static {
        CRX_2X_NODE_TYPES.add("crx:XmlNode");
        CRX_2X_NODE_TYPES.add("crx:XmlCharacterData");
        CRX_2X_NODE_TYPES.add("crx:XmlElement");
        CRX_2X_NODE_TYPES.add("crx:XmlDocument");
        CRX_2X_NODE_TYPES.add("crx:XmlProcessingInstruction");
        CRX_2X_NODE_TYPES.add("crx:Package");
        CRX_2X_NODE_TYPES.add("crx:ItemFilter");
        CRX_2X_NODE_TYPES.add("crx:HierarchyFilter");
        CRX_2X_NODE_TYPES.add("crx:OPVValueFilter");
        CRX_2X_NODE_TYPES.add("crx:DeclaredTypeFilter");
        CRX_2X_NODE_TYPES.add("crx:NodeTypeFilter");
        CRX_2X_NODE_TYPES.add("crx:XPathFilter");
        CRX_2X_NODE_TYPES.add("crx:ResourceBundle");
        CRX_2X_NODE_TYPES.add("crx:RequestMapping");
        CRX_2X_NODE_TYPES.add("crx:NodeTypeRequestMapping");
        CRX_2X_NODE_TYPES.add("crx:PathRequestMapping");
    }

    public static final Set<String> CRX_3X_NODE_TYPES = new HashSet<String>(CRX_2X_NODE_TYPES);
    static {
        CRX_3X_NODE_TYPES.addAll(OAK_1X_NODE_TYPES);
    }


}
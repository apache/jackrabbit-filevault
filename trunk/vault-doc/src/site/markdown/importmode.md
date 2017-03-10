<!--
   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to You under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
-->
Import Mode
===========
The import mode defines how importing content is applied to existing content in the repository. It is controlled by the [ImportOptions][api.ImportOptions] and the [WorkspaceFilter][api.WorkspaceFilter] and defaults to `REPLACE`.

Modes
-----
It is important to note, that the import mode always operates on entire nodes and subtrees, and never just on properties (with an exception of the group membership). For example an `ImportMode.MERGE` does **not** merge the properties from an existing node with those of the package.

**`ImportMode.REPLACE`**
: This is the normal behaviour. Existing content is replaced completely by the imported content, i.e. is overridden or deleted accordingly.

**`ImportMode.UPDATE`**
: Existing content is updated. Existing content is replaced, new content is added and none is deleted.

**`ImportMode.MERGE`**
: Existing content is not modified, i.e. only new content is added and none is deleted or modified.

Nested import modes (not implemented)
-------------------------------------
If 2 filters overlap, import modes can nest. The filter root that is closest to the node defines which import mode is relevant. for example:

````
<filter root="/content/foo" mode="replace"/>
<filter root="/content/foo/custom" mode="merge"/>
````

this has the effect, that the node `/content/foo` is replaced, but the subtree at `/content/foo/custom` is merged. however, this only works if the package contains `/content/foo`. if it is missing, the entire subtree at `/content/foo` is removed.

Import Mode behaviour on authorizables:
----------------------------------------------------
If an authorizable with the same name already exists, the active `ImportMode` controls how the existing authorizables are affected:

**`ImportMode.REPLACE`**
: Replaces the authorizable node completely with the content in the package. The importer effectively deletes and re-creates the authorizable at the path specified in the package (internally the content is imported using the content handler with `IMPORT_UUID_COLLISION_REMOVE_EXISTING`). Note that any sub-nodes of the authorizable are treated like normal content and obey the normal filter rules. so the following filter should only replace the users's node, but not its sub nodes:

````
<filter root="/home/users/test">
  <exclude pattern="/home/users/test/.*" />
</filter>
````


**`ImportMode.UPDATE`**
: Replaces the authorizable node completely with the content in the package **in place**. The importer effectively deletes and re-creates the authorizable at the path specified in the package (internally the content is imported using the content handler with `IMPORT_UUID_COLLISION_REPLACE_EXISTING`). Note that any sub-nodes of the authorizable are treated like normal content and obey the normal filter rules. However, if the authorizable existed at a different path as specified in the repository, the importer keeps track of the remapping and calculates the filters accordingly.


**`ImportMode.MERGE`**
: Has no effect if the authorizable already existed except for group memberships (see below). Note that any sub-nodes of the authorizable are treated like normal content and obey the normal filter rules. However, if the authorizable existed at a different path as specified in the repository, the importer keeps track of the remapping and calculates the filters accordingly.

### Implementation Detail
The authorizable are imported using the JCR import content handler and rely on the fact that authorizables have a their UUID computed based on their id. If a repository implementation does not follow this assumption, filevault will need to find a different strategy to import authorizables.

Merging Group Members
---------------------
`ImportMode.MERGE` has special semantics for Groups. In this case, the group members of the package are merged with the existing group members in the repository. This is especially useful when adding a new authorizable including its group membership.


Scope of the workspace filter
-----------------------------
Note that the workspace filter of the package refers on the content of the package and not the existing authorizable. For example, if the package contains a user at `/home/users/t/test` which already exists in the repository at `/home/users/custom/te/test` then the workspace filter `/home/users/t` covers the user, but not `/home/users/custom`.

However, the importer keeps track of potential remapping of existing users and tries to calculate the filters accordingly.

<!-- references -->
[api.WorkspaceFilter]: apidocs/org/apache/jackrabbit/vault/fs/api/WorkspaceFilter.html
[api.ImportMode]: apidocs/org/apache/jackrabbit/vault/fs/api/ImportMode.html
[api.ImportOptions]: apidocs/org/apache/jackrabbit/vault/fs/io/ImportOptions.html
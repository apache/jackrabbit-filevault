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
The import mode defines how imported content affects existing content in the repository. It is controlled by the [ImportMode][api.ImportMode] set in the [Workspace Filter](filter.html) and defaults to `REPLACE`. 

Details on how node ids are treated during import are outlined at [Referenceable Nodes](referenceablenodes.html)

<!-- MACRO{toc} -->

Regular content
----------------
The import mode handling is inconsistent and has many edge cases for the mode `MERGE` and `UPDATE`. Therefore FileVault 3.5.0 introduces the new modes `MERGE_PROPERTIES` and `UPDATE_PROPERTIES` (in [JCRVLT-255][JCRVLT-255]) which behave much more predicatable. The details are outlined at the [JavaDoc][api.ImportMode].

Access control list
----------------------------------------------------
The import mode handling for access control lists (name `rep:ACL`, `rep:CugPolicy` or `rep:PrincipalPolicy`) is  only affected by the [`acHandling` package property](properties.html) and not by the import mode set on the filter rule.

Authorizables
----------------------------------------------------
If an authorizable with the same name already exists, the active `ImportMode` controls how the existing authorizables are affected:

**`ImportMode.REPLACE`**
: Replaces the authorizable node completely with the content in the package. The importer effectively deletes and re-creates the authorizable at the path specified in the package (internally the content is imported using the content handler with `IMPORT_UUID_COLLISION_REMOVE_EXISTING`). Note that any sub-nodes of the authorizable are treated like normal content and obey the normal filter rules. so the following filter should only replace the users's node, but not its sub nodes:

````
<filter root="/home/users/test">
  <exclude pattern="/home/users/test/.*" />
</filter>
````


**`ImportMode.UPDATE`,`ImportMode.UPDATE_PROPERTIES`**
: Replaces the authorizable node completely with the content in the package **in place**. The importer effectively deletes and re-creates the authorizable at the path specified in the package (internally the content is imported using the content handler with `IMPORT_UUID_COLLISION_REPLACE_EXISTING`). Note that any sub-nodes of the authorizable are treated like normal content and obey the normal filter rules. However, if the authorizable existed at a different path as specified in the repository, the importer keeps track of the remapping and calculates the filters accordingly.


**`ImportMode.MERGE`,`ImportMode.MERGE_PROPERTIES`**
: Has no effect if the authorizable already existed except for group memberships (see below). Note that any sub-nodes of the authorizable are treated like normal content and obey the normal filter rules. However, if the authorizable existed at a different path as specified in the repository, the importer keeps track of the remapping and calculates the filters accordingly.

### Merging Group Members
`ImportMode.MERGE` has special semantics for Groups. In this case, the group members of the package are merged with the existing group members in the repository. This is especially useful when adding a new authorizable including its group membership.

### Implementation Detail
The authorizables are imported using the JCR import content handler and rely on the fact that authorizables have a their UUID computed based on their id. If a repository implementation does not follow this assumption, filevault will need to find a different strategy to import authorizables.


### Scope of the workspace filter
Note that the workspace filter of the package refers to the content of the package and not the existing authorizable. For example, if the package contains a user at `/home/users/t/test` which already exists in the repository at `/home/users/custom/te/test` then the workspace filter `/home/users/t` covers the user, but not `/home/users/custom`.

However, the importer keeps track of potential remapping of existing users and tries to calculate the filters accordingly.

[api.WorkspaceFilter]: apidocs/org/apache/jackrabbit/vault/fs/api/WorkspaceFilter.html
[api.ImportMode]: apidocs/org/apache/jackrabbit/vault/fs/api/ImportMode.html
[JCRVLT-255]: https://issues.apache.org/jira/browse/JCRVLT-255
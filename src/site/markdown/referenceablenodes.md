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

# Referenceable Nodes

<!-- MACRO{toc} -->

## Overview

The JCR 2.0 specification defines [referenceable nodes][1]. Those carry a `jcr:uuid` property which uniquely identifies a node within a repository workspace. FileVault exports will contain this (protected) property `jcr:uuid` (when present) which is important to keep [referential integrity][2].
The import behavior depends on the used FileVault version.

## Import behavior prior FileVault 3.5.2

The ids of referenceable nodes are only kept during import when a node with the same name does not yet exist in the repository. For existing nodes the ids are never updated with the value from the package. They either get a new id or keep their old one (in case the old node was already a referenceable node). In case of conflicts with nodes which are not siblings (i.e. don't share their direct parent node) the newly imported node gets a new id. 
In case of conflicts among siblings the existing conflicting node is [removed][5] but all its references are kept (i.e. they point to a different path after the import) when the existing conflicting node is contained in the filter rules, if the conflicting node is outside the filter rules the to-be imported node is skipped (and import continues with its children as if they were below the existing one).

## Import behavior since FileVault 3.5.2

Since version 3.5.2 ([JCRVLT-551](https://issues.apache.org/jira/browse/JCRVLT-551)) FileVault tries to use the `jcr:uuid` of the node in the package even if a same named node does already exist. In case this cannot be achieved while keeping referential integrity of old and new nodes an exception is thrown. Existing nodes with conflicting identifiers or reference properties towards conflicting identifiers are removed in advance if they are contained in the [filter rules][4] to reduce the conflict potential.

### Id Handling Policies

The import behavior of packages with can be tweaked with `ImportOptions.setIdConflictPolicy(...)` per package or the default can be overridden in the [OSGi Configuration](config.html). For further details refer to the [JavaDoc][3]. The old behavior can be achieved with `IdConflictPolicy.LEGACY`.

Note that `CREATE_NEW_ID` will enforce creation of new identifiers, even when no node with conflicting ids.

[1]: https://s.apache.org/jcr-2.0-spec/3_Repository_Model.html#3.8%20Referenceable%20Nodes
[2]: https://s.apache.org/jcr-2.0-spec/3_Repository_Model.html#3.8.2%20Referential%20Integrity
[3]: apidocs/org/apache/jackrabbit/vault/fs/api/IdConflictPolicy.html
[4]: filter.html
[5]: https://s.apache.org/jcr-2.0-spec/11_Import.html#11.8.2%20Remove%20Existing%20Node
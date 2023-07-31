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

Metadata
===========

Every repository export/package carries metadata (within the folder `).

Category | Path | Description | Relevant for Import or Export? | Mandatory for Packages?
--- | --- | --- | ---
[Workspace Filters](filter.html) | `META-INF/vault/filter.xml` | Contains import rules and subtree paths to be imported/exported | both | yes
[Package Properties](properties.html) | `META-INF/vault/properties.xml` (and `META-INF/MANIFEST.MF`) | Main metadata of packages | import | yes
[FileVault FS Configuration](config.html#FileVault_Filesystem_Configuration) | `META-INF/vault/config.xml` | FS configuration affecting serialization of aggregates | export | no
[Settings](settings.html) | `META-INF/vault/settings.xml` | Allows to ignore certain file names | export | no
[Node Types and Namespaces](nodetypes.html) | `META-INF/vault/*.cnd` | Registration of JCR node types and namespaces | import | no
[Privileges ](privileges.html) | `META-INF/vault/privileges.xml` | Registration of custom JCR privileges | import | no
[Package Definition](packagedefinition.html) | `META-INF/vault/definition/.content.xml`| Additional metadata | import | no
[Install Hooks](installhooks.html) | `META-INF/vault/hooks/*.jar` | Allows post/pre-processing of packages | import | no
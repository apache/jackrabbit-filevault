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

Every repository export/package carries metadata (within the folder `META-INF/vault`).

Category | Location | Description | Relevant for Import or Export?
--- | --- | --- | ---
[Workspace Filters](filter.html) | `filter.xml` | Contains import rules and subtree paths to be imported/exported | both
[Package Properties](properties.html) | `properties.xml` | Main metadata of packages | import
[FileVault FS Configuration](config.html#FileVault_Filesystem_Configuration) | `config.xml` | FS configuration affecting serialization of aggregates | export  
[Settings](settings.html) | `settings.xml` | Allows to ignore certain file names | export
[Node Types and Namespaces](nodetypes.html) | `*.cnd` | Registration of JCR node types and namespaces | import
[Privileges ](privileges.html) | `privileges.xml` | Registration of custom JCR privileges | import
[Package Definition](packagedefinition.html) | `definition/.content.xml`| Additional metadata | import
[Install Hooks](installhooks.html) | `hooks/*.jar` | Allows post/pre-processing of packages | import
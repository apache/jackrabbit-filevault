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

Node Types and Namespaces
===========

Packages can register custom [JCR node types](https://s.apache.org/jcr-2.0-spec/8_Node_Type_Discovery.html) and [Namespaces](https://s.apache.org/jcr-2.0-spec/3_Repository_Model.html#3.2.1%20Namespaces) during import by carrying arbitrarily many `.cnd` files. All files names matching the regular expression pattern set in [package property](properties.html) `cndPattern` as well as all `*.cnd` files below `META-INF/vault` are considered. Details around the CND file format can be found at <https://jackrabbit.apache.org/jcr/node-type-notation.html>.
Node types and namespaces whose qualified name/prefix/uri is already registered are not touched. This also means that existing namespaces and node types are never modified but only once initially installed in case they are not yet there. Also remapping an existing namespace URI to another prefix is not supported.

Implicit Namespace Registration
-------

Apart from the explicit namespace registration via CND files mentioned above they are sometimes also registered implicitly.
[FileVault Document View (DocView) Format](./docview.html) enforces the declaration of each namespace with a mandatory prefix in the XML file. Those namespace mappings are globally registered lazily (i.e. when the according XML is processed) in case

1) the namespace URI is not registered (mapped) yet in the repository namespace registry
2) the prefix is not yet bound to another URI

In all other cases the namespace mapping is just [overwritten for the current session](https://s.apache.org/jcr-2.0-spec/3_Repository_Model.html#3.5.2%20Session-Local%20Mappings) (used for importing the docview XML).

Due to this implicit registration there is technically no need to register namespaces which are used outside the node types in CND files.

Namespace Prefixes
-------

As the [Standard Form of JCR Paths](https://s.apache.org/jcr-2.0-spec/3_Repository_Model.html#3.4.3.1%20Standard%20Form) only uses qualified names (i.e. leveraging prefixes instead of full URIs) it is important that the destination repository uses the same prefixes as are being used in the package.

Although you can redefine a namespace URI to be mapped from another prefix in the [FileVault Document View (DocView) Format](./docview.html) this will only be used during parsing that file (i.e. once during import) but is not persisted in the destination repository in case the namespace URI is already mapped to another prefix or the prefix is already bound to another URI. Particularly it won't affect:

1. property values containing a path in the JCR standard form (for properties of type `STRING`). [Property type `PATH`](https://s.apache.org/jcr-2.0-spec//3_Repository_Model.html#3.6.1.10%20PATH) on the other hand internally always stores the full namespace URL and the local name, therefore the qualified name is calculated for each conversion to string and takes into account the current prefix mapping.
2. the standard form of JCR paths containing namespaced items

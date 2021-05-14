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

Config
===========

<!-- MACRO{toc} -->

The FileVault Filesystem Configuration can be adjusted with a `META-INF/vault/config.xml` file in content packages or in the [Vault CLI](usage.html).

It allows to tweak aggregation settings of the [Vault FS](vaultfs.html). The default config is at <https://github.com/apache/jackrabbit-filevault/blob/trunk/vault-core/src/main/resources/org/apache/jackrabbit/vault/fs/config/defaultConfig-1.1.xml>.

The following XML elements below the root element `vaultfs` are considered. 

## Properties


To exclude binaries from exports/imports you can use the property

```xml
    <properties>
        <!-- configure binary-less serialization -->
        <useBinaryReferences>true</useBinaryReferences>
    </properties>
```

## Aggregates

Aggregates which influence the serialization during exports are configured in `aggregate` child elements having an optional `type` attribute and a `title`.

The following `types` are known: 

Type | Used Class | Description
--- | --- | ---
`file` | `FileAggregator` | Serializes `nt:file` or `nt:resource` nodes into simple files, potentially accompanied by a `.dir` folder containing a `.content.xml` with the metadata.
`filefolder` | `FileFolderAggregator` | Serializes all `nt:hierarchyNode` nodes that have or define a `jcr:content` child node and excludes child nodes that are nt:hierarchyNodes into directories.
`nodetype`| `NodeTypeAggregator`| Serializes `nt:nodeType` nodes into `*.xcnd` files
`full` | `FullCoverageAggregator` | Serializes full node structures (including all children) into a DocView file named `<nodename>.xml`
`generic` | `GenericAggregator` | the default if no `type` is set, serializes the node and its properties into a `.content.xml` DocView file and the binaries in separate files with extension `.binary`.

Every type except for the first can be restricted via `matches` and `contains` child elements. The former applies a given filter to the current item, while the latter must match ones of the children.
Both contain arbitrarily many `include` or `exclude` nodes. 

Each have either an element `class` for a fully qualified class name or consists of one of the following attributes (which implicitly sets a class)

Filter attribute | Values | Additional attributes | Used Filter Class | Description
--- | --- | --- | --- | ---
`nodeType` |  the node type | `respectSupertype` set to `true` means that also all subtypes of the given node type should be considered. | `NodeTypeItemFilter` | Only applies to the given node type.
`isNode` | Either `true` or `false` (the default) | - | `IsNodeFilter` | Only applies to nodes (and not to properties) or vice-versa.
`name` | the qualified name | - |`NameItemFilter` | Only applies to items having the given name
`isMandatory` | Either `true` or `false` (the default) | - | `IsMandatoryFilter` | Only applies to properties/nodes which are marked as (non-)mandatory in the node type definition depending on the given value.

## Handlers

Handlers which deserialize nodes from packages during import are registered in `handler` child elements having an optional `type` attribute.

The following `types` are known:

Type | Used Class | Description
--- | --- | ---
`file` | `FileArtifactHandler` | Deserializes `nt:file`s from generic or XML generic serializations
`folder` | `FolderArtifactHandler` | Deserializes folders
`nodetype` | `NodeTypeArtifactHandler` | Deserializes `nt:nodeType` nodes from `*.xcnd` files
`generic` | `GenericArtifactHandler` | Deserializes DocView artifacts, the default if no `type` is set


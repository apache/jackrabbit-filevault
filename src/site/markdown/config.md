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

Configuration
===========

<!-- MACRO{toc} -->

## FileVault Core Bundle

The configuration in this section affects the *FileVault Core Bundle* which is used for all client and server-side features of FileVault. It has a global effect (i.e. is not content-packages specific)

### System Properties

The following system properties or OSGi Framework properties can be used 

Property Name | Type | Default Value | Description | Related JIRA Ticket | Supported since
--- | --- | --- | --- | --- | ---
`vault.useNioArchive` | `Boolean` | `false` | If set to `true `uses an [Archive implementation](https://github.com/apache/jackrabbit-filevault/blob/master/vault-core/src/main/java/org/apache/jackrabbit/vault/fs/io/ZipNioArchive.java) based on the [Java NIO Zip File System Provider](https://docs.oracle.com/javase/8/docs/technotes/guides/io/fsp/zipfilesystemprovider.html) instead of the [default implementation](https://github.com/apache/jackrabbit-filevault/blob/master/vault-core/src/main/java/org/apache/jackrabbit/vault/fs/io/ZipArchive.java) which is based on [java.util.jar](https://docs.oracle.com/javase/8/docs/api/java/util/jar/package-summary.html). | [JCRVLT-533](https://issues.apache.org/jira/browse/JCRVLT-533) | 3.5.4
`vault.enableStackTraces` | `Boolean` | `false` | If set to `true` persists a stack trace for every opened Archive and logs it in case it was not properly closed. Should only be enabled on non-production environments as this has a negative performance impact. | [JCRVLT-591](https://issues.apache.org/jira/browse/JCRVLT-591) | 3.5.10


### OSGi Configuration

If running inside an OSGi container further aspects can be configured with the OSGi metatype for PID `org.apache.jackrabbit.vault.packaging.impl.PackagingImpl`.

## FileVault Filesystem Configuration

The FileVault Filesystem Configuration can be adjusted with a `META-INF/vault/config.xml` file in content packages or in the [Vault CLI](usage.html).

It allows to tweak aggregation settings of the [Vault FS](vaultfs.html). The default config is at <https://github.com/apache/jackrabbit-filevault/blob/trunk/vault-core/src/main/resources/org/apache/jackrabbit/vault/fs/config/defaultConfig-1.1.xml>. The alternative default config at <https://github.com/apache/jackrabbit-filevault/blob/trunk/vault-core/src/main/resources/org/apache/jackrabbit/vault/fs/config/defaultConfig-1.1-binaryless.xml> is automatically used once the package property `useBinaryReferences` is set to `true`.

The following XML elements below the root element `vaultfs` are considered. 

### Properties

To exclude binaries from exports you can use the property

```xml
    <properties>
        <!-- configure binary-less serialization -->
        <useBinaryReferences>true</useBinaryReferences>
    </properties>
```

Using this flag leads to binary references being included in the [FileVault DocView XMLs](docview.html).

### Aggregates

Aggregates which influence the serialization during *exports* are configured in `aggregate` child elements having an optional `type` attribute and a `title`.

The following `types` are known: 

Type | Used Class | Description
--- | --- | ---
`file` | `FileAggregator` | Serializes `nt:file` or `nt:resource` nodes into simple files, potentially accompanied by a `.dir` folder containing a `.content.xml` with the metadata.
`filefolder` | `FileFolderAggregator` | Serializes all `nt:hierarchyNode` nodes that have or define a `jcr:content` child node and excludes child nodes that are nt:hierarchyNodes into directories.
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

### Handlers (only used for Vault CLI)

Handlers which deserialize nodes from packages during import are registered in `handler` child elements having an optional `type` attribute.

The following `types` are known:

Type | Used Class | Description
--- | --- | ---
`file` | `FileArtifactHandler` | Deserializes `nt:file`s from generic or XML generic serializations
`folder` | `FolderArtifactHandler` | Deserializes folders
`generic` | `GenericArtifactHandler` | Deserializes DocView artifacts, the default if no `type` is set


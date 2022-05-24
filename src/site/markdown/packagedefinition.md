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

Package Definition
===========

Packages may *optionally* contain a serialized JCR node of type `vlt:PackageDefinition` at `META-INF/vault/definition/.content.xml`. Its format follows the [FileVault DocView serialization](docview.html). It is automatically deserialized to `/etc/packages/<group>/<name>-<version>.zip/jcr:content/vlt:definition` during package upload when the JCR based package manager is being used.

The node type `vlt:PackageDefinition` is defined as 

```
[vlt:PackageDefinition] > nt:unstructured
  orderable
  - artifactId (string)
  - jcr:created (date)
  - jcr:createdBy (string)
  - jcr:lastModified (date)
  - lastUnpackedBy (string)
  - jcr:description (string)
  - groupId (string)
  - lastUnpacked (date)
  - version (string)
  - jcr:lastModifiedBy (string)
  - dependencies (string) multiple
  + thumbnail (nt:base) = nt:unstructured
  + filter (nt:base) = nt:unstructured
```

Due to it extending `nt:unstructured` it allows arbitrary properties and child nodes in addition to the explicitly defined ones.

This node contains primarily the most important package properties and the package filter rules and in addition may carry additional *implementation specific properties*.
Most of the properties are automatically (over)written during package upload with values coming from the [package properties](properties.html) (details at [JcrPackageDefinitionImpl.unwrap(..)](https://github.com/apache/jackrabbit-filevault/blob/39b4463904719a423a3ebe000b049b0653557591/vault-core/src/main/java/org/apache/jackrabbit/vault/packaging/impl/JcrPackageDefinitionImpl.java#L227)).

The primary use case is easy access of the most important package meta data after upload to the JCR-based package manager (without doing extraction and parsing of the underlying archive). As the node may also contain other implementation-specific properties for some cases it may be useful to also include that serialized node in the package itself. 

As most properties are redundant and automatically (over)written during package upload with values coming from the [package properties](properties.html) or the [filter](filter.html) only non-redundant properties should be included in the serialized node inside the content package package itself (details at [JcrPackageDefinitionImpl.unwrap(..)](https://github.com/apache/jackrabbit-filevault/blob/39b4463904719a423a3ebe000b049b0653557591/vault-core/src/main/java/org/apache/jackrabbit/vault/packaging/impl/JcrPackageDefinitionImpl.java#L227)).

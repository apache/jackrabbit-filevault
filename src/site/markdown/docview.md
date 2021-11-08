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

FileVault Document View (DocView) Format
=================

<!-- MACRO{toc} -->

Overview
----------

FileVault uses a slightly different format than the Document View specified by [JCR 2.0][jcr-docview].
In general all nodes that cannot be serialized as plain directories or as plain files are 
serialized into DocView XML files. If the node can only be partially mapped to a directory or file,
it will be accompanied with a `.content.xml` containing the residual content. 

For example, a _full coverage_ content tree, starting at the node `example` will be serialized into
an `example.xml` file, using the (FileVault) DocView format.

For example, a _sling:Folder_ node, named `libs` will be serialized into a directory `libs` and a
`libs/.content.xml` file, using the (FileVault) DocView format.

Also see the [Vault FS article](vaultfs.html) about this.

Deviations from the JCR Document View
-----------------------------

### Root Element

The root element of the FileVault DocView is always `jcr:root` no matter of the node name it serializes.
Because the node name is implicitly given by either the file name or the directory name, it would be
redundant to repeat the node name in the document.

Example:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<jcr:root xmlns:sling="http://sling.apache.org/jcr/sling/1.0" xmlns:jcr="http://www.jcp.org/jcr/1.0"
    jcr:primaryType="sling:Folder"
    title="Libraries"/>
```

As by that the filename implicitly sets the root element name, its namespace is also supposed to be declared in the XML in case it is using a prefix. This is similar to all other elements in the XML.

### Empty Elements

The deserialization treats empty elements different than the default [JCR 2.0 DocView Import][import-docview], as
empty elements never create a new node in the repository but are merely used to define the child node 
sort order.
Nodes/properties below empty elements will never be removed during import ([JCRVLT-251](https://issues.apache.org/jira/browse/JCRVLT-251))

### Property Values

The probably biggest different to the [JCR 2.0 DocView][jcr-docview] is the handling of the property values.
All properties are serialized as XML attributes as in JCR, but their values have the property type 
encoded. The format of the attribute value is:

```
property-value := [ "{" property-type "}" ] ( value | "[" [ value { "," value } ] "]"
```

If no type is specified, it defaults to [STRING][pt-string].
As types all arguments accepted by [PropertyType.valueFromName(String)](https://s.apache.org/jcr-2.0-javadoc/javax/jcr/PropertyType.html#valueFromName(java.lang.String)) are valid.
This is all strings defined by the constants whose names start with `TYPENAME_` in [PropertyType](https://s.apache.org/jcr-2.0-javadoc/javax/jcr/PropertyType.html) and `BinaryRef` for binary reference values (see below).

Multi-value properties contain the values as comma-separated list enclosed by `[` and `]`. The special value `\0` must be used to for a singleton multi-value property containing only the empty string.

Examples:

| Type    | Value           | Serialized               |
|---------|-----------------|--------------------------|
| String  | "Hello, world!" | "Hello, world!"          |
| Long    | 42              | "{Long}42"               |
| Boolean | true            | "{Boolean}true"          |
| Double[]| {1.0, 2.5, 3.0} | "{Double}[1.0,2.5,3.0]" |

#### Binary Properties

In contrast to [JCR 2.0 DocView][jcr-docview] binary properties are not supported inline via base64 encoding. Instead either a [dedicated .binary file must be used](vaultfs.html#Binary_Properties) or a regular file aggregate which sets the `jcr:content/jcr:data` binary property implicitly.

Only for binary values which also implement [`org.apache.jackrabbit.api.ReferenceBinary`][ref-binary] the string identifier of the *binary reference* is optionally given directly inside in a FileVault DocView ([JCR-3534](https://issues.apache.org/jira/browse/JCR-3534)). This only happens in case the [package property](properties.html) `useBinaryReferences` is set to `true`. Package imports use this approach whenever a binary reference property in FileVault DocView XML is found (with `property-type` = `BinaryRef`). This only works if the source and the destination repository of the package share the same data store.

Empty binary attributes (with values `"{Binary}"`) will always leave the according property in the repository untouched during import (in case such a property already exists). Every other value for binary properties leads to an exception.

### Escaping

The raw attribute value is escaped in order to preserve the special semantics:

| Character | Escape Sequence | Comment |
|-----------|-----------------|---------|
| `\`       | `\\`            |         |
| `,`       | `\,`           | Only necessary for multi-value properties |
| `[`       | `\[`           | Only necessary at the start of single-value properties |
| `{`       | `\{`           | Only necessary at the start of single-value properties |
| invalid xml character| `\uXXXX` | Unicode code points |
| `<empty string>` | `\0` | Only necessary within singleton multi-value properties to indicate an empty string item ([JCRVLT-4](https://issues.apache.org/jira/browse/JCRVLT-4)). |

Please note, that this escaping only concerns the raw attribute value. If the value contains
characters that cannot be used in XML attributes, like quotes `"`, the according [XML entities](https://www.w3.org/TR/xml/#dt-escape) need to be used.


[jcr-docview]: https://s.apache.org/jcr-2.0-spec/7_Export.html#7.3%20Document%20View
[import-docview]: https://s.apache.org/jcr-2.0-spec/11_Import.html#11.1%20Importing%20Document%20View
[pt-string]: https://s.apache.org/jcr-2.0-javadoc/javax/jcr/PropertyType.html#STRING
[ref-binary]: https://jackrabbit.apache.org/oak/docs/apidocs/org/apache/jackrabbit/api/ReferenceBinary.html
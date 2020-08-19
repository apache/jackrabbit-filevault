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

FileVault DocView
=================

<!-- MACRO{toc} -->

Overview
----------

FileVault uses a slightly different version of the Document View specified by [JCR 2.0][jcr-docview].
In general all nodes that cannot be serialized as plain directories or as plain files are 
serialized into DocView XML files. If the node can only be partially mapped to a directory or file,
it will be accompanied with a `.content.xml` containing the residual content. 

For example, a _full coverage_ content tree, starting at the node `example` will be serialized into
a `example.xml` file, using the (filevault) DocView format.

For example, a _sling:Folder_ node, named `libs` will be serialized into a directory `libs` and a
`libs/.content.xml` file, using the (filevault) DocView format.

Also see the [vault-fs](vaultfs.html) article about this.

Deviations to the JCR DocView
-----------------------------

### Root Element

The root element of the filevault DocView is always `jcr:root` no matter of the node name it serializes.
Because the the node name is implicitly given by either the filename or the directory name, it would be
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

The deserialization treats empty elements different as the default [JCR 2.0 Import][import-docview], as
empty elements never create a new node in the repository but are merely used to define the childnode 
sort order.
Nodes/properties below empty elements will never be removed during import ([JCRVLT-251](https://issues.apache.org/jira/browse/JCRVLT-251))

### Property Values

The probably biggest different to the [JCR 2.0][jcr-docview] is the handling of the property values.
All properties are serialized as XML attributes as in JCR, but their values have the property type 
encoded. The format of the attribute value is:

```
property-value := [ "{" property-type "}" ] ( value | "[" [ value { "," value } ] "]"
```

If no type is specified, it defaults to [STRING][pt-string].
As types all arguments accepted by [PropertyType.valueFromName(String)](https://docs.adobe.com/docs/en/spec/jsr170/javadocs/jcr-2.0/javax/jcr/PropertyType.html#valueFromName(java.lang.String)) are valid.
This is all strings defined by the constants whose names start with `TYPENAME_` in [PropertyType](https://docs.adobe.com/docs/en/spec/jsr170/javadocs/jcr-2.0/javax/jcr/PropertyType.html).

Examples:

| Type    | Value           | Serialized               |
|---------|-----------------|--------------------------|
| String  | "Hello, world!" | "Hello, world!"          |
| Long    | 42              | "{Long}42"               |
| Boolean | true            | "{Boolean}true"          |
| Double[]| {1.0, 2.5, 3.0} | "{Double}[1.0,2.5,3.0]" |

### Escaping

The raw attribute value is escaped in order to preserve the special semantics:

| Character | Escape Sequence | Comment |
|-----------|-----------------|---------|
| `\`       | `\\`            |         |
| `,`       | `\,`           | Only for multi value properties |
| `[`       | `\[`           | Only at the start of single value properties |
| `{`       | `\{`           | Only at the start of single value properties |
| invalid xml | `\uXXXX` | |

Please note, that this escaping only concerns the raw attribute value. If the value contains
characters that cannot be used in XML attributes, like quotes `"`, they will further be escaped
by the XML formatter.


[jcr-docview]: https://docs.adobe.com/content/docs/en/spec/jcr/2.0/7_Export.html#7.3%20Document%20View
[import-docview]: https://docs.adobe.com/content/docs/en/spec/jcr/2.0/11_Import.html#11.1%20Importing%20Document%20View
[pt-string]: https://docs.adobe.com/docs/en/spec/jsr170/javadocs/jcr-2.0/javax/jcr/PropertyType.html#STRING
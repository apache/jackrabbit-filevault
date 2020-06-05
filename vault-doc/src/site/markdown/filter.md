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

Workspace Filter
================
One of the most important meta files of a vault checkout or a content package is the `filter.xml` which is present in
the `META-INF/vault` directory. The `filter.xml` is used to load and initialize
the [WorkspaceFilter][api.WorkspaceFilter]. The workspace filter defines what parts of the JCR repository are
imported or exported during the respective operations through `vlt` or package management.

<!-- MACRO{toc} -->

General Structure
-----------------
The `filter.xml` consists of a set of `filter` elements, each with a mandatory `root` attribute and an optional list of
`include` and `exclude` child elements.

Example:

    <workspaceFilter version="1.0">
        <filter root="/apps/project1" />
        <filter root="/etc/project1">
            <exclude pattern=".*\.gif" />
            <include pattern="/etc/project1/static(/.*)?" />
        </filter>
        <filter root="/etc/map" mode="merge" />
    </workspaceFilter>

### Filter Elements
The filter elements are independent of each other and define include and exclude patters for subtrees. The root of a
subtree is defined by the `root` attribute, which must be an absolute path.
The filter element can have an optional `mode` attribute which specified the [import mode][api.ImportMode] used when
importing content. the following values are possible:

1. `replace` : This is the normal behavior. Existing content is replaced completely by the imported content, i.e. is overridden or deleted accordingly.
1. `merge` : Existing content is not modified, i.e. only new content is added and none is deleted or modified.
1. `update` : Existing content is updated, new content is added and none is deleted.

For a more detailed description of the import mode, see [here](importmode.html).

In addition it is possible to influence the auto-detection of the package type (if not explicitly specified in the `properties.xml`) with the attribute `type`. The only supported value as of now is `cleanup` which means that the filter rule is ignored for the auto-detection of  the package type ([JCRVLT-220](https://issues.apache.org/jira/browse/JCRVLT-220))


### Include and Exclude Elements
The include and exclude elements can be added as optional children to the `filter` element to allow more fine grained filtering of the subtree during import and export. They have a
mandatory `pattern` attribute which has the format of a [regexp][api.Pattern]. The regexp is matched against the full _path_ of the
respective or potential JCR node, so it either must start with `/` (absolute regex) or a wildcard (relative regex).

#### Order
The order of the include and exclude elements is important. The paths are tested in a sequential order against all
patterns and the type of the **last matching** element determines if the path is included or not. One caveat is, that
the type of the first pattern defines the default behavior, so that the filter is more natural to write. If the first
pattern is include, then the default is exclude and vice versa.

The following example _only_ includes the nodes in `/tmp` that end with `.gif`.

    <filter root="/tmp">
        <include pattern=".*\.gif"/>
    </filter>

The following example includes _all_ nodes in `/tmp` except those that end with `.gif`.

    <filter root="/tmp">
        <exclude pattern=".*\.gif"/>
    </filter>

#### Property Filtering

Since FileVault 3.1.28 ([JCRVLT-120](https://issues.apache.org/jira/browse/JCRVLT-120)) it is not only possible to filter on node level but also only include/exclude certain properties below a certain node by setting the attribute `matchProperties` on the `exlude`/`include` element to `true`. 

	<filter root="/tmp">
        <exclude pattern="/tmp/property1" matchProperties="true"/>
    </filter>

Then the `pattern` is matched against property paths instead of node paths.
If the attribute `matchProperties` is not set all properties below the given node paths are included/excluded. Otherwise the excluded properties are not contained in the exported package and during import not touched in the repository.


Usage for Export
----------------
When exporting content into the filesystem or a content package, the workspace filter defines which nodes are
serialized. It is important to know, that only the nodes that match the filter are actually traversed, which can lead to unexpected results.

for example:

    <filter root="/tmp">
        <include pattern="/tmp/a(/.*)?"/>
        <include pattern="/tmp/b/c(/.*)?"/>
    </filter>

Will include the `/tmp/a` subtree, but not the `/tmp/b/c` subtree, since `/tmp/b` does not match the filter and is
therefor not traversed.

There is one exception, if **all** the pattern are relative (i.e. don't start with a slash), then the algorithm is:

1. start at the filter root
2. traverse **all** child nodes recursively
3. if the path of the child node matches the regexp, include it in the export

Usage for Import/Installation
-------------------
When importing (i.e. installing) content packages into a repository  the workspace filter defines which nodes are deserialized and overwritten in the repository.
Nodes/Properties being covered by some filter rules but not contained in the to be imported content are **removed** from the repository.

The exact rules are outlined below

Item covered by filter rule | Item contained in the Content Package | Item contained in the Repository (prior to Import/Installation) | State of Item in Repository after Import/Installation
--- | --- | --- | ---
no | yes | yes | not touched
no | no | yes | not touched
no | yes | no | *nodes which are ancestors of covered rules*: deserialized from content package (for backwards compatibility reasons), *nodes which are not ancestors of covered rules*: not touched. One should not rely on this behaviour, i.e. all items in the content package should always be covered by some filter rule to make the behaviour more explicit.
no | no | no | not existing (not touched)
yes | yes | yes | overwritten
yes | no | yes | removed
yes | yes | no | deserialized from content package
yes | no | no | not existing

### Uncovered ancestor nodes

All *uncovered* ancestor nodes are either

1. created with the node type and properties given in the package (in case the node type *is* given with a `.content.xml` at the right location and the node does not yet exist in the repo)
1. since version 3.4.4 ([JCRVLT-417](https://issues.apache.org/jira/browse/JCRVLT-417)) created with the ancestor node type's default child type or if that is not set or prior to version 3.4.4 created with node type `nt:folder` (in case the the node type is *not* given with a `.content.xml` at the right location and the node does not yet exist in the repo) or
1. not touched at all (in case they are already existing in the repo, no matter which node type is given with a `.content.xml` at the according location) 

### Example

Content Package Filter

```
<filter root="/tmp">
    <include pattern="/tmp/a(/.*)?"/>
    <include pattern="/tmp/b(/.*)?/>
    <exclude pattern="/tmp/b/property1" matchProperties="true"/>
    <include pattern="/tmp/c(/.*)?"/>
</filter>
```

Content Package Serialized Content

```
+ /jcr_root/
  + tmp/
  	 + a/
  	   - property1="new"
  	 + b/
  	   - property1="new"
  	   - property2="new"
```

#### Repository State Before Installation/Import
```
+ /tmp/
  + b/
    - property1="old"
    - property2="old"
  + c/
    - property1="old"
```

#### Repository State After Installation/Import
```
+ /tmp/
  + a/
    - property1="new"
  + b/
    - property1="old"
    - property2="new"
```

[api.WorkspaceFilter]: apidocs/org/apache/jackrabbit/vault/fs/api/WorkspaceFilter.html
[api.ImportMode]: apidocs/org/apache/jackrabbit/vault/fs/api/ImportMode.html
[api.Pattern]: https://docs.oracle.com/javase/7/docs/api/java/util/regex/Pattern.html
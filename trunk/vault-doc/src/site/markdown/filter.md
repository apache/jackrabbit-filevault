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

General Structure
-----------------
The `filter.xml` consists of a set of `filter` elements, each with a `root` attribute and an optional list of
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

### filter elements
The filter elements are independent of each other and define include and exclude patters for subtrees. The root of a
subtree is defined by the `root` attribute, which must be an absolute path.

The filter element can have an optional `mode` attribute which specified the [import mode][api.ImportMode] used when
importing content. the following values are possible:

"replace"
: This is the normal behavior. Existing content is replaced completely by the imported content, i.e. is overridden or
  deleted accordingly.

"merge"
: Existing content is not modified, i.e. only new content is added and none is deleted or modified.

"update"
: Existing content is updated, new content is added and none is deleted.

For a more detailed description of the import mode, see [here](importmode.html)

### include and exclude elements
the include and exclude elements allow more fine grained filtering of the subtree during import and export. they have a
mandatory `pattern` attribute which has the format of a regexp. the regexp is matched against the _path_ of the
respective or potential JCR node, thus can be relative or absolute.

#### order
the order of the include and exclude elements is important. the paths are tested in a sequential order against all
patterns and the type of the last matching element determines if the path is included or not. One caveat is, that
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

Usage for export
----------------
When exporting content into the filesystem or a content package, the workspace filter defines which nodes are
serialized. It is important to know, that only the nodes that match the filter are actually traversed, which can lead
to unexpected results.

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


<!-- references -->
[api.WorkspaceFilter]: apidocs/org/apache/jackrabbit/vault/fs/api/WorkspaceFilter.html
[api.ImportMode]: apidocs/org/apache/jackrabbit/vault/fs/api/ImportMode.html
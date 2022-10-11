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

Authorizables
=================

<!-- MACRO{toc} -->

Overview
----------

JCR 2.0 does not actually define how authorizables are persisted.
However, both Jackrabbit and Oak store them inside the JCR. The following formats are supported by FileVault with these JCR implementations.

Users/Groups
---------------------

Both JCR users and groups are stored in a node of type `rep:User` and `rep:Group` respectively. The detailed format is outlined at <https://jackrabbit.apache.org/oak/docs/security/user/default.html#representation-in-the-repository>.
Those nodes are exported in [enhanced FileVault DocView format](docview.html).
During import the DocView XML is converted to [System View][jcr-sysview] and then imported via regular JCR means.

[jcr-sysview]: https://s.apache.org/jcr-2.0-spec/7_Export.html#7.2%20System%20View
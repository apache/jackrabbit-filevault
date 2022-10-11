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

Access Control Lists
=================

<!-- MACRO{toc} -->

Overview
----------

JCR 2.0 manages the authorization in form of [Access Control Lists (ACLs)][jcr-acls]. Similar as for [authorizables](authorizables.html) the specification does not actually define how ACLs are persisted but only defines an [API][jcr-acl-api] on top of it.
However, both Jackrabbit and Oak store authorization information inside the JCR. The following formats are supported by FileVault with these JCR implementations. For all of them the export uses [enhanced FileVault DocView format](docview.html) based on the internal repository representation, while the import uses JCR ACL API to manually import them based on the DocView format.

Standard ACLs
---------------------

The standard ACLs are stored in a node named `rep:policy` below the node to which they apply. Alternatively, they are stored in a node `repo:policy` on the top level for repository level policies. The detailed format is outlined at <https://jackrabbit.apache.org/oak/docs/security/accesscontrol/default.html#representation-in-the-repository>. They are supported by both Oak and Jackrabbit.

Closed User Groups (CUGs)
-------------------

The CUG ACLs are stored in a node named `cug:policy` below the node to which they apply. They are only supported in Oak.
The detailed format is outlined at <https://jackrabbit.apache.org/oak/docs/security/authorization/cug.html#representation-in-the-repository>.

Principal Based ACLs
--------------------

The principal based ACLs are stored in a node named `rep:principalPolicy` separate from the node to which they apply. The exact location depends on the implementation. They are only supported in Oak.
The detailed format is outlined at <https://jackrabbit.apache.org/oak/docs/security/authorization/principalbased.html#representation-in-the-repository>.


[jcr-acls]: https://s.apache.org/jcr-2.0-spec/16_Access_Control_Management.html
[jcr-acl-api]: https://s.apache.org/jcr-2.0-spec/16_Access_Control_Management.html#16.5%20Access%20Control%20Lists

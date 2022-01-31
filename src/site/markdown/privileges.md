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

Privileges
===========

Packages can register custom [JCR privileges](https://s.apache.org/jcr-2.0-spec/16_Access_Control_Management.html#16.2%20Privilege%20Discovery) during import by carrying a `META-INF/vault/privileges.xml` file.  

Its DTD is defined as

```xml
<!DOCTYPE privileges [
<!ELEMENT privileges (privilege)+>
<!ELEMENT privilege (contains)+>
<!ATTLIST privilege abstract (true|false) false>
<!ATTLIST privilege name NMTOKEN #REQUIRED>
<!ELEMENT contains EMPTY>
<!ATTLIST contains name NMTOKEN #REQUIRED>
]>
```

The implementation is leveraging the [Jackrabbit API PrivilegeManager](https://www.javadoc.io/doc/org.apache.jackrabbit/oak-jackrabbit-api/latest/org/apache/jackrabbit/api/security/authorization/PrivilegeManager.html).

The privilege **name** must be given in [qualified form](https://s.apache.org/jcr-2.0-spec/3_Repository_Model.html#3.2.5.2%20Qualified%20Form). Every element may carry [XML namespace declarations](https://www.w3.org/TR/2006/REC-xml-names11-20060816/#ns-decl) which are automatically registered in the destination repository during import as well. This should be used when the privilege is using a custom namespace URL.

Aggregate privileges can be registered with the additional element `contains` which should reference an existing privilege name.

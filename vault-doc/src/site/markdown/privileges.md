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

Packages can register custom [JCR privileges](https://docs.adobe.com/docs/en/spec/jcr/2.0/16_Access_Control_Management.html#16.2%20Privilege%20Discovery) during import by carrying a `META-INF/vault/privileges.xml` file. 

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

Aggregate privileges can be registered with the additional element `contains` which should reference an existing privilege name.

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

Config
===========

The FileVault Filesystem Configuration can be adjusted with a `META-INF/vault/config.xml` file. 

It allows to tweak aggregation settings of the [Vault FS](vaultfs.html). The default config is at <https://github.com/apache/jackrabbit-filevault/blob/trunk/vault-core/src/main/resources/org/apache/jackrabbit/vault/fs/config/defaultConfig-1.1.xml>.

To exclude binaries from exports/imports you can use the setting

```xml
    <properties>
        <!-- configure binary-less serialization -->
        <useBinaryReferences>true</useBinaryReferences>
    </properties>
```

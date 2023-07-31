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

Settings
===========

The FileVault settings can be adjusted with a `META-INF/vault/settings.xml` file. 
The settings allow to ignore certain files/directories during import operations.
An example settings file looks like this:

```xml
<vault>
  <ignore name=".DS_Store"/>
  <ignore name=".svn"/>"
</vault>
```

The `name` attribute is compared with the file/directory name (not the full file path) and is not parsed as regular expression!
Both `.vlt` directories and files having a name starting with `.vlt-` are always implicitly ignored and don't need to be added.

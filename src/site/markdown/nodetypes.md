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

Node Types
===========

Packages can register custom [JCR node types](https://docs.adobe.com/docs/en/spec/jcr/2.0/8_Node_Type_Discovery.html) during import by carrying arbitrarily many `.cnd` files. All files names matching the regular expression pattern set in [package property](properties.html) `cndPattern` as well as all `*.cnd` files below `META-INF/vault` are considered. Details around the CND file format can be found at <https://jackrabbit.apache.org/jcr/node-type-notation.html>.

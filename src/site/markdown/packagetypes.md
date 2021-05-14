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

Package Types
================

Overview
------
Package types limit what repository locations a content package may contain ([JCRVLT-170](https://issues.apache.org/jira/browse/JCRVLT-170)). In addition they may impose other limitations in terms of install hooks, package dependencies and allowed filter rules.
The package type classification helps to clarify the purpose and also the deployment of the package (e.g. when using the [Oak Composite Node Store](https://jackrabbit.apache.org/oak/docs/nodestore/compositens.html), which mounts several repository folders in read-only mode).
The package type is set in the [package properties](properties.html).

Package types are not enforced in any way (yet) when importing packages. Currently they are only enforced by the [jackrabbit-packagetype validator][validators] and to a certain degree by third party tools like [Apache Sling Content-Package to Feature Model Converter](https://github.com/apache/sling-org-apache-sling-feature-cpconverter).

Restrictions
--------

Package Type | Allowed repository locations | Allows OSGi bundles/configurations/sub packages | Allows package dependencies | Allows [install hooks](installhooks.html)
--- | --- | --- | --- | ---
Application | `/apps`, `/libs` | no | yes | no
Content | everything except `/apps`, `/libs` | partially (only `content` sub packages) | yes | yes
Container | no regular content | yes | no | yes
Mixed | all | yes | yes | yes

Application Package
----------

This package type is supposed to be used for everything which makes up the "code" part of the application which is used directly from the repository, i.e. (non-bundled) scripts, resource types, ...
To ease combination of multiple application packages they should contain only disjunct sub trees (i.e. no include/exclude filter patterns). As this [cannot always be achieved in reality](https://issues.apache.org/jira/browse/JCRVLT-403) this rule can be relaxed with the [validator setting `allowComplexFilterRulesInApplicationPackages`][validators].
In addition they must not contain [install hooks](installhooks.html).

Content Package 
----------

This package must not contain any nodes below `/apps` or `/libs`. Sub packages of type `content` are allowed (but only below `/etc/packages`). All other sub package types are not valid in this type.


Container Package
---------

Container packages act as deployment vehicle and don't contain regular nodes. Only OSGi bundles, configuration and sub packages (for use with the [OSGi Installer](https://sling.apache.org/documentation/bundles/jcr-installer-provider.html)) are allowed. In addition also sub packages below `/etc/packages/...` are supported.

Containers may be nested which means they may contain itself packages of type `container`.


Mixed Package
-------

This legacy package type imposes no restrictions and is only defined for backwards-compatibility reasons. For new packages rather one of the other types should be used as mixed packages may not deployable in certain scenarios.


[validators]: validation.html#Standard_Validators
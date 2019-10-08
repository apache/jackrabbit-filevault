
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
# Validation

## Overview

The artifact `org.apache.jackrabbit.vault:vault-validation` provides both an API for validating FileVault packages as well as an SPI for implementing validators. In addition this JAR contains useful validators.

This validation framework is supposed to be used as 

1. dependency for custom validators (SPI)
2. library for build tools which want to call validation on FileVault packages (API and Implementation)


## Validators

### Settings

It is possible to adjust every validator registered in the system (both default and external validators) via settings. Settings have a common section (which apply to every validator) but also support validator specific options.

Element | Description | Default Value
--- | --- | ---
`defaultSeverity` | Each validation message has a severity. The default validation message severity of each validator can be influenced with this parameter. If a validator emits different types of validation messages the other types can be influenced via `options`. | `error`
`isDisabled` | A boolean flag defining whether validator is disabled or not. | `false`
`options` | A map (i.e. keys and values) of validator specific options. The supported options for the validators are outlined below. | empty

Each validator settings are set for a specific validator id.

### Standard Validators

ID  |  Description | Options
--- | --- | ---
`jackrabbit-filter` |  Checks for validity of the [filter.xml](./filter.html) (according to a predefined  XML schema). In addition checks that every [docview xml node](./docview.html) is contained in the filter. It also makes sure that all filter root's ancestors are either known/valid roots or are contained in the package dependencies. For ancestor nodes which are not covered by a filter at least a `warn` is emitted. Also it makes sure that `pattern` values for includes/excludes as well as `root` values for each filter entry are valid. Orphaned filter rules (i.e. ones not being necessary) lead to validation issues as well. | *severityForUncoveredAncestorNodes*: severity of validation messages for uncovered ancestor nodes.<br/>*severityForUncoveredFilterRootAncestors*: severity of validation messages for uncovered filter root ancestors. Only relevant for package type != application (default = warn)<br/>*severityForOrphanedFilterRules*: severity of validation messages for orphaned filter rules (default = info)<br/>*validRoots*: comma-separated list of valid roots (default = `"/,/libs,/apps,/etc,/var,/tmp,/content"`)
`jackrabbit-properties ` | Checks for validity of the  [properties.xml](./properties.html) | none
`jackrabbit-dependencies` | Checks for overlapping filter roots of the referenced package dependencies as well as for valid package dependency references (i.e. references which can be resolved). | *severityForUnresolvedDependencies*: severity of validation messages for unresolved dependencies (default = warn)
`jackrabbit-docviewparser` | Checks if all docview files in the package are compliant with the [(extended) Document View Format](docview.html). This involves checking for XML validity as well as checking for correct property types. | none
`jackrabbit-emptyelements` | Check for empty elements within DocView files (used for ordering purposes, compare with  [(extended) Document View Format](docview.html)) which are included in the filter with import=replace as those are actually not replaced! | none
`jackrabbit-mergelimitations` | Checks for the limitation of import mode=merge outlined at [JCRVLT-255][jcrvlt-255]. | none
`jackrabbit-oakindex` |  Checks if the package (potentially) modifies/creates an OakIndexDefinition. This is done by evaluating both the filter.xml for potential matches as well as the actual content for nodes with jcr:primaryType  `oak:indexDefinition`. | none
`jackrabbit-packagetype` | Checks if the package type is correctly set for this package, i.e. is compliant with all rules outlined at [JCRVLT-170][jcrvlt-170]. | none
`jackrabbit-primarynodetype` | Checks if all non empty elements within [DocView files](docview.html) have the mandatory property `jcr:primaryType` set. | none


### Custom Validators

The SPI for implementing custom validators is provided in [this package][javadoc.spi].
The validators are registered via a `ValidatorFactory` which is supposed to be registered via the [ServiceLoader][javadoc.serviceloader].

The SPI is exported from the artifact `org.apache.jackrabbit.vault:vault-validation` as well.

The validator which is returned via the `ValidatorFactory` is one of the following types below package `org.apache.jackrabbit.filevault.maven.packaging.validator`

Validator Class | Description | `META-INF` or `jcr_root` | Called from another validator
--- | --- | --- | ---
`DocumentViewXmlValidator` | Called for each node serialized into a DocView element | `jcr_root` | no
`NodePathValidator` | Called for each node path contained in the package (even for ones not listed in the filter.xml) | `jcr_root` | no
`JcrPathValidator` | Called for each file path contained in the package | `jcr_root` | no
`GenericJcrDataValidator` | Called for all serialized nodes which are *not* DocViewXml | `jcr_root` | no
`FilterValidator`| Called for the `vault/filter.xml` file | `META-INF` | yes (`jackrabbit-filter`)
`PropertiesValidator` | Called for the `vault/properties.xml` file | `META-INF` | yes (`jackrabbit-properties`)
`GenericMetaInfDataValidator` | Called for all META-INF files (even `vault/filter.xml` nor `vault/properties.xml`). In general prefer the higher level validators (i.e. `FilterValidator` or `PropertiesValidator` if possible) | `META-INF` | no
`MetaInfFilePathValidator` | Called for each file path contained in the package below META-INF | `META-INF` | no

## Validation API

The API for calling validation on specific files is provided in [this package][javadoc.api].

First you need one instance of `ValidationExecutorFactory`.
For each new `ValidationContext` (i.e. new package context) you create a new `ValidationExecutor` via `ValidationExecutorFactory.createValidationExecutor(...)`.
For each file you then call either

- `ValidationExecutor.validateJcrRoot(...)` for input streams referring to files which are supposed to end up in the repository or 
- `ValidationExecutor.validateMetaInf(...)` for input streams representing metaInf data of the FileVault package

The Validation API is currently used by the [FileVault Package Maven Plugin][filevault.maven].

[javadoc.spi]: apidocs/org/apache/jackrabbit/vault/validation/spi/package-summary.html
[javadoc.api]: apidocs/org/apache/jackrabbit/vault/validation/package-summary.html
[javadoc.serviceloader]: https://docs.oracle.com/javase/8/docs/api/java/util/ServiceLoader.html
[filevault.maven]: http://jackrabbit.apache.org/filevault-package-maven-plugin/
[jcrvlt-255]: https://issues.apache.org/jira/browse/JCRVLT-255
[jcrvlt-170]: https://issues.apache.org/jira/browse/JCRVLT-170
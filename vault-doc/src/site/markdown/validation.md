
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

<!-- MACRO{toc} -->

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
`jackrabbit-filter` |  Checks for validity of the [filter.xml](./filter.html) (according to a predefined  XML schema). In addition checks that every [docview xml node](./docview.html) is contained in the filter. It also makes sure that all filter root's ancestors are either known/valid roots or are contained in the package dependencies. For ancestor nodes which are not covered by a filter at least a `warn` is emitted. Also it makes sure that `pattern` values for includes/excludes as well as `root` values for each filter entry are valid. Orphaned filter rules (i.e. ones not being necessary) lead to validation issues as well. | *severityForUncoveredAncestorNodes*: severity of validation messages for uncovered ancestor nodes.<br/>*severityForUncoveredFilterRootAncestors*: severity of validation messages for uncovered filter root ancestors. (default = `error`, for package type=application or `warn` for all other package types)<br/>*severityForOrphanedFilterRules*: severity of validation messages for orphaned filter rules (default = `info`)<br/>*validRoots*: comma-separated list of valid roots (default = `"/,/libs,/apps,/etc,/var,/tmp,/content"`)
`jackrabbit-properties ` | Checks for validity of the  [properties.xml](./properties.html) | none
`jackrabbit-dependencies` | Checks for overlapping filter roots of the referenced package dependencies as well as for valid package dependency references (i.e. references which can be resolved). | *severityForUnresolvedDependencies*: severity of validation messages for unresolved dependencies (default = `warn`)
`jackrabbit-docviewparser` | Checks if all docview files in the package are compliant with the [(extended) Document View Format](docview.html). This involves checking for XML validity as well as checking for correct property types. | none
`jackrabbit-emptyelements` | Check for empty elements within DocView files (used for ordering purposes, compare with  [(extended) Document View Format](docview.html)) which are included in the filter with import=replace as those are actually not replaced! | none
`jackrabbit-mergelimitations` | Checks for the limitation of import mode=merge outlined at [JCRVLT-255][jcrvlt-255]. | none
`jackrabbit-oakindex` |  Checks if the package (potentially) modifies/creates an OakIndexDefinition. This is done by evaluating both the filter.xml for potential matches as well as the actual content for nodes with jcr:primaryType  `oak:indexDefinition`. | none
`jackrabbit-packagetype` | Checks if the package type is correctly set for this package, i.e. is compliant with all rules outlined at [Package Types](packagetypes.html). | *jcrInstallerNodePathRegex*: the regex of the node paths which all OSGi bundles and configurations within packages must match ([JCR Installer](https://sling.apache.org/documentation/bundles/jcr-installer-provider.html)) (default=`/([^/]*/){0,4}?(install|config)(\\.[^/]*)*/(\\d{1,3}/)?.+?\\.`).<br/>*additionalJcrInstallerFileNodePathRegex*: the regex of all file node paths which all OSGi bundles and configurations within packages must match. This must match in addition to the regex from `jcrInstallerNodePathRegex`  (default=`.+?\\.(jar|config|cfg|cfg\\.json)`).<br/>*legacyTypeSeverity*: the severity of the validation message for package type `mixed` (default = `warn`).<br/>*noTypeSeverity*: the severity of the validation message when package type is not set at all (default = `warn`).<br/>*prohibitMutableContent*: boolean flag determining whether package type `content` or `mixed` (mutable content) leads to a validation message with severity error (default = `false`). Useful when used with [Oak Composite NodeStore](https://jackrabbit.apache.org/oak/docs/nodestore/compositens.html).<br/>*prohibitImmutableContent*: boolean flag determining whether package type `app`, `container` or `mixed` (immutable content) leads to a validation message with severity error (default = `false`). Useful when used with [Oak Composite NodeStore](https://jackrabbit.apache.org/oak/docs/nodestore/compositens.html).<br/>*allowComplexFilterRulesInApplicationPackages*: boolean flag determining whether complex rules (containing includes/excludes) are allowed in application content packages (default = `false`).<br/>*allowInstallHooksInApplicationPackages*: boolean flag determining whether [install hooks](installhooks.html) are allowed in application content packages (default = `false`).<br/>*immutableRootNodeNames*: comma-separated list of immutable root node names (default = `"apps,libs"`)
`jackrabbit-nodetypes` | Checks if all non empty elements within [DocView files](docview.html) have the mandatory property `jcr:primaryType` set and follow the [node type definition of their given type](https://jackrabbit.apache.org/jcr/node-types.html). | *cnds*: A URI pointing to one or multiple [CNDs](https://jackrabbit.apache.org/jcr/node-type-notation.html) (separated by `,`) which define the additional namespaces and nodetypes used apart from the [default ones defined in JCR 2.0](https://docs.adobe.com/docs/en/spec/jcr/2.0/3_Repository_Model.html#3.7.11%20Standard%20Application%20Node%20Types). If a URI is pointing to a JAR, the validator will leverage all the nodetypes being mentioned in the [`Sling-Nodetypes` manifest header](https://sling.apache.org/documentation/bundles/content-loading-jcr-contentloader.html#declared-node-type-registration). Apart from the [standard protocols](https://docs.oracle.com/javase/7/docs/api/java/net/URL.html#URL(java.lang.String,%20java.lang.String,%20int,%20java.lang.String)) the scheme `tccl` can be used to reference names from the [Thread's context class loader](https://docs.oracle.com/javase/7/docs/api/java/lang/Thread.html#getContextClassLoader()). In the Maven plugin context this is the [plugin classloader](http://maven.apache.org/guides/mini/guide-maven-classloading.html?ref=driverlayer.com/web#3-plugin-classloaders).<br/>*defaultNodeType*: the node type in expanded or qualified form which is used for unknown ancestor nodes which are not given otherwise (default = `nt:folder`). *Note* **Using the default is pretty conservative but the safest approach. It may lead to a lot of issues as `nt:folder` is heavily restricted. In general you cannot know with which type the parent node already exists in the resource and FileVault itself for a long time created `nt:folder` nodes as [intermediates](filter.html#Uncovered_ancestor_nodes) so this is the safest option. If you are sure that the intermediate node types are of the correct type, you should uise a type with no restrictions (`nt:unstructured`)**.<br/>*severityForUnknownNodetypes*: The severity of issues being emitted due to an unknown primary/mixin type set on a node (default = `WARN`).<br/>*validNameSpaces*: Configure list of namespaces that are known to be valid. Syntax: `prefix1=http://uri1,prefix2=http://uri2,...`.
`jackrabbit-accesscontrol` | Checks that [access control list nodes (primary type `rep:ACL`, `rep:CugPolicy` and `rep:PrincipalPolicy`)](https://jackrabbit.apache.org/oak/docs/security/accesscontrol/default.html#Representation_in_the_Repository) are only used when the [package property's](./properties.html) `acHandling` is set to something but `ignore` or `clear`. | none

### Custom Validators

The SPI for implementing custom validators is provided in [this package][javadoc.spi].
The validators are registered via a `ValidatorFactory` which is supposed to be registered via the [ServiceLoader][javadoc.serviceloader].

The SPI is exported from the artifact `org.apache.jackrabbit.vault:vault-validation` as well.

The validator which is returned via the `ValidatorFactory` is one of the following types below package `org.apache.jackrabbit.filevault.maven.packaging.validator`

Validator Class | Description | Scope | Called from another validator
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
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

Content Package Properties
================
One of the most important meta files of a content package is the `properties.xml` which is present in
the `META-INF/vault` directory. It defines defines several meta data around the content package itself
which are just used for informational purpose (like `lastModifiedBy`) and other properties which actually influence how the package
is installed (like `subPackageHandling`). In addition to that there is the `MANIFEST.MF` within `META-INF` which contains only informational metadata.

<!-- MACRO{toc} -->

properties.xml
-----------------
The `properties.xml` follows the format of a Java properties file in XML format as defined by [java.util.Properties](https://docs.oracle.com/javase/8/docs/api/java/util/Properties.html). The individual keys are defined within [PackageProperties][api.PackageProperties].


Example:

    <?xml version="1.0" encoding="utf-8" standalone="no"?>
    <!DOCTYPE properties SYSTEM "http://java.sun.com/dtd/properties.dtd">
    <properties>
	<comment>FileVault Package Properties</comment>
	<entry key="createdBy">admin</entry>
	<entry key="name">acs-aem-commons-content</entry>
	<entry key="lastModified">2018-06-14T11:50:32.369+02:00</entry>
	<entry key="lastModifiedBy">admin</entry>
	<entry key="requiresRestart">false</entry>
	<entry key="acHandling">merge</entry>
	<entry key="created">2018-06-14T11:50:34.431+02:00</entry>
	<entry key="buildCount">1</entry>
	<entry key="version">3.15.0</entry>
	<entry key="requiresRoot">false</entry>
	<entry key="dependencies">day/cq60/product:cq-content:6.2.136</entry>
	<entry key="packageFormatVersion">2</entry>
	<entry key="description">Maven Multimodule project for ACS AEM Commons.</entry>
	<entry key="group">adobe/consulting</entry>
	<entry key="lastWrapped">2018-06-14T11:50:32.369+02:00</entry>
	<entry key="lastWrappedBy">admin</entry>
	</properties>

### Entries

| Property | Description | Required | Default
| -------- | ------- | ------ | ----- 
| name    | The name of the package. Determines the entry node under which this package would be uploaded to a repository. | yes | n/a
| group | The group of the package, determines the ancestor node under which the package would be uploaded to a repository.  | yes | n/a
| version | The version of the package | yes | n/a
| description | A description of the package | no | empty
| lastModified | A date string in the format `±YYYY-MM-DDThh:mm:ss.SSSTZD` specifying when the package has been last modified (see also [ISO8601][api.ISO8601]) | no | empty
| lastModifiedBy | A user name indicating who last modified this package | no | empty
| buildCount | An integer indicating how often this package has been built | no | empty
| dependencies | Comma-separated list of dependencies. Each dependency has the format `<group>:<name>:<version or versionrange>`. See [Dependencies][api.Dependency]. | no | empty
| dependencies-locations | Optional comma-separated list of dependencies' locations. Each item has the format `<package-id>=<uri>`. Currently [FileVault Package Maven Plugin](https://jackrabbit.apache.org/filevault-package-maven-plugin/index.html) is using a URI scheme for Maven coordinates like `maven:<groupId>:<artifactId>:<version>[[:<classifier>]:packaging]`. | no | empty
| created | A date string in the format `±YYYY-MM-DDThh:mm:ss.SSSTZD` specifying when the package has been created initially (see also [ISO8601][api.ISO8601]) | no | empty
| createdBy | A user name indicating who initially created this package | no | empty
| lastWrapped | A date string in the format `±YYYY-MM-DDThh:mm:ss.SSSTZD` specifying when the package has been last wrapped (i.e. rebuilt) (see also [ISO8601][api.ISO8601]) | no | empty
| lastWrappedBy | A user name indicating who last modified this package | no | empty
| acHandling | See [AccessControlHandling][api.AccessControlHandling]. | no | ignore
| cndPattern | A [Java regular expression pattern](https://docs.oracle.com/javase/8/docs/api/java/util/regex/Pattern.html) which specifies where to look for CND files within the given package (in addition to all `*.cnd` files below `META-INF/vault`) | no | `^/(apps|libs)/([^/]+/){1,2}nodetypes/.+\\.cnd$`
| requiresRoot | If set to `true` indicates that only admin sessions can install this package | no | `false`
| requiresRestart | If set to `true` indicates that the system should be restarted after this package has been installed | no | `false`
| noIntermediateSaves | If set to `true` indicates no intermediate saves should be performed while installing this package | no | `false`
| subPackageHandling | see [SubPackageHandling][api.SubPackageHandling] | no | `*;install`
| useBinaryReferences | If set to `true` indicates that binary references should be used instead of the actual binary | no | `false`
| packageType | Possible values: `application`, `content`, `container` or `mixed`. Further details at [Package Types](packagetypes.html). | no | ?
| installhook.\<name\>.class | The FQN of the class which acts as an [install hook](installhooks.html). The `<name>` can be an arbitrary string (but must not contain a dot). | no | n/a
| packageFormatVersion | The version of this package as integer value. Versions newer than 2 are not yet supported during installation. | no | 2
| allowIndexDefinitions | If set to `true` indicates that the package contains an [Oak Index Definition](https://jackrabbit.apache.org/oak/docs/query/indexing.html#index-defnitions). Otherwise the package is not supposed to contain an index definition. This may be important to know prior to installation as installing/updating an index definition might have a severe performance impact especially on large repositories| no | false
| groupId | The Maven groupId of the underlying Maven module from which this package was built. Only set if built via the [FileVault Package Maven Plugin](https://jackrabbit.apache.org/filevault-package-maven-plugin/index.html) | no | n/a
| artifactId | The Maven artifactId of the underlying Maven module from which this package was built. Only set if built via the [FileVault Package Maven Plugin](https://jackrabbit.apache.org/filevault-package-maven-plugin/index.html) | no | n/a

Manifest File
---------------

Since version 3.1.40 ([JCRVLT-32](https://issues.apache.org/jira/browse/JCRVLT-32)) properties are now also partly stored within the `MANIFEST.MF` of the ZIP content package. Currently those are not evaluated during installation, though. All attributes which are currently being generated are listed in [AbstractExporter][api.AbstractExporter].

| Attribute | Description |
| -------- | ------- |
| Content-Package-Dependencies | Same as `dependencies` within the `properties.xml` |
| Content-Package-Dependencies-Locations | Same as `dependencies-locations` within the `properties.xml` |
| Content-Package-Description | Same as `description` within the `properties.xml` |
| Content-Package-Type | Same as `packageType` within the `properties.xml`
| Content-Package-Id | The string format of the [PackageId][api.PackageId] |
| Content-Package-Roots | All filter roots coming from the [filter.xml](filter.html) in a string. The individual roots are concatenated by `,` |
| Import-Package | Taken over from the [OSGi specification](https://www.osgi.org/bundle-headers-reference/). Contains a list of all Java packages being used by scripts/classes contained in this content package. Currently only being generated by the [filevault-package-maven-plugin](http://jackrabbit.apache.org/filevault-package-maven-plugin/) but not through the exporter API.

[api.PackageProperties]: apidocs/org/apache/jackrabbit/vault/packaging/PackageProperties.html
[api.AccessControlHandling]: apidocs/org/apache/jackrabbit/vault/fs/io/AccessControlHandling
[api.SubPackageHandling]: apidocs/org/apache/jackrabbit/vault/packaging/SubPackageHandling.html
[api.ISO8601]: https://jackrabbit.apache.org/api/2.12/org/apache/jackrabbit/util/ISO8601.html
[api.Dependency]: apidocs/org/apache/jackrabbit/vault/packaging/Dependency.html
[api.AbstractExporter]: apidocs/org/apache/jackrabbit/vault/fs/io/AbstractExporter.html
[api.PackageId]: apidocs/org/apache/jackrabbit/vault/packaging/PackageId.html

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

Content Package Install Hooks
================

Overview
------
Install hooks are just Java classes which are called during package installation. One can use them to execute arbitrary operations automatically on certain stages during package import.

Install hooks must implement the interface [`org.apache.jackrabbit.vault.packaging.InstallHook`][api.InstallHook].

Restricting the use of install hooks
------
Packages with install hooks can be restricted by user or rejected entirely. These restrictions apply to both internal and external hooks. A rejected installation fails with a `PackageException`; hooks are not silently skipped.

Configure the following properties on the service used for installation:

* JCR package manager: PID `org.apache.jackrabbit.vault.packaging.impl.PackagingImpl`
* File-system package registry: PID `org.apache.jackrabbit.vault.packaging.registry.impl.FSPackageRegistry`

**Restrict by user (default):** Only `admin`, `system` and members of `administrators` may install packages containing hooks. Since FileVault 3.4.6, `authIdsForHookExecution` can allow additional authorizable IDs or principal names ([JCRVLT-427](https://issues.apache.org/jira/browse/JCRVLT-427)). An empty list does not revoke the built-in administrative permissions. Other users cannot install these packages, but may perform dry runs, which do not load package hooks.

**Reject for all users:** Set the boolean property `rejectPackagesWithHooks` to `true` to reject packages containing hooks, including dry runs. This overrides the user-based permissions, including those of administrators. Rejection occurs before package hooks are loaded or package content is imported. The default is `false`.

Packages without hooks are unaffected by these hook restrictions. Each package is checked when it is installed, including dependencies and subpackages; rejection does not roll back packages already installed as part of the same dependency or subpackage tree.

Programmatic callers can enable the same policy with `new AbstractPackageRegistry.SecurityConfig(authIdsForHooks, authIdsForRoots, true)` and pass it to the package manager, registry or package extraction API that accepts a security configuration. The `VaultPackage.extract(Session, ImportOptions)` overload without a security configuration retains the default behavior.


Internal Install Hooks
------
The internal install hooks have to be packaged as JARs and placed in `META-INF/vault/hooks` within the content package. The JAR needs to have a [`Main-Class` attribute in its manifest](https://docs.oracle.com/javase/8/docs/technotes/guides/jar/jar.html#Main_Attributes) pointing to the hook class. 


External Install Hooks
-----
External install hooks are loaded through the class loader by their fully qualified class name. The class name is given in the [package property](properties.html) `installhook.<name>.class` where `<name>` is an arbitrary string (must not contain a dot).

The following class loaders are used by default to load the given class:

1. The class loader which loaded the `InstallHookProcessorImpl` class (in an OSGi runtime this is the bundle class loader of the FileVault bundle)
2. The [context class loader of the current thread](https://docs.oracle.com/javase/8/docs/api/java/lang/Thread.html#getContextClassLoader--).

The class loader can be overridden by calling [`ImportOptions.setHookClassLoader(...)`][api.ImportOptions] and pass the import options then to the package importer.

You must make sure that the external install hook class is accessible from the used class loader.

3rd Party Hooks
-----
Name | Description | Link
--- | --- | ---
Asset Workflow Hook | Disables AEM Asset workflows during package installation | <https://github.com/orbinson/be.orbinson.aem.sdk-dev-tools#asset-workflow-hook>
Oak Reindex Hook | Automatically manages the reindexing in Oak when index definitions are installed | <https://github.com/code-distillery/filevault-oak-reindex-hook>
AEM Easy Content Upgrade | Executes Groovy scripts | <https://github.com/valtech/aem-easy-content-upgrade>
Access Control Tool for AEM | Installs JCR permissions from YAML configurations | <https://github.com/Netcentric/accesscontroltool>
Vault Upgrade Hook | Executes additional operations via Groovy, Sling Pipes and some other means | <https://github.com/Netcentric/vault-upgrade-hook>

Please raise a PR to get other 3rd party hooks listed above.

[api.InstallHook]: apidocs/org/apache/jackrabbit/vault/packaging/InstallHook.html
[api.ImportOptions]: apidocs/org/apache/jackrabbit/vault/fs/io/ImportOptions.html

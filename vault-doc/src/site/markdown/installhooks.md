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

Execution of install hooks is only allowed for certain users. Prior to FileVault 3.4.6 this was only possible for users `admin`, `system` or members of group `administrators`, in newer versions the allowed user ids are configurable via OSGi configuration with PID `org.apache.jackrabbit.vault.packaging.impl.PackagingImpl` ([JCRVLT-427](https://issues.apache.org/jira/browse/JCRVLT-427)). Installation of packages containing an install hooks leads to a `PackageException`  for other non-allowed users.


Internal Install Hooks
------
The internal install hooks have to be packaged as JARs and placed in `META-INF/vault/hooks` within the content package. The JAR needs to have a [`Main-Class` attribute in its manifest](https://docs.oracle.com/javase/8/docs/technotes/guides/jar/jar.html#Main_Attributes) pointing to the hook class. 


External Install Hooks
-----
External install hooks are loaded through the class loader by their fully qualified class name. The class name is given in the [package property](properties.html) `installhook.<name>.class` where `<name>` is an arbitrary string (must not contain a dot).

The following class loaders are used by default to load the given class:

1. The class loader which loaded the `InstallHookProcessorImpl` class (in an OSGi container this is the bundle class loader of the FileVault bundle)
2. The [context class loader of the current thread](https://docs.oracle.com/javase/8/docs/api/java/lang/Thread.html#getContextClassLoader--).

The class loader can be overridden by calling [`ImportOptions.setHookClassLoader(...)`][api.ImportOptions] and pass the import options then to the package importer.

You must make sure that the external install hook class is accessible from the used class loader.

[api.InstallHook]: apidocs/org/apache/jackrabbit/vault/packaging/InstallHook.html
[api.ImportOptions]: apidocs/org/apache/jackrabbit/vault/fs/io/ImportOptions.html
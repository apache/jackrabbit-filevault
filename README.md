[![ASF Jira](https://img.shields.io/badge/ASF%20JIRA-JCRVLT-orange)](https://issues.apache.org/jira/projects/JCRVLT/summary)
![GitHub](https://img.shields.io/github/license/apache/jackrabbit-filevault)
[![Maven Central](https://img.shields.io/maven-central/v/org.apache.jackrabbit.vault/vault-cli.svg?label=Maven%20Central)](https://search.maven.org/artifact//org.apache.jackrabbit.vault/vault-cli)
[![Build Status](https://img.shields.io/travis/com/apache/jackrabbit-filevault/master)](https://travis-ci.com/apache/jackrabbit-filevault)
[![SonarCloud Status](https://sonarcloud.io/api/project_badges/measure?project=apache_jackrabbit-filevault&metric=alert_status)](https://sonarcloud.io/dashboard?id=apache_jackrabbit-filevault)
[![SonarCloud Coverage](https://sonarcloud.io/api/project_badges/measure?project=apache_jackrabbit-filevault&metric=coverage)](https://sonarcloud.io/component_measures/metric/coverage/list?id=apache_jackrabbit-filevault)
[![SonarCloud Bugs](https://sonarcloud.io/api/project_badges/measure?project=apache_jackrabbit-filevault&metric=bugs)](https://sonarcloud.io/component_measures/metric/reliability_rating/list?id=apache_jackrabbit-filevault)
[![SonarCloud Vulnerabilities](https://sonarcloud.io/api/project_badges/measure?project=apache_jackrabbit-filevault&metric=vulnerabilities)](https://sonarcloud.io/component_measures/metric/security_rating/list?id=apache_jackrabbit-filevault)

Welcome to Apache Jackrabbit FileVault
===========================================================

The FileVault introduces a JCR repository to filesystem mapping. The mapping
is exposed by an API and used by the "FileVault Content Packages" which allow to
create portable packages of repository content.

The Vault Command Line Interface aka "vlt" provides a Subversion like
utility to work and develop with repository content.

Apache Jackrabbit FileVault is a project of the Apache Software Foundation.

Documentation
=============
Please refer to the documentation at 
<https://jackrabbit.apache.org/filevault/>


Building FileVault
===========================================

You can build FileVault like this:

    mvn clean install

You need Maven 3.6.2 (or higher) with Java 8 (or higher) for the build.
For more instructions, please see the documentation at:

   <https://jackrabbit.apache.org/building-jackrabbit.html>

Building FileVault Site
============================================

The FileVault documentation lives as Markdown files in `src/site/markdown` such
that it easy to view e.g. from GitHub. The Maven site plugin
can be used to build and deploy a web site from those sources as follows:

1. From the reactor build the site with javadoc:

        $ mvn site

2. Review the site at `target/site`
3. Deploy the site to <https://jackrabbit.apache.org/filevault/> using:

        $ mvn site-deploy

4. Finally review the site at <https://jackrabbit.apache.org/filevault/index.html>.


Note: To skip the final commit use `-Dscmpublish.skipCheckin=true`. You can then
review all pending changes in `vault-doc/target/scmpublish-checkout` and follow
up with `svn commit` manually.

Note: Every committer should be able to deploy the site. No fiddling with
credentials needed since deployment is done via svn commit to
`https://svn.apache.org/repos/asf/jackrabbit/site/live/filevault`.

License (see also LICENSE)
==============================

```
Collective work: Copyright 2013 The Apache Software Foundation.

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
```

Mailing Lists
=============

To get involved with the Apache Jackrabbit project, start by having a
look at our website and joining our mailing lists. For more details about
Jackrabbit mailing lists as well as links to list archives, please see:

   <https://jackrabbit.apache.org/mailing-lists.html>

Latest development
==================

The latest FileVault source code is available at

   <https://github.com/apache/jackrabbit-filevault>

Credits
=======

See <https://jackrabbit.apache.org/jackrabbit-team.html> for the list of
Jackrabbit committers and main contributors.

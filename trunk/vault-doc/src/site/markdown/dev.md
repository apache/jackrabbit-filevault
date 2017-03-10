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
Developing
==========

Latest development
------------------
The latest FileVault source code is available via Subversion at

* https://svn.apache.org/repos/asf/jackrabbit/commons/filevault/trunk

or with ViewVC at

* https://svn.apache.org/viewvc/jackrabbit/commons/filevault/trunk

or on GitHub at

* https://github.com/apache/jackrabbit-filevault


To checkout the main Jackrabbit source tree, run

    svn checkout https://svn.apache.org/repos/asf/jackrabbit/commons/filevault/trunk jackrabbit-filevault
    
or

    git clone https://github.com/apache/jackrabbit-filevault.git


Building FileVault
------------------
You can build FileVault using maven:

    cd jackrabbit-filevault
    mvn clean install

You need Maven 2.0.9 (or higher) with Java 5 (or higher) for the build.
For more instructions, please also see the documentation at:

* http://jackrabbit.apache.org/building-jackrabbit.html

Issue Tracker
-------------
Apache Jackrabbit FileVault uses Jira for tracking bug reports and requests for improvements, new features, 
and other changes.

The issue tracker is available at https://issues.apache.org/jira/browse/JCRVLT and is readable by everyone. 
A Jira account is needed to create new issues and to comment on existing issues. Use the
[registration form](https://issues.apache.org/jira/secure/Signup!default.jspa) to request an account if you 
do not already have one.

See the [Jackrabbit Issue Tracker Page](http://jackrabbit.apache.org/issue-tracker.html) for more information.


Mailing Lists
-------------
To get involved with the Apache Jackrabbit project, start by having a
look at our website and joining our mailing lists. For more details about
Jackrabbit mailing lists as well as links to list archives, please see:

* http://jackrabbit.apache.org/mailing-lists.html

Releasing
---------
See the extensive [Release Documentation](howto_release.html) on the steps
involved to release Jackrabbit FileVault.

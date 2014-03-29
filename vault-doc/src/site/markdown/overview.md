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
Overview
========

**NOTE**: Parts of the following documentation is outdated and needs review
- - - 

Jackrabbit FileVault introduces a JCR repository to filesystem mapping. The mapping is exposed by and API and used by several tools:

* JackrabbitVaultPackaging that defines a package including the files, configuration and filter information that allows export/import packages of content.
* Vault Command Line Interface aka `vlt` that provides a subversion like utility to work and develop with repository content.

The base of the Jackrabbit FileVault is the [VautFs](vaultfs.html) which provides the api for accessing repository through a filesystem like mapping.

![Vault API](vault_api.png?raw=true)

How it works
------------
Jackrabbit FileVault works similar to subversion. Usually you checkout a local copy of the (partial) content of the repository and make modifications to it. Once you're finished you upload the modified stuff again. The content is mapped to a local filesystem structure using the JcrFs API. The mechanism works like subversion where you have a copy of the unmodified file and some information about the entries.

Subversion & Vault living together
----------------------------------
One of the goals of Jackrabbit FileVault is to provide the ability to store repository content in a SCM for example in subversion. One problem occurs that the _control files_ of vault are not to be checked into subversion since this could cause problem in concurrent development. Those files are kept in the `.vlt` directory which must be excluded from subversion. Using the `svn:ignore` property is not advisable because one can forget to define it. A better option is to use the `global-ignores` option in the subversion user configuration:

    ...
    ### Section for configuring miscellaneous Subversion options.
    [miscellany]
    ### Set global-ignores to a set of whitespace-delimited globs
    ### which Subversion will ignore in its 'status' output, and
    ### while importing or adding files and directories.
    global-ignores = .vlt
    ...
### Use Cases
The following workflows illustrate that the Vault/Subversion coupling works and could easily be automated. Plans are to propagate additions and removals automatically using javahl or a similar java-svn binding.

Imagine the following scenario:

* The application XYZ has some repository content that is exported and stored in subversion
* User A and B both have a local checkout
* User A and B work with local test repositories


**Workflow 1 - Modification:**

1. User A does a fix in a jsp, checks it into his local repository and tests if the fix works
2. User A commits the jsp into subversion
3. User B update this copy via subversion and gets the modifications of the jsp
4. User B can now checkin the modifications to his local repository

**Workflow 2 - Addition:**

1. User A creates a new file on his local filesystem
2. User A uses `vlt` to add and commit it to his local repository
3. User A also adds the file to his subversion working copy and commits it
4. User B updates subversion and gets the new file
5. User B updates vault and notifies that this new file is not added to the vault yet
6. User B adds the file as well to his vault and commits it to the repository.

**Workflow 3 - Deletion:**

1. User A deletes a file using `vlt`. This marks the file as deleted and removes it from disk
2. User A commits the changes to his local repository
3. User A marks the file as deleted using =svn= and commits the changes
4. User B updates subversion which deletes his local copy
5. User B marks the file as deleted using `vlt` and commits the changes to his local repository

Export/Checkout directory structure
-----------------------------------
The root directory of a vault checkout contains a `META-INF/vault` directory which holds the serialization configuration (`config.xml`) the filter information (`filter.xml`) and other settings. The repository content is placed in a directory named `jcr_root`. eg:

    + mycheckeout
      + META-INF
      + jcr_root

### `META-INF/vault/config.xml`
Contains the VaultFs configuration that is (was) used for this checkout (export).

### `META-INF/vault/filter.xml`
Contains the workspace filter that is (was) used for this checkout (export). also see [Workspace Filter](filter.html) for more details.

### User specific config files 
Some configuration files are stored in the user's home directory. usually under `~/.vault`.

#### `~/.vault/settings.xml`
Holds some per user configuration like globally ignored files, etc.

#### `~/.vault/auth.xml`
Holds authorization information for known repositories.

Usage
-----
The console tool is called `vlt` and has the following usage:

    $vlt --help
    
    ----------------------------------------------------------------------------------------------
    Jackrabbit FileVault [version 3.0.0] Copyright 2013 by Apache Software Foundation.
    See LICENSE.txt for more information.
    ----------------------------------------------------------------------------------------------
    Usage:
      vlt [options] <command> [arg1 [arg2 [arg3] ..]]
    ----------------------------------------------------------------------------------------------
    
    Global options:
      -Xjcrlog <arg>           Extended JcrLog options (omit argument for help)
      -Xdavex <arg>            Extended JCR remoting options (omit argument for help)
      --credentials <arg>      The default credentials to use
      --config <arg>           The JcrFs config to use
      -v (--verbose)           verbose output
      -q (--quiet)             print as little as possible
      --version                print the version information and exit
      --log-level <level>      the log4j log level
      -h (--help) <command>    print this help
    Commands:
      export                   Export the Vault filesystem
      import                   Import a Vault filesystem
      checkout (co)            Checkout a Vault file system
      status (st)              Print the status of working copy files and directories.
      update (up)              Bring changes from the repository into the working copy.
      info                     Displays information about a local file.
      commit (ci)              Send changes from your working copy to the repository.
      revert (rev)             Restore pristine working copy file (undo most local edits).
      resolved (res)           Remove 'conflicted' state on working copy files or directories.
      propget (pg)             Print the value of a property on files or directories.
      proplist (pl)            Print the properties on files or directories.
      propset (ps)             Set the value of a property on files or directories.
      add                      Put files and directories under version control.
      delete (del,rm)          Remove files and directories from version control.
      diff (di)                Display the differences between two paths.
      rcp                      Remote copy of repository content.
      sync                     Control vault sync service
      console                  Run an interactive console
    ----------------------------------------------------------------------------------------------

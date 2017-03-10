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
Usage
=====

**NOTE**: Parts of the following documentation is outdated and needs review
- - - 

Vault Console Tool
------------------
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

Checkout
--------
_todo_

Checkin
-------
_todo_

Adding / Removing Files
-----------------------
_todo_

Vault Sync
----------
The vault sync service is used to synchronize repository content with a local filesystem representation and vice versa.
This is achieved by installing an OSGi service that will listen for repository changes and scans the filesystem contents
periodically. It uses the same serialization format as vault for mapping the repository contents to disk.

The vault sync service is a development tool and it is highly discouraged to use it on a productive system.
Also note that the service can only sync with the local filesystem and cannot be used for remote development.

The initial version (2.3.22 / 2.4.24) only supports simple files and folders but detects special vault serialized files
(`.content.xml`, `dialog.xml`, etc) and ignores them silently. Thus it is possible to use vault sync on a default vlt
checkout.

### Installation and Configuration
The vault sync service is provided as bundle which needs to be installed in Oak (or any other sling runtime). Once the
service is running it can be configured with the following parameters:

vault.sync.syncroots
: One or many local filesystem paths that define the sync roots.

vault.sync.fscheckinterval
: Frequency (in seconds) of which the filesystem should be scanned for changes. default is 5 seconds.

vault.sync.enabled
: General flag that enables/disables the service

Note: It is advisable to configure the service via a `sling:OsgiConfig` node with the name
`org.apache.jackrabbit.vault.sync.impl.VaultSyncServiceImpl`. Once the service has configured sync roots it will
initialize the sync root with the following files:

`.vlt-sync-config.properties`
: Per sync-root config

`.vlt-sync.log`
: log file that contains information about the operations performed during syncing

`.vlt-sync-filter.xml`
: filter that is neede to configure what portions of the repository are synced.

The vault sync service can also be installed using the vlt command line tool. See _Vlt Integration_ below.

### .vlt-sync-filter.xml
The sync filter has the format of a normal vault workspace filter. If the sync root lies on a vlt checkout, specifically
points to a jcr_root directory of a such, then no `.vlt-sync-filter.xml` is initialized in the sync root, but the one
defined by the respective vlt checkout is used. this is usually `META-INF/vault/filter.xml`.

### .vlt-sync-config.properties
This config has the following default content:

    #
    # Vault Sync Config File
    #
    # Note: Changes to this file are detected automatically if the Vault Sync Service is
    # running and this directory is configured as sync root.
    #
    # Supported Properties:
    # ---------------------
    #
    # disabled = ( "true" | "false" )
    #     Defines if syncing of this directory is generally disabled. It can be useful
    #     to disabled syncing temporarily if structural changes need to be done that required
    #     several modifications.
    #
    #     defaults to: false
    disabled=false

    # sync-once= ( "" | "JCR2FS" | "FS2JCR" )
    #     If non empty the next scan will trigger a full sync in the direction indicated.
    #
    #     JCR2FS: 'export' all content in the JCR repository and write to the local disk.
    #     FS2JCR: 'import' all content from the disk into the JCR repository.
    #
    #     defaults to: ""
    sync-once=

    # sync-log = <filename>
    #     Defines the filename of the sync log.
    #
    #     defaults to: .vlt-sync.log
    #
    #sync-log=.vlt-sync.log

A note to _sync-one_: This property is used to trigger a directional full sync between the repository and the file
system (usinig `JCR2FS`) or vice versa (using `FS2JCR`). This is especially useful when installing vault sync for the
first time or adding a new sync root. Allow a maximum of 5 seconds (or whatever you configured the sync interval to be)
for a configuration change to be detected. Check the `.vlt-sync.log` or the `error.log` of your instance for progress or
errors.

For example you started of a project directly in the repository using CRXDE Lite and now decide to use your local IDE
for further development. After configuring the sync root and the filter, you would set the sync-once property to
`JCR2FS` to get all your files created in the local sync root.

For example you start with a fresh repository and want to import local files that you retrieved from a SCM. After
configuring the sync root and the filter (which should be under SCM, too), you would set the sync-once property
to `FS2JCR`.

### Operation
Once properly configured and running you should do an initial full sync to get system in a synchronized state.
to do so, follow the notes above regarding the sync-once configuration property. After that each repository modification
within the specified filter will trigger a repository-to-filesystem synchronization of the modified nodes
(see initial note on supported serializations). The repository changes are applied almost immediately since JCR
observation events are pushed to it's listeners.

On each sync interval, the service scans the filesystem for changes and eventually synchronizes the changes back with
the repository. So allow a maximum of 5 seconds (or whatever you configured the sync interval to be) after you made
changes to the local files.

Note: Be very careful on structural changes on the local filesystem as they might result in removing potentially large
trees in the repository.

### vlt integration
The vault sync service can be used standalone or together with a vlt checkout. using the later, using the later
initialization is easy as executing: `vlt sync` on the command line. the following sync related commands are available
by vlt (as of version 2.3.22):

    $ vlt sync --help
    Usage:
     sync -v|--force|-u <uri> <command> <localPath>

    Description:
      Allows to control the vault sync service. Without any arguments this command tries to put the current CWD under sync control. If executed within a vlt checkout, it uses the respective filter and host to configure the syncing. If executed outside of a vlt checkout, it registers synchronization only if the directory is empty.

      Subcommands:
        install      Installs the Vault Sync service on the remote server
        status (st)  Display status information.
        register     Register a new sync directory
        unregister   Unregisters a sync directory

      Most of the commands take an optional local path as argument which then specifies the sync directory. If the --uri is omitted it is auto-detected from the current vault checkout.

      Note: the vault sync service creates a .vlt-sync-config.properties in the sync directory. See the inline comments for further options.

      Examples:

      Listing sync roots of a server:
        vlt --credentials admin:admin sync --uri http://localhost:8080/crx status

      Add the current CWD as sync directory:
        vlt sync register

    Options:
      -v (--verbose)      verbose output
      --force             force certain commands to execute.
      -u (--uri) <uri>    Specifies the URI of the sync host.
      <command>           Sync Command
      <localPath>         local path (optional)

### installing the service using vlt
The vlt sync install command can be used to install the vault sync service bundle and configuration automatically.
the bundle is installed below `/libs/system/vault/install` and the config node is created at
`/libs/system/vault/org.apache.jackrabbit.vault.sync.impl.VaultSyncServiceImpl`. Initially the service is enabled but
no sync roots are configured.

Example:

    $ vlt --credentials admin:admin sync --uri http://localhost:8080/crx install

### displaying the service status
The status command can be used to display information about the running sync service. Note that the status command does
not fetch any live data from the service but rather reads the configuration at `/libs/system/vault/org.apache.jackrabbit.vault.sync.impl.VaultSyncServiceImpl`

Example:

    $ vlt sync status --uri http://localhost:4502/crx
    Connecting via JCR remoting to http://localhost:4502/crx/server
    Listing sync status for http://localhost:4502/crx/server/-/jcr:root
    - Sync service is enabled.
    - No sync directories configured.

### adding/removing sync roots
The register and unregister commands are used to add and remove sync roots from the configuration.

Example (executed in a vlt checkout):

    $ vlt sync register
    Connecting via JCR remoting to http://localhost:4502/crx/server
    Added new sync directory: /tmp/workspace/vltsync/jcr_root
    $ vlt sync status
    Connecting via JCR remoting to http://localhost:4502/crx/server
    Listing sync status for http://localhost:4502/crx/server/-/jcr:root
    - Sync service is enabled.
    - syncing directory: /tmp/workspace/vltsync/jcr_root

### Quick setup
The most common task is probably installing the service and register the current vlt checkout as sync root. this can be
done using the sync command without arguments.

Example (executed in a vlt checkout):

    $ vlt sync
    Connecting via JCR remoting to http://localhost:4502/crx/server
    Starting initialization of sync service in existing vlt checkout /tmp/workspace/vltsync/jcr_root for http://localhost:4502/crx/server/-/jcr:root
    Preparing to install vault-sync-2.3.22.jar...
    Updated bundle: vault-sync-2.3.22.jar
    Created new config at /libs/crx/vault/config/org.apache.jackrabbit.vault.sync.impl.VaultSyncServiceImpl
    Added new sync directory: /tmp/workspace/vltsync/jcr_root

The directory `/tmp/workspace/vltsync/jcr_root` is now enabled for syncing.
You might perform a 'sync-once' by setting the
appropriate flag in the `/tmp/workspace/vltsync/jcr_root/.vlt-sync-config.properties` file.


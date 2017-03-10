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
Vault Remote Copy (rcp)
=======================

Jackrabbit vault offers a simple method to copy nodes between repositories.


Commandline Mode
----------------
````
$ vlt rcp --help
Usage:
 rcp -q|-r|-b <size>|-t <seconds>|-R <path>|-u|-n|-e <arg1> [<arg2> ...]|--no-ordering <src> <dst>

Description:
  Copies a node tree from one remote repository to another. Note that <src> points at the source node, and <dst>points at the destination path, which parent node must exist.

  Note: Due to bug in command line processing, the --exclude options need to be followed by another option before the <src> and <dst> arguments.
  Example:
    vlt rcp -e ".*\.txt" -r http://localhost:4502/crx/-/jcr:root/content http://admin:admin@localhost:4503/crx/-/jcr:root/content_copy


Options:
  -q (--quiet)                        print as little as possible
  -r (--recursive)                    descend recursively
  -b (--batchSize) <size>             number of nodes until intermediate save
  -t (--throttle) <seconds>           number of seconds to wait after an intermediate save
  -R (--resume) <path>                source path to resume operation after a restart
  -u (--update)                       overwrite/delete existing nodes.
  -n (--newer)                        respect lastModified properties for update.
  -e (--exclude) <arg> [<arg> ...]    regexp of excluded source paths.
  --no-ordering                       disable node ordering for updated content
  <src>                               the repository address of the source tree
  <dst>                               the repository address of the destination node
````


Vault RCP Server Bundle
-----------------------
The vault rcp server bundle provides a very simple vault remote copy task management that can be controlled via a json/http interface. This special vault rcp version can only be used to **import** content from remote repositories.

### Usage
The vault rcp server maintains a list of _remote copy tasks_ that can be controlled via the http interface at `/system/jackrabbit/filevault/rcp`. The request and responses are JSON formatted.

#### List Tasks (GET)
Simply lists all available tasks. In addition to listing the parameters that were passed when the task was created, it also list some information about the current state of the task.

| Property | Comment |
| -------- | ------- |
| state    | Current state of the task. One of `NEW` , `RUNNING`, `ENDED`, `STOPPED` |
| currentPath | The path that is currently processed. |
| lastSavedPath | The path of the node that was saved last. Useful for resuming from aborted tasks. |
| totalNodes | Total number of Nodes already processed |
| totalSize | Total size of data already processed (approximative) |
| currentNodes | Number of nodes in the current batch. |
| currentSize | Data size of current batch (approximative) | 

##### Example

    GET /system/jackrabbit/filevault/rcp HTTP/1.1
    Host: localhost:4502
    
    HTTP/1.1 200 OK
    Content-Type: application/json;charset=utf-8
	
	{
	"tasks": [{
	    "id": "test-id-1234",
        "src": "http://admin:admin@localhost:4503/crx/server/-/jcr:root/content/geometrixx",
        "dst": "/tmp/test1",
        "recursive": true,
        "batchsize": 2048,
        "update": true,
        "onlyNewer": false,
        "throttle": 1,
        "resumeFrom": null,
	    "excludes": [
	        "/content/geometrixx/(en|fr)/tools(/.*)?"
        ],
	    "status": {
            "state": "STOPPED",
            "currentPath": "/content/geometrixx/en/products/triangle",
            "lastSavedPath": "/content/geometrixx/en/products",
            "totalNodes": 10841,
            "totalSize": 1915798,
            "currentSize": 122640,
            "currentNodes": 601
        }
    }]


#### Create Task (POST)
Creates a new task.

| Property     | Required | Comment |
| ------------ | -------- | ------- |
| cmd          | X | Needs to be "**create**". |
| id           | - | Id for new task. if omitted a random id is used. |
| src          | X | URI of the remote source repository. |
| dst          | X | Destination path in the local repository. |
| batchsize    | - | Size of batch until intermediate size. Default is 1024. |
| recursive    | - | **true** to descend recursively. Default is _false_. |
| update       | - | **true** to overwrite and/or delete existing nodes. Default is _false_. |
| newer        | - | **true** to respect _lastModified_ properties for update. Default is _false_. |
| throttle     | - | Number of seconds to sleep after each intermediate save. Default is _0_. |
| resumeFrom   | - | Source path to resume a prior aborted copy. Note that the algorithm simply skips all source nodes until the _resumeFrom_ path is found. It is necessary that the content structure of the source repository does not change in between runs, and that content already needs to be present in the detination location. |
| excludes     | - | Array of regular expressions that exclude source paths. |


##### Example
    POST /system/jackrabbit/filevault/rcp HTTP/1.1
    Host: localhost:4502
    Content-Type: application/json
    
	{
        "cmd":"create",
        "id":"test-id-1234",
        "src":"http://admin:admin@localhost:4503/crx/-/jcr:root/content/geometrixx",
        "dst":"/tmp/test1",
        "batchsize": 2048,
        "update": true,
        "onlyNewer": false,
        "recursive": true,
        "throttle": 1,
        "resumeFrom": "/content/geometrixx/fr",
        "excludes": [
            "/content/geometrixx/(en|fr)/tools(/.*)?"
        ]
    }

    HTTP/1.1 201 Created
    Content-Type: application/json;charset=utf-8
    Location: /libs/granite/packaging/rcp.tasks/test-id-1234
    
    {
        "status": "ok",
        "id": "test-id-1234"
    }
    
#### Start Task (POST)
Starts a previously created task.

| Property     | Required | Comment                  |
| ------------ | -------- | ------------------------ |
| cmd          | X        | Needs to be "**start**". |
| id           | X        | Task Id to start.        |


##### Example
    POST /system/jackrabbit/filevault/rcp HTTP/1.1
    Host: localhost:4502
    Content-Type: application/json

    {
        "cmd": "start",
        "id": "test-id-1234"
    }
    

    HTTP/1.1 200 OK
    Content-Type: application/json;charset=utf-8

    {
        "status": "ok",
        "id": "test-id-1234"
    }


#### Stop Task (POST)
Stops a previously start task.

| Property     | Required | Comment                  |
| ------------ | -------- | ------------------------ |
| cmd          | X        | Needs to be "**stop**".  |
| id           | X        | Task Id to start.        |


##### Example
    POST /system/jackrabbit/filevault/rcp HTTP/1.1
    Host: localhost:4502
    Content-Type: application/json

    {
        "cmd": "stop",
        "id": "test-id-1234"
    }
    

    HTTP/1.1 200 OK
    Content-Type: application/json;charset=utf-8

    {
        "status": "ok",
        "id": "test-id-1234"
    }



#### Remove Task (POST)
Removes a previously start task.

| Property     | Required | Comment                   |
| ------------ | -------- | ------------------------- |
| cmd          | X        | Needs to be "**remove**". |
| id           | X        | Task Id to start.         |


##### Example
    POST /system/jackrabbit/filevault/rcp HTTP/1.1
    Host: localhost:4502
    Content-Type: application/json

    {
        "cmd": "remove",
        "id": "test-id-1234"
    }
    

    HTTP/1.1 200 OK
    Content-Type: application/json;charset=utf-8

    {
        "status": "ok",
        "id": "test-id-1234"
    }




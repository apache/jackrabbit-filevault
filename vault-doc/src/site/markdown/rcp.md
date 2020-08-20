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

<!-- MACRO{toc} -->

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

### Exclusion patterns

Please note that vlt uses the [java regexp](https://docs.oracle.com/javase/7/docs/api/java/util/regex/Pattern.html) to
process the exclusion patterns. The patterns have to patch the entire path of the node in order to be excluded. For
example this regexp `\p{ASCII}*([^\p{ASCII}]\p{ASCII}*)+` excludes all paths containing non-ascii characters.

### HTTP Proxy

HTTP Proxy can be enabled using the system properties outlined at [Java proxy settings](https://docs.oracle.com/javase/8/docs/technotes/guides/net/proxies.html).

For this to work in addition the system property `jackrabbit.client.useSystemProperties` needs to be set to `true`.

#### Example:

```
$ export VLT_OPTS="-Dhttp.proxyHost=my.proxy.com -Dhttp.proxyPort=8888 -Djackrabbit.client.useSystemProperties=true"
$ vlt rcp -e ".*\.txt" -r http://admin:admin@localtest.me:4502/crx/-/jcr:root/content/screens http://admin:admin@localtest.me:4502/crx/-/jcr:root/content_copy
```

Vault RCP Server Bundle
-----------------------
The vault rcp server bundle provides a very simple vault remote copy task management that can be controlled via a json/http interface. This special vault rcp version can only be used to **import** content from remote repositories.

### Usage
The vault rcp server maintains a list of _remote copy tasks_ that can be controlled via the http interface at `/system/jackrabbit/filevault/rcp`. The request and responses are JSON formatted.

#### Get Info (GET)
Exposes information about the RCP ReST endpoint. Is triggered with selector `info` and extension `json`: `/system/jackrabbit/filevault/rcp.info.json`

##### Example

    GET /system/jackrabbit/filevault/rcp.info.json HTTP/1.1
    Host: localhost:4502
    
    HTTP/1.1 200 OK
    Content-Type: application/json;charset=utf-8
    
    {
        "Bundle-SymbolicName": "org.apache.jackrabbit.vault.rcp",
        "Bundle-Version": "3.4.7.SNAPSHOT",
        "Bundle-Vendor": "The Apache Software Foundation"
    }

#### List Tasks (GET)
Simply lists all available tasks. In addition to listing the parameters that were passed when the task was created, it also list some information about the current state of the task.

Optionally you can limit the information to one specific task only by giving its task id as suffix in the form `/system/jackrabbit/filevault/rcp.json/<taskid>`.

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
| cmd          | X  | Needs to be "**create**". |
| id           | \- | Id for new task. if omitted a random id is used. |
| src          | X  | URI of the remote source repository. |
| srcCreds     | \- | Credentials to use for accessing the source repository in the format `<username>{:<password>}`. Alternatively put those in the URI given in `src`. |
| dst          | X  | Destination path in the local repository. |
| batchsize    | \- | Size of batch until intermediate size. Default is 1024. |
| recursive    | \- | **true** to descend recursively. Default is _false_. |
| update       | \- | **true** to overwrite and/or delete existing nodes. Default is _false_. |
| newer        | \- | **true** to respect _lastModified_ properties for update. Default is _false_. |
| throttle     | \- | Number of seconds to sleep after each intermediate save. Default is _0_. |
| resumeFrom   | \- | Source path to resume a prior aborted copy. Note that the algorithm simply skips all source nodes until the _resumeFrom_ path is found. It is necessary that the content structure of the source repository does not change in between runs, and that content already needs to be present in the detination location. |
| excludes     | \- | Array of java regular expressions that exclude source paths. |
| filter       | \- | Serialized [filter.xml](filter.html) specifing which repository areas to copy. Only used if `excludes` is not given. Make sure that the value is properly escaped. |
| allowSelfSignedCertificate | \- | **true** to accept self-signed certificated. Only applicable if src URI starts with https. |
| disableHostnameVerification | \- | **true** to disable host name verification against the certificate. Only applicable if src URI starts with https. |
| connectionTimeoutMs | \- | The connection timeout in milliseconds. 0 for infinite, -1 for system default. |
| requestTimeoutMs | \- | The request timeout in milliseconds. 0 for infinite, -1 for system default. |
| socketTimeoutMs | \- | The socket timeout in milliseconds. 0 for infinite, -1 for system default. |
| useSystemProperties | \- | **true** to use the java default system properties for connection settings. Further information at <https://hc.apache.org/httpcomponents-client-ga/httpclient/apidocs/org/apache/http/impl/client/HttpClientBuilder.html> |
| proxyHost | \- | The host name of the proxy to use. |
| proxyPort | \- | The port of the proxy to use. Only applicable if `proxyHost` is set. |
| proxyProtocol | \- | The protocol for which to use the proxy. Only applicable if `proxyHost` is set. Default (not set) means proxy is used for both HTTP and HTTPs. |
| proxyUsername | \- | The username used for authentication at the proxy. Only applicable if `proxyHost` is set. |
| proxyPassword | \- | The password used for authentication at the proxy. Only applicable if `proxyUsername ` is set. |

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
    Location: /system/jackrabbit/filevault/rcp/test-id-1234
    
    {
        "status": "ok",
        "id": "test-id-1234"
    }
#### Edit Task (POST)
Edits an existing task. Almost al properties are optional. Unused properties are not modified!

| Property     | Required | Comment |
| ------------ | -------- | ------- |
| cmd          | X  | Needs to be "**edit**". |
| id           | X | Id of existing task. |
| src          | \-  | URI of the remote source repository. |
| srcCreds     | \- | Credentials to use for accessing the source repository in the format `<username>{:<password>}`. Alternatively put those in the URI given in `src`. |
| dst          | \-  | Destination path in the local repository. |
| batchsize    | \- | Size of batch until intermediate size. Default is 1024. |
| recursive    | \- | **true** to descend recursively. Default is _false_. |
| update       | \- | **true** to overwrite and/or delete existing nodes. Default is _false_. |
| newer        | \- | **true** to respect _lastModified_ properties for update. Default is _false_. |
| throttle     | \- | Number of seconds to sleep after each intermediate save. Default is _0_. |
| resumeFrom   | \- | Source path to resume a prior aborted copy. Note that the algorithm simply skips all source nodes until the _resumeFrom_ path is found. It is necessary that the content structure of the source repository does not change in between runs, and that content already needs to be present in the detination location. |
| excludes     | \- | Array of java regular expressions that exclude source paths. |
| filter       | \- | Serialized [filter.xml](filter.html) specifing which repository areas to copy. Only used if `excludes` is not given. Make sure that the value is properly escaped. |
| allowSelfSignedCertificate | \- | **true** to accept self-signed certificated. Only applicable if src URI starts with https. |
| disableHostnameVerification | \- | **true** to disable host name verification against the certificate. Only applicable if src URI starts with https. |
| connectionTimeoutMs | \- | The connection timeout in milliseconds. 0 for infinite, -1 for system default. |
| requestTimeoutMs | \- | The request timeout in milliseconds. 0 for infinite, -1 for system default. |
| socketTimeoutMs | \- | The socket timeout in milliseconds. 0 for infinite, -1 for system default. |
| useSystemProperties | \- | **true** to use the java default system properties for connection settings. Further information at <https://hc.apache.org/httpcomponents-client-ga/httpclient/apidocs/org/apache/http/impl/client/HttpClientBuilder.html> |
| proxyHost | \- | The host name of the proxy to use. |
| proxyPort | \- | The port of the proxy to use. Only applicable if `proxyHost` is set. |
| proxyProtocol | \- | The protocol for which to use the proxy. Only applicable if `proxyHost` is set. Default (not set) means proxy is used for both HTTP and HTTPs. |
| proxyUsername | \- | The username used for authentication at the proxy. Only applicable if `proxyHost` is set. |
| proxyPassword | \- | The password used for authentication at the proxy. Only applicable if `proxyUsername ` is set. |


##### Example
    POST /system/jackrabbit/filevault/rcp HTTP/1.1
    Host: localhost:4502
    Content-Type: application/json
    
	{
        "cmd":"edit",
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

    HTTP/1.1 200 OK
    Content-Type: application/json;charset=utf-8
    Location: /system/jackrabbit/filevault/rcp/test-id-1234
    
    {
        "status": "ok",
        "id": "test-id-1234"
    }

    
#### Set Task Credentials (POST)
Sets credentials (or overwrites those) for an already existing task.

| Property     | Required | Comment |
| ------------ | -------- | ------- |
| cmd          | X | Needs to be "**set-credentials**". |
| id           | X | Task ID whose credentials should be set. |
| srcCreds     | /- | Credentials to use for accessing the source repository in the format `<username>{:<password>}`. Leave out to remove credentials |



##### Example
    POST /system/jackrabbit/filevault/rcp HTTP/1.1
    Host: localhost:4502
    Content-Type: application/json
    
	{
        "cmd":"set-credentials",
        "id":"test-id-1234",
        "srcCreds":"myusername:mypassword"
    }

    HTTP/1.1 200 OK
    Content-Type: application/json;charset=utf-8
    
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




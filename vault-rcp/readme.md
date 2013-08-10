Vault RCP Server Bundle
=======================
The vault rcp server bundle provides a very simple vault remote copy task management that can be controlled via a json/http interface. This special vault rcp version can only be used to **import** content from remote repositories.

Testing and Examples
--------------------
The `src/test/resources` directory contains some shell scripts and json payloads that show how the rcp server can be used.

Usage
-----
The vault rcp server maintains a list of _remote copy_ that can be controlled via the http interface at `/libs/granite/packaging/rcp`. The request and responses are JSON formatted. 

### List Tasks (GET)
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

#### Example

    GET /libs/granite/packaging/rcp HTTP/1.1
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


### Create Task (POST)
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


#### Example
    POST /libs/granite/packaging/rcp HTTP/1.1
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
    
### Start Task (POST)
Starts a previously created task.

| Property     | Required | Comment                  |
| ------------ | -------- | ------------------------ |
| cmd          | X        | Needs to be "**start**". |
| id           | X        | Task Id to start.        |


#### Example
    POST /libs/granite/packaging/rcp HTTP/1.1
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


### Stop Task (POST)
Stops a previously start task.

| Property     | Required | Comment                  |
| ------------ | -------- | ------------------------ |
| cmd          | X        | Needs to be "**stop**".  |
| id           | X        | Task Id to start.        |


#### Example
    POST /libs/granite/packaging/rcp HTTP/1.1
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



### Remove Task (POST)
Removes a previously start task.

| Property     | Required | Comment                   |
| ------------ | -------- | ------------------------- |
| cmd          | X        | Needs to be "**remove**". |
| id           | X        | Task Id to start.         |


#### Example
    POST /libs/granite/packaging/rcp HTTP/1.1
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




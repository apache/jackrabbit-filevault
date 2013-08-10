#!/bin/sh
FILE=$1
if [ "$FILE" == "" ]; then
    echo "usage: $0 payload"
    exit 1;
fi 
curl -u admin:admin -X POST --data-binary @$FILE -H "Content-Type: application/json" http://localhost:4502/libs/granite/packaging/rcp
echo ""

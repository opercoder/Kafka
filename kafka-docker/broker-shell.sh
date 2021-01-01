#!/bin/bash
set -e

CONTAINER_ID=$(docker ps | grep "$1" | awk '{ print $1 }')
docker exec -it "$CONTAINER_ID" /bin/bash
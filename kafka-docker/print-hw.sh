#!/bin/bash
set -ex

CONTAINER_ID=$(docker ps | grep "$1" | awk '{ print $1 }')
docker exec -t "$CONTAINER_ID" /opt/kafka/bin/kafka-run-class.sh kafka.tools.GetOffsetShell --broker-list "$1:9092" --time -1 --topic "$2"
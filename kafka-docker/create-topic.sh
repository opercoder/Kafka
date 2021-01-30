#!/bin/bash
set -ex

CONTAINER_ID=$(docker ps | grep "$1" | awk '{ print $1 }')
docker exec -t "$CONTAINER_ID" /opt/kafka/bin/kafka-topics.sh --bootstrap-server "$1:9092" --topic "$2" --replication-factor 3 --partitions "${3:-1}" --create
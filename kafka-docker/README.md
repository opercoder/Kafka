# Kafka Docker

Скрипты и конфиги Docker для запуска и работы с кластером Kafka состоящим из 3 брокеров.
Форк https://github.com/wurstmeister/kafka-docker.

## Cluster Operations

Within `kafka-docker` folder:

### Spin-up a cluster
3 brokers, 1 zk, min.insync.replicas = 2, no auto-topic creation, pinned broker IDs (101,102,103) and ports (9092,9093,9094)

```
$ sed -i '' -e 's/192.168.2.17/YOUR_IP_ADDRESS/' docker-compose-cluster.yaml
$ docker-compose -f docker-compose-cluster.yaml up -d
```

### Open a broker shell

```
$ ./broker-shell.sh [CONTAINER-BROKER-NAME]
$ ./broker-shell.sh kafka-1
```

### Create a topic

```
$ ./create-topic.sh [CONTAINER-BROKER-NAME] [TOPIC-NAME]
$ ./create-topic.sh kafka-1 test
```

### Describe a topic

```
$ ./describe-topic.sh [CONTAINER-BROKER-NAME] [TOPIC-NAME]
$ ./describe-topic.sh kafka-1 test
```

### Delete a topic

```
$ ./delete-topic.sh [CONTAINER-BROKER-NAME] [TOPIC-NAME]
$ ./delete-topic.sh kafka-1 test
```

### Print high-watermark for a topic

```
$ ./print-hw.sh [CONTAINER-BROKER-NAME] [TOPIC-NAME]
$ ./print-hw.sh kafka-1 test
```
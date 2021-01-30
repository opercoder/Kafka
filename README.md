# slurm-kafka

## Cluster Operations

From within `kafka-docker`folder:

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

------

## Clients

First, build the clients: `mvn package`.

### Producer

```
$ java -cp test-clients/target/test-clients-1.0-SNAPSHOT-jar-with-dependencies.jar io.slurm.kafka.TestProducer --help
Usage: test-producer [-hV] [-a=<acks>] -b=<bootstrapServer> -c=<count>
                     [-s=<sleep>] -t=<topic>
Sends messages to a topic continuously
  -a, --acks=<acks>     Acks configuration, one of [1,0,-1] (default: 1)
  -b, --bootstrap-server=<bootstrapServer>
                        Kafka Broker to connect to [HOST:PORT]
  -c, --count=<count>   Number of messages to send
  -h, --help            Show this help message and exit.
  -s, --sleep=<sleep>   Sleep time between message sends, in milliseconds
                          (default: 0ms)
  -t, --topic=<topic>   Kafka Topic to write into
  -V, --version         Print version information and exit.
```

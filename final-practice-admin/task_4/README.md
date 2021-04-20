# Задание 4

## Запустить Trogdor

Отредактировать конфиг примерно как в файле `trogdor.conf`

На каждой ноде запустим агента:

```
./bin/trogdor.sh agent -c ./config/trogdor.conf -n node-1 &> /tmp/trogdor-agent.log &
```

На node-1 запустим координатор:

```
./bin/trogdor.sh coordinator -c ./config/trogdor.conf -n node-1 &> /tmp/trogdor-coordinator.log &
```

## Имитировать неисправность на стороне брокера при помощи ProcessStopFault

```json
{
    "class": "org.apache.kafka.trogdor.fault.ProcessStopFaultSpec",
    "startMs": 1000,
    "durationMs": 30000,
    "nodeNames": ["node-2"],
    "javaProcessName": "Kafka"
}
```

```
./bin/trogdor.sh client createTask -t localhost:8889 -i process_stop --spec ./tests/fault/process_stop.json
```

## Имитировать разрыв сети при помощи NetworkPartitionFault

```json
{
    "class": "org.apache.kafka.trogdor.fault.NetworkPartitionFaultSpec",
    "startMs": 1000,
    "durationMs": 30000,
    "partitions": [["node-1", "node-2"], ["node-3"]]
}
```

Посмотреть, что там с заданием:

```
./bin/trogdor.sh client showTask -t localhost:8889 -i partitioner
```

Выполнить задание без запуска координатора, только на агенте:

```bash
./bin/trogdor.sh agent -n node-1 -c ./config/trogdor.conf --exec ./tests/spec/process_stop.json
```


## Повторить имитации, изменяя продолжительность действия проблемы

Как нетрудно догадаться, за это отвечает настройка `durationMs` в json-файле.

## Увеличить latency между брокерами

```
tc qdisc add dev eth0 root tbf rate 1mbit burst 32kbit latency 400ms
```

## Увеличить latency между продюсером и кластером

```
tc qdisc add dev eth0 root netem delay 200ms
```

Удаление ограничений tc делается так:

```
tc qdisc del dev eth0 root
```

## Выключить один брокер руками “навсегда”. Вернуть ноду в качестве "новой". Восстановить балансировку партиций нашего топика

```bash
systemctl stop kafka
systemctl stop zookeeper

rm /tmp/lib/kafka/kafka-logs/* -rf
rm /tmp/lib/kafka/kafka-logs/.* -rf
vim config/server.properties
```

(нужно заменить id брокера на другое число, например 4)

```bash
vim /opt/kafka_2.13-2.7.0/topics-to-move.json
```

```json
{"topics":  [
    {"topic": "final-practices"}
 ],
"version":1
}
```

```bash
./bin/kafka-reassign-partitions.sh --generate --bootstrap-server node-1.<ваш номер студента>:9092 --topics-to-move-json-file /opt/kafka_2.13-2.7.0/topics-to-move.json --broker-list "1,2,4"
```

```bash
./bin/kafka-reassign-partitions.sh --bootstrap-server node-1.<ваш номер студента>:9092 --reassignment-json-file reassigned.json  --execute
```

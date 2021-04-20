# Задание 3

## Установить Burrow и настроить сбор метрики consumer lag через Prometheus

Вы можете использовать docker-образы, но ниже в целях обучения даны примеры команд, как это сделать без применения контейнеризации.

```
wget https://github.com/linkedin/Burrow/releases/download/v1.3.6/Burrow_1.3.6_linux_amd64.tar.gz

mkdir /opt/burrow
tar xzvf /opt/Burrow_1.3.6_linux_amd64.tar.gz -C /opt/burrow

vim /opt/burrow/config/burrow.toml

useradd burrow

vim /etc/systemd/system/burrow.service

systemctl daemon-reload
systemctl start burrow

cd /opt
wget https://github.com/jirwin/burrow_exporter/releases/download/v0.0.6/burrow-exporter_linux_amd64.zip

unzip -j burrow-exporter_linux_amd64.zip -d /opt/burrow/

vim /etc/systemd/system/burrow-exporter.service

systemctl daemon-reload
systemctl start burrow-exporter

vim /etc/prometheus/prometheus.yml

systemctl restart prometheus
```

## Добавить Prometheus JMX Exporter конфиг файл с запросами для сбора метрик producer/consumer

cd /opt/kafka_2.13-2.7.0/metrics/
wget https://raw.githubusercontent.com/prometheus/jmx_exporter/master/example_configs/kafka-connect.yml

## Создать тестовый топик с 3 партициями, фактором репликации 3, минимальным числом синхронных реплик 2 и запретом на “грязные” выборы лидера

```
./bin/kafka-topics.sh --create --bootstrap-server node-1.<ваш номер студента>:9092 --replication-factor 3 --partitions 3 --config min.insync.replicas=2 --config unclean.leader.election.enable=false --topic final-practice
```

## Запустить ProducerPerformance клиента

```
KAFKA_OPTS="-javaagent:/opt/kafka_2.13-2.7.0/metrics/jmx_prometheus_javaagent-0.15.0.jar=7075:/opt/kafka_2.13-2.7.0/metrics/kafka-connect.yml" /opt/kafka_2.13-2.7.0/bin/kafka-console-producer.sh --bootstrap-server node-1.<ваш номер студента>:9092 --topic final-practice
```

## Запустить ConsumerPerformance клиента

```
KAFKA_OPTS="-javaagent:/opt/kafka_2.13-2.7.0/metrics/jmx_prometheus_javaagent-0.15.0.jar=7076:/opt/kafka_2.13-2.7.0/metrics/kafka-connect.yml" /opt/kafka_2.13-2.7.0/bin/kafka-console-consumer.sh --bootstrap-server node-1.<ваш номер студента>:9092 --topic final-practice --from-beginning
```

## Создать новый дашборд в Grafana c метриками consumer lag и клиентскими метриками из Prometheus

Можно импортировать готовый файл `grafana_dashboard.json`

# Задание 2

Настроим службы Kafka и ZooKeeper для запуска вместе с нашим JMX Exporter агентом на порту 7071/7072 соответственно:

```bash
vim /etc/systemd/system/kafka.service

# добавляем строку под уже имеющимся Environment
Environment=KAFKA_OPTS=-javaagent:/opt/kafka_2.13-2.7.0/metrics/jmx_prometheus_javaagent-0.15.0.jar=7071:/opt/kafka_2.13-2.7.0/metrics/jmx-exporter-kafka.yml
```

```bash
vim /etc/systemd/system/zookeeper.service

# добавляем строку под уже имеющимся Environment
Environment=SERVER_JVMFLAGS=-javaagent:/opt/kafka_2.13-2.7.0/metrics/jmx_prometheus_javaagent-0.15.0.jar=7072:/opt/kafka_2.13-2.7.0/metrics/jmx-exporter-zookeeper.yml
```

Перезагружаем systemd, Kafka и ZooKeeper:

```
systemctl daemon-reload
systemctl restart kafka.service
systemctl restart zookeeper.service
```

Повторить эти манипуляции нужно на каждой ноде.

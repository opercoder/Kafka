# Задание 1

У вас в распоряжении три хоста:

- `node-1.<ваш номер студента>`
- `node-2.<ваш номер студента>`
- `node-3.<ваш номер студента>`

Для простоты переключаемся на суперпользователя:

```bash
sudo -i
```

Выполняем команды ниже:

## Установка Zookeeper

```bash
cd /opt
# wget https://apache-mirror.rbc.ru/pub/apache/zookeeper/zookeeper-3.6.3/apache-zookeeper-3.6.3-bin.tar.gz
tar -xzf apache-zookeeper-3.6.3-bin.tar.gz

useradd zookeeper
mkdir /var/lib/zookeeper && chown zookeeper: /var/lib/zookeeper
mkdir /var/log/zookeeper && chown zookeeper: /var/log/zookeeper

vim /opt/apache-zookeeper-3.6.3-bin/conf/zoo.cfg
# примерное содержимое есть в файле zoo.cfg, нужно отредактировать плейсхолдеры

echo "1" > /var/lib/zookeeper/myid

cat << 'EOF' > /etc/systemd/system/zookeeper.service
[Unit]
Description=Zookeeper
Before=
After=network.target

[Service]
Type=forking
User=zookeeper
Environment=ZOO_LOG_DIR=/var/log/zookeeper JAVA_HOME=/usr/lib/jvm/zulu11 CHDIR=/var/lib/zookeeper
ExecStart=/opt/apache-zookeeper-3.6.3-bin/bin/zkServer.sh start
Restart=on-abort

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
systemctl start zookeeper
```

Всё это нужно повторить на каждой ноде, заменяя идентификаторы для нод в соответствии с их именами (node-1 будет соответствовать "1" в файле myid, и т.д.), а также не забывая про плейсхолдер `<ваш номер студента>`.
Отслеживать, что происходит с zookeeper, можно в логе на каждой ноде: `/var/log/zookeeper/*.out`

## Установка Kafka

```bash
cd /opt
# wget https://archive.apache.org/dist/kafka/2.7.0/kafka_2.13-2.7.0.tgz
tar -xzf kafka_2.13-2.7.0.tgz
cd kafka_2.13-2.7.0/

useradd kafka
mkdir /var/lib/kafka && chown kafka: /var/lib/kafka
mkdir /var/log/kafka && chown kafka: /var/log/kafka
mkdir -p /tmp/lib/kafka/kafka-logs && chown kafka: /tmp/lib/kafka/kafka-logs

vim /opt/kafka_2.13-2.7.0/config/server.properties
# примерное содержимое есть в файле server.properties

cat << 'EOF' > /etc/systemd/system/kafka.service
[Unit]
Description=Kafka Broker
Before=
After=network.target

[Service]
User=kafka
Environment=JAVA_HOME=/usr/lib/jvm/zulu11 CHDIR=/var/lib/kafka LOG_DIR=/var/log/kafka/log
ExecStart=/opt/kafka_2.13-2.7.0/bin/kafka-server-start.sh /opt/kafka_2.13-2.7.0/config/server.properties
Restart=on-abort

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
systemctl start kafka
```

Повторить на каждой ноде.

В конфиге кафки обратите внимание на параметры `broker.id` - они различаются у каждой ноды, а также исправьте `zookeeper.connect` - снова заменив плейсхолдер `<ваш номер студента>` на актуальное для вас значение.
Отслеживать, что происходит с kafka, можно в логе на каждой ноде: `/var/log/kafka/log/server.log`

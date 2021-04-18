# Final Practice: Clients

Решение финального задания по клиентам.

## Сборка

Для сборки запустите `mvn package` в корневой директории проекта.

## [Описание продьюсера сообщений об оплатах](https://gitlab.slurm.io/edu/kafka/-/blob/master/test-clients/src/main/java/io/slurm/kafka/TestProducer.java)

## io.slurm.kafka.ReadWriteApp

Простое транзакционное приложение совмещающее в себе функционал Consumer и Transactional Producer для
Exactly-Once обработки случайно сгенерированных оплат.
Приложение ориентируется на время появления сообщения во входном топике, за счет чего решается следующие проблемы, возникающие при работе с локальным временем:
 - Проблема идемпотентности времени в сгененированных на разных серверах агрегациях
 - Проблема обработки пиковых нагрузок (пришло много сообщений одномоментно и приложение задает период агрегации относительно своей скорости)             

Как было отмечено ранее: корректная имплементация Tumbling Windows полна тонкостей и крайне сложна. 
Наше приложение также не лишено недостатков: для начала генерации Tumbling Windows требуется прочитать хотя бы одно сообщение из топика. 

Слушает заданный входной топик `--in` и группирует сообщения в поминутные (по-умолчанию, параметр `--window-width`) окна со статистикой 
по сумме "оплат" из каждой страны и через каждую платежную систему. Полученные агрегации отправляются в топик `--out`.
Время блокировки потока для опроса кафки задается параметром `--input-poll-freq`

```
$ java -cp final-practice-clients/target/final-practice-clients-1.0-SNAPSHOT-jar-with-dependencies.jar io.slurm.kafka.ReadWriteApp --help
  Usage: <main class> [-hV] -b=<bootstrapServer> -g=<group> --id=<id>
                      --in=<inputTopic> [--input-poll-freq=<inputPollFrequency>]
                      --out=<outputTopic> [-w=<tumblingWindowWidth>]
  Reads messages from a topic and aggregate them to window with stats
    -b, --bootstrap-server=<bootstrapServer>
                          Kafka Broker to connect to [HOST:PORT]
    -g, --group=<group>   Consumer Group ID
    -h, --help            Show this help message and exit.
        --id=<id>         Unique Processor ID
        --in, --input-topic=<inputTopic>
                          Kafka Topic to read from
        --input-poll-freq=<inputPollFrequency>
                          Input topic poll frequency in seconds. (default: 1)
        --out, --output-topic=<outputTopic>
                          Kafka Topic to write into
    -V, --version         Print version information and exit.
    -w, --window-width=<tumblingWindowWidth>
                          Tumbling window width in seconds. (default: 60)
```
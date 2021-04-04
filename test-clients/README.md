# Test Clients

Тестовые клиенты Producer и Consumer использующиеся в практиках.

## Сборка

Для сборки клиентов запустите `mvn package` в корневой директории проекта.

## io.slurm.kafka.TestProducer

Тестовый producer генерирует случайные сообщения типа "оплата" и отправляет их в заданный топик.

Структура сообщения:
```
{
    "uuid":"3ee4184a-f48d-4dc3-91d3-08bbc477f2ce",
    "productName":"Fantastic Aluminum Car",
    "chargedAmount":100,
    "creditCardNumber":"6767-6333-6265-7320",
    "creditCardType":"SOLO",
    "countryCode":"RO",
    "isSuccessful":false
}
```

Позволяет динамически указывать acks, задержку между отправками и включать идемпотентность.

```
$ java -cp test-clients/target/test-clients-1.0-SNAPSHOT-jar-with-dependencies.jar io.slurm.kafka.TestProducer -b=localhost:9092 -t input -c 10000 --randomize-sleep --help
Usage: <main class> [-hiV] [--randomize-sleep] [-a=<acks>] -b=<bootstrapServer>
                    -c=<count> [-s=<sleep>] -t=<topic>
Sends messages to a topic continuously
  -a, --acks=<acks>       Acks configuration, one of [1,0,-1]. If idempotence
                            is enabled "-1" will be enforced. (default: 1)
  -b, --bootstrap-server=<bootstrapServer>
                          Kafka Broker to connect to [HOST:PORT]
  -c, --count=<count>     Number of messages to send
  -h, --help              Show this help message and exit.
  -i, --idempotent        Enable Idempotence (default: false)
      --randomize-sleep   Randomizes sleep interval between subsequent produce
                            requests within [0, --sleep] ms. Default: false
  -s, --sleep=<sleep>     Sleep time between message sends, in milliseconds
                            (default: 0ms)
  -t, --topic=<topic>     Kafka Topic to write into
  -V, --version           Print version information and exit.
```

## io.slurm.kafka.ReadProcessWriteExactlyOnceApp

Простое транзакционное приложение совмещающее в себе функционал Consumer и Transactional Producer для
Exactly-Once обработки случайно сгенерированных оплат.

Слушает заданный входной топик `--in` и отфильтровывает платежи с суммой свыше 90 USD (по-умолчанию) в топик `--out`.

Позволяет динамически контролировать group id, static membership и cooperative rebalancing консюмера.

```
$ java -cp test-clients/target/test-clients-1.0-SNAPSHOT-jar-with-dependencies.jar io.slurm.kafka.ReadProcessWriteExactlyOnceApp --help
Usage: <main class> [-hV] [--[no-]cooperative-rebalancing] [--[no-]
                    static-membership] -b=<bootstrapServer>
                    [--charge-threshold=<suspiciousChargeThreshold>] -g=<group>
                    --id=<id> --in=<inputTopic> --out=<outputTopic>
Reads messages from a topic and processes them
  -b, --bootstrap-server=<bootstrapServer>
                        Kafka Broker to connect to [HOST:PORT]
      --charge-threshold=<suspiciousChargeThreshold>
                        Suspicious Charge Threshold. Default: 90 USD
  -g, --group=<group>   Consumer Group ID
  -h, --help            Show this help message and exit.
      --id=<id>         Unique Processor ID
      --in, --input-topic=<inputTopic>
                        Kafka Topic to read from
      --[no-]cooperative-rebalancing
                        Enable Cooperative Rebalancing. Default: true
      --[no-]static-membership
                        Enable Static Membership. Default: true
      --out, --output-topic=<outputTopic>
                        Kafka Topic to write into
  -V, --version         Print version information and exit.
```
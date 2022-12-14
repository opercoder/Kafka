# Подсказки для выполнения заданий финального проекта

## Задание 1

Установить и запустить кластер из трех брокеров Apache Kafka и трех ZooKeeper

[Ответ на задание 1](task_1/README.md)

## Задание 2

- Добавить JMX Exporter агентов для брокеров Apache Kafka и серверов ZooKeeper (не забыть про рестарт)
- Проверить метрики кластера Apache Kafka на уже готовом дашборде – `http://grafana.<ваш номер студента>.edu.slurm.io/dashboards`

[Ответ на задание 2](task_2/README.md)

## Задание 3

- Установить Burrow и настроить сбор метрики consumer lag через Prometheus;
- Добавить Prometheus JMX Exporter конфиг файл с запросами для сбора метрик producer/consumer.
- Создать тестовый топик с 3 партициями, фактором репликации 3, минимальным числом синхронных реплик 2 и запретом на "грязные" выборы лидера;
- Запустить ProducerPerformance клиента;
- Запустить ConsumerPerformance клиента;
- Создать новый дашборд в Grafana c метриками consumer lag и клиентскими метриками из Prometheus.

[Ответ на задание 3](task_3/README.md)

## Задание 4

- Запустить Trogdor;
- Имитировать неисправность на стороне брокера при помощи ProcessStopFault;
- Имитировать разрыв сети при помощи NetworkPartitionFault;
- Повторить имитации, изменяя продолжительность действия проблемы;
- Увеличить latency между брокерами;
- Увеличить latency между продюсером и кластером;
- Выключить один брокер руками "навсегда". Вернуть ноду в качестве "новой". Восстановить балансировку партиций нашего топика.

[Ответ на задание 4](task_4/README.md)

## Задание 5

- Как мы поняли, что с кластером все в порядке?
- Как мы поймали момент, когда начались проблемы? Какие метрики помогли нам в этом? На какие метрики мы бы хотели повесить алерты? Было ли в это время что-то интересное в логах брокеров? Был ли достаточным уровень логирования на брокерах?
- Все ли партиции были в синхронном состоянии? Как повели себя при этом асинхронные клиенты? А как бы повели синхронные с `acks=all`?
- Что еще могло пойти не так? Как бы мы это заметили?
- Была ли просадка в скорости работы наших клиентов?
- Появились ли дубли? Потеряли ли мы данные? Как можно это понять?
- Можно ли было автоматизировать восстановление после каких-то неисправностей? Есть ли уже готовые инструменты для этого?

- Подумать, что может пойти не так с вашим боевым кластером Kafka и какие требования вы к нему предъявляете. Составить **disaster recovery plan**. В плане описать "флаги", сигнализирующие о наличии проблем и шаги по их устранению.

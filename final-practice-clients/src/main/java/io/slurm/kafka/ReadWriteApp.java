package io.slurm.kafka;

import io.slurm.kafka.message.RandomChargeMessage;
import io.slurm.kafka.message.TumblingWindowMessage;

import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.ProducerFencedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(mixinStandardHelpOptions = true, description = "Reads messages from a topic and aggregate them to window with stats")
public class ReadWriteApp implements Callable<Integer> {

  private final Logger logger = LoggerFactory.getLogger(ReadWriteApp.class);

  // aggregated stats window
  private TumblingWindowMessage tumblingWindow = null;

  // current tumbling window deadline
  private long tumblingWindowTimeoutDeadline = Long.MAX_VALUE;

  private final static SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");

  @Option(names = {"-b",
      "--bootstrap-server"}, required = true, description = "Kafka Broker to connect to [HOST:PORT]")
  private String bootstrapServer;

  @Option(names = {"--in",
      "--input-topic"}, required = true, description = "Kafka Topic to read from")
  private String inputTopic;

  @Option(names = {"--out",
      "--output-topic"}, required = true, description = "Kafka Topic to write into")
  private String outputTopic;

  @Option(names = {"-g", "--group"}, required = true, description = "Consumer Group ID")
  private String group;

  @Option(names = {"--id"}, required = true, description = "Unique Processor ID")
  private String id;

  @Option(names = {"-w",
      "--window-width"}, description = "Tumbling window width in seconds. (default: 60)")
  private int tumblingWindowWidth = 60;

  @Option(names = {
      "--input-poll-freq"}, description = "Input topic poll frequency in seconds. (default: 1)")
  private int inputPollFrequency = 1;

  private Consumer<String, String> consumer;
  private Producer<String, String> producer;

  public Integer call() {
    init();
    consumer.subscribe(Collections.singleton(inputTopic));
    while (!Thread.interrupted()) {
      process();
    }
    return 0;
  }

  private void process() {
    var records = consumer.poll(Duration.ofSeconds(inputPollFrequency));
    try {
      // close the window if no new records arrived within a deadline
      if (records.isEmpty()) {
        if (isTumblingWindowOpen() && tumblingWindowTimeoutDeadline < System.currentTimeMillis()) {
          sendAndClearTumblingWindow();
        }
        return;
      }
      for (ConsumerRecord<String, String> record : records) {
        var currentRecordTimestamp = record.timestamp();
        // open a window if there isn't exist one
        if (!isTumblingWindowOpen()) {
          openTumblingWindow(currentRecordTimestamp);
          // send and re-open a window if the current one is stale
        } else if (tumblingWindow.getWindowEndTime() < currentRecordTimestamp) {
          sendAndClearTumblingWindow();
          openTumblingWindow(currentRecordTimestamp);
        }
        var currentRecordValue = RandomChargeMessage.fromJson(record.value());
        // aggregate stats
        tumblingWindow.appendCountryStats(currentRecordValue.getCountryCode(),
            currentRecordValue.getChargedAmount());
        tumblingWindow.appendCreditCardStats(currentRecordValue.getCreditCardType(),
            currentRecordValue.getChargedAmount());
      }
    } catch (KafkaException e) {
      logger.warn("Encountered Kafka Exception, resetting consumer position", e);
      resetConsumerToLastCommittedPosition();
    }
  }

  private boolean isTumblingWindowOpen() {
    return tumblingWindow != null;
  }

  private void openTumblingWindow(long currentRecordTimestamp) {
    var newWindow = new TumblingWindowMessage(currentRecordTimestamp,
        currentRecordTimestamp + tumblingWindowWidth * 1000L);
    logger.info("Opening a new Tumbling Window for the time interval [{} - {}]({}ms)",
        dateFormat.format(newWindow.getWindowStartTime()),
        dateFormat.format(newWindow.getWindowEndTime()),
        newWindow.getWindowEndTime() - newWindow.getWindowStartTime());
    tumblingWindow = newWindow;
    tumblingWindowTimeoutDeadline = System.currentTimeMillis() + tumblingWindowWidth * 1000L;
  }

  private void sendAndClearTumblingWindow() {
    logger.info("Sending a Tumbling Window for the time interval [{} - {}]({}ms) to topic {}",
        dateFormat.format(tumblingWindow.getWindowStartTime()),
        dateFormat.format(tumblingWindow.getWindowEndTime()),
        tumblingWindow.getWindowEndTime() - tumblingWindow.getWindowStartTime(),
        outputTopic);

    try {
      producer.beginTransaction();
      producer.send(new ProducerRecord<>(outputTopic, tumblingWindow.toJson()));
      var offsets = consumerOffsets();
      producer.sendOffsetsToTransaction(offsets, consumer.groupMetadata());
      producer.commitTransaction();
      logCommittedOffsets(offsets);
    } catch (ProducerFencedException e) {
      throw new RuntimeException(String
          .format("The transactional.id %s has been claimed by another process",
              getTransactionalProducerId()));
    } catch (KafkaException e) {
      logger.warn("Encountered Kafka Exception, aborting transaction", e);
      producer.abortTransaction();
      throw e;
    } finally {
      clearTumblingWindow();
    }
  }

  private void clearTumblingWindow() {
    tumblingWindow = null;
    tumblingWindowTimeoutDeadline = Long.MAX_VALUE;
  }

  private void init() {
    consumer = getIsolatedConsumer();
    producer = getTransactionalProducer();
  }

  private Map<TopicPartition, OffsetAndMetadata> consumerOffsets() {
    return consumer.assignment().stream().collect(Collectors.toMap(
        tp -> tp,
        tp -> new OffsetAndMetadata(consumer.position(tp))
    ));
  }

  private void logCommittedOffsets(Map<TopicPartition, OffsetAndMetadata> offsets) {
    var sb = new StringBuilder();
    sb.append("Committed offsets:\n");
    offsets.forEach((k, v) -> sb.append(String.format("\t%s : %s\n", k, v.offset())));
    logger.info(sb.toString());
  }

  private void resetConsumerToLastCommittedPosition() {
    var committed = consumer.committed(consumer.assignment());
    consumer.assignment().forEach(tp -> {
      var offsetAndMetadata = committed.get(tp);
      if (offsetAndMetadata != null) {
        consumer.seek(tp, offsetAndMetadata.offset());
      } else {
        consumer.seekToBeginning(Collections.singleton(tp));
      }
    });
  }

  private Consumer<String, String> getIsolatedConsumer() {
    var props = new Properties();
    props.put("bootstrap.servers", bootstrapServer);
    props.put("group.id", group);
    // disable automatic offset commits — we need to commit them as part of transaction
    props.put("enable.auto.commit", "false");
    // enable read-level isolation for consumer
    props.put("isolation.level", "read_committed");
    props.put("auto.offset.reset", "earliest");
    props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
    props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
    // limit consumer.poll() to return 1 record at a time
    props.put("max.poll.records", "1");
    return new KafkaConsumer<>(props);
  }

  private Producer<String, String> getTransactionalProducer() {
    var id = getTransactionalProducerId();
    var props = new Properties();
    props.put("bootstrap.servers", bootstrapServer);
    // enable transactions
    props.put("transactional.id", id);
    // It is suggested to have a short transaction timeout to clear pending offsets faster
    // see https://cwiki.apache.org/confluence/display/KAFKA/KIP-447%3A+Producer+scalability+for+exactly+once+semantics
    props.put("transaction.timeout.ms", 10_000);
    props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
    props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");

    var producer = new KafkaProducer<String, String>(props);
    producer.initTransactions();
    return producer;
  }

  private String getTransactionalProducerId() {
    return String.format("%s-producer-%s", group, id);
  }

  public static void main(String... args) {
    System.exit(new CommandLine(new ReadWriteApp()).execute(args));
  }

}

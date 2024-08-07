package io.slurm.kafka;

import io.slurm.kafka.message.RandomChargeMessage;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.FencedInstanceIdException;
import org.apache.kafka.common.errors.ProducerFencedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(mixinStandardHelpOptions = true, description = "Reads messages from a topic and processes them")
public class ReadProcessWriteExactlyOnceApp implements Callable<Integer>,
    ConsumerRebalanceListener {

  private final Logger logger = LoggerFactory
      .getLogger(ReadProcessWriteExactlyOnceApp.class);

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

  @Option(names = {
      "--no-static-membership"}, negatable = true, description = "Enable Static Membership. (default: true)")
  private boolean enableStaticMembership = true;

  @Option(names = {
      "--no-cooperative-rebalancing"}, negatable = true, description = "Enable Cooperative Rebalancing. (default: true)")
  private boolean enableCooperativeRebalancing = true;

  @Option(names = {"--charge-threshold"}, description = "Suspicious Charge Threshold. (default: 90)")
  private int suspiciousChargeThreshold = 90;

  private Consumer<String, String> consumer;
  private Producer<String, String> producer;
  
  public Integer call() {
    init();
    consumer.subscribe(Collections.singleton(inputTopic), this);
    while (!Thread.interrupted()) {
      process();
    }
    return 0;
  }

  private void process() {
    var records = consumer.poll(Duration.ofSeconds(60));
    if (records.isEmpty()) {
      return;
    }

    try {
      producer.beginTransaction();
      var suspiciousCharges = new ArrayList<RandomChargeMessage>();
      records.forEach(record -> {
        var chargeMessage = RandomChargeMessage.fromJson(record.value());
        if (chargeMessage.getChargedAmount() > suspiciousChargeThreshold) {
          suspiciousCharges.add(chargeMessage);
        }
      });
      if (suspiciousCharges.size() > 0) {
        suspiciousCharges
            .forEach(r -> producer.send(new ProducerRecord<>(outputTopic, r.toJson())));
      }
      var offsets = consumerOffsets();
      producer.sendOffsetsToTransaction(offsets, consumer.groupMetadata());
      producer.commitTransaction();
      logger.info("Processed {} records, filtered {} suspicious charges into {}", records.count(),
          suspiciousCharges.size(), outputTopic);
      logCommittedOffsets(offsets);
    } catch (ProducerFencedException e) {
      throw new RuntimeException(String
          .format("The transactional.id %s has been claimed by another process",
              getTransactionalProducerId()));
    } catch (FencedInstanceIdException e) {
      throw new RuntimeException(String
          .format("The group.instance.id %s has been claimed by another process",
              getGroupInstanceId()));
    } catch (KafkaException e) {
      logger
          .warn("Encountered Kafka Exception, aborting transaction and resetting consumer position",
              e);
      producer.abortTransaction();
      resetConsumerToLastCommittedPosition();
    }
  }

  private void logCommittedOffsets(Map<TopicPartition, OffsetAndMetadata> offsets) {
    var sb = new StringBuilder();
    sb.append("Committed offsets:\n");
    offsets.forEach((k, v) -> sb.append(String.format("\t%s : %s\n", k, v.offset())));
    logger.info(sb.toString());
  }

  private void init() {
    consumer = getIsolatedConsumer();
    producer = getTransactionalProducer();
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

  private Map<TopicPartition, OffsetAndMetadata> consumerOffsets() {
    return consumer.assignment().stream().collect(Collectors.toMap(
        tp -> tp,
        tp -> new OffsetAndMetadata(consumer.position(tp))
    ));
  }

  @Override
  public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
    logger.info("Received partition assignment after rebalancing: " + partitions);
    emulateSleep(partitions);
  }

  @Override
  public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
    logger.info("Revoked partition assignment to kick-off rebalancing: " + partitions);
    emulateSleep(partitions);
  }

  private void emulateSleep(Collection<TopicPartition> partitions) {
    if (partitions.isEmpty()) {
      return;
    }
    int sleepMs = partitions.size() * 1_000;
    logger.info("Sleeping for {} ms to emulate long assignment process for {} partitions...",
        sleepMs, partitions.size());
    try {
      Thread.sleep(sleepMs);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private Consumer<String, String> getIsolatedConsumer() {
    var props = new Properties();
    props.put("bootstrap.servers", bootstrapServer);
    props.put("group.id", group);
    // disable automatic offset commits — we need to commit them as part of transaction
    props.put("enable.auto.commit", "false");
    // enable read-level isolation for consumer
    props.put("isolation.level", "read_committed");
    if (enableStaticMembership) {
      props.put("group.instance.id", getGroupInstanceId());
    }
    props.put("auto.offset.reset", "earliest");
    if (enableCooperativeRebalancing) {
      props.put("partition.assignment.strategy",
          "org.apache.kafka.clients.consumer.CooperativeStickyAssignor");
    }
    props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
    props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
    // tell consumer to fetch messages in slightly bigger batches than default (1 byte)
    props.put("fetch.min.bytes", "1000");
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

  private String getGroupInstanceId() {
    return String.format("%s-consumer-%s", group, id);
  }

  private String getTransactionalProducerId() {
    return String.format("%s-producer-%s", group, id);
  }

  public static void main(String... args) {
    System.exit(new CommandLine(new ReadProcessWriteExactlyOnceApp()).execute(args));
  }

}

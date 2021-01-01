package io.slurm.kafka;

import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "test-producer", mixinStandardHelpOptions = true, version = "1.0",
    description = "Sends messages to a topic continuously")
public class TestProducer implements Callable<Integer> {

  private final Logger logger = LoggerFactory.getLogger(TestProducer.class);

  @Option(names = {"-b",
      "--bootstrap-server"}, required = true, description = "Kafka Broker to connect to [HOST:PORT]")
  private String bootstrapServer;

  @Option(names = {"-t", "--topic"}, required = true, description = "Kafka Topic to write into")
  private String topic;

  @Option(names = {"-a", "--acks"}, description = "Acks configuration, one of [1,0,-1] (default: 1)")
  private String acks = "1";

  @Option(names = {"-c", "--count"}, required = true, description = "Number of messages to send")
  private int count;

  @Option(names = {"-s", "--sleep"}, description = "Sleep time between message sends, in milliseconds (default: 0ms)")
  private long sleep = 0;

  private int sentTotal = 0;
  private final AtomicInteger errorTotal = new AtomicInteger(0);
  private final AtomicInteger deliveredTotal = new AtomicInteger(0);

  public Integer call() throws InterruptedException {
    var props = new Properties();
    props.put("client.id", "slurm-producer");
    props.put("bootstrap.servers", bootstrapServer);
    props.put("acks", acks);
    props.put("retries", "0");
    props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
    props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");

    var producer = new KafkaProducer<String, String>(props);
    for (int i = 0; i < count; i++) {
      producer.send(new ProducerRecord<>(topic, Integer.toString(i)), (recordMetadata, e) -> {
        if (e != null) {
          errorTotal.incrementAndGet();
        } else {
          deliveredTotal.incrementAndGet();
        }
        if ((deliveredTotal.get() + errorTotal.get()) % 10000 == 0) {
          printStats();
        }
      });
      sentTotal++;
      if (sleep > 0) {
        Thread.sleep(sleep);
      }
    }
    producer.close();
    logger.info("--- Final Stats ---");
    printStats();
    return 0;
  }

  private void printStats() {
    logger.info("Sent: {}, Delivered: {}, Failed: {}", sentTotal, deliveredTotal.get(),
        errorTotal.get());
  }

  public static void main(String... args) {
    System.exit(new CommandLine(new TestProducer()).execute(args));
  }

}

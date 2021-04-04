package io.slurm.kafka;

import io.slurm.kafka.message.RandomChargeMessage;
import java.util.Arrays;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.Callable;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(mixinStandardHelpOptions = true, description = "Sends messages to a topic continuously")
public class TestProducer implements Callable<Integer> {

  private final static Random random = new Random();
  private final Logger logger = LoggerFactory.getLogger(TestProducer.class);

  @Option(names = {"-b",
      "--bootstrap-server"}, required = true, description = "Kafka Broker to connect to [HOST:PORT]")
  private String bootstrapServer;

  @Option(names = {"-t", "--topic"}, required = true, description = "Kafka Topic to write into")
  private String topic;

  @Option(names = {"-a",
      "--acks"}, description = "Acks configuration, one of [1,0,-1]. If idempotence is enabled \"-1\" will be enforced. (default: 1)")
  private String acks = "1";

  @Option(names = {"-c", "--count"}, required = true, description = "Number of messages to send")
  private int count;

  @Option(names = {"-s",
      "--sleep"}, description = "Sleep time between message sends, in milliseconds. Can be randomized with --randomize-sleep option. (default: 0ms)")
  private int sleep = 0;

  @Option(names = {
      "--randomize-sleep"}, description = "Randomizes sleep interval between subsequent produce requests within [0, --sleep] ms. (default: false)")
  private boolean randomizeSleep = false;

  @Option(names = {"-i", "--idempotent"}, description = "Enable Idempotence (default: false)")
  private boolean idempotent = false;

  @SuppressWarnings("BusyWait")
  public Integer call() throws InterruptedException {
    var props = new Properties();
    props.put("client.id", "slurm-producer");
    props.put("bootstrap.servers", bootstrapServer);
    props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
    props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
    if (idempotent) {
      props.put("enable.idempotence", "true");
    } else {
      props.put("acks", acks);
      props.put("retries", "0");
    }

    if (randomizeSleep && sleep == 0) {
      throw new IllegalArgumentException("Randomized sleep requires --sleep to be above 0");
    }

    var producer = new KafkaProducer<String, String>(props);
    var stats = new Stats(count, 1000);

    for (int i = 0; i < count; i++) {
      var sendStartMs = System.currentTimeMillis();
      producer.send(new ProducerRecord<>(topic, new RandomChargeMessage().toJson()),
          stats.nextCompletion(sendStartMs));
      if (sleep > 0) {
        var sleepTimeMs = randomizeSleep ? random.nextInt(sleep) : sleep;
        logger.debug("Sleeping for {}ms in-between produce requests", sleepTimeMs);
        Thread.sleep(sleepTimeMs);
      }
    }
    producer.close();
    logger.info("--- Final Stats ---");
    stats.printTotal();
    return 0;
  }

  private static class Stats {

    private final Logger logger = LoggerFactory.getLogger(Stats.class);

    private final long start;
    private long windowStart;
    private final int[] latencies;
    private final int sampling;
    private int iteration;
    private int index;
    private long deliveredCount;
    private long errorCount;
    private int maxLatency;
    private long totalLatency;
    private long windowDelivered;
    private long windowErrors;
    private int windowMaxLatency;
    private long windowTotalLatency;
    private final long reportingInterval;

    public Stats(long numRecords, int reportingInterval) {
      this.start = System.currentTimeMillis();
      this.windowStart = System.currentTimeMillis();
      this.iteration = 0;
      this.sampling = (int) (numRecords / Math.min(numRecords, 500000));
      this.latencies = new int[(int) (numRecords / this.sampling) + 1];
      this.index = 0;
      this.maxLatency = 0;
      this.totalLatency = 0;
      this.windowDelivered = 0;
      this.windowMaxLatency = 0;
      this.windowTotalLatency = 0;
      this.totalLatency = 0;
      this.reportingInterval = reportingInterval;
    }

    public void record(int iter, boolean isSuccessful, int latency, long time) {
      if (isSuccessful) {
        this.deliveredCount++;
      } else {
        this.errorCount++;
      }
      this.totalLatency += latency;
      this.maxLatency = Math.max(this.maxLatency, latency);
      if (isSuccessful) {
        this.windowDelivered++;
      } else {
        this.windowErrors++;
      }
      this.windowTotalLatency += latency;
      this.windowMaxLatency = Math.max(windowMaxLatency, latency);
      if (iter % this.sampling == 0) {
        this.latencies[index] = latency;
        this.index++;
      }
      /* maybe report the recent perf */
      if (time - windowStart >= reportingInterval) {
        printWindow();
        newWindow();
      }
    }

    public Callback nextCompletion(long start) {
      var cb = new PerfCallback(this.iteration, start, this);
      this.iteration++;
      return cb;
    }

    public void printWindow() {
      long elapsed = System.currentTimeMillis() - windowStart;
      double recsPerSec = 1000.0 * windowDelivered / (double) elapsed;
      logger.info(String.format(
          "%d records delivered, %d records failed, %.1f records/sec, %.1f ms avg latency, %.1f ms max latency.%n",
          windowDelivered,
          windowErrors,
          recsPerSec,
          windowTotalLatency / (double) windowDelivered,
          (double) windowMaxLatency)
      );
    }

    public void newWindow() {
      this.windowStart = System.currentTimeMillis();
      this.windowDelivered = 0;
      this.windowErrors = 0;
      this.windowMaxLatency = 0;
      this.windowTotalLatency = 0;
    }

    public void printTotal() {
      long elapsed = System.currentTimeMillis() - start;
      double recsPerSec = 1000.0 * deliveredCount / (double) elapsed;
      int[] percs = percentiles(this.latencies, index, 0.5, 0.95, 0.99, 0.999);
      logger.info(String.format(
          "%d records delivered, %d records failed, %f records/sec, %.2f ms avg latency, %.2f ms max latency, %d ms 50th, %d ms 95th, %d ms 99th, %d ms 99.9th.%n",
          deliveredCount,
          errorCount,
          recsPerSec,
          totalLatency / (double) deliveredCount,
          (double) maxLatency,
          percs[0],
          percs[1],
          percs[2],
          percs[3])
      );
    }

    private static int[] percentiles(int[] latencies, int count, double... percentiles) {
      int size = Math.min(count, latencies.length);
      Arrays.sort(latencies, 0, size);
      int[] values = new int[percentiles.length];
      for (int i = 0; i < percentiles.length; i++) {
        int index = (int) (percentiles[i] * size);
        values[i] = latencies[index];
      }
      return values;
    }
  }

  private static final class PerfCallback implements Callback {

    private final long start;
    private final int iteration;
    private final Stats stats;

    public PerfCallback(int iter, long start, Stats stats) {
      this.start = start;
      this.stats = stats;
      this.iteration = iter;
    }

    public void onCompletion(RecordMetadata metadata, Exception exception) {
      long now = System.currentTimeMillis();
      int latency = (int) (now - start);
      boolean isSuccessful = exception == null;
      this.stats.record(iteration, isSuccessful, latency, now);
      if (exception != null) {
        exception.printStackTrace();
      }
    }
  }

  public static void main(String... args) {
    System.exit(new CommandLine(new TestProducer()).execute(args));
  }

}

package io.slurm.kafka.message;

import com.google.gson.Gson;
import java.util.Random;
import java.util.UUID;

public class RandomChargeMessage {

  private final static Random random = new Random();
  private final static Gson gson = new Gson();
  private String uuid;
  private int chargedAmount;
  private String creditCardNumber;

  public RandomChargeMessage() {
    this.uuid = UUID.randomUUID().toString();
    this.chargedAmount = 1 + random.nextInt(99_999);
    this.creditCardNumber = generateCardNumber();
  }

  public String toJson() {
    return gson.toJson(this);
  }

  public static RandomChargeMessage fromJson(String json) {
    return gson.fromJson(json, RandomChargeMessage.class);
  }

  private String generateCardNumber() {
    var builder = new StringBuilder();
    for (int i = 0; i < 16; i++) {
      int generate = random.nextInt(9);
      builder.append(generate);
    }
    return builder.toString();
  }

  public String getUuid() {
    return uuid;
  }

  public int getChargedAmount() {
    return chargedAmount;
  }

  public String getCreditCardNumber() {
    return creditCardNumber;
  }

}

package io.slurm.kafka.message;

import com.github.javafaker.CreditCardType;
import com.github.javafaker.Faker;
import com.google.gson.Gson;
import java.util.UUID;

public class RandomChargeMessage {

  private final static Gson gson = new Gson();
  private final static Faker faker = new Faker();

  private final String uuid;
  private final String productName;
  private final int chargedAmount;
  private final String creditCardNumber;
  private final String creditCardType;
  private final String countryCode;

  public RandomChargeMessage() {
    var creditCardType = randomCreditCardType();
    this.uuid = UUID.randomUUID().toString();
    this.productName = faker.commerce().productName();
    this.chargedAmount = 1 + faker.random().nextInt(100);
    this.creditCardNumber = faker.finance().creditCard(creditCardType);
    this.creditCardType = creditCardType.name();
    this.countryCode = faker.address().countryCode();
  }

  private CreditCardType randomCreditCardType() {
    return CreditCardType.values()[faker.random().nextInt(CreditCardType.values().length)];
  }

  public String toJson() {
    return gson.toJson(this);
  }

  public static RandomChargeMessage fromJson(String json) {
    return gson.fromJson(json, RandomChargeMessage.class);
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

  public String getProductName() {
    return productName;
  }

  public String getCreditCardType() {
    return creditCardType;
  }

  public String getCountryCode() {
    return countryCode;
  }

}

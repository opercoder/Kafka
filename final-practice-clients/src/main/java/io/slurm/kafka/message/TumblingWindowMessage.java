package io.slurm.kafka.message;

import com.google.gson.Gson;

import java.util.Map;
import java.util.HashMap;

public class TumblingWindowMessage {

  private final static Gson gson = new Gson();

  private final long windowStartTime;
  private final long windowEndTime;
  private final Map<String, Map<String, Integer>> windowsStats = new HashMap<>() {{
    put("byCountryStats", new HashMap<>());
    put("byCreditCardTypeStats", new HashMap<>());
  }};

  public TumblingWindowMessage(long windowStartTime, long windowEndTime) {
    this.windowStartTime = windowStartTime;
    this.windowEndTime = windowEndTime;
  }

  public String toJson() {
    return gson.toJson(this);
  }

  public long getWindowStartTime() {
    return this.windowStartTime;
  }

  public long getWindowEndTime() {
    return windowEndTime;
  }

  public void appendCountryStats(String country, int value) {
    int currentStats = windowsStats.get("byCountryStats").getOrDefault(country, 0);
    windowsStats.get("byCountryStats").put(country, currentStats + value);
  }

  public void appendCreditCardStats(String creditCardType, int value) {
    int currentStats = windowsStats.get("byCreditCardTypeStats")
        .getOrDefault(creditCardType, 0);
    windowsStats.get("byCreditCardTypeStats").put(creditCardType, currentStats + value);
  }
}

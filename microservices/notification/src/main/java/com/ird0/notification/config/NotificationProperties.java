package com.ird0.notification.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "notification")
public class NotificationProperties {

  private Api api = new Api();
  private Directory directory = new Directory();
  private Webhook webhook = new Webhook();
  private Retry retry = new Retry();
  private Scheduler scheduler = new Scheduler();

  @Data
  public static class Api {
    @SuppressWarnings("java:S1075") // Default value, configurable via properties
    private String basePath = "/api/v1/notifications";
  }

  @Data
  public static class Directory {
    private String insurersUrl = "http://localhost:8084";
  }

  @Data
  public static class Webhook {
    private int connectTimeout = 5000;
    private int readTimeout = 10000;
  }

  @Data
  public static class Retry {
    private int maxAttempts = 5;
    private long initialDelay = 1000;
    private double backoffMultiplier = 2.0;
    private long maxDelay = 60000;
  }

  @Data
  public static class Scheduler {
    private long fixedDelay = 30000;
    private long initialDelay = 10000;
  }
}

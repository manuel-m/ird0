package com.ird0.incident.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "incident")
public class IncidentProperties {

  private Api api = new Api();
  private Directory directory = new Directory();
  private Notification notification = new Notification();

  @Data
  public static class Api {
    @SuppressWarnings("java:S1075") // Default value, configurable via properties
    private String basePath = "/api/v1/incidents";
  }

  @Data
  public static class Directory {
    private String policyholdersUrl = "http://localhost:8081";
    private String insurersUrl = "http://localhost:8084";
    private String expertsUrl = "http://localhost:8082";
    private String providersUrl = "http://localhost:8083";
  }

  @Data
  public static class Notification {
    private String url = "http://localhost:8086";
    private boolean enabled = true;
  }
}

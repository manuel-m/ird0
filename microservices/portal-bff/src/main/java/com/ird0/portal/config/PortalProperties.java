package com.ird0.portal.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "portal")
public class PortalProperties {

  private Api api = new Api();
  private Services services = new Services();

  @Data
  public static class Api {
    private String basePath = "/api/portal/v1";
  }

  @Data
  public static class Services {
    private String incidentUrl = "http://localhost:8085";
    private String policyholdersUrl = "http://localhost:8081";
    private String expertsUrl = "http://localhost:8082";
    private String insurersUrl = "http://localhost:8084";
  }
}

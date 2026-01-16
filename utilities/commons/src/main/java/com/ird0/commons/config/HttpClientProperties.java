package com.ird0.commons.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "commons.http-client")
public class HttpClientProperties {

  private long connectTimeout = 5000;
  private long readTimeout = 10000;
}

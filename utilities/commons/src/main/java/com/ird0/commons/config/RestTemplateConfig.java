package com.ird0.commons.config;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(HttpClientProperties.class)
public class RestTemplateConfig {

  private final HttpClientProperties properties;

  @Bean
  @ConditionalOnMissingBean
  public RestTemplate restTemplate(RestTemplateBuilder builder) {
    return builder
        .connectTimeout(Duration.ofMillis(properties.getConnectTimeout()))
        .readTimeout(Duration.ofMillis(properties.getReadTimeout()))
        .build();
  }
}

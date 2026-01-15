package com.ird0.notification.config;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
@RequiredArgsConstructor
public class RestTemplateConfig {

  private final NotificationProperties properties;

  @Bean
  public RestTemplate restTemplate(RestTemplateBuilder builder) {
    return builder
        .connectTimeout(Duration.ofMillis(properties.getWebhook().getConnectTimeout()))
        .readTimeout(Duration.ofMillis(properties.getWebhook().getReadTimeout()))
        .build();
  }
}

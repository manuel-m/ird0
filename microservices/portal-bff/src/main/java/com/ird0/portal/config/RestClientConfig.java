package com.ird0.portal.config;

import java.time.Duration;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestClientConfig {

  @Bean
  public RestClient restClient(RestTemplateBuilder restTemplateBuilder) {
    RestTemplate restTemplate =
        restTemplateBuilder
            .connectTimeout(Duration.ofSeconds(5))
            .readTimeout(Duration.ofSeconds(30))
            .build();
    return RestClient.create(restTemplate);
  }

  @Bean
  public RestClient.Builder restClientBuilder() {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(5000);
    requestFactory.setReadTimeout(30000);
    return RestClient.builder().requestFactory(requestFactory);
  }
}

package com.ird0.portal.config;

import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Configuration
public class RestClientConfig {

  private static final String CLIENT_REGISTRATION_ID = "portal-bff";

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
  @Primary
  public RestClient.Builder restClientBuilder(
      @Autowired(required = false) OAuth2AuthorizedClientManager authorizedClientManager) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(5000);
    requestFactory.setReadTimeout(30000);

    RestClient.Builder builder = RestClient.builder().requestFactory(requestFactory);

    if (authorizedClientManager != null) {
      builder.requestInterceptor(
          (request, body, execution) -> {
            OAuth2AuthorizeRequest authorizeRequest =
                OAuth2AuthorizeRequest.withClientRegistrationId(CLIENT_REGISTRATION_ID)
                    .principal(CLIENT_REGISTRATION_ID)
                    .build();
            OAuth2AuthorizedClient authorizedClient =
                authorizedClientManager.authorize(authorizeRequest);
            if (authorizedClient == null) {
              log.error(
                  "Failed to obtain OAuth2 access token for client '{}'. "
                      + "Check client credentials and Keycloak connectivity.",
                  CLIENT_REGISTRATION_ID);
              throw new IllegalStateException(
                  "Unable to obtain access token for service-to-service authentication. "
                      + "Verify KEYCLOAK_CLIENT_SECRET matches the Keycloak client configuration.");
            }
            request.getHeaders().setBearerAuth(authorizedClient.getAccessToken().getTokenValue());
            return execution.execute(request, body);
          });
    }

    return builder;
  }
}

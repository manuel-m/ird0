package com.ird0.notification.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  @Value("${openapi.server.url:http://localhost:8086}")
  private String serverUrl;

  @Bean
  public OpenAPI notificationOpenAPI() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Notification Service API")
                .description("REST API for webhook notifications")
                .version("1.0.0")
                .contact(new Contact().name("IRD0 Development Team")))
        .servers(List.of(new Server().url(serverUrl).description("API Server")));
  }
}

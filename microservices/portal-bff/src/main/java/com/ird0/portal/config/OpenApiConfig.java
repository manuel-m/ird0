package com.ird0.portal.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  private static final String SECURITY_SCHEME_NAME = "oauth2";

  @Value("${openapi.server.url:}")
  private String serverUrl;

  @Value(
      "${spring.security.oauth2.resourceserver.jwt.issuer-uri:http://localhost:8180/realms/ird0}")
  private String issuerUri;

  @Bean
  public OpenAPI portalOpenAPI() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Insurance Portal API")
                .description("Backend-for-Frontend API for the Insurance Portal")
                .version("1.0.0")
                .contact(new Contact().name("IRD0 Development Team")))
        .components(
            new Components()
                .addSecuritySchemes(
                    SECURITY_SCHEME_NAME,
                    new SecurityScheme()
                        .type(SecurityScheme.Type.OAUTH2)
                        .flows(
                            new OAuthFlows()
                                .authorizationCode(
                                    new OAuthFlow()
                                        .authorizationUrl(
                                            issuerUri + "/protocol/openid-connect/auth")
                                        .tokenUrl(issuerUri + "/protocol/openid-connect/token")
                                        .scopes(
                                            new Scopes()
                                                .addString("openid", "OpenID Connect")
                                                .addString("profile", "User profile")
                                                .addString("email", "Email address"))))))
        .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME));
  }

  @Bean
  public OpenApiCustomizer serverUrlCustomizer() {
    return openApi -> {
      // When server URL is configured, set it (for Swagger UI "Try it out")
      // When not configured, use "/" to generate relative paths in clients
      // Note: "/" prevents SpringDoc from adding an auto-generated absolute URL
      if (serverUrl != null && !serverUrl.isBlank()) {
        openApi.servers(List.of(new Server().url(serverUrl).description("API Server")));
      } else {
        openApi.servers(List.of(new Server().url("/").description("Relative path")));
      }
    };
  }
}

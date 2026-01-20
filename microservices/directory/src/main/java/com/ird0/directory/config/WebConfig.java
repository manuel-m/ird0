package com.ird0.directory.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    // Allow CORS for OpenAPI endpoints (Swagger UI aggregation)
    registry
        .addMapping("/v3/api-docs/**")
        .allowedOrigins("*")
        .allowedMethods("GET", "OPTIONS")
        .allowedHeaders("*")
        .maxAge(3600);

    registry
        .addMapping("/swagger-ui/**")
        .allowedOrigins("*")
        .allowedMethods("GET", "OPTIONS")
        .allowedHeaders("*")
        .maxAge(3600);

    // Allow CORS for API endpoints (Swagger UI "Try it out")
    registry
        .addMapping("/api/**")
        .allowedOrigins("*")
        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
        .allowedHeaders("*")
        .maxAge(3600);
  }
}

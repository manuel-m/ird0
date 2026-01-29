package com.ird0.portal.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Slf4j
@Configuration
@EnableWebSecurity
@Profile("nosecurity")
public class NoSecurityConfig {

  @PostConstruct
  public void warnNoSecurity() {
    log.warn("##########################################################");
    log.warn("#  WARNING: Security is DISABLED ('nosecurity' profile)  #");
    log.warn("#  All endpoints are publicly accessible without auth!   #");
    log.warn("#  DO NOT USE IN PRODUCTION!                             #");
    log.warn("##########################################################");
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable()).authorizeHttpRequests(auth -> auth.anyRequest().permitAll());

    return http.build();
  }
}

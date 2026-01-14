package com.ird0.directory.config;

import java.io.File;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.integration.file.remote.session.CachingSessionFactory;
import org.springframework.integration.file.remote.session.SessionFactory;
import org.springframework.integration.sftp.session.DefaultSftpSessionFactory;

@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "directory.sftp-import", name = "enabled", havingValue = "true")
public class SftpIntegrationConfig {

  private final SftpImportProperties properties;

  @Bean
  @SuppressWarnings("rawtypes")
  public SessionFactory sftpSessionFactory() {
    DefaultSftpSessionFactory factory = new DefaultSftpSessionFactory(true);
    factory.setHost(properties.getHost());
    factory.setPort(properties.getPort());
    factory.setUser(properties.getUsername());
    // Configure host key verification using known_hosts file
    File knownHostsFile = new File(properties.getKnownHostsPath());
    if (knownHostsFile.exists()) {
      factory.setKnownHostsResource(new FileSystemResource(knownHostsFile));
      log.info("SFTP host key verification enabled using: {}", properties.getKnownHostsPath());
    } else {
      log.warn(
          "Known hosts file not found at: {} - SFTP connections will fail for security",
          properties.getKnownHostsPath());
    }

    File privateKeyFile = new File(properties.getPrivateKeyPath());
    if (privateKeyFile.exists()) {
      factory.setPrivateKey(new FileSystemResource(privateKeyFile));
      log.info(
          "SFTP session factory configured: host={}, port={}",
          properties.getHost(),
          properties.getPort());
    } else {
      log.warn("SFTP private key not found - SFTP import may fail");
    }

    factory.setTimeout(properties.getConnectionTimeout());

    return new CachingSessionFactory<>(factory);
  }
}

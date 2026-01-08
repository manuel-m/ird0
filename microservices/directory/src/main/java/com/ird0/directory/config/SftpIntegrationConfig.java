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
    factory.setAllowUnknownKeys(true);

    File privateKeyFile = new File(properties.getPrivateKeyPath());
    if (privateKeyFile.exists()) {
      factory.setPrivateKey(new FileSystemResource(privateKeyFile));
      log.info(
          "SFTP session factory configured: host={}, port={}, user={}, privateKey={}",
          properties.getHost(),
          properties.getPort(),
          properties.getUsername(),
          properties.getPrivateKeyPath());
    } else {
      log.warn(
          "SFTP private key not found at: {} - SFTP import may fail",
          properties.getPrivateKeyPath());
    }

    factory.setTimeout(properties.getConnectionTimeout());

    return new CachingSessionFactory<>(factory);
  }
}

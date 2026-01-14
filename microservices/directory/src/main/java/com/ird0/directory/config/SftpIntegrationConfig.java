package com.ird0.directory.config;

import java.io.File;
import java.io.IOException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.integration.file.remote.session.CachingSessionFactory;
import org.springframework.integration.file.remote.session.SessionFactory;
import org.springframework.integration.sftp.session.DefaultSftpSessionFactory;

@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "directory.sftp-import", name = "enabled", havingValue = "true")
public class SftpIntegrationConfig {

  private final SftpImportProperties properties;
  private final Optional<VaultSftpKeyLoader> vaultSftpKeyLoader;

  @Bean
  @SuppressWarnings("rawtypes")
  public SessionFactory sftpSessionFactory() throws IOException {
    DefaultSftpSessionFactory factory = new DefaultSftpSessionFactory(true);
    factory.setHost(properties.getHost());
    factory.setPort(properties.getPort());
    factory.setUser(properties.getUsername());

    // Configure known hosts - try Vault first, then file
    configureKnownHosts(factory);

    // Configure private key - try Vault first, then file
    configurePrivateKey(factory);

    factory.setTimeout(properties.getConnectionTimeout());

    log.info(
        "SFTP session factory configured: host={}, port={}",
        properties.getHost(),
        properties.getPort());

    return new CachingSessionFactory<>(factory);
  }

  private void configureKnownHosts(DefaultSftpSessionFactory factory) throws IOException {
    // Try Vault first
    if (vaultSftpKeyLoader.isPresent() && vaultSftpKeyLoader.get().hasKnownHosts()) {
      try {
        Resource knownHostsResource = vaultSftpKeyLoader.get().getKnownHostsResource();
        factory.setKnownHostsResource(knownHostsResource);
        log.info("SFTP host key verification enabled using Vault");
        return;
      } catch (IOException e) {
        log.warn("Failed to load known hosts from Vault: {}, falling back to file", e.getMessage());
      }
    }

    // Fall back to file
    File knownHostsFile = new File(properties.getKnownHostsPath());
    if (knownHostsFile.exists()) {
      factory.setKnownHostsResource(new FileSystemResource(knownHostsFile));
      log.info("SFTP host key verification enabled using: {}", properties.getKnownHostsPath());
    } else {
      log.warn(
          "Known hosts file not found at: {} - SFTP connections will fail for security",
          properties.getKnownHostsPath());
    }
  }

  private void configurePrivateKey(DefaultSftpSessionFactory factory) throws IOException {
    // Try Vault first
    if (vaultSftpKeyLoader.isPresent() && vaultSftpKeyLoader.get().hasClientKey()) {
      try {
        Resource clientKeyResource = vaultSftpKeyLoader.get().getClientKeyResource();
        factory.setPrivateKey(clientKeyResource);
        log.info("SFTP client private key loaded from Vault");
        return;
      } catch (IOException e) {
        log.warn("Failed to load client key from Vault: {}, falling back to file", e.getMessage());
      }
    }

    // Fall back to file
    File privateKeyFile = new File(properties.getPrivateKeyPath());
    if (privateKeyFile.exists()) {
      factory.setPrivateKey(new FileSystemResource(privateKeyFile));
      log.info("SFTP client private key loaded from: {}", properties.getPrivateKeyPath());
    } else {
      log.warn("SFTP private key not found - SFTP import may fail");
    }
  }
}

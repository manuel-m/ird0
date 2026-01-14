package com.ird0.directory.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.metadata.MetadataStore;
import org.springframework.integration.metadata.PropertiesPersistingMetadataStore;

/**
 * Configuration for MetadataStore used to track processed SFTP files.
 *
 * <p>The metadata store persists information about processed files (like timestamps) to prevent
 * re-processing unchanged files across application restarts.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "directory.sftp-import", name = "enabled", havingValue = "true")
public class MetadataStoreConfig {

  private final SftpImportProperties properties;

  @Bean
  public MetadataStore metadataStore() {
    PropertiesPersistingMetadataStore store = new PropertiesPersistingMetadataStore();
    store.setBaseDirectory(properties.getMetadataDirectory());
    log.info("MetadataStore configured (persistent): {}", properties.getMetadataDirectory());
    return store;
  }
}

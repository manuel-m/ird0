package com.ird0.directory.startup;

import com.ird0.directory.config.SftpImportProperties;
import com.ird0.directory.repository.DirectoryEntryRepository;
import com.ird0.directory.service.CsvImportService;
import com.ird0.directory.service.SftpClientService;
import java.io.InputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "directory.sftp-import", name = "enabled", havingValue = "true")
public class DataImportRunner implements ApplicationRunner {

  private final SftpImportProperties properties;
  private final SftpClientService sftpClient;
  private final CsvImportService csvImporter;
  private final DirectoryEntryRepository repository;

  @Override
  public void run(ApplicationArguments args) {
    if (!properties.isEnabled()) {
      log.info("SFTP import is disabled");
      return;
    }

    long existingRecords = repository.count();
    if (existingRecords > 0) {
      log.info(
          "Database already contains {} records, skipping SFTP import (idempotent behavior)",
          existingRecords);
      return;
    }

    try {
      log.info(
          "Starting SFTP import from {}:{}{} (user: {})",
          properties.getHost(),
          properties.getPort(),
          properties.getRemoteFilePath(),
          properties.getUsername());

      InputStream csvData = sftpClient.downloadFile(properties.getRemoteFilePath());

      CsvImportService.ImportResult result = csvImporter.importFromCsv(csvData);

      log.info(
          "SFTP import completed successfully: {} total rows, {} imported, {} failed",
          result.totalRows(),
          result.successRows(),
          result.failedRows());

    } catch (Exception e) {
      log.error(
          "SFTP import failed - continuing startup without initial data. Error: {}",
          e.getMessage(),
          e);
    }
  }
}

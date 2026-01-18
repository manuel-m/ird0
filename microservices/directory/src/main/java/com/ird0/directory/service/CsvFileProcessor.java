package com.ird0.directory.service;

import com.ird0.directory.config.SftpImportProperties;
import com.ird0.directory.dto.AuditRecord;
import com.ird0.directory.dto.ImportResult;
import com.ird0.directory.exception.CsvProcessingException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.integration.metadata.MetadataStore;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "directory.sftp-import", name = "enabled", havingValue = "true")
public class CsvFileProcessor {

  private final CsvImportService csvImportService;
  private final MetadataStore metadataStore;
  private final SftpImportProperties properties;
  private final ImportErrorHandler errorHandler;
  private final ImportAuditService auditService;

  public void processFile(File csvFile) {
    String filename = csvFile.getName();
    long currentTimestamp = csvFile.lastModified();

    String storedTimestamp = metadataStore.get(filename);

    if (storedTimestamp != null) {
      long lastProcessedTimestamp = Long.parseLong(storedTimestamp);
      if (currentTimestamp <= lastProcessedTimestamp) {
        log.info(
            "File '{}' has not changed since last poll (current: {}, stored: {}), skipping processing",
            filename,
            currentTimestamp,
            lastProcessedTimestamp);
        deleteFile(csvFile);
        return;
      }
      log.info(
          "File '{}' has been modified (current: {}, stored: {}), will process",
          filename,
          currentTimestamp,
          lastProcessedTimestamp);
    } else {
      log.info("File '{}' not seen before, will process", filename);
    }

    String checksum = auditService.calculateChecksum(csvFile);

    try (InputStream inputStream = new FileInputStream(csvFile)) {
      ImportResult result = csvImportService.importFromCsvWithBatching(inputStream);

      log.info(
          "Import completed for {}: {} total, {} new, {} updated, {} unchanged, {} failed",
          filename,
          result.totalRows(),
          result.newRows(),
          result.updatedRows(),
          result.unchangedRows(),
          result.failedRows());

      metadataStore.put(filename, String.valueOf(currentTimestamp));

      if (properties.getRetry().isEnabled()) {
        errorHandler.clearRetryCount(filename);
      }

      auditService.writeAuditAsync(
          AuditRecord.success(filename, AuditRecord.ImportType.SCHEDULED, result, checksum));

      deleteFile(csvFile);

    } catch (IOException | RuntimeException e) {
      handleImportError(csvFile, filename, checksum, e);
    }
  }

  private void deleteFile(File csvFile) {
    if (csvFile.exists()) {
      try {
        Files.delete(csvFile.toPath());
        log.debug("Deleted file: {}", csvFile.getName());
      } catch (IOException e) {
        log.warn("Failed to delete file: {}", csvFile.getName());
      }
    }
  }

  private void handleImportError(File csvFile, String filename, String checksum, Exception e) {
    if (!properties.getRetry().isEnabled() || !properties.getErrorHandling().isEnabled()) {
      log.error("Failed to process file {}: {}", filename, e.getMessage(), e);
      auditService.writeAuditAsync(
          AuditRecord.error(
              filename, AuditRecord.ImportType.SCHEDULED, e.getMessage(), null, checksum));
      deleteFile(csvFile);
      throw new CsvProcessingException("CSV processing failed", e);
    }

    int retryCount = errorHandler.getRetryCount(filename);

    if (retryCount >= properties.getRetry().getMaxAttempts()) {
      try {
        File dlqFile = errorHandler.moveToDeadLetterQueue(csvFile);
        errorHandler.storeLastError(filename, e.getMessage());
        auditService.writeAuditAsync(
            AuditRecord.failed(
                filename, AuditRecord.ImportType.SCHEDULED, e.getMessage(), checksum));
        log.error(
            "Import failed after {} retries for file {}, moved to dead letter queue: {}",
            retryCount,
            filename,
            dlqFile.getAbsolutePath(),
            e);
      } catch (IOException ioException) {
        log.error("Failed to move file {} to dead letter queue", filename, ioException);
        deleteFile(csvFile);
      }
    } else {
      try {
        errorHandler.incrementRetryCount(filename);
        errorHandler.storeLastError(filename, e.getMessage());
        File errorFile = errorHandler.moveToErrorDirectory(csvFile);
        long retryDelay = errorHandler.calculateRetryDelay(retryCount + 1);
        auditService.writeAuditAsync(
            AuditRecord.error(
                filename, AuditRecord.ImportType.SCHEDULED, e.getMessage(), null, checksum));
        log.warn(
            "Import failed for file {}, attempt {}/{}. Will retry. Moved to error directory: {}. Next retry in {}ms",
            filename,
            retryCount + 1,
            properties.getRetry().getMaxAttempts(),
            errorFile.getAbsolutePath(),
            retryDelay,
            e);
      } catch (IOException ioException) {
        log.error("Failed to move file {} to error directory", filename, ioException);
        deleteFile(csvFile);
      }
    }
  }

  @EventListener(ApplicationReadyEvent.class)
  public void scanErrorDirectoryOnStartup() {
    if (!properties.getErrorHandling().isEnabled()) {
      return;
    }

    File errorDir = new File(properties.getErrorHandling().getErrorDirectory());
    if (!errorDir.exists()) {
      return;
    }

    File[] errorFiles = errorDir.listFiles((dir, name) -> name.endsWith(".csv"));
    if (errorFiles == null || errorFiles.length == 0) {
      return;
    }

    log.info("Found {} files in error directory for retry processing", errorFiles.length);

    for (File errorFile : errorFiles) {
      String filename = errorFile.getName();

      if (errorHandler.shouldRetry(filename)) {
        try {
          File localFile = new File(properties.getLocalDirectory(), filename);
          Files.move(errorFile.toPath(), localFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
          log.info("Moved {} from error directory to local directory for retry", filename);
        } catch (IOException e) {
          log.error("Failed to move error file {} for retry", filename, e);
        }
      }
    }
  }
}

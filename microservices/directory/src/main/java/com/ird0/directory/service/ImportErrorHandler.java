package com.ird0.directory.service;

import com.ird0.directory.config.SftpImportProperties;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.integration.metadata.MetadataStore;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "directory.sftp-import", name = "enabled", havingValue = "true")
public class ImportErrorHandler {

  private static final String RETRY_COUNT_SUFFIX = ".retry_count";
  private static final String LAST_ERROR_TIME_SUFFIX = ".last_error_time";
  private static final String LAST_ERROR_MESSAGE_SUFFIX = ".last_error_message";
  private static final DateTimeFormatter TIMESTAMP_FORMATTER =
      DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

  private final SftpImportProperties properties;
  private final MetadataStore metadataStore;

  public int getRetryCount(String filename) {
    String key = filename + RETRY_COUNT_SUFFIX;
    String value = metadataStore.get(key);
    if (value == null) {
      return 0;
    }
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      log.warn("Invalid retry count value for file {}: {}", filename, value);
      return 0;
    }
  }

  public void incrementRetryCount(String filename) {
    int currentCount = getRetryCount(filename);
    metadataStore.put(filename + RETRY_COUNT_SUFFIX, String.valueOf(currentCount + 1));
    metadataStore.put(filename + LAST_ERROR_TIME_SUFFIX, LocalDateTime.now().toString());
  }

  public void clearRetryCount(String filename) {
    metadataStore.remove(filename + RETRY_COUNT_SUFFIX);
    metadataStore.remove(filename + LAST_ERROR_TIME_SUFFIX);
    metadataStore.remove(filename + LAST_ERROR_MESSAGE_SUFFIX);
  }

  public void storeLastError(String filename, String errorMessage) {
    String truncatedMessage =
        errorMessage != null && errorMessage.length() > 500
            ? errorMessage.substring(0, 500) + "..."
            : errorMessage;
    metadataStore.put(filename + LAST_ERROR_MESSAGE_SUFFIX, truncatedMessage);
  }

  public File moveToErrorDirectory(File file) throws IOException {
    return moveFile(file, properties.getErrorHandling().getErrorDirectory(), file.getName());
  }

  public File moveToDeadLetterQueue(File file) throws IOException {
    String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMATTER);
    String baseFilename = file.getName();
    int dotIndex = baseFilename.lastIndexOf('.');
    String name = dotIndex > 0 ? baseFilename.substring(0, dotIndex) : baseFilename;
    String extension = dotIndex > 0 ? baseFilename.substring(dotIndex) : "";
    String dlqFilename = name + "_failed_" + timestamp + extension;

    return moveFile(file, properties.getErrorHandling().getDeadLetterDirectory(), dlqFilename);
  }

  private File moveFile(File source, String targetDir, String targetFilename) throws IOException {
    ensureDirectoryExists(targetDir);
    File destination = new File(targetDir, targetFilename);
    Files.move(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
    log.debug("Moved file {} to: {}", source.getName(), destination.getAbsolutePath());
    return destination;
  }

  public boolean shouldRetry(String filename) {
    int retryCount = getRetryCount(filename);
    return retryCount < properties.getRetry().getMaxAttempts();
  }

  public long calculateRetryDelay(int retryCount) {
    if (retryCount <= 0) {
      return 0;
    }

    double delay =
        properties.getRetry().getInitialDelay()
            * Math.pow(properties.getRetry().getBackoffMultiplier(), (retryCount - 1));

    long maxDelay = properties.getRetry().getMaxDelay();
    return Math.min((long) delay, maxDelay);
  }

  private void ensureDirectoryExists(String path) {
    File directory = new File(path);
    if (!directory.exists()) {
      if (directory.mkdirs()) {
        log.info("Created directory: {}", directory.getAbsolutePath());
      } else {
        log.warn("Failed to create directory: {}", directory.getAbsolutePath());
      }
    }
  }
}

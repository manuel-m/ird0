package com.ird0.directory.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ird0.directory.config.AuditAsyncConfig;
import com.ird0.directory.config.SftpImportProperties;
import com.ird0.directory.dto.AuditRecord;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/** Service for writing audit records to the filesystem as JSON files. */
@Slf4j
@Service
public class ImportAuditService {

  private static final String AUDIT_FILE_EXTENSION = ".audit.json";
  private static final DateTimeFormatter TIMESTAMP_FORMATTER =
      DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS").withZone(ZoneId.systemDefault());

  private final SftpImportProperties properties;
  private final Executor auditExecutor;
  private final ObjectMapper objectMapper;

  public ImportAuditService(
      SftpImportProperties properties,
      @Qualifier(AuditAsyncConfig.AUDIT_EXECUTOR) Executor auditExecutor) {
    this.properties = properties;
    this.auditExecutor = auditExecutor;
    this.objectMapper = createObjectMapper();
  }

  private ObjectMapper createObjectMapper() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    return mapper;
  }

  /**
   * Writes an audit record asynchronously.
   *
   * @param record the audit record to write
   * @return a CompletableFuture that completes when the write is done
   */
  public CompletableFuture<Void> writeAuditAsync(AuditRecord record) {
    if (!properties.getAudit().isEnabled()) {
      return CompletableFuture.completedFuture(null);
    }

    if (properties.getAudit().isAsyncEnabled()) {
      return CompletableFuture.runAsync(() -> writeAudit(record), auditExecutor);
    } else {
      writeAudit(record);
      return CompletableFuture.completedFuture(null);
    }
  }

  /**
   * Writes an audit record synchronously.
   *
   * @param record the audit record to write
   */
  public void writeAudit(AuditRecord record) {
    if (!properties.getAudit().isEnabled()) {
      return;
    }

    try {
      Path targetDir = getTargetDirectory(record.status());
      Files.createDirectories(targetDir);

      String filename = generateFilename(record);
      Path targetPath = targetDir.resolve(filename);

      Path tempFile = Files.createTempFile(targetDir, "audit-", ".tmp");
      try {
        if (properties.getAudit().isPrettyPrint()) {
          objectMapper.writerWithDefaultPrettyPrinter().writeValue(tempFile.toFile(), record);
        } else {
          objectMapper.writeValue(tempFile.toFile(), record);
        }

        Files.move(tempFile, targetPath, StandardCopyOption.ATOMIC_MOVE);
        log.debug("Wrote audit record: {}", targetPath);
      } catch (IOException e) {
        Files.deleteIfExists(tempFile);
        throw e;
      }
    } catch (IOException e) {
      log.error("Failed to write audit record for {}: {}", record.sourceFileName(), e.getMessage());
    }
  }

  /**
   * Calculates SHA-256 checksum of a file.
   *
   * @param file the file to checksum
   * @return hex-encoded checksum, or null if checksum is disabled or fails
   */
  public String calculateChecksum(File file) {
    if (!properties.getAudit().isIncludeChecksum()) {
      return null;
    }

    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      try (InputStream is = Files.newInputStream(file.toPath())) {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = is.read(buffer)) != -1) {
          digest.update(buffer, 0, read);
        }
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException | IOException e) {
      log.warn("Failed to calculate checksum for {}: {}", file.getName(), e.getMessage());
      return null;
    }
  }

  /**
   * Calculates SHA-256 checksum from an InputStream.
   *
   * @param inputStream the input stream to checksum
   * @return hex-encoded checksum, or null if checksum is disabled or fails
   */
  public String calculateChecksum(InputStream inputStream) {
    if (!properties.getAudit().isIncludeChecksum()) {
      return null;
    }

    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] buffer = new byte[8192];
      int read;
      while ((read = inputStream.read(buffer)) != -1) {
        digest.update(buffer, 0, read);
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException | IOException e) {
      log.warn("Failed to calculate checksum from input stream: {}", e.getMessage());
      return null;
    }
  }

  private Path getTargetDirectory(AuditRecord.Status status) {
    return switch (status) {
      case SUCCESS -> Path.of(properties.getMetadataDirectory());
      case ERROR -> Path.of(properties.getErrorHandling().getErrorDirectory());
      case FAILED -> Path.of(properties.getErrorHandling().getDeadLetterDirectory());
    };
  }

  private String generateFilename(AuditRecord record) {
    String basename = extractBasename(record.sourceFileName());
    String timestamp = TIMESTAMP_FORMATTER.format(record.timestamp());
    return String.format("%s_%s_%s%s", basename, timestamp, record.status(), AUDIT_FILE_EXTENSION);
  }

  private String extractBasename(String filename) {
    if (filename == null) {
      return "unknown";
    }
    int dotIndex = filename.lastIndexOf('.');
    return dotIndex > 0 ? filename.substring(0, dotIndex) : filename;
  }
}

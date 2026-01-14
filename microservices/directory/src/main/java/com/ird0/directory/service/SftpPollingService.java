package com.ird0.directory.service;

import com.ird0.directory.config.SftpImportProperties;
import com.ird0.directory.config.ssh.MinaSftpClient;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.sftp.client.SftpClient.DirEntry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.integration.metadata.MetadataStore;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Scheduled service that polls SFTP server for CSV files using certificate-based authentication.
 *
 * <p>This service replaces Spring Integration SFTP polling with a simpler scheduled task that uses
 * {@link MinaSftpClient} for SFTP operations. Key features:
 *
 * <ul>
 *   <li>Uses SSH certificates from Vault CA for authentication
 *   <li>Polls for *.csv files at configurable intervals
 *   <li>Tracks processed files via MetadataStore
 *   <li>Downloads new/modified files for processing
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "directory.sftp-import", name = "enabled", havingValue = "true")
public class SftpPollingService {

  private final MinaSftpClient sftpClient;
  private final CsvFileProcessor csvFileProcessor;
  private final SftpImportProperties properties;
  private final MetadataStore metadataStore;

  /**
   * Scheduled task to poll SFTP server for CSV files.
   *
   * <p>Runs at the interval specified by {@code directory.sftp-import.polling.fixed-delay}. Uses
   * initial delay from {@code directory.sftp-import.polling.initial-delay}.
   */
  @Scheduled(
      fixedDelayString = "${directory.sftp-import.polling.fixed-delay:120000}",
      initialDelayString = "${directory.sftp-import.polling.initial-delay:1000}")
  public void pollSftpServer() {
    log.debug("Starting SFTP poll for {}:{}", properties.getHost(), properties.getPort());

    try {
      Iterable<DirEntry> files = sftpClient.listFiles(".");

      int csvCount = 0;
      for (DirEntry entry : files) {
        if (entry.getFilename().endsWith(".csv") && !entry.getAttributes().isDirectory()) {
          csvCount++;
          processRemoteFile(entry.getFilename());
        }
      }

      log.debug("SFTP poll complete. Found {} CSV files.", csvCount);

    } catch (Exception e) {
      log.error("SFTP polling failed: {}", e.getMessage(), e);
    }
  }

  private void processRemoteFile(String filename) {
    try {
      // Get remote file modification time
      long remoteTimestamp = sftpClient.getLastModified(filename);

      // Check if file has been modified since last poll
      String storedTimestamp = metadataStore.get(filename);
      if (storedTimestamp != null) {
        long lastProcessedTimestamp = Long.parseLong(storedTimestamp);
        if (remoteTimestamp <= lastProcessedTimestamp) {
          log.debug(
              "File '{}' has not changed since last poll (remote: {}, stored: {}), skipping",
              filename,
              remoteTimestamp,
              lastProcessedTimestamp);
          return;
        }
        log.info(
            "File '{}' has been modified (remote: {}, stored: {}), will download",
            filename,
            remoteTimestamp,
            lastProcessedTimestamp);
      } else {
        log.info("File '{}' not seen before, will download", filename);
      }

      // Download file to local directory
      Path localDir = Path.of(properties.getLocalDirectory());
      Files.createDirectories(localDir);
      Path localPath = localDir.resolve(filename);

      sftpClient.downloadFile(filename, localPath);

      // Set the file's modification time to match remote
      File localFile = localPath.toFile();
      localFile.setLastModified(remoteTimestamp);

      // Process the downloaded file
      csvFileProcessor.processFile(localFile);

    } catch (IOException e) {
      log.error("Failed to process remote file '{}': {}", filename, e.getMessage(), e);
    }
  }
}

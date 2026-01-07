package com.ird0.directory.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.integration.metadata.MetadataStore;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CsvFileProcessor {

  private final CsvImportService csvImportService;
  private final MetadataStore metadataStore;

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

    try (InputStream inputStream = new FileInputStream(csvFile)) {
      CsvImportService.ImportResult result =
          csvImportService.importFromCsvWithBatching(inputStream);

      log.info(
          "Import completed for {}: {} total, {} new, {} updated, {} unchanged, {} failed",
          filename,
          result.totalRows(),
          result.newRows(),
          result.updatedRows(),
          result.unchangedRows(),
          result.failedRows());

      metadataStore.put(filename, String.valueOf(currentTimestamp));

    } catch (Exception e) {
      log.error("Failed to process file {}: {}", filename, e.getMessage(), e);
      throw new RuntimeException("CSV processing failed", e);
    } finally {
      deleteFile(csvFile);
    }
  }

  private void deleteFile(File csvFile) {
    if (csvFile.exists()) {
      if (csvFile.delete()) {
        log.debug("Deleted file: {}", csvFile.getName());
      } else {
        log.warn("Failed to delete file: {}", csvFile.getName());
      }
    }
  }
}

package com.ird0.directory.service;

import com.ird0.directory.dto.ImportResult;
import com.ird0.directory.model.DirectoryEntry;
import com.ird0.directory.repository.DirectoryEntryRepository;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles batch persistence of directory entries with proper transaction boundaries.
 *
 * <p>This service is separated from CsvImportService to ensure Spring AOP proxy works correctly for
 * transactional methods. Each batch is processed in its own transaction (REQUIRES_NEW), allowing
 * partial success: if one batch fails, previously committed batches are not rolled back.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CsvBatchWriter {

  private final DirectoryEntryRepository repository;

  /**
   * Processes a batch of directory entries, persisting them to the database.
   *
   * <p>Each call runs in its own transaction (REQUIRES_NEW). This ensures that:
   *
   * <ul>
   *   <li>Batches are committed independently
   *   <li>A failure in batch N does not rollback batches 1..N-1
   *   <li>The caller can continue processing subsequent batches after a failure
   * </ul>
   *
   * @param batch the list of entries to persist
   * @return ImportResult with counts of new, updated, unchanged, and failed entries
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public ImportResult processBatch(List<DirectoryEntry> batch) {
    log.debug("Processing batch of {} entries", batch.size());

    int newRows = 0;
    int updatedRows = 0;
    int unchangedRows = 0;
    int failedRows = 0;

    for (DirectoryEntry entry : batch) {
      try {
        Optional<DirectoryEntry> existing = repository.findByEmail(entry.getEmail());

        if (existing.isEmpty()) {
          entry.generateId();
          repository.upsertByEmail(entry);
          newRows++;
        } else {
          DirectoryEntry existingEntry = existing.get();
          if (hasChanged(existingEntry, entry)) {
            entry.generateId();
            repository.upsertByEmail(entry);
            updatedRows++;
          } else {
            unchangedRows++;
          }
        }
      } catch (DataAccessException | IllegalStateException e) {
        log.warn("Failed to process entry with email {}: {}", entry.getEmail(), e.getMessage());
        failedRows++;
      }
    }

    return new ImportResult(batch.size(), newRows, updatedRows, unchangedRows, failedRows);
  }

  /**
   * Checks if any relevant field has changed between the existing and new entry.
   *
   * <p>Email is not compared as it is the unique key used for lookups.
   */
  private boolean hasChanged(DirectoryEntry existing, DirectoryEntry newEntry) {
    return !Objects.equals(existing.getName(), newEntry.getName())
        || !Objects.equals(existing.getType(), newEntry.getType())
        || !Objects.equals(existing.getPhone(), newEntry.getPhone())
        || !Objects.equals(existing.getAddress(), newEntry.getAddress())
        || !Objects.equals(existing.getAdditionalInfo(), newEntry.getAdditionalInfo());
  }
}

package com.ird0.directory.service;

import com.ird0.directory.model.DirectoryEntry;
import com.ird0.directory.repository.DirectoryEntryRepository;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CsvImportService {

  private static final int BATCH_SIZE = 500;
  private static final String EMAIL_REGEX = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
  private final DirectoryEntryRepository repository;

  public record ImportResult(
      int totalRows, int newRows, int updatedRows, int unchangedRows, int failedRows) {}

  @Transactional
  public ImportResult importFromCsvWithBatching(InputStream csvData) throws IOException {
    log.info("Starting batched CSV import with batch size: {}", BATCH_SIZE);

    int totalRows = 0;
    int newRows = 0;
    int updatedRows = 0;
    int unchangedRows = 0;
    int failedRows = 0;

    List<DirectoryEntry> batch = new ArrayList<>(BATCH_SIZE);

    try (Reader reader = new InputStreamReader(csvData);
        CSVParser parser = createCsvParser(reader)) {

      for (CSVRecord record : parser) {
        totalRows++;

        try {
          DirectoryEntry entry = parseRecord(record);
          if (entry != null) {
            batch.add(entry);

            if (batch.size() >= BATCH_SIZE) {
              ImportResult batchResult = processBatch(batch);
              newRows += batchResult.newRows();
              updatedRows += batchResult.updatedRows();
              unchangedRows += batchResult.unchangedRows();
              failedRows += batchResult.failedRows();
              batch.clear();
            }
          } else {
            failedRows++;
          }
        } catch (IllegalArgumentException e) {
          log.warn("Failed to parse record {}: {}", record.getRecordNumber(), e.getMessage());
          failedRows++;
        }
      }

      if (!batch.isEmpty()) {
        ImportResult batchResult = processBatch(batch);
        newRows += batchResult.newRows();
        updatedRows += batchResult.updatedRows();
        unchangedRows += batchResult.unchangedRows();
        failedRows += batchResult.failedRows();
      }
    }

    log.info(
        "Batched CSV import completed: {} total, {} new, {} updated, {} unchanged, {} failed",
        totalRows,
        newRows,
        updatedRows,
        unchangedRows,
        failedRows);
    return new ImportResult(totalRows, newRows, updatedRows, unchangedRows, failedRows);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  protected ImportResult processBatch(List<DirectoryEntry> batch) {
    log.debug("Processing batch of {} entries", batch.size());
    return upsertBatch(batch);
  }

  private boolean hasChanged(DirectoryEntry existing, DirectoryEntry newEntry) {
    return !java.util.Objects.equals(existing.getName(), newEntry.getName())
        || !java.util.Objects.equals(existing.getType(), newEntry.getType())
        || !java.util.Objects.equals(existing.getPhone(), newEntry.getPhone())
        || !java.util.Objects.equals(existing.getAddress(), newEntry.getAddress())
        || !java.util.Objects.equals(existing.getAdditionalInfo(), newEntry.getAdditionalInfo());
  }

  private ImportResult upsertBatch(List<DirectoryEntry> entries) {
    int newRows = 0;
    int updatedRows = 0;
    int unchangedRows = 0;
    int failedRows = 0;

    for (DirectoryEntry entry : entries) {
      try {
        java.util.Optional<DirectoryEntry> existing = repository.findByEmail(entry.getEmail());

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
      } catch (org.springframework.dao.DataAccessException | IllegalStateException e) {
        log.warn("Failed to process entry with email {}: {}", entry.getEmail(), e.getMessage());
        failedRows++;
      }
    }

    return new ImportResult(entries.size(), newRows, updatedRows, unchangedRows, failedRows);
  }

  private CSVParser createCsvParser(Reader reader) throws IOException {
    return CSVFormat.DEFAULT
        .builder()
        .setHeader()
        .setSkipHeaderRecord(true)
        .setTrim(true)
        .build()
        .parse(reader);
  }

  private DirectoryEntry parseRecord(CSVRecord record) {
    String name = getField(record, "name");
    String type = getField(record, "type");
    String email = getField(record, "email");
    String phone = getField(record, "phone");

    if (name == null
        || name.isEmpty()
        || type == null
        || type.isEmpty()
        || email == null
        || email.isEmpty()
        || phone == null
        || phone.isEmpty()) {
      log.warn(
          "Skipping record {} - missing required fields (name, type, email, phone)",
          record.getRecordNumber());
      return null;
    }

    if (!email.matches(EMAIL_REGEX)) {
      log.warn("Skipping record {} - invalid email format: {}", record.getRecordNumber(), email);
      return null;
    }

    DirectoryEntry entry = new DirectoryEntry();
    entry.setName(name);
    entry.setType(type);
    entry.setEmail(email);
    entry.setPhone(phone);
    entry.setAddress(getField(record, "address"));
    entry.setAdditionalInfo(getField(record, "additionalInfo"));

    return entry;
  }

  private String getField(CSVRecord record, String fieldName) {
    try {
      String value = record.get(fieldName);
      return (value != null && !value.isEmpty()) ? value : null;
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}

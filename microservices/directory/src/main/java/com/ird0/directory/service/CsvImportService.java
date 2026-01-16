package com.ird0.directory.service;

import com.ird0.directory.dto.ImportResult;
import com.ird0.directory.model.DirectoryEntry;
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

/**
 * Service for importing directory entries from CSV files.
 *
 * <p>This service handles CSV parsing and validation. Batch persistence is delegated to {@link
 * CsvBatchWriter}, which processes each batch in its own transaction. This separation ensures:
 *
 * <ul>
 *   <li>Spring AOP proxies work correctly for transactional methods
 *   <li>Each batch is committed independently
 *   <li>Partial imports can succeed even if some batches fail
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CsvImportService {

  private static final int BATCH_SIZE = 500;
  private static final String EMAIL_REGEX = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";

  private final CsvBatchWriter batchWriter;

  /**
   * Imports directory entries from a CSV input stream using batched processing.
   *
   * <p>The CSV must have a header row with columns: name, type, email, phone (required), and
   * optionally: address, additionalInfo.
   *
   * <p>Processing is done in batches of {@value #BATCH_SIZE} rows. Each batch is persisted in its
   * own transaction, so a failure in one batch does not affect previously committed batches.
   *
   * @param csvData the input stream containing CSV data
   * @return ImportResult with counts of processed rows
   * @throws IOException if reading the CSV fails
   */
  public ImportResult importFromCsvWithBatching(InputStream csvData) throws IOException {
    log.info("Starting batched CSV import with batch size: {}", BATCH_SIZE);

    ImportResult result = ImportResult.empty();
    int totalRows = 0;
    int failedRows = 0;

    List<DirectoryEntry> batch = new ArrayList<>(BATCH_SIZE);

    try (Reader reader = new InputStreamReader(csvData);
        CSVParser parser = createCsvParser(reader)) {

      for (CSVRecord csvRecord : parser) {
        totalRows++;

        try {
          DirectoryEntry parsedEntry = parseRecord(csvRecord);
          if (parsedEntry != null) {
            batch.add(parsedEntry);

            if (batch.size() >= BATCH_SIZE) {
              result = result.add(batchWriter.processBatch(batch));
              batch.clear();
            }
          } else {
            failedRows++;
          }
        } catch (IllegalArgumentException e) {
          log.warn(
              "Failed to parse CSV record {}: {}", csvRecord.getRecordNumber(), e.getMessage());
          failedRows++;
        }
      }

      if (!batch.isEmpty()) {
        result = result.add(batchWriter.processBatch(batch));
      }
    }

    ImportResult finalResult =
        new ImportResult(
            totalRows,
            result.newRows(),
            result.updatedRows(),
            result.unchangedRows(),
            result.failedRows() + failedRows);

    log.info(
        "Batched CSV import completed: {} total, {} new, {} updated, {} unchanged, {} failed",
        finalResult.totalRows(),
        finalResult.newRows(),
        finalResult.updatedRows(),
        finalResult.unchangedRows(),
        finalResult.failedRows());

    return finalResult;
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

  private DirectoryEntry parseRecord(CSVRecord csvRecord) {
    String name = getField(csvRecord, "name");
    String type = getField(csvRecord, "type");
    String email = getField(csvRecord, "email");
    String phone = getField(csvRecord, "phone");

    if (name == null
        || name.isEmpty()
        || type == null
        || type.isEmpty()
        || email == null
        || email.isEmpty()
        || phone == null
        || phone.isEmpty()) {
      log.warn(
          "Skipping CSV record {} - missing required fields (name, type, email, phone)",
          csvRecord.getRecordNumber());
      return null;
    }

    if (!email.matches(EMAIL_REGEX)) {
      log.warn(
          "Skipping CSV record {} - invalid email format: {}", csvRecord.getRecordNumber(), email);
      return null;
    }

    DirectoryEntry entry = new DirectoryEntry();
    entry.setName(name);
    entry.setType(type);
    entry.setEmail(email);
    entry.setPhone(phone);
    entry.setAddress(getField(csvRecord, "address"));
    entry.setAdditionalInfo(getField(csvRecord, "additionalInfo"));

    return entry;
  }

  private String getField(CSVRecord csvRecord, String fieldName) {
    try {
      String value = csvRecord.get(fieldName);
      return (value != null && !value.isEmpty()) ? value : null;
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}

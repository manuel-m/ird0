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
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CsvImportService {

  private final DirectoryEntryRepository repository;

  public record ImportResult(int totalRows, int successRows, int failedRows) {}

  @Transactional
  public ImportResult importFromCsv(InputStream csvData) throws IOException {
    log.info("Starting CSV import");

    List<DirectoryEntry> entries = new ArrayList<>();
    int totalRows = 0;
    int failedRows = 0;

    try (Reader reader = new InputStreamReader(csvData);
        CSVParser parser =
            CSVFormat.DEFAULT
                .builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setTrim(true)
                .build()
                .parse(reader)) {

      for (CSVRecord record : parser) {
        totalRows++;

        try {
          DirectoryEntry entry = parseRecord(record);
          if (entry != null) {
            entries.add(entry);
          } else {
            failedRows++;
          }
        } catch (Exception e) {
          log.warn("Failed to parse CSV record {}: {}", record.getRecordNumber(), e.getMessage());
          failedRows++;
        }
      }
    }

    if (!entries.isEmpty()) {
      repository.saveAll(entries);
      log.info("Successfully imported {} records from CSV", entries.size());
    }

    int successRows = totalRows - failedRows;
    return new ImportResult(totalRows, successRows, failedRows);
  }

  private DirectoryEntry parseRecord(CSVRecord record) {
    String name = getField(record, "name");
    String type = getField(record, "type");
    String email = getField(record, "email");
    String phone = getField(record, "phone");

    if (name == null || name.isEmpty() || type == null || type.isEmpty() || email == null
        || email.isEmpty() || phone == null || phone.isEmpty()) {
      log.warn(
          "Skipping record {} - missing required fields (name, type, email, phone)",
          record.getRecordNumber());
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

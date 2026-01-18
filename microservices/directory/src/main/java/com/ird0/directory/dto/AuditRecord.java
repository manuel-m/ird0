package com.ird0.directory.dto;

import java.time.Instant;

/**
 * Audit record for CSV import operations. Written to filesystem as JSON for compliance tracking and
 * troubleshooting.
 *
 * @param sourceFileName Original CSV filename
 * @param timestamp ISO 8601 instant when the import was processed
 * @param importType Whether import was scheduled (SFTP) or via REST API
 * @param status Outcome of the import operation
 * @param errorMessage Error details (null for success)
 * @param statistics Import statistics (null for failed imports)
 * @param checksum SHA-256 hash of source file (null if checksum disabled)
 */
public record AuditRecord(
    String sourceFileName,
    Instant timestamp,
    ImportType importType,
    Status status,
    String errorMessage,
    Statistics statistics,
    String checksum) {

  public enum ImportType {
    SCHEDULED,
    API
  }

  public enum Status {
    SUCCESS,
    ERROR,
    FAILED
  }

  /**
   * Import statistics nested record.
   *
   * @param totalRows Total CSV rows processed
   * @param newRows Rows inserted (new entries)
   * @param updatedRows Rows updated (existing entries with changes)
   * @param unchangedRows Rows skipped (existing entries, no changes)
   * @param failedRows Rows that failed validation or processing
   */
  public record Statistics(
      int totalRows, int newRows, int updatedRows, int unchangedRows, int failedRows) {

    /** Creates Statistics from an ImportResult. */
    public static Statistics fromImportResult(ImportResult result) {
      return new Statistics(
          result.totalRows(),
          result.newRows(),
          result.updatedRows(),
          result.unchangedRows(),
          result.failedRows());
    }
  }

  /** Creates a SUCCESS audit record. */
  public static AuditRecord success(
      String sourceFileName, ImportType importType, ImportResult result, String checksum) {
    return new AuditRecord(
        sourceFileName,
        Instant.now(),
        importType,
        Status.SUCCESS,
        null,
        Statistics.fromImportResult(result),
        checksum);
  }

  /** Creates an ERROR audit record (retry eligible). */
  public static AuditRecord error(
      String sourceFileName,
      ImportType importType,
      String errorMessage,
      ImportResult result,
      String checksum) {
    return new AuditRecord(
        sourceFileName,
        Instant.now(),
        importType,
        Status.ERROR,
        errorMessage,
        result != null ? Statistics.fromImportResult(result) : null,
        checksum);
  }

  /** Creates a FAILED audit record (max retries exceeded). */
  public static AuditRecord failed(
      String sourceFileName, ImportType importType, String errorMessage, String checksum) {
    return new AuditRecord(
        sourceFileName, Instant.now(), importType, Status.FAILED, errorMessage, null, checksum);
  }
}

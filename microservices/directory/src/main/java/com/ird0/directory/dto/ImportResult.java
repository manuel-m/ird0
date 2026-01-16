package com.ird0.directory.dto;

/**
 * Result of a CSV import operation, tracking the outcome of each row processed.
 *
 * @param totalRows Total number of rows processed from the CSV
 * @param newRows Rows that were inserted (new entries)
 * @param updatedRows Rows that were updated (existing entries with changes)
 * @param unchangedRows Rows that were skipped (existing entries, no changes)
 * @param failedRows Rows that failed validation or processing
 */
public record ImportResult(
    int totalRows, int newRows, int updatedRows, int unchangedRows, int failedRows) {

  /** Creates an empty result with all counts at zero. */
  public static ImportResult empty() {
    return new ImportResult(0, 0, 0, 0, 0);
  }

  /**
   * Combines this result with another, summing all counts.
   *
   * @param other the result to add
   * @return a new ImportResult with combined counts
   */
  public ImportResult add(ImportResult other) {
    return new ImportResult(
        this.totalRows + other.totalRows,
        this.newRows + other.newRows,
        this.updatedRows + other.updatedRows,
        this.unchangedRows + other.unchangedRows,
        this.failedRows + other.failedRows);
  }
}

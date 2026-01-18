# PRD

**Policyholder microservice** is responsible for importing **CSV files from an external SFTP server**.
  
  >   * Owns the PostgreSQL database
  * Is enriched by the imported CSV data
The CSV import itself **works correctly**:
* Files are fetched successfully from SFTP
* CSV parsing works
* Data is correctly sent to / stored in PostgreSQL
* Logs confirm successful imports and failures
❗ **Problem**
The **file-based audit mechanism is missing / not implemented**, even though it is required for compliance and troubleshooting.
I need to **persist audit artifacts on the filesystem** inside the import microservice container.
The required directory structure is:
```
/app/
├── data/
│   ├── sftp-metadata/   # Successful import metadata (timestamps, file names, checksums, status)
│   ├── sftp-errors/     # Failed imports eligible for retry
│   └── sftp-failed/     # Imports that exceeded max retry attempts
```

# Implementation

1. For **each import attempt** (scheduled or REST-triggered):
   >
   >    * Write an audit record to the filesystem
   * Persist at least:
     >
     >      * source file name
     * import timestamp
     * import type (SCHEDULED / API)
     * status (SUCCESS / ERROR / FAILED)
     * error message (if any)
2. **Successful imports**
   >
   >    * Write metadata files to `/app/data/sftp-metadata`
3. **Failed imports**
   >
   >    * On first failures, write files to `/app/data/sftp-errors`
   * Implement a retry mechanism
   * After max retries, move the audit file to `/app/data/sftp-failed`
4. The solution must:
   >
   >    * Be thread-safe
   * Work in a containerized environment (Docker / Kubernetes)
   * Use Spring Boot best practices
   * Avoid blocking the main import flow unnecessarily
5. Provide:
   >
   >    * A **clean architecture proposal**
   * Java/Spring components involved (services, listeners, etc.)
   * Example Java code (file writing, retry handling)
   * Any useful configuration (application.yml)
### Goal
Propose a **robust audit implementation** without changing the existing import logic, only extending it to persist audit information properly.
Think like you are fixing a real production issue where logs are not enough and filesystem auditing is mandatory.
Scheduled and and imports triggered by Rest API shall both be handled

# Plan
  ╭──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────╮
  │ File-Based Audit Mechanism for CSV Import                                                                                                                                                │
  │                                                                                                                                                                                          │
  │ Overview                                                                                                                                                                                 │
  │                                                                                                                                                                                          │
  │ Add a file-based audit system that writes JSON audit records for each CSV import attempt (scheduled SFTP or REST API). This enables compliance tracking and troubleshooting beyond logs. │
  │                                                                                                                                                                                          │
  │ Directory Structure (already configured in YAML)                                                                                                                                         │
  │                                                                                                                                                                                          │
  │ /app/data/                                                                                                                                                                               │
  │ ├── sftp-metadata/   # SUCCESS audit files                                                                                                                                               │
  │ ├── sftp-errors/     # ERROR audit files (retry eligible)                                                                                                                                │
  │ └── sftp-failed/     # FAILED audit files (max retries exceeded)                                                                                                                         │
  │                                                                                                                                                                                          │
  │ New Components                                                                                                                                                                           │
  │                                                                                                                                                                                          │
  │ 1. AuditRecord.java (DTO)                                                                                                                                                                │
  │                                                                                                                                                                                          │
  │ Path: microservices/directory/src/main/java/com/ird0/directory/dto/AuditRecord.java                                                                                                      │
  │                                                                                                                                                                                          │
  │ Java record containing:                                                                                                                                                                  │
  │ - sourceFileName - Original CSV filename                                                                                                                                                 │
  │ - timestamp - ISO 8601 instant                                                                                                                                                           │
  │ - importType - Enum: SCHEDULED | API                                                                                                                                                     │
  │ - status - Enum: SUCCESS | ERROR | FAILED                                                                                                                                                │
  │ - errorMessage - Error details (null for success)                                                                                                                                        │
  │ - statistics - Nested record from ImportResult (total, new, updated, unchanged, failed rows)                                                                                             │
  │ - checksum - SHA-256 hash of source file                                                                                                                                                 │
  │                                                                                                                                                                                          │
  │ Factory methods: success(), error(), failed()                                                                                                                                            │
  │                                                                                                                                                                                          │
  │ 2. ImportAuditService.java (Service)                                                                                                                                                     │
  │                                                                                                                                                                                          │
  │ Path: microservices/directory/src/main/java/com/ird0/directory/service/ImportAuditService.java                                                                                           │
  │                                                                                                                                                                                          │
  │ Responsibilities:                                                                                                                                                                        │
  │ - Write audit records to filesystem as JSON                                                                                                                                              │
  │ - Calculate SHA-256 checksums                                                                                                                                                            │
  │ - Route files to correct directory based on status                                                                                                                                       │
  │ - Thread-safe via atomic file writes (write to temp, then move)                                                                                                                          │
  │ - Async writes via dedicated thread pool (non-blocking)                                                                                                                                  │
  │                                                                                                                                                                                          │
  │ Key methods:                                                                                                                                                                             │
  │ - writeAuditAsync(AuditRecord) - Async write                                                                                                                                             │
  │ - writeAudit(AuditRecord) - Sync write                                                                                                                                                   │
  │ - calculateChecksum(File) - SHA-256 hash                                                                                                                                                 │
  │ - moveToFailed(String) - Move audit file when max retries exceeded                                                                                                                       │
  │                                                                                                                                                                                          │
  │ 3. AuditAsyncConfig.java (Configuration)                                                                                                                                                 │
  │                                                                                                                                                                                          │
  │ Path: microservices/directory/src/main/java/com/ird0/directory/config/AuditAsyncConfig.java                                                                                              │
  │                                                                                                                                                                                          │
  │ Dedicated thread pool for audit writes:                                                                                                                                                  │
  │ - Core pool: 2 threads                                                                                                                                                                   │
  │ - Max pool: 4 threads                                                                                                                                                                    │
  │ - Queue capacity: 100                                                                                                                                                                    │
  │ - Thread prefix: audit-                                                                                                                                                                  │
  │                                                                                                                                                                                          │
  │ Integration Points                                                                                                                                                                       │
  │                                                                                                                                                                                          │
  │ CsvFileProcessor.java (Scheduled Imports)                                                                                                                                                │
  │                                                                                                                                                                                          │
  │ Path: microservices/directory/src/main/java/com/ird0/directory/service/CsvFileProcessor.java                                                                                             │
  │                                                                                                                                                                                          │
  │ Modifications:                                                                                                                                                                           │
  │ 1. Inject ImportAuditService                                                                                                                                                             │
  │ 2. Calculate checksum before processing (line ~31)                                                                                                                                       │
  │ 3. After successful import (line ~74): Call auditService.writeAuditAsync(AuditRecord.success(...))                                                                                       │
  │ 4. In handleImportError():                                                                                                                                                               │
  │   - When moving to DLQ (line ~103): Call auditService.writeAuditAsync(AuditRecord.failed(...))                                                                                           │
  │   - When moving to error dir (line ~119): Call auditService.writeAuditAsync(AuditRecord.error(...))                                                                                      │
  │                                                                                                                                                                                          │
  │ DirectoryEntryController.java (API Imports)                                                                                                                                              │
  │                                                                                                                                                                                          │
  │ Path: microservices/directory/src/main/java/com/ird0/directory/controller/DirectoryEntryController.java                                                                                  │
  │                                                                                                                                                                                          │
  │ Modifications to uploadCsv() method (line 72-91):                                                                                                                                        │
  │ 1. Inject ImportAuditService                                                                                                                                                             │
  │ 2. Calculate checksum from multipart file                                                                                                                                                │
  │ 3. On success (line ~86): Call auditService.writeAuditAsync(AuditRecord.success(...))                                                                                                    │
  │ 4. On IOException (line ~88): Call auditService.writeAuditAsync(AuditRecord.error(...))                                                                                                  │
  │                                                                                                                                                                                          │
  │ Configuration                                                                                                                                                                            │
  │                                                                                                                                                                                          │
  │ Add to SftpImportProperties.java                                                                                                                                                         │
  │                                                                                                                                                                                          │
  │ Add nested Audit class:                                                                                                                                                                  │
  │ @Data                                                                                                                                                                                    │
  │ public static class Audit {                                                                                                                                                              │
  │   private boolean enabled = true;                                                                                                                                                        │
  │   private boolean includeChecksum = true;                                                                                                                                                │
  │   private boolean prettyPrint = false;                                                                                                                                                   │
  │   private boolean asyncEnabled = true;                                                                                                                                                   │
  │ }                                                                                                                                                                                        │
  │                                                                                                                                                                                          │
  │ Add to policyholders.yml                                                                                                                                                                 │
  │                                                                                                                                                                                          │
  │ directory:                                                                                                                                                                               │
  │   sftp-import:                                                                                                                                                                           │
  │     audit:                                                                                                                                                                               │
  │       enabled: true                                                                                                                                                                      │
  │       include-checksum: true                                                                                                                                                             │
  │       pretty-print: false                                                                                                                                                                │
  │       async-enabled: true                                                                                                                                                                │
  │                                                                                                                                                                                          │
  │ Audit File Format                                                                                                                                                                        │
  │                                                                                                                                                                                          │
  │ Filename pattern: {basename}_{yyyyMMdd_HHmmss_SSS}_{STATUS}.audit.json                                                                                                                   │
  │                                                                                                                                                                                          │
  │ Example: policyholders_20260118_143025_123_SUCCESS.audit.json                                                                                                                            │
  │                                                                                                                                                                                          │
  │ Content:                                                                                                                                                                                 │
  │ {                                                                                                                                                                                        │
  │   "sourceFileName": "policyholders.csv",                                                                                                                                                 │
  │   "timestamp": "2026-01-18T14:30:25.123Z",                                                                                                                                               │
  │   "importType": "SCHEDULED",                                                                                                                                                             │
  │   "status": "SUCCESS",                                                                                                                                                                   │
  │   "errorMessage": null,                                                                                                                                                                  │
  │   "statistics": {                                                                                                                                                                        │
  │     "totalRows": 1000,                                                                                                                                                                   │
  │     "newRows": 150,                                                                                                                                                                      │
  │     "updatedRows": 50,                                                                                                                                                                   │
  │     "unchangedRows": 795,                                                                                                                                                                │
  │     "failedRows": 5                                                                                                                                                                      │
  │   },                                                                                                                                                                                     │
  │   "checksum": "a3f2b8c4d5e6f7..."                                                                                                                                                        │
  │ }                                                                                                                                                                                        │
  │                                                                                                                                                                                          │
  │ Thread Safety                                                                                                                                                                            │
  │                                                                                                                                                                                          │
  │ 1. Atomic writes: Write to temp file, then Files.move() with ATOMIC_MOVE                                                                                                                 │
  │ 2. Immutable records: AuditRecord is a Java record                                                                                                                                       │
  │ 3. Stateless checksum: MessageDigest created per calculation                                                                                                                             │
  │ 4. Dedicated executor: Audit writes don't block import flow                                                                                                                              │
  │                                                                                                                                                                                          │
  │ Implementation Order                                                                                                                                                                     │
  │                                                                                                                                                                                          │
  │ 1. Create AuditRecord.java - Data model (no dependencies)                                                                                                                                │
  │ 2. Add Audit nested class to SftpImportProperties.java                                                                                                                                   │
  │ 3. Update policyholders.yml with audit config                                                                                                                                            │
  │ 4. Create AuditAsyncConfig.java - Thread pool                                                                                                                                            │
  │ 5. Create ImportAuditService.java - Core service                                                                                                                                         │
  │ 6. Modify CsvFileProcessor.java - Add audit calls                                                                                                                                        │
  │ 7. Modify DirectoryEntryController.java - Add audit calls                                                                                                                                │
  │                                                                                                                                                                                          │
  │ Verification                                                                                                                                                                             │
  │                                                                                                                                                                                          │
  │ 1. Scheduled import test:                                                                                                                                                                │
  │ docker compose up policyholders-svc sftp-server                                                                                                                                          │
  │ # Wait for scheduled poll (2 min)                                                                                                                                                        │
  │ ls -la data/sftp-metadata/  # Should see .audit.json files                                                                                                                               │
  │ 2. API import test:                                                                                                                                                                      │
  │ curl -X POST http://localhost:8081/api/policyholders/import \                                                                                                                            │
  │   -F "file=@test.csv"                                                                                                                                                                    │
  │ ls -la data/sftp-metadata/                                                                                                                                                               │
  │ 3. Error handling test:                                                                                                                                                                  │
  │ # Upload invalid CSV                                                                                                                                                                     │
  │ curl -X POST http://localhost:8081/api/policyholders/import \                                                                                                                            │
  │   -F "file=@invalid.csv"                                                                                                                                                                 │
  │ ls -la data/sftp-errors/                                                                                                                                                                 │
  │                                                                                                                                                                                          │
  │ Files to Create                                                                                                                                                                          │
  │ ┌─────────────────────────────────┬───────────────────────────┐                                                                                                                          │
  │ │              File               │        Description        │                                                                                                                          │
  │ ├─────────────────────────────────┼───────────────────────────┤                                                                                                                          │
  │ │ dto/AuditRecord.java            │ Audit data model (record) │                                                                                                                          │
  │ ├─────────────────────────────────┼───────────────────────────┤                                                                                                                          │
  │ │ config/AuditAsyncConfig.java    │ Thread pool config        │                                                                                                                          │
  │ ├─────────────────────────────────┼───────────────────────────┤                                                                                                                          │
  │ │ service/ImportAuditService.java │ Audit file writer         │                                                                                                                          │
  │ └─────────────────────────────────┴───────────────────────────┘                                                                                                                          │
  │ Files to Modify                                                                                                                                                                          │
  │ ┌──────────────────────────────────────────┬─────────────────────────────────┐                                                                                                           │
  │ │                   File                   │             Changes             │                                                                                                           │
  │ ├──────────────────────────────────────────┼─────────────────────────────────┤                                                                                                           │
  │ │ config/SftpImportProperties.java         │ Add Audit nested class          │                                                                                                           │
  │ ├──────────────────────────────────────────┼─────────────────────────────────┤                                                                                                           │
  │ │ configs/policyholders.yml                │ Add audit configuration         │                                                                                                           │
  │ ├──────────────────────────────────────────┼─────────────────────────────────┤                                                                                                           │
  │ │ service/CsvFileProcessor.java            │ Inject audit service, add calls │                                                                                                           │
  │ ├──────────────────────────────────────────┼─────────────────────────────────┤                                                                                                           │
  │ │ controller/DirectoryEntryController.java │ Inject audit service, add calls │                                                                                                           │
  │ └──────────────────────────────────────────┴─────────────────────────────────┘


mvn clean install -DskipTests
java -jar /tmp/makeit/utilities/directory-data-generator/target/directory-data-generator.jar
curl -X POST http://localhost:8081/api/policyholders/import -F "file=@policyholders.csv" 



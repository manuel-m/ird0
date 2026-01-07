# Directory Service - SFTP Polling Architecture

This document provides a detailed technical deep-dive into the Spring Integration SFTP polling system used by the Policyholders service.

## Overview

The Policyholders service uses Spring Integration to implement a **polling-based SFTP import system** that:
- Polls an SFTP server every 2 minutes
- Downloads CSV files automatically
- Detects file changes using timestamps
- Imports data with intelligent change detection
- Skips unchanged data to optimize database writes

This is different from a startup-based import that runs once. Instead, this continuously polls for updates.

---

## Spring Integration Architecture

Spring Integration is a framework implementing Enterprise Integration Patterns (EIP). It provides:

| Component | Purpose |
|-----------|---------|
| **Channels** | Message pathways for communication |
| **Endpoints** | Components that send/receive messages |
| **Adapters** | Connect to external systems (SFTP, FTP, JMS, etc.) |
| **Pollers** | Trigger periodic execution of flows |
| **Filters** | Conditional message routing |

In our implementation, we use:
- **SFTP Inbound Adapter** - Downloads files from SFTP server
- **Poller** - Triggers downloads every 2 minutes
- **File Filters** - Select `*.csv` files
- **Message Channels** - Pass downloaded files to processing logic
- **Service Activator** - Processes downloaded files

---

## Step-by-Step Flow

### 1. Application Startup

```
Docker Compose starts containers
        ↓
Spring Boot initializes DirectoryApplication
        ↓
Spring scans for @Configuration classes
        ↓
Conditional beans activated based on sftp-import.enabled=true
```

**Key File:** `src/main/java/com/ird0/directory/DirectoryApplication.java`

### 2. Configuration Loading

Spring Boot loads configuration in this order:

1. `application.yml` (common config)
2. `policyholders.yml` (instance-specific overrides)
3. Environment variables (from Docker Compose)

**Key Configuration:** `configs/policyholders.yml`

```yaml
directory:
  sftp-import:
    enabled: true                    # Activates SFTP polling
    host: localhost
    port: 2222
    username: policyholder-importer
    private-key-path: ./keys/sftp_client_key
    polling:
      fixed-delay: 120000            # Poll every 2 minutes
      initial-delay: 1000            # First poll after 1 second
      batch-size: 500                # CSV batch size
    local-directory: ./temp/sftp-downloads
    metadata-directory: ./data/sftp-metadata
```

### 3. Spring Integration Components Initialization

**3.1 Properties Binding**

**File:** `src/main/java/com/ird0/directory/config/SftpImportProperties.java`

```java
@Data
@Component
@ConfigurationProperties(prefix = "directory.sftp-import")
public class SftpImportProperties {
  private boolean enabled = false;
  private String host = "localhost";
  private int port = 2222;
  private String username = "policyholder-importer";
  private String privateKeyPath = "./keys/sftp_client_key";
  private Polling polling = new Polling();
  private String localDirectory = "./temp/sftp-downloads";
  // ...
}
```

Spring automatically binds YAML properties to this class.

**3.2 SFTP Session Factory**

**File:** `src/main/java/com/ird0/directory/config/SftpIntegrationConfig.java`

```java
@Bean
public SessionFactory<SftpClient.DirEntry> sftpSessionFactory() {
  DefaultSftpSessionFactory factory = new DefaultSftpSessionFactory(true);
  factory.setHost(properties.getHost());
  factory.setPort(properties.getPort());
  factory.setUser(properties.getUsername());
  factory.setPrivateKey(new FileSystemResource(properties.getPrivateKeyPath()));
  factory.setAllowUnknownKeys(true);
  factory.setTimeout(properties.getConnectionTimeout());

  CachingSessionFactory<SftpClient.DirEntry> cachingFactory =
      new CachingSessionFactory<>(factory);

  return cachingFactory;
}
```

**What this creates:**

```
DefaultSftpSessionFactory
    ↓ (configures)
1. SSH connection parameters (host, port, user)
2. Authentication (SSH private key)
3. Timeout settings
    ↓ (wraps with)
CachingSessionFactory
    ↓ (provides)
Pooled, reusable SFTP sessions
```

**Key Concepts:**
- `DefaultSftpSessionFactory` - Spring Integration's factory for SFTP connections
- Uses Apache MINA SSHD library under the hood
- `CachingSessionFactory` - Pools SSH sessions for reuse
- `setAllowUnknownKeys(true)` - Accept any server host key (dev convenience)

**3.3 SFTP Polling Flow Configuration**

**File:** `src/main/java/com/ird0/directory/config/SftpPollingFlowConfig.java`

This is the **heart of the polling system**:

```java
@Bean
public IntegrationFlow sftpPollingFlow() {
  return IntegrationFlow.from(
          Sftp.inboundAdapter(sftpSessionFactory)
              .remoteDirectory(".")                    // Poll remote directory
              .filter(new AlwaysAcceptFileListFilter()) // Custom filter
              .patternFilter("*.csv")                  // Only CSV files
              .localDirectory(new File(properties.getLocalDirectory()))
              .preserveTimestamp(true)                 // Keep remote timestamp
              .deleteRemoteFiles(false)                // Don't delete from SFTP
              .autoCreateLocalDirectory(true)
              .localFilter(null),                      // Disable local deduplication
          e -> e.poller(
              Pollers.fixedDelay(properties.getPolling().getFixedDelay())
                  .maxMessagesPerPoll(1)))
      .channel(sftpFileChannel())
      .handle("csvFileProcessor", "processFile")
      .get();
}
```

**Flow Breakdown:**

| Component | Configuration | Purpose |
|-----------|---------------|---------|
| `Sftp.inboundAdapter` | Uses `sftpSessionFactory` | Downloads files from SFTP server |
| `.remoteDirectory(".")` | Root directory | Where to look for files |
| `.filter(AlwaysAcceptFileListFilter)` | Custom filter | Accept all files (bypass Spring's default deduplication) |
| `.patternFilter("*.csv")` | Glob pattern | Only download CSV files |
| `.localDirectory(...)` | Download path | Where to save files locally |
| `.preserveTimestamp(true)` | Keep file time | Preserve remote file's modification time |
| `.localFilter(null)` | Disable filter | Allow re-downloading same file |
| `Pollers.fixedDelay(120000)` | 2 minutes | Poll interval |
| `.maxMessagesPerPoll(1)` | One file | Process one file per poll |
| `.handle("csvFileProcessor", ...)` | Service activator | Call CsvFileProcessor.processFile() |

**Why `localFilter(null)`?**

By default, Spring Integration uses `AcceptOnceFileListFilter` which prevents re-downloading files. We disable this to allow timestamp-based change detection in `CsvFileProcessor`.

**3.4 Metadata Store**

```java
@Bean
public MetadataStore metadataStore() {
  return new SimpleMetadataStore();
}
```

Stores file timestamps in-memory to track which files have been processed and detect changes.

---

### 4. Polling Execution (Every 2 Minutes)

When the poller triggers:

```
┌─────────────────────────────────────────────────┐
│ POLLER TRIGGERS (every 2 minutes)               │
└─────────────────────────────────────────────────┘
        ↓
┌─────────────────────────────────────────────────┐
│ SFTP Inbound Adapter:                           │
│  1. Connect to SFTP server                      │
│  2. List files in remote directory "."          │
│  3. Apply filters:                              │
│     - AlwaysAcceptFileListFilter (all files)    │
│     - PatternFilter (*.csv only)                │
│  4. Download matching files to local directory  │
│  5. Preserve remote file timestamps             │
└─────────────────────────────────────────────────┘
        ↓
┌─────────────────────────────────────────────────┐
│ Message Channel:                                │
│  - Sends File object to handler                 │
└─────────────────────────────────────────────────┘
        ↓
┌─────────────────────────────────────────────────┐
│ Service Activator:                              │
│  - Calls CsvFileProcessor.processFile(File)     │
└─────────────────────────────────────────────────┘
```

Behind the scenes, Spring Integration:

1. Gets a session from `CachingSessionFactory`
2. Opens SSH connection using the private key
3. Authenticates with the SFTP server
4. Opens SFTP channel (SFTP protocol over SSH)
5. Lists files matching patterns
6. Downloads each file to local directory
7. Creates `File` object with preserved timestamp
8. Passes to message channel
9. Automatically closes channel and returns session to pool

---

### 5. File Processing with Timestamp Detection

**File:** `src/main/java/com/ird0/directory/service/CsvFileProcessor.java`

```java
public void processFile(File csvFile) {
  String filename = csvFile.getName();
  long currentTimestamp = csvFile.lastModified();

  String storedTimestamp = metadataStore.get(filename);

  if (storedTimestamp != null) {
    long lastProcessedTimestamp = Long.parseLong(storedTimestamp);
    if (currentTimestamp <= lastProcessedTimestamp) {
      // FILE UNCHANGED - SKIP
      log.info("File '{}' has not changed, skipping processing", filename);
      deleteFile(csvFile);
      return;
    }
  }

  // FILE CHANGED OR NEW - PROCESS
  try (InputStream inputStream = new FileInputStream(csvFile)) {
    ImportResult result = csvImportService.importFromCsvWithBatching(inputStream);

    log.info("Import completed for {}: {} total, {} new, {} updated, {} unchanged, {} failed",
        filename, result.totalRows(), result.newRows(), result.updatedRows(),
        result.unchangedRows(), result.failedRows());

    // Store timestamp for next comparison
    metadataStore.put(filename, String.valueOf(currentTimestamp));
  } finally {
    deleteFile(csvFile);
  }
}
```

**Logic:**

```
Download policyholders.csv (timestamp: 1234567890)
        ↓
Check MetadataStore for "policyholders.csv"
        ↓
    Not found → First time seeing this file → PROCESS
    Found → Compare timestamps:
            ├─ Current <= Stored → UNCHANGED → SKIP
            └─ Current > Stored → MODIFIED → PROCESS
        ↓
    [PROCESS PATH]
        ↓
Import CSV with change detection
        ↓
Store new timestamp in MetadataStore
        ↓
Delete local file
```

---

### 6. CSV Import with Change Detection

**File:** `src/main/java/com/ird0/directory/service/CsvImportService.java`

**6.1 ImportResult Structure**

```java
public record ImportResult(
    int totalRows,      // Total CSV rows processed
    int newRows,        // New inserts
    int updatedRows,    // Updates to changed data
    int unchangedRows,  // Skipped (data identical)
    int failedRows      // Validation/processing failures
) {}
```

**6.2 Batched Import Flow**

```java
@Transactional
public ImportResult importFromCsvWithBatching(InputStream csvData) {
  int totalRows = 0, newRows = 0, updatedRows = 0, unchangedRows = 0, failedRows = 0;
  List<DirectoryEntry> batch = new ArrayList<>(BATCH_SIZE);

  try (CSVParser parser = createCsvParser(reader)) {
    for (CSVRecord record : parser) {
      totalRows++;
      DirectoryEntry entry = parseRecord(record);

      if (entry != null) {
        batch.add(entry);

        if (batch.size() >= 500) {
          ImportResult batchResult = processBatch(batch);
          newRows += batchResult.newRows();
          updatedRows += batchResult.updatedRows();
          unchangedRows += batchResult.unchangedRows();
          failedRows += batchResult.failedRows();
          batch.clear();
        }
      }
    }
  }

  return new ImportResult(totalRows, newRows, updatedRows, unchangedRows, failedRows);
}
```

**6.3 Change Detection Per Row**

```java
private ImportResult upsertBatch(List<DirectoryEntry> entries) {
  int newRows = 0, updatedRows = 0, unchangedRows = 0, failedRows = 0;

  for (DirectoryEntry entry : entries) {
    try {
      Optional<DirectoryEntry> existing = repository.findByEmail(entry.getEmail());

      if (existing.isEmpty()) {
        // NEW ROW - INSERT
        repository.upsertByEmail(entry);
        newRows++;
      } else {
        DirectoryEntry existingEntry = existing.get();
        if (hasChanged(existingEntry, entry)) {
          // CHANGED ROW - UPDATE
          repository.upsertByEmail(entry);
          updatedRows++;
        } else {
          // UNCHANGED ROW - SKIP DATABASE WRITE
          unchangedRows++;
        }
      }
    } catch (Exception e) {
      failedRows++;
    }
  }

  return new ImportResult(entries.size(), newRows, updatedRows, unchangedRows, failedRows);
}
```

**6.4 Change Comparison**

```java
private boolean hasChanged(DirectoryEntry existing, DirectoryEntry newEntry) {
  return !Objects.equals(existing.getName(), newEntry.getName())
      || !Objects.equals(existing.getType(), newEntry.getType())
      || !Objects.equals(existing.getPhone(), newEntry.getPhone())
      || !Objects.equals(existing.getAddress(), newEntry.getAddress())
      || !Objects.equals(existing.getAdditionalInfo(), newEntry.getAdditionalInfo());
}
```

Compares all fields except `email` (the unique key).

---

### 7. Database Upsert (PostgreSQL)

**File:** `src/main/java/com/ird0/directory/repository/DirectoryEntryRepository.java`

```java
@Modifying
@Query(
    value = """
      INSERT INTO directory_entry (name, type, email, phone, address, additional_info)
      VALUES (:#{#entry.name}, :#{#entry.type}, :#{#entry.email},
              :#{#entry.phone}, :#{#entry.address}, :#{#entry.additionalInfo})
      ON CONFLICT (email) DO UPDATE SET
          name = EXCLUDED.name,
          type = EXCLUDED.type,
          phone = EXCLUDED.phone,
          address = EXCLUDED.address,
          additional_info = EXCLUDED.additional_info
      """,
    nativeQuery = true)
void upsertByEmail(@Param("entry") DirectoryEntry entry);
```

Uses PostgreSQL's `ON CONFLICT` clause for efficient upsert:
- If email doesn't exist: INSERT
- If email exists: UPDATE all fields

---

## Complete Flow Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│ 1. SPRING BOOT STARTUP                                          │
│    policyholders.yml loaded: sftp-import.enabled=true           │
└─────────────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────────────┐
│ 2. SPRING INTEGRATION CONFIGURATION                            │
│    (@ConditionalOnProperty activates beans)                    │
│                                                                  │
│  ┌──────────────────────────────────────────┐                  │
│  │ SftpIntegrationConfig                    │                  │
│  │  ├─ DefaultSftpSessionFactory            │                  │
│  │  ├─ CachingSessionFactory                │                  │
│  │  └─ SftpRemoteFileTemplate               │                  │
│  └──────────────────────────────────────────┘                  │
│                                                                  │
│  ┌──────────────────────────────────────────┐                  │
│  │ SftpPollingFlowConfig                    │                  │
│  │  ├─ MetadataStore (in-memory)            │                  │
│  │  ├─ ThreadPoolTaskExecutor               │                  │
│  │  ├─ MessageChannel (queue)               │                  │
│  │  └─ IntegrationFlow (polling)            │                  │
│  └──────────────────────────────────────────┘                  │
└─────────────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────────────┐
│ 3. APPLICATION READY - POLLING STARTS                          │
│    Initial delay: 1 second                                      │
│    Poll interval: 2 minutes (120000ms)                          │
└─────────────────────────────────────────────────────────────────┘
                        ↓
          ╔═══════════════════════════════╗
          ║   EVERY 2 MINUTES: POLL       ║
          ╚═══════════════════════════════╝
                        ↓
┌─────────────────────────────────────────────────────────────────┐
│ 4. SFTP INBOUND ADAPTER                                        │
│                                                                  │
│  ┌───────────────────────────────────────┐                     │
│  │ Connect to sftp-server:2222           │                     │
│  │  ↓                                     │                     │
│  │ List files: *.csv                     │                     │
│  │  ↓                                     │                     │
│  │ Download policyholders.csv            │                     │
│  │  → temp/sftp-downloads/               │                     │
│  │  → Preserve timestamp                 │                     │
│  └───────────────────────────────────────┘                     │
└─────────────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────────────┐
│ 5. CSV FILE PROCESSOR                                          │
│                                                                  │
│  File: policyholders.csv                                        │
│  Timestamp: 1234567890                                          │
│                                                                  │
│  Check MetadataStore:                                           │
│  ┌─────────────────────────────────────┐                       │
│  │ Key: "policyholders.csv"            │                       │
│  │ Stored: 1234567890                  │                       │
│  └─────────────────────────────────────┘                       │
│                                                                  │
│  Current (1234567890) <= Stored (1234567890)?                  │
│  ├─ YES → SKIP PROCESSING → DELETE FILE                        │
│  └─ NO  → CONTINUE TO IMPORT                                   │
└─────────────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────────────┐
│ 6. CSV IMPORT SERVICE (Batched)                               │
│                                                                  │
│  Parse CSV with Apache Commons CSV                              │
│  Batch size: 500 rows                                           │
│                                                                  │
│  For each row:                                                  │
│  ┌─────────────────────────────────────────┐                   │
│  │ 1. Validate required fields             │                   │
│  │    (name, type, email, phone)           │                   │
│  │ 2. Check: repository.findByEmail(email) │                   │
│  │    ├─ Not found → INSERT → newRows++    │                   │
│  │    └─ Found:                             │                   │
│  │       ├─ hasChanged? YES → UPDATE       │                   │
│  │       │                  → updatedRows++ │                   │
│  │       └─ hasChanged? NO  → SKIP         │                   │
│  │                          → unchangedRows++                   │
│  └─────────────────────────────────────────┘                   │
│                                                                  │
│  Transaction Strategy: Each batch in separate transaction       │
│  (@Transactional(propagation = REQUIRES_NEW))                  │
└─────────────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────────────┐
│ 7. DATABASE (PostgreSQL)                                       │
│                                                                  │
│  Upsert using ON CONFLICT (email):                              │
│  ┌─────────────────────────────────────────┐                   │
│  │ INSERT INTO directory_entry (...)       │                   │
│  │ VALUES (...)                             │                   │
│  │ ON CONFLICT (email) DO UPDATE SET ...   │                   │
│  └─────────────────────────────────────────┘                   │
│                                                                  │
│  Database: policyholders_db                                     │
└─────────────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────────────┐
│ 8. METADATA STORAGE & CLEANUP                                  │
│                                                                  │
│  ┌─────────────────────────────────────────┐                   │
│  │ MetadataStore.put("policyholders.csv",  │                   │
│  │                   "1234567890")         │                   │
│  └─────────────────────────────────────────┘                   │
│                ↓                                                 │
│  ┌─────────────────────────────────────────┐                   │
│  │ Delete local file:                      │                   │
│  │ temp/sftp-downloads/policyholders.csv   │                   │
│  └─────────────────────────────────────────┘                   │
└─────────────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────────────┐
│ 9. LOGGING                                                     │
│                                                                  │
│  INFO  Import completed for policyholders.csv:                  │
│        100 total, 5 new, 10 updated, 85 unchanged, 0 failed    │
└─────────────────────────────────────────────────────────────────┘
                        ↓
              ╔═══════════════════════╗
              ║  WAIT 2 MINUTES       ║
              ║  THEN POLL AGAIN      ║
              ╚═══════════════════════╝
```

---

## Key Implementation Details

### Transaction Management

Each batch runs in its own transaction:

```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
protected ImportResult processBatch(List<DirectoryEntry> batch) {
  return upsertBatch(batch);
}
```

**Benefit:** If one batch fails, others can still succeed.

### Email as Unique Key

The `DirectoryEntry` entity defines email as unique:

```java
@Column(unique = true, nullable = false)
private String email;
```

Database table has a unique constraint on email column.

### In-Memory Metadata Store

The `SimpleMetadataStore` stores timestamps in memory:
- Fast lookups
- Lost on application restart (acceptable - will just reprocess files once)
- For production, consider persistent store (Redis, database, etc.)

### Polling Configuration

Controlled via properties:
- `fixed-delay`: Time between poll completions (120000ms = 2 minutes)
- `initial-delay`: Delay before first poll (1000ms = 1 second)
- `max-messages-per-poll`: Process one file at a time (1)

---

## Performance Optimizations

### 1. Database Write Efficiency
- **Unchanged rows skipped**: No UPDATE if data identical
- **Batch processing**: 500 rows per transaction
- **Upsert operation**: Single query per row (INSERT or UPDATE)

### 2. File Processing Efficiency
- **Timestamp comparison**: Fast check before parsing
- **Immediate cleanup**: Delete local file after processing
- **Session pooling**: Reuse SSH connections via `CachingSessionFactory`

### 3. Resource Management
- **Thread pool**: Dedicated executor for SFTP operations
- **Message channel**: Queued processing (max 10 messages)
- **Auto-cleanup**: Spring Integration handles connection cleanup

---

## Troubleshooting

### Poll not triggering
- Check: `directory.sftp-import.enabled=true` in policyholders.yml
- Check: Polling configuration (fixed-delay, initial-delay)
- Check logs for Spring Integration initialization

### Files not downloading
- Check SFTP server connectivity
- Check SSH key authentication
- Check remote directory path
- Check file pattern filter (*.csv)

### Unchanged rows counted as updated
- Verify `hasChanged()` logic includes all relevant fields
- Check database values vs. CSV values (whitespace, nulls)

### Performance issues
- Reduce batch size if memory constrained
- Increase poll interval if too frequent
- Consider persistent MetadataStore for faster restarts

---

## References

- [Spring Integration SFTP Documentation](https://docs.spring.io/spring-integration/reference/sftp.html)
- [Enterprise Integration Patterns](https://www.enterpriseintegrationpatterns.com/)
- [Apache MINA SSHD](https://mina.apache.org/sshd-project/)
- [PostgreSQL ON CONFLICT](https://www.postgresql.org/docs/current/sql-insert.html#SQL-ON-CONFLICT)

# SFTP Import Architecture

## Overview

The Policyholders service includes an automated SFTP polling system that imports CSV data from an SFTP server into PostgreSQL with intelligent two-level change detection. The system uses Spring Integration for SFTP polling and Apache Commons CSV for file parsing.

**Key Features:**
- Automated polling every 2 minutes
- File-level change detection (timestamp-based)
- Row-level change detection (field comparison)
- Batch processing (500 rows per transaction)
- Detailed import metrics (new/updated/unchanged/failed counts)
- SSH key authentication
- Idempotent operations (safe to re-run)

## Data Flow

```
SFTP Server (port 2222, *.csv files)
        ↓
Spring Integration Inbound Adapter (polls every 2 minutes)
        ↓
Download to local directory (./temp/sftp-downloads)
        ↓
CsvFileProcessor (file timestamp comparison)
        ├─ If unchanged → Skip, delete local file
        └─ If changed/new → Process
                ↓
        CsvImportService (parse CSV, batch processing)
                ├─ Validate required fields
                ├─ For each row:
                │   ├─ findByEmail(email)
                │   ├─ If not found → INSERT (new)
                │   ├─ If found + changed → UPDATE (updated)
                │   └─ If found + unchanged → SKIP (unchanged)
                └─ Return ImportResult
                ↓
        Log: "Import completed: X total, Y new, Z updated, W unchanged, V failed"
                ↓
        Store file timestamp in MetadataStore
                ↓
        Delete local file
```

## Spring Integration Polling Architecture

### Configuration

**SFTP Import Properties (policyholders.yml):**

```yaml
directory:
  sftp-import:
    enabled: true
    host: ${SFTP_HOST:localhost}
    port: ${SFTP_PORT:2222}
    username: ${SFTP_USERNAME:policyholder-importer}
    private-key-path: ${SFTP_PRIVATE_KEY_PATH:./keys/sftp_client_key}
    connection-timeout: 10000
    polling:
      fixed-delay: 120000        # Poll every 2 minutes (milliseconds)
      initial-delay: 5000         # Wait 5 seconds after startup
      batch-size: 500             # Rows per database transaction
    local-directory: ./temp/sftp-downloads
    metadata-directory: ./data/sftp-metadata
```

**Key Parameters:**

| Parameter | Value | Purpose |
|-----------|-------|---------|
| `enabled` | true | Enable/disable SFTP import |
| `host` | localhost | SFTP server hostname |
| `port` | 2222 | SFTP server port |
| `username` | policyholder-importer | SFTP username |
| `private-key-path` | ./keys/sftp_client_key | SSH private key location |
| `connection-timeout` | 10000ms | SFTP connection timeout |
| `fixed-delay` | 120000ms | Polling interval (2 minutes) |
| `initial-delay` | 5000ms | Delay before first poll |
| `batch-size` | 500 | Rows per transaction |

### Inbound Channel Adapter

**Component:** `SftpPollingFlowConfig.java`

**Configuration:**
```java
@Bean
public IntegrationFlow sftpInboundFlow(SessionFactory<LsEntry> sftpSessionFactory) {
    return IntegrationFlows
        .from(Sftp.inboundAdapter(sftpSessionFactory)
            .preserveTimestamp(true)              // Preserve file lastModified
            .remoteDirectory("/")                  // SFTP root directory
            .regexFilter(".*\\.csv$")             // Only *.csv files
            .localDirectory(new File(localDirectory))
            .autoCreateLocalDirectory(true),
            e -> e.poller(Pollers
                .fixedDelay(pollingInterval)
                .initialDelay(initialDelay)))
        .handle(csvFileProcessor, "processFile")  // Process downloaded files
        .get();
}
```

**Key Features:**
- `preserveTimestamp(true)`: Keeps original file modification time
- `regexFilter(".*\\.csv$")`: Only downloads CSV files
- `autoCreateLocalDirectory(true)`: Creates local directory if missing
- `fixedDelay`: Polls at fixed intervals (not cron-based)

### SFTP Session Factory

**Component:** `SftpPollingFlowConfig.java`

**Configuration:**
```java
@Bean
public SessionFactory<LsEntry> sftpSessionFactory() {
    DefaultSftpSessionFactory factory = new DefaultSftpSessionFactory();
    factory.setHost(host);
    factory.setPort(port);
    factory.setUser(username);
    factory.setPrivateKey(new FileSystemResource(privateKeyPath));
    factory.setAllowUnknownKeys(true);
    factory.setTimeout(connectionTimeout);
    return factory;
}
```

**Authentication:**
- **Vault SSH CA mode** (recommended): Dynamic certificates from Vault - see [vault-ssh-ca.md](vault-ssh-ca.md)
- **Static key mode** (fallback): SSH private key from `./keys/sftp_client_key` - see [ssh-keys.md](ssh-keys.md)
- `allowUnknownKeys`: Accepts any host key (development setting)
- Production: Use Vault SSH CA with certificate-based authentication

## File-Level Change Detection

### Timestamp-Based Detection

**Component:** `CsvFileProcessor.java`

**Process:**
1. Extract file's `lastModified` timestamp
2. Query `MetadataStore` for stored timestamp
3. Compare timestamps:
   - **Not in metadata**: First time seen → PROCESS
   - **Current ≤ Stored**: File unchanged → SKIP, delete local file
   - **Current > Stored**: File modified → PROCESS

**Implementation:**
```java
public void processFile(File file) {
    long currentTimestamp = file.lastModified();
    String storedValue = metadataStore.get(file.getName());

    if (storedValue != null) {
        long storedTimestamp = Long.parseLong(storedValue);
        if (currentTimestamp <= storedTimestamp) {
            log.info("File '{}' unchanged, skipping", file.getName());
            file.delete();
            return;
        }
    }

    // Process file
    ImportResult result = csvImportService.importFromCsv(file);

    // Store timestamp after successful import
    metadataStore.put(file.getName(), String.valueOf(currentTimestamp));
    file.delete();
}
```

**Benefits:**
- Avoids parsing unchanged files
- Reduces CPU and memory usage
- Fast comparison (timestamp only)
- Prevents unnecessary database queries

**Limitation:**
- `SimpleMetadataStore` is in-memory (metadata lost on restart)
- After restart, all files reprocessed once
- Row-level detection prevents duplicate data writes

### MetadataStore Implementation

**Current:** `SimpleMetadataStore` (in-memory, non-persistent)

```java
@Bean
public SimpleMetadataStore metadataStore() {
    return new SimpleMetadataStore();  // In-memory only
}
```

**Production Enhancement (Future):**

Use persistent metadata store:

```java
@Bean
public PropertiesMetadataStore metadataStore() {
    PropertiesMetadataStore store = new PropertiesMetadataStore();
    store.setBaseDirectory("./data/sftp-metadata");
    return store;  // Persists to file
}
```

Or database-backed:

```java
@Bean
public JdbcMetadataStore metadataStore(DataSource dataSource) {
    return new JdbcMetadataStore(dataSource);  // Persists to database
}
```

## Row-Level Change Detection

### Field Comparison Logic

**Component:** `CsvImportService.java`

**Process:**
1. Parse CSV row into `DirectoryEntry`
2. Query database: `findByEmail(email)` (email is unique key)
3. If not found: **INSERT** → Count as "new"
4. If found: Compare fields
   - If any field changed: **UPDATE** → Count as "updated"
   - If all fields identical: **SKIP** → Count as "unchanged"

**Implementation:**
```java
public ImportResult importFromCsvWithBatching(File csvFile) {
    int totalRows = 0, newRows = 0, updatedRows = 0, unchangedRows = 0, failedRows = 0;

    for (CSVRecord record : csvRecords) {
        totalRows++;

        DirectoryEntry entry = parseRecord(record);
        Optional<DirectoryEntry> existing = repository.findByEmail(entry.getEmail());

        if (existing.isEmpty()) {
            repository.save(entry);
            newRows++;
        } else if (hasChanged(existing.get(), entry)) {
            updateEntity(existing.get(), entry);
            repository.save(existing.get());
            updatedRows++;
        } else {
            unchangedRows++;  // No database write
        }
    }

    return new ImportResult(totalRows, newRows, updatedRows, unchangedRows, failedRows);
}
```

**Change Detection Fields:**
```java
private boolean hasChanged(DirectoryEntry existing, DirectoryEntry newEntry) {
    return !Objects.equals(existing.getName(), newEntry.getName())
        || !Objects.equals(existing.getType(), newEntry.getType())
        || !Objects.equals(existing.getPhone(), newEntry.getPhone())
        || !Objects.equals(existing.getAddress(), newEntry.getAddress())
        || !Objects.equals(existing.getAdditionalInfo(), newEntry.getAdditionalInfo());
}
```

**Note:** Email is not compared (it's the lookup key, cannot change)

**Benefits:**
- Prevents unnecessary UPDATE statements
- Reduces database I/O
- Preserves audit timestamps for truly unchanged rows
- Accurate import metrics

## CSV Parsing and Batch Processing

### Apache Commons CSV

**Parser Configuration:**
```java
CSVFormat format = CSVFormat.DEFAULT.builder()
    .setHeader()                    // First row is header
    .setSkipHeaderRecord(true)      // Skip header in data
    .setIgnoreEmptyLines(true)      // Skip blank lines
    .setTrim(true)                  // Trim whitespace
    .build();

try (Reader reader = new FileReader(csvFile);
     CSVParser parser = new CSVParser(reader, format)) {

    List<CSVRecord> records = parser.getRecords();
    // Process records in batches
}
```

**Expected CSV Format:**
```csv
name,type,email,phone,address,additionalInfo
John Doe,individual,john@example.com,555-1234,123 Main St,Account since 2020
Smith Family,family,smith@example.com,555-5678,456 Oak Ave,Members: 4
```

**Required Fields:**
- `name` - Entry name
- `type` - Entry type (individual, family, corporate)
- `email` - Email address (unique identifier)
- `phone` - Phone number

**Optional Fields:**
- `address` - Physical address
- `additionalInfo` - Additional metadata

### Batch Processing

**Configuration:** `batch-size: 500` (rows per transaction)

**Process:**
1. Read all CSV records into memory
2. Split into batches of 500 rows
3. Process each batch in separate transaction
4. Commit after each batch

**Transaction Boundaries:**
```java
@Transactional
public ImportResult processBatch(List<DirectoryEntry> batch) {
    // Each batch is a separate transaction
    // Partial failure: Log errors, commit successful rows
}
```

**Benefits:**
- Balances memory usage and transaction size
- Partial failure doesn't rollback entire import
- Progress preserved on errors
- Can handle large CSV files (>10,000 rows)

**Failure Handling:**
- Invalid rows logged as "failed"
- Valid rows in batch still committed
- Import continues with next batch

## Import Results Tracking

### ImportResult Record

```java
public record ImportResult(
    int totalRows,      // Total CSV rows processed
    int newRows,        // New inserts
    int updatedRows,    // Updates to changed data
    int unchangedRows,  // Skipped (data identical)
    int failedRows      // Validation/processing failures
) {}
```

**Validation:**
- `totalRows = newRows + updatedRows + unchangedRows + failedRows`

### Log Output Examples

**First import (all new):**
```
INFO  Batched CSV import completed: 100 total, 100 new, 0 updated, 0 unchanged, 0 failed
```

**Reimport with no changes:**
```
INFO  Batched CSV import completed: 100 total, 0 new, 0 updated, 100 unchanged, 0 failed
```

**Reimport with mixed operations:**
```
INFO  Batched CSV import completed: 105 total, 5 new, 10 updated, 88 unchanged, 2 failed
```

**Interpretation:**
- **5 new**: New email addresses not in database
- **10 updated**: Existing entries with field changes (name, phone, etc.)
- **88 unchanged**: Existing entries, all fields identical (no DB write)
- **2 failed**: Validation errors (missing required fields, invalid email)

## Transaction Management

### Transaction Boundaries

**Service-Level Transactions:**
```java
@Service
public class CsvImportService {

    @Transactional
    public ImportResult importFromCsvWithBatching(File csvFile) {
        // Entire import in single transaction
        // Rollback on exception
    }
}
```

**Batch Transactions:**

For large files, each batch can be a separate transaction:

```java
public ImportResult importLargeCsv(File csvFile) {
    List<CSVRecord> allRecords = parseAll(csvFile);
    List<List<CSVRecord>> batches = partition(allRecords, 500);

    for (List<CSVRecord> batch : batches) {
        processBatchInTransaction(batch);  // Separate @Transactional method
    }
}
```

**Error Handling:**
- Transaction rollback on unchecked exceptions
- Failed rows logged, successful rows committed
- Import continues after batch failure

## Performance Characteristics

### Throughput

**Typical Performance:**
- CSV parsing: ~10,000 rows/second
- Database writes: ~1,000 rows/second
- Bottleneck: Database I/O

**With Change Detection:**
- All unchanged: ~5,000 rows/second (no DB writes)
- All new: ~1,000 rows/second (all inserts)
- Mixed: ~2,000-3,000 rows/second

**Batch Size Impact:**

| Batch Size | Memory | Performance | Recovery |
|------------|--------|-------------|----------|
| 100 | Low | Good | Minimal loss |
| 500 | Medium | Best | Some loss |
| 1000 | High | Good | More loss |

**Current Choice: 500** - Optimal balance

### Scaling Considerations

**Current Capacity:**
- CSV file size: Up to ~10MB (50,000 rows)
- Processing time: ~30-60 seconds
- Memory usage: ~50MB per import

**Limitations:**
- Single-threaded processing
- Entire CSV loaded into memory
- No parallel batch processing

**Future Enhancements:**
- Stream processing for large files (>100MB)
- Parallel batch execution
- Async import with status tracking

## Configuration Reference

### Complete Property Reference

```yaml
directory:
  sftp-import:
    enabled: true                                    # Enable/disable import
    host: localhost                                  # SFTP server hostname
    port: 2222                                       # SFTP server port
    username: policyholder-importer                  # SFTP username
    private-key-path: ./keys/sftp_client_key         # SSH private key
    connection-timeout: 10000                        # Connection timeout (ms)
    polling:
      fixed-delay: 120000                            # Poll interval (ms)
      initial-delay: 5000                            # Delay before first poll (ms)
      batch-size: 500                                # Rows per transaction
    local-directory: ./temp/sftp-downloads           # Local download directory
    metadata-directory: ./data/sftp-metadata         # Metadata storage
    error-handling:
      enabled: true                                  # Enable error handling
      error-directory: ./data/sftp-errors            # Error file storage
      dead-letter-directory: ./data/sftp-failed      # Failed file storage
    retry:
      enabled: true                                  # Enable retry logic
      max-attempts: 3                                # Max retry attempts
      initial-delay: 5000                            # Initial retry delay (ms)
      backoff-multiplier: 1.5                        # Exponential backoff
      max-delay: 300000                              # Max retry delay (ms)
```

## Manual Import REST Endpoint

**Alternative to automatic polling:**

```bash
POST /api/policyholders/import
Content-Type: multipart/form-data
```

**Usage:**
```bash
curl -X POST http://localhost:8081/api/policyholders/import \
  -F "file=@policyholders.csv"
```

**Response:**
```json
{
  "totalRows": 100,
  "newRows": 50,
  "updatedRows": 30,
  "unchangedRows": 18,
  "failedRows": 2
}
```

**Benefits:**
- On-demand import (no waiting for poll)
- Test imports during development
- Emergency data updates
- Same change detection logic as polling

## Future Enhancements

### From SFTP Import Review

**Priority: HIGH - Retry Logic**
- Current: No retry on import failures
- Recommendation: Implement exponential backoff retry
- Benefit: Resilience to transient failures

**Priority: MEDIUM - Manual Import Endpoint**
- Status: ✅ Already implemented
- Endpoint: `POST /api/policyholders/import`

**Priority: LOW-MEDIUM - Import History Tracking**
- Current: No audit trail of imports
- Recommendation: Store import results in database
- Benefit: Troubleshooting, analytics, compliance

**Priority: LOW - Persistent Metadata Store**
- Current: In-memory (lost on restart)
- Recommendation: File-based or database-backed
- Benefit: Avoid reprocessing after restart

## Related Topics

- [USER_GUIDE.md#sftp-import-operations](../USER_GUIDE.md#sftp-import-operations) - Operational procedures
- [vault-ssh-ca.md](vault-ssh-ca.md) - Vault SSH CA certificate authentication (recommended)
- [ssh-keys.md](ssh-keys.md) - Static SSH key authentication (fallback)
- [configuration.md](configuration.md) - Configuration management
- [database.md](database.md) - Database operations
- [reviews/sftp-import-review-2026-01-08.md](../reviews/sftp-import-review-2026-01-08.md) - Technical review

## References

- [Spring Integration SFTP Support](https://docs.spring.io/spring-integration/reference/sftp.html)
- [Apache Commons CSV](https://commons.apache.org/proper/commons-csv/)
- [Spring Batch (for large-scale imports)](https://spring.io/projects/spring-batch)

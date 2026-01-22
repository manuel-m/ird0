# SFTP Data Import

## Overview

The SFTP Data Import feature provides automated synchronization of directory data from external systems. The Policyholders Service polls an SFTP server at regular intervals, downloads CSV files, and imports new or changed records with intelligent deduplication.

### Business Value

- **Automated Data Sync**: No manual data entry required
- **Change Detection**: Only processes changed files and records
- **Audit Trail**: Import metrics track all data changes
- **Secure Transfer**: SSH public key authentication

---

## User Stories

### US-SFTP-001: Automated Polling
**As a** system administrator
**I want** the system to automatically poll for new data files
**So that** directory data stays synchronized without manual intervention

**Acceptance Criteria**:
- System polls SFTP every 2 minutes
- Unchanged files (same timestamp) are skipped
- New and modified files are downloaded and processed

### US-SFTP-002: File Change Detection
**As a** system administrator
**I want** the system to detect file changes
**So that** unchanged files are not reprocessed

**Acceptance Criteria**:
- File timestamp compared to last processed timestamp
- Only files with newer timestamps are processed
- Metadata stored for each processed file

### US-SFTP-003: Row Change Detection
**As a** system administrator
**I want** the system to detect row-level changes
**So that** unchanged records don't trigger database writes

**Acceptance Criteria**:
- Existing records identified by email (unique key)
- Field-by-field comparison for existing records
- Only changed fields trigger UPDATE
- Identical records counted as "unchanged"

### US-SFTP-004: Import Results
**As a** system administrator
**I want** to see import statistics
**So that** I can monitor data synchronization health

**Acceptance Criteria**:
- Total rows processed
- New records inserted
- Existing records updated
- Unchanged records skipped
- Failed records with error details

---

## Business Rules

| Rule ID | Description | Enforcement |
|---------|-------------|-------------|
| BR-SFTP-001 | Poll interval configurable (default: 2 min) | Spring Integration config |
| BR-SFTP-002 | Only *.csv files processed | File filter |
| BR-SFTP-003 | Email uniqueness determines insert vs update | Service layer logic |
| BR-SFTP-004 | Failed rows don't stop import | Try-catch per row |
| BR-SFTP-005 | Batch size: 500 rows per transaction | Configuration |
| BR-SFTP-006 | SSH key authentication only | SFTP server config |

---

## Architecture

### Data Flow

```
┌─────────────────┐
│  External CSV   │
│    Producer     │
└────────┬────────┘
         │ Upload via SFTP
         ▼
┌─────────────────┐
│  SFTP Server    │◄──────┐
│    :2222        │       │
└────────┬────────┘       │
         │                │
         │ Poll every 2 min
         ▼                │
┌─────────────────┐       │
│  Policyholders  │       │
│    Service      │       │
│    :8081        │       │
└────────┬────────┘       │
         │                │
         │ Check timestamp│
         │                │
┌────────▼────────┐       │
│ File Changed?   │       │
│                 │───No──┘
└────────┬────────┘
         │ Yes
         ▼
┌─────────────────┐
│  Parse CSV      │
│  Row by Row     │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ Check Email     │
│ in Database     │
└────────┬────────┘
         │
    ┌────┴────┐
    │         │
    ▼         ▼
┌───────┐ ┌───────┐
│INSERT │ │Compare│
│ new   │ │fields │
└───────┘ └───┬───┘
              │
         ┌────┴────┐
         │         │
         ▼         ▼
     ┌───────┐ ┌────────┐
     │UPDATE │ │ SKIP   │
     │changed│ │unchanged│
     └───────┘ └────────┘
```

### Component Diagram

```
┌─────────────────────────────────────────────────────────┐
│                 Policyholders Service                    │
│                                                          │
│  ┌──────────────────┐    ┌─────────────────────────┐   │
│  │ SFTP Session     │───>│ Inbound Channel         │   │
│  │ Factory          │    │ Adapter                  │   │
│  │ (credentials)    │    │ (poll every 2 min)       │   │
│  └──────────────────┘    └───────────┬─────────────┘   │
│                                      │                  │
│                          ┌───────────▼─────────────┐   │
│                          │ File Message Handler    │   │
│                          │ (downloads to temp)     │   │
│                          └───────────┬─────────────┘   │
│                                      │                  │
│  ┌──────────────────┐    ┌───────────▼─────────────┐   │
│  │ Metadata Store   │◄──>│ Import Service          │   │
│  │ (file timestamps)│    │ (parse + import)        │   │
│  └──────────────────┘    └───────────┬─────────────┘   │
│                                      │                  │
│                          ┌───────────▼─────────────┐   │
│                          │ Directory Repository    │   │
│                          │ (database operations)   │   │
│                          └───────────┬─────────────┘   │
└──────────────────────────────────────┼──────────────────┘
                                       │
                           ┌───────────▼─────────────┐
                           │    PostgreSQL           │
                           │    policyholders_db     │
                           └─────────────────────────┘
```

---

## Two-Level Change Detection

### Level 1: File-Level Detection

Uses file modification timestamp to skip unchanged files.

| Condition | Action |
|-----------|--------|
| File timestamp > stored timestamp | Process file |
| File timestamp = stored timestamp | Skip file |
| File not in metadata | Process file (new) |

**Implementation**:
- Spring Integration `SimpleMetadataStore`
- Stores: `{filename -> lastModified}`
- In-memory (lost on restart)

### Level 2: Row-Level Detection

Compares each row against existing database record.

| Condition | Action | Counted As |
|-----------|--------|------------|
| Email not in DB | INSERT | newRows |
| Email exists, fields differ | UPDATE | updatedRows |
| Email exists, fields identical | SKIP | unchangedRows |

**Compared Fields**:
- name
- type
- phone
- address
- additionalInfo
- webhookUrl

---

## CSV Format

### Required Columns

| Column | Description | Required |
|--------|-------------|----------|
| name | Full name | Yes |
| type | individual/family/corporate | Yes |
| email | Email address (unique key) | Yes |
| phone | Phone number | Yes |
| address | Physical address | No |
| additionalInfo | Free-form notes | No |
| webhookUrl | Webhook endpoint | No |

### Example CSV

```csv
name,type,email,phone,address,additionalInfo,webhookUrl
John Doe,individual,john@example.com,+33612345678,123 Main St Paris,Premium customer,https://hooks.example.com/john
Jane Smith,family,jane@example.com,+33698765432,456 Oak Ave Lyon,,
Acme Corp,corporate,contact@acme.com,+33145678900,789 Business Blvd,Enterprise account,https://hooks.acme.com/notify
```

---

## Configuration

### SFTP Import Settings

```yaml
# application.yml
sftp:
  import:
    enabled: true
    host: ${SFTP_HOST:sftp-server}
    port: ${SFTP_PORT:2222}
    username: ${SFTP_USERNAME:policyholder-importer}
    private-key-path: ${SFTP_PRIVATE_KEY_PATH:./keys/client_key}
    remote-directory: /
    local-directory: ./temp/sftp-downloads
    file-pattern: "*.csv"
    polling-interval: 120000  # 2 minutes
    initial-delay: 5000       # 5 seconds
    batch-size: 500
```

### Vault SSH CA (Optional)

When Vault is enabled, ephemeral certificates are used instead of static keys:

```yaml
vault:
  enabled: ${VAULT_ENABLED:false}
  addr: ${VAULT_ADDR:http://vault:8200}
  token: ${VAULT_TOKEN}
  ssh-ca:
    mount-path: ssh-client-signer
    role-name: directory-service
    ttl: 15m
    principal: policyholder-importer
```

---

## Import Process

### Step-by-Step Flow

1. **Poll SFTP**
   - Spring Integration polls SFTP server
   - Lists files matching `*.csv` pattern

2. **Check Metadata**
   - For each file, check stored timestamp
   - Skip files with unchanged timestamps

3. **Download File**
   - Download changed/new files to local temp directory
   - Store file in `./temp/sftp-downloads/`

4. **Parse CSV**
   - Open file with Apache Commons CSV
   - Read headers, validate required columns

5. **Process Rows**
   - For each row (in batches of 500):
     - Validate required fields
     - Lookup email in database
     - Determine operation (INSERT/UPDATE/SKIP)
     - Execute operation

6. **Update Metadata**
   - Store new file timestamp
   - File will be skipped on next poll (if unchanged)

7. **Log Results**
   - Log import summary
   - Track metrics

### Import Results

```java
public class ImportResult {
    private int totalRows;      // Total rows in file
    private int newRows;        // New records inserted
    private int updatedRows;    // Existing records updated
    private int unchangedRows;  // Identical records skipped
    private int failedRows;     // Rows that failed validation
    private List<String> errors; // Error messages
}
```

---

## SFTP Server

### Read-Only Access

The SFTP server provides read-only access to the `./data` directory:

| Operation | Allowed |
|-----------|---------|
| LIST (ls) | Yes |
| GET (download) | Yes |
| PUT (upload) | No |
| DELETE | No |
| MKDIR | No |
| RENAME | No |

### Authentication

**Static Keys (Default)**:
- Client generates RSA key pair
- Public key added to `authorized_keys`
- Private key used for connection

**Vault SSH CA (Optional)**:
- Ephemeral RSA-4096 key pair generated per connection
- Public key signed by Vault CA
- 15-minute certificate TTL
- Forward secrecy: keys never stored

---

## Monitoring

### Health Check

```bash
# SFTP Server health
curl http://localhost:9090/actuator/health
```

### Import Metrics

Log output includes import statistics:

```
INFO  ImportService - Import completed:
  file=policyholders.csv,
  total=1000,
  new=150,
  updated=45,
  unchanged=800,
  failed=5
```

### Manual Import Trigger

```bash
# Trigger import manually (for testing)
curl -X POST http://localhost:8081/api/policyholders/import/trigger
```

---

## Error Handling

### Row-Level Errors

| Error | Handling |
|-------|----------|
| Invalid email format | Skip row, log error |
| Missing required field | Skip row, log error |
| Duplicate email in file | Skip duplicate, keep first |
| Database error | Skip row, log error, continue |

### File-Level Errors

| Error | Handling |
|-------|----------|
| SFTP connection failed | Retry on next poll |
| File download failed | Skip file, log error |
| Invalid CSV format | Skip file, log error |
| Empty file | Skip file, log warning |

### Transaction Management

- Batch size: 500 rows per transaction
- Partial failure: Failed rows logged, successful rows committed
- No rollback of entire import on single row failure

---

## Testing

### Generate Test Data

```bash
# Use data generator utility
java -jar utilities/directory-data-generator/target/directory-data-generator-1.0.0-exec.jar \
  --count 1000 \
  --output ./data/policyholders.csv
```

### Test SFTP Connection

```bash
# Connect with static key
sftp -i ./keys/client_key -P 2222 policyholder-importer@localhost

# List files
sftp> ls
policyholders.csv

# Download file
sftp> get policyholders.csv
```

### Verify Import

```bash
# Check entry count after import
curl http://localhost:8081/api/policyholders | jq 'length'

# Check specific entry by email
curl http://localhost:8081/api/policyholders?email=john@example.com
```

---

## Troubleshooting

### Common Issues

| Issue | Cause | Solution |
|-------|-------|----------|
| Files not processing | Timestamp unchanged | Touch file or force reprocess |
| Authentication failed | Wrong key format | Use RSA key, check authorized_keys |
| Import stopped | Service restart | Metadata lost, files reprocess |
| Duplicate email error | CSV has duplicates | Dedupe CSV before upload |

### Debug Logging

```yaml
logging:
  level:
    com.ird0.directory.sftp: DEBUG
    org.springframework.integration: DEBUG
```

---

## Related Documentation

- [PRD.md](../PRD.md) - Product requirements (FR-DIR-003, FR-DIR-004)
- [ARCHITECTURE.md](../ARCHITECTURE.md) - SFTP integration architecture
- [directory-management.md](directory-management.md) - Directory API reference
- [topics/sftp-import.md](../topics/sftp-import.md) - Technical deep-dive
- [topics/vault-ssh-ca.md](../topics/vault-ssh-ca.md) - Certificate authentication
- [topics/ssh-keys.md](../topics/ssh-keys.md) - Static key management

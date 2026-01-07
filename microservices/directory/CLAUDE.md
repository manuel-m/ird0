# Directory Service

This document provides detailed guidance for working with the Directory Service microservice.

## Overview

The Directory Service is a multi-instance REST API for managing directory entries (policyholders, experts, and providers). It uses Spring Boot 3.5.0, Spring Data JPA with Hibernate, and PostgreSQL for data persistence.

**Key Features:**
- Multi-instance deployment (same codebase, different configurations)
- RESTful CRUD operations
- PostgreSQL database (separate database per instance)
- Spring Boot Actuator for health monitoring
- Configuration-driven API paths and database locations

## Multi-Instance Microservice Pattern

This service demonstrates a unique architecture where a single microservice is deployed **three times** with different configurations:

| Instance | Port | API Path | Database |
|----------|------|----------|----------|
| Policyholders | 8081 | `/api/policyholders` | `policyholders_db` (PostgreSQL) |
| Experts | 8082 | `/api/experts` | `experts_db` (PostgreSQL) |
| Providers | 8083 | `/api/providers` | `providers_db` (PostgreSQL) |

Each instance:
- Runs the same application code (`DirectoryApplication.java`)
- Uses the same controller, service, and repository classes
- Loads different configuration files at startup
- Operates independently with its own database

This pattern eliminates code duplication while maintaining service isolation.

## Configuration Files

Configuration files are located in `configs/`:

### `application.yml` (Common Configuration)

Shared configuration applied to all instances:

```yaml
spring:
  application:
    name: directory-service

  datasource:
    driver-class-name: org.postgresql.Driver
    username: ${POSTGRES_USER:directory_user}
    password: ${POSTGRES_PASSWORD:directory_pass}

  jpa:
    hibernate:
      ddl-auto: update                                    # Auto-update schema
    show-sql: true                                         # Log SQL statements
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics                      # Actuator endpoints
```

**Key Settings:**
- `hibernate.ddl-auto: update` - Automatically creates/updates database schema
- `show-sql: true` - Logs all SQL queries for debugging
- PostgreSQL dialect from Hibernate core (no additional dependency required)
- Credentials from environment variables with defaults for local development
- Actuator endpoints enabled for monitoring

### Instance-Specific Configuration Files

Each instance has its own YAML file that overrides specific settings:

**`policyholders.yml`:**
```yaml
server:
  port: 8081

directory:
  api:
    base-path: /api/policyholders
  sftp-import:
    enabled: true
    host: ${SFTP_HOST:localhost}
    port: ${SFTP_PORT:2222}
    username: ${SFTP_USERNAME:policyholder-importer}
    private-key-path: ${SFTP_PRIVATE_KEY_PATH:./keys/sftp_client_key}
    remote-file-path: policyholders.csv
    connection-timeout: 10000
    polling:
      fixed-delay: 120000      # Poll every 2 minutes (in milliseconds)
      initial-delay: 1000      # Wait 1 second after startup before first poll
      batch-size: 500          # Process CSV in batches of 500 rows
    local-directory: ./temp/sftp-downloads
    metadata-directory: ./data/sftp-metadata

spring:
  datasource:
    url: jdbc:postgresql://${POSTGRES_HOST:localhost}:${POSTGRES_PORT:5432}/policyholders_db
```

**`experts.yml`:**
```yaml
server:
  port: 8082

directory:
  api:
    base-path: /api/experts

spring:
  datasource:
    url: jdbc:postgresql://${POSTGRES_HOST:localhost}:${POSTGRES_PORT:5432}/experts_db
```

**`providers.yml`:**
```yaml
server:
  port: 8083

directory:
  api:
    base-path: /api/providers

spring:
  datasource:
    url: jdbc:postgresql://${POSTGRES_HOST:localhost}:${POSTGRES_PORT:5432}/providers_db
```

**Configuration Loading:**
Instances load both files in order: `application.yml` (common) → instance-specific YAML (overrides).

**Environment Variables:**
- `POSTGRES_HOST`: Database host (default: `localhost` for local, `postgres` in Docker)
- `POSTGRES_PORT`: Database port (default: `5432`)
- `POSTGRES_USER`: Database username (default: `directory_user`)
- `POSTGRES_PASSWORD`: Database password (default: `directory_pass`)

## SFTP Import Architecture

The Policyholders service includes an SFTP polling system that automatically imports CSV data from an SFTP server. This section describes how the import flow works.

### Overview

The SFTP import system uses Spring Integration to poll an SFTP server every 2 minutes, download CSV files, detect changes, and import data into the PostgreSQL database with intelligent change detection.

**Key Components:**
- `SftpPollingFlowConfig` - Spring Integration flow configuration
- `CsvFileProcessor` - File processing with timestamp-based change detection
- `CsvImportService` - CSV parsing and database import with change detection
- `AlwaysAcceptFileListFilter` - SFTP file filter (accepts all files)
- `MetadataStore` - Stores file timestamps to track changes

### How It Works

**1. SFTP Polling (Every 2 Minutes)**
- Spring Integration polls the SFTP server (configured via `polling.fixed-delay`)
- Filters for `*.csv` files in the remote directory
- Downloads matching files to local directory (preserving timestamps)
- Triggers `CsvFileProcessor` for each downloaded file

**2. Timestamp-Based Change Detection**
- `CsvFileProcessor` checks the file's `lastModified` timestamp
- Compares with stored timestamp in `MetadataStore`
- If unchanged: Logs skip message and deletes local file
- If changed or new: Proceeds to import

**3. CSV Import with Change Detection**
- Reads CSV file with Apache Commons CSV
- Validates required fields (name, type, email, phone)
- Processes in batches of 500 rows
- For each row:
  - Checks if email exists in database
  - If new: INSERT (counted as "new")
  - If exists and data changed: UPDATE (counted as "updated")
  - If exists and data unchanged: SKIP database write (counted as "unchanged")
- Returns detailed `ImportResult` with breakdown of operations

**4. Enhanced Import Results**
- `ImportResult` tracks five counts:
  - `totalRows` - Total CSV rows processed
  - `newRows` - Rows inserted (new entries)
  - `updatedRows` - Rows updated (existing entries with changes)
  - `unchangedRows` - Rows skipped (existing entries, no changes)
  - `failedRows` - Rows that failed validation or processing

**5. Metadata Storage**
- After successful import, stores file timestamp in `MetadataStore`
- Used for next poll to detect file changes
- Prevents reprocessing unchanged files

### Configuration Properties

```yaml
directory:
  sftp-import:
    enabled: true                    # Enable/disable SFTP import
    host: localhost                   # SFTP server hostname
    port: 2222                        # SFTP server port
    username: policyholder-importer   # SFTP username
    private-key-path: ./keys/sftp_client_key  # SSH private key path
    remote-file-path: policyholders.csv       # Remote CSV filename (not used in polling)
    connection-timeout: 10000         # Connection timeout (milliseconds)
    polling:
      fixed-delay: 120000             # Poll interval (2 minutes)
      initial-delay: 1000             # Delay before first poll (1 second)
      batch-size: 500                 # CSV batch size for database writes
    local-directory: ./temp/sftp-downloads     # Local download directory
    metadata-directory: ./data/sftp-metadata   # Metadata storage directory
```

### Import Flow Diagram

```
SFTP Server (*.csv files)
        ↓
[Spring Integration Polling] (every 2 minutes)
        ↓
[Download to local-directory]
        ↓
[CsvFileProcessor]
        ├─ Check timestamp in MetadataStore
        ├─ If unchanged → Log skip → Delete file
        └─ If changed/new → Process
                ↓
        [CsvImportService.importFromCsvWithBatching]
                ├─ Parse CSV (batch size: 500)
                ├─ Validate required fields
                └─ For each entry:
                      ├─ findByEmail(email)
                      ├─ If not exists → INSERT (new)
                      ├─ If exists + changed → UPDATE (updated)
                      └─ If exists + unchanged → SKIP (unchanged)
                ↓
        [Return ImportResult]
                ↓
        Log: "Import completed: X total, Y new, Z updated, W unchanged, V failed"
                ↓
        [Store timestamp in MetadataStore]
                ↓
        [Delete local file]
```

### Change Detection Logic

The `hasChanged()` method compares all fields between existing and new entries:

```java
private boolean hasChanged(DirectoryEntry existing, DirectoryEntry newEntry) {
  return !Objects.equals(existing.getName(), newEntry.getName())
      || !Objects.equals(existing.getType(), newEntry.getType())
      || !Objects.equals(existing.getPhone(), newEntry.getPhone())
      || !Objects.equals(existing.getAddress(), newEntry.getAddress())
      || !Objects.equals(existing.getAdditionalInfo(), newEntry.getAdditionalInfo());
}
```

**Note:** Email is not compared (it's the unique key for lookups).

### Log Output Examples

**File unchanged (timestamp match):**
```
INFO  File 'policyholders.csv' has not changed since last poll (current: 1234567890, stored: 1234567890), skipping processing
```

**File changed - first import:**
```
INFO  File 'policyholders.csv' not seen before, will process
INFO  Starting batched CSV import with batch size: 500
INFO  Batched CSV import completed: 100 total, 100 new, 0 updated, 0 unchanged, 0 failed
INFO  Import completed for policyholders.csv: 100 total, 100 new, 0 updated, 0 unchanged, 0 failed
```

**File changed - reimport with no data changes:**
```
INFO  File 'policyholders.csv' has been modified (current: 1234567999, stored: 1234567890), will process
INFO  Starting batched CSV import with batch size: 500
INFO  Batched CSV import completed: 100 total, 0 new, 0 updated, 100 unchanged, 0 failed
INFO  Import completed for policyholders.csv: 100 total, 0 new, 0 updated, 100 unchanged, 0 failed
```

**File changed - mixed operations:**
```
INFO  Batched CSV import completed: 105 total, 5 new, 10 updated, 90 unchanged, 0 failed
INFO  Import completed for policyholders.csv: 105 total, 5 new, 10 updated, 90 unchanged, 0 failed
```

### Performance Benefits

**Database Efficiency:**
- Skips unnecessary UPDATE operations for unchanged data
- Only writes to database when data actually changes
- Reduces database load on repeated imports

**File Change Detection:**
- Avoids reprocessing files that haven't changed
- Uses file timestamp comparison (fast)
- Downloads file only if potentially changed

### Implementation Files

| File | Purpose |
|------|---------|
| `config/SftpPollingFlowConfig.java` | Spring Integration SFTP polling flow |
| `config/SftpImportProperties.java` | Configuration properties binding |
| `config/AlwaysAcceptFileListFilter.java` | SFTP file filter (accepts all files) |
| `service/CsvFileProcessor.java` | File processing with timestamp tracking |
| `service/CsvImportService.java` | CSV parsing and database import |
| `repository/DirectoryEntryRepository.java` | JPA repository with upsert method |

## Building the Directory Service

**Build directory microservice only:**
```bash
mvn -f microservices/directory/pom.xml clean package
```

**Build from project root:**
```bash
mvn clean package
```

**Output:**
- `target/directory-1.0.0.jar` (~8KB) - Standard JAR with classes only (used as Maven dependency)
- `target/directory-1.0.0-exec.jar` (~65MB) - Executable Spring Boot JAR with all dependencies

**Note:** The standard JAR is used as a dependency by the `directory-data-generator` utility.

## Running Locally

Run a single instance locally for development and debugging:

**Policyholders service (port 8081):**
```bash
cd microservices/directory
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.config.location=file:configs/application.yml,file:configs/policyholders.yml"
```

**Experts service (port 8082):**
```bash
cd microservices/directory
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.config.location=file:configs/application.yml,file:configs/experts.yml"
```

**Providers service (port 8083):**
```bash
cd microservices/directory
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.config.location=file:configs/application.yml,file:configs/providers.yml"
```

**What Happens:**
- Service starts on the configured port
- Connects to PostgreSQL database (requires PostgreSQL to be running)
- Database schema is auto-created/updated based on JPA entities
- SQL logging is enabled (`show-sql: true`)
- Service is ready to accept HTTP requests

**To stop the service:**
Press `Ctrl+C`

### Running with Local PostgreSQL

**Option 1: Use Docker Compose PostgreSQL**
```bash
docker compose up postgres -d
```
Then run services locally (they will connect to Docker PostgreSQL on localhost:5432).

**Option 2: Local PostgreSQL Installation**

Create databases (one-time setup):
```bash
psql -U postgres -c "CREATE DATABASE policyholders_db;"
psql -U postgres -c "CREATE DATABASE experts_db;"
psql -U postgres -c "CREATE DATABASE providers_db;"
psql -U postgres -c "CREATE USER directory_user WITH PASSWORD 'directory_pass';"
psql -U postgres -c "GRANT ALL PRIVILEGES ON DATABASE policyholders_db TO directory_user;"
psql -U postgres -c "GRANT ALL PRIVILEGES ON DATABASE experts_db TO directory_user;"
psql -U postgres -c "GRANT ALL PRIVILEGES ON DATABASE providers_db TO directory_user;"
```

## Running with Docker

**Run specific instance:**
```bash
# From project root
docker compose up policyholders
docker compose up experts
docker compose up providers
```

**Run all instances:**
```bash
docker compose up policyholders experts providers
```

## Testing the API

### Basic CRUD Operations

Replace `8081` and `/api/policyholders` with the appropriate port and path for experts (8082, `/api/experts`) or providers (8083, `/api/providers`).

**List all entries (empty initially):**
```bash
curl http://localhost:8081/api/policyholders
```

**Create a new entry:**
```bash
curl -X POST http://localhost:8081/api/policyholders \
  -H "Content-Type: application/json" \
  -d '{"name":"John Doe","type":"individual","email":"john@example.com","phone":"555-1234"}'
```

**Get a specific entry:**
```bash
curl http://localhost:8081/api/policyholders/1
```

**Update an entry:**
```bash
curl -X PUT http://localhost:8081/api/policyholders/1 \
  -H "Content-Type: application/json" \
  -d '{"name":"John Doe","type":"individual","email":"john.doe@example.com","phone":"555-5678"}'
```

**Delete an entry:**
```bash
curl -X DELETE http://localhost:8081/api/policyholders/1
```

### DirectoryEntry Model

All endpoints accept/return the `DirectoryEntry` model:

```json
{
  "id": 1,
  "name": "John Doe",
  "type": "individual",
  "email": "john.doe@example.com",
  "phone": "555-1234",
  "address": "123 Main St, Springfield, IL",
  "additionalInfo": "Account since 1985-03-15"
}
```

**Fields:**
- `id` (Long) - Auto-generated primary key
- `name` (String) - Entry name
- `type` (String) - Entry type: `individual`, `family`, or `corporate`
- `email` (String) - Email address
- `phone` (String) - Phone number
- `address` (String, optional) - Physical address
- `additionalInfo` (String, optional) - Additional metadata

## Actuator Endpoints

Spring Boot Actuator provides health and metrics endpoints:

```bash
# Health check
curl http://localhost:8081/actuator/health

# Application info
curl http://localhost:8081/actuator/info

# Metrics
curl http://localhost:8081/actuator/metrics

# List all available endpoints
curl http://localhost:8081/actuator
```

Replace port `8081` with `8082` for experts or `8083` for providers.

## Running Tests

**Run tests for directory microservice:**
```bash
mvn -f microservices/directory/pom.xml test
```

**Run tests from project root:**
```bash
mvn test
```

## Key Implementation Notes

### Spring Boot Application Entry Point

`DirectoryApplication.java` is the main entry point with `@SpringBootApplication` annotation:

```java
@SpringBootApplication
public class DirectoryApplication {
    public static void main(String[] args) {
        SpringApplication.run(DirectoryApplication.class, args);
    }
}
```

This single application class is reused across all three service instances, with behavior controlled entirely by the injected YAML configuration.

### Configuration-Driven API Paths

The REST controller uses Spring's property placeholder to configure the base path dynamically:

```java
@RestController
@RequestMapping("${directory.api.base-path:/api/entries}")
public class DirectoryEntryController {
    // ...
}
```

The `directory.api.base-path` property is set in instance-specific YAML files:
- Policyholders: `/api/policyholders`
- Experts: `/api/experts`
- Providers: `/api/providers`

This allows the same controller code to serve different API paths based on configuration.

### PostgreSQL with Hibernate

Each service instance uses its own PostgreSQL database in a shared PostgreSQL container.

**Database Architecture:**
```
PostgreSQL Container (port 5432)
├── policyholders_db → Policyholders Service (port 8081)
├── experts_db → Experts Service (port 8082)
└── providers_db → Providers Service (port 8083)
```

**Key Features:**
- **Data Isolation**: Each database is completely isolated
- **Connection Pooling**: Each service maintains its own HikariCP connection pool
- **Schema Management**: Hibernate auto-creates/updates schema (`ddl-auto: update`)
- **Data Persistence**: Data persists in Docker volume `postgres-data`

**PostgreSQL Dialect:**
```yaml
spring.jpa.properties.hibernate.dialect: org.hibernate.dialect.PostgreSQLDialect
```
Included in Hibernate core (no additional dependencies required).

**Database Schema:**
```sql
CREATE TABLE directory_entry (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(50),
    email VARCHAR(255),
    phone VARCHAR(50),
    address TEXT,
    additional_info TEXT
);
```
Schema is automatically created by Hibernate based on the `DirectoryEntry` entity.

**SQL Logging:**
With `show-sql: true`, all SQL queries are logged to the console:
```
Hibernate: insert into directory_entry (address, additional_info, email, name, phone, type) values (?, ?, ?, ?, ?, ?) returning id
```

### Standard CRUD Operations

All services expose the same REST API structure:

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `{base-path}` | List all entries |
| GET | `{base-path}/{id}` | Get single entry by ID |
| POST | `{base-path}` | Create new entry |
| PUT | `{base-path}/{id}` | Update existing entry |
| DELETE | `{base-path}/{id}` | Delete entry by ID |

Implementation is handled by standard Spring Data JPA repository pattern:
- `DirectoryEntryController` - REST endpoints
- `DirectoryEntryService` - Business logic
- `DirectoryEntryRepository` - JPA repository interface

## File Paths

Key source files in `src/main/java/com/ird0/directory/`:

- `DirectoryApplication.java` - Main Spring Boot entry point
- `controller/DirectoryEntryController.java` - REST API controller
- `model/DirectoryEntry.java` - JPA entity model
- `repository/DirectoryEntryRepository.java` - Spring Data JPA repository
- `service/DirectoryEntryService.java` - Business logic layer

## Dependencies

Key dependencies (managed by parent POM):

- `spring-boot-starter-web` - REST API support
- `spring-boot-starter-data-jpa` - JPA/Hibernate support
- `spring-boot-starter-actuator` - Health and metrics
- `org.postgresql:postgresql` - PostgreSQL JDBC driver (runtime)
- `org.projectlombok:lombok` - Boilerplate reduction

## Docker Configuration

The service is deployed via Docker Compose with three instances:

```yaml
policyholders:
  build:
    context: .
    dockerfile: microservices/directory/Dockerfile
    args:
      APP_YML: policyholders.yml
  ports:
    - "8081:8080"
  volumes:
    - ./data:/app/data
```

The `APP_YML` build argument selects which instance-specific configuration file to embed in the Docker image.

## Troubleshooting

| Issue | Solution |
|-------|----------|
| Port already in use | Stop conflicting service or change port in instance-specific YAML |
| Cannot connect to database | Verify PostgreSQL running: `docker compose ps postgres` |
| Schema not created | Verify `ddl-auto: update` in application.yml |
| Wrong API path | Check `directory.api.base-path` in instance-specific YAML |
| Authentication failed | Check `POSTGRES_USER` and `POSTGRES_PASSWORD` environment variables |
| SQL not logging | Ensure `show-sql: true` in application.yml |
| Database does not exist | Check init script logs: `docker compose logs postgres` |
| Connection pool exhausted | Increase `hikari.maximum-pool-size` or reduce concurrent requests |

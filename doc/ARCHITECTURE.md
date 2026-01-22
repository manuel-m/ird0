# IRD0 System Architecture

## Overview

IRD0 is an insurance platform built on a microservices architecture using Spring Boot 3.5.0 and Java 21. The system manages directory entries for policyholders, experts, and providers, with an integrated SFTP server for automated data imports.

**Core Services:**
- **Directory Service** - Multi-instance REST API for managing directory entries (policyholders, experts, providers, insurers)
- **Incident Service** - Insurance claim incident management with state machine and workflow orchestration
- **Notification Service** - Webhook-based notification dispatch with retry logic and audit trail
- **Portal BFF** - Backend-for-frontend aggregation layer for the Angular dashboard
- **SFTP Server** - Secure file transfer service for exposing CSV files via SFTP protocol
- **Data Generator Utility** - CLI tool for generating realistic test data
- **Angular Frontend** - Claims dashboard and management UI

> **For product requirements and feature specifications, see [PRD.md](PRD.md)**

**Key Architectural Characteristics:**
- Multi-instance microservice pattern (single codebase, multiple deployments)
- PostgreSQL multi-database architecture (six databases, single container)
- Microservices choreography with REST-based integration
- Circuit breaker pattern for resilient inter-service communication
- SFTP polling with intelligent change detection
- Docker multi-stage builds with dependency caching
- DTO layer with MapStruct for clean API contracts
- UUID-based primary keys for global uniqueness
- Event-driven notifications with webhook dispatch

## Multi-Instance Microservice Pattern

### Design Overview

The Directory Service uses a unique architectural pattern where a single microservice codebase is deployed three times with different runtime configurations. This approach balances code reuse with service isolation.

**Service Instances:**

| Instance      | Port | API Base Path        | Database | Purpose |
|---------------|------|----------------------|----------|---------|
| Policyholders | 8081 | `/api/policyholders` | `policyholders_db` | Manage policyholder directory |
| Experts       | 8082 | `/api/experts`       | `experts_db` | Manage expert directory |
| Providers     | 8083 | `/api/providers`     | `providers_db` | Manage provider directory |
| Insurers      | 8084 | `/api/insurers`      | `insurers_db` | Manage insurers directory |

### Configuration Strategy

Each instance shares the same codebase but uses different YAML configuration files located in `microservices/directory/configs/`:

**Configuration Layering:**
1. **Common Configuration** (`application.yml`) - Shared settings for all instances
   - JPA/Hibernate settings
   - Actuator configuration
   - Common Spring Boot properties
   - Logging configuration

2. **Instance-Specific Overrides** (`policyholders.yml`, `experts.yml`, `providers.yml`)
   - Server port (8081, 8082, 8083)
   - API base path (`/api/policyholders`, `/api/experts`, `/api/providers`)
   - Database connection URL (policyholders_db, experts_db, providers_db)
   - Instance-specific features (e.g., SFTP import only for policyholders)

**Docker Configuration Selection:**

The project uses modular Docker Compose files (infrastructure, directory, apps) that are combined via the main docker-compose.yml using the `include` directive. Directory services use the `APP_YML` build argument to select which configuration file to inject:

```yaml
# From docker-compose.directory.yml
policyholders-svc:
  build:
    args:
      APP_YML: policyholders.yml
  ports:
    - "${POLICYHOLDERS_HOST_PORT}:${SERVICE_INTERNAL_PORT}"  # 8081:8080
  networks:
    insurance-network:
      aliases:
        - ${POLICYHOLDERS_SERVICE_HOST}  # Creates 'policyholders' DNS alias

experts-svc:
  build:
    args:
      APP_YML: experts.yml
  ports:
    - "${EXPERTS_HOST_PORT}:${SERVICE_INTERNAL_PORT}"  # 8082:8080
```

**Compose File Organization:**
- `docker-compose.infrastructure.yml` - Core services (postgres, vault, networks, volumes)
- `docker-compose.directory.yml` - Directory microservices (4 instances)
- `docker-compose.apps.yml` - Application services (incident, notification, sftp)
- `docker-compose.yml` - Main orchestrator using `include` directive

The Dockerfile copies the specified configuration file:

```dockerfile
ARG APP_YML
COPY configs/${APP_YML} /app/config/application.yml
```

### Design Rationale

**Why Multi-Instance Pattern?**

**Advantages:**
- **Code Reuse**: Single codebase reduces maintenance overhead
- **Consistent Behavior**: All services implement the same API contract
- **Simplified Testing**: Test once, deploy multiple times
- **Easy Evolution**: Changes apply to all instances automatically
- **Resource Efficiency**: Shared Docker dependency layers
- **Development Speed**: Add new instance types by creating a new YAML file

**Trade-offs:**
- **Coupled Evolution**: Changes affect all instances (mitigated by configuration overrides)
- **Deployment Complexity**: Must coordinate deployments across instances
- **Limited Specialization**: Instance-specific logic requires configuration flags
- **Database Isolation**: Separate databases prevent cross-instance queries (by design)

**When to Use This Pattern:**
- Services share the same domain model (DirectoryEntry)
- API contracts are identical across instances
- Business logic is consistent across entity types
- Differences are primarily data segregation and configuration

### Alternative Architectures Considered

**1. Monolithic Service with Type Discriminator**
- Single service, single database, `type` field distinguishes policyholders/experts/providers
- **Rejected**: Doesn't provide data isolation, scaling constraints

**2. Separate Microservices per Entity Type**
- Three completely independent codebases
- **Rejected**: High maintenance overhead, code duplication, slower development

**3. Shared Library with Three Services**
- Common library containing business logic, three thin services
- **Considered but deferred**: More complex build/versioning, over-engineering for current needs

## PostgreSQL Multi-Database Architecture

### Database Structure

The system uses a single PostgreSQL 16 container with six isolated databases, supporting all microservices.

**PostgreSQL Container:**
- Image: `postgres:16-alpine`
- Port: `5432`
- Volume: `postgres-data` (named volume for persistence)
- Initialization: Custom script creates six databases on first startup

**Databases:**

| Database | Service | Purpose |
|----------|---------|---------|
| `policyholders_db` | Policyholders Service | Policyholder directory entries |
| `experts_db` | Experts Service | Expert directory entries |
| `providers_db` | Providers Service | Provider directory entries |
| `insurers_db` | Insurers Service | Insurer directory entries |
| `incidents_db` | Incident Service | Insurance claim incidents |
| `notifications_db` | Notification Service | Webhook notifications |

**Shared Credentials:**
- Username: `directory_user`
- Password: `directory_pass` (should use secrets in production)

### Database Initialization

The init script (`scripts/init-multiple-databases.sh`) runs on container first startup:

```bash
#!/bin/bash
set -e

# Split comma-separated database names
IFS=',' read -ra DATABASES <<< "$POSTGRES_MULTIPLE_DATABASES"

# Create each database
for db in "${DATABASES[@]}"; do
  echo "Creating database: $db"
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<-EOSQL
    CREATE DATABASE $db;
    GRANT ALL PRIVILEGES ON DATABASE $db TO $POSTGRES_USER;
EOSQL
done
```

Triggered by environment variable in docker-compose.infrastructure.yml:
```yaml
POSTGRES_MULTIPLE_DATABASES: policyholders_db,experts_db,providers_db,insurers_db,incidents_db,notifications_db
```

### Schema Management

**Hibernate DDL Auto-Update:**
- Strategy: `spring.jpa.hibernate.ddl-auto=update`
- Hibernate automatically creates and updates schema on startup
- Each service manages its own database schema independently

**DirectoryEntry Table:**
```sql
CREATE TABLE directory_entry (
    id UUID PRIMARY KEY,
    name VARCHAR(255),
    type VARCHAR(50),
    email VARCHAR(255) UNIQUE NOT NULL,
    phone VARCHAR(50),
    address TEXT,
    additional_info TEXT
);
```

**UUID Primary Keys:**
- Type: `UUID` (128-bit, PostgreSQL native type)
- Generation: Application-generated via `@PrePersist` lifecycle hook
- Format: Standard UUID format (e.g., `c9088e6f-86a4-4001-9a6a-554510787dd9`)
- Benefits: Global uniqueness, no coordination needed, merge-friendly

### Connection Pooling

**HikariCP Configuration:**
- Default pool size: 10 connections per service instance
- Connection timeout: 30 seconds
- Maximum lifetime: 30 minutes
- Leak detection threshold: 60 seconds (development)

### Data Persistence

**Docker Volume Strategy:**
- Named volume: `postgres-data`
- Persists across container restarts and rebuilds
- Survives `docker compose down` (destroyed only with `-v` flag)
- Location: Docker-managed storage

**Backup Considerations:**
- Volume can be backed up with `docker cp` or volume plugins
- pg_dump recommended for logical backups
- Point-in-time recovery requires WAL archiving (not configured)

### Design Rationale

**Why Multi-Database instead of Single Database?**

**Advantages:**
- **Data Isolation**: Complete separation prevents cross-contamination
- **Security Boundary**: Credential compromise limits blast radius
- **Independent Scaling**: Database-level partitioning enables future sharding
- **Backup Granularity**: Can backup/restore individual databases
- **Testing Isolation**: Integration tests can target specific databases
- **Regulatory Compliance**: Different data classifications can have different policies

**Trade-offs:**
- **No Cross-Instance Queries**: Cannot join policyholders with providers in SQL
- **Transaction Boundaries**: Distributed transactions require application-level coordination
- **Resource Overhead**: Multiple databases consume more memory than one (minimal with modern PostgreSQL)

**Why Single Container instead of Multiple PostgreSQL Instances?**

**Advantages:**
- **Resource Efficiency**: Single PostgreSQL process uses less memory
- **Operational Simplicity**: One container to manage, monitor, backup
- **Network Simplicity**: Single connection endpoint
- **Cost**: Lower cloud infrastructure costs (single RDS instance vs. multiple instances)

**Production Considerations:**
- For high-scale production, consider separate PostgreSQL instances per service
- Enables independent scaling and resource allocation
- Current architecture supports small to medium scale (up to ~100K entries per database)

## SFTP Integration Architecture

### Overview

The Policyholders Service includes an automated SFTP import system that polls the SFTP server for CSV files, detects changes, and imports data with intelligent deduplication.

**Integration Flow:**
```
SFTP Server (port 2222)
    ↓ (polls every 2 minutes)
Policyholders Service
    ↓ (downloads CSV)
Local Temp Storage
    ↓ (file-level change detection)
Metadata Store
    ↓ (CSV parsing + row-level change detection)
PostgreSQL (policyholders_db)
```

### Spring Integration Polling

**Polling Configuration:**
- Polling interval: 120,000ms (2 minutes)
- Initial delay: 5,000ms (5 seconds after startup)
- Remote directory: `/` (SFTP root)
- File pattern: `*.csv`

**Components:**
- **SFTP Session Factory**: Manages SFTP connections with SSH key authentication
- **Inbound Channel Adapter**: Polls SFTP server at fixed intervals
- **Message Handler**: Processes downloaded files
- **Metadata Store**: Tracks file timestamps for change detection

### Two-Level Change Detection

**1. File-Level Detection (Timestamp-Based):**
- Compares file `lastModified` timestamp with stored metadata
- If timestamp unchanged: Skip file processing
- If timestamp changed or file new: Proceed to import
- Uses Spring Integration `SimpleMetadataStore` (in-memory)
- Limitation: Metadata lost on service restart (all files reprocessed)

**2. Row-Level Detection (Field Comparison):**
- For each CSV row, lookup entry by email (unique key)
- If not exists: **INSERT** (counted as "new")
- If exists:
  - Compare all fields (name, type, phone, address, additionalInfo)
  - If any field changed: **UPDATE** (counted as "updated")
  - If all fields identical: **SKIP** (counted as "unchanged")

### CSV Import Process

**Import Pipeline:**

1. **Download**: SFTP session downloads file to local temp directory (`./temp/sftp-downloads`)
2. **Validation**: Check file format, required columns
3. **Parsing**: Apache Commons CSV parses file
4. **Batch Processing**: Process rows in batches (default: 500 rows per batch)
5. **Per-Row Logic**:
   - Validate required fields (name, type, email, phone)
   - Check email exists in database
   - Determine operation: INSERT, UPDATE, or SKIP
   - Execute operation in transaction
6. **Results Tracking**: Count totalRows, newRows, updatedRows, unchangedRows, failedRows
7. **Metadata Update**: Store file timestamp for next poll

**Transaction Management:**
- Each batch is a separate transaction
- Partial failure: Failed rows logged, successful rows committed
- No rollback of entire import on single row failure

### SFTP Server Architecture

**Read-Only File System:**
- Custom `CsvVirtualFileSystemView` wraps native file system
- Blocks write operations (PUT, DELETE, MKDIR, RMDIR)
- Allows read operations (GET, LS, CD)
- Exposes `./data` directory to SFTP clients

**Authentication:**
- SSH public key authentication only (no password auth)
- `PublicKeyAuthenticator` validates keys against `authorized_keys` file
- Key format: Three-field format (key-type, key-data, username)
- Supported: RSA 2048-bit keys (Ed25519 not supported in Apache SSHD 2.12.0)

**Host Key:**
- Generated automatically on first startup via `SimpleGeneratorHostKeyProvider`
- Persisted in `./keys/hostkey.pem`
- RSA 2048-bit default
- Persistent across container restarts (stored in bind mount)

### Design Rationale

**Why SFTP instead of REST API?**

**Advantages:**
- **Standard Protocol**: External systems often have SFTP clients built-in
- **File-Based Integration**: Batch processing model suits bulk data updates
- **Security**: SSH public key authentication is well-understood
- **Audit Trail**: File timestamps provide natural versioning
- **Decoupling**: Producer and consumer don't need to be online simultaneously

**Trade-offs:**
- **Polling Overhead**: Regular polling even when no changes
- **Latency**: Up to 2-minute delay for new data
- **Complexity**: Requires SSH key management

**Why Two-Level Change Detection?**

**File-Level (Timestamp) Benefits:**
- Avoid downloading unchanged files
- Reduce network bandwidth
- Skip parsing overhead

**Row-Level (Field Comparison) Benefits:**
- Avoid unnecessary database writes
- Preserve audit timestamps for truly unchanged rows
- Reduce database I/O
- Import results accurately reflect actual changes

**Production Enhancements (Future):**
- Persistent metadata store (database or Redis) instead of in-memory
- Retry logic for failed imports (currently missing)
- Import history tracking (audit table)
- Manual import trigger REST endpoint (already implemented)

## Incident Service Architecture

### Overview

The Incident Service manages insurance claim incidents through their complete lifecycle. It orchestrates workflows across directory services for validation and notification services for alerting, providing a central hub for incident management.

**Service Details:**
- Port: 8085 (host), 8080 (internal)
- Database: `incidents_db` (PostgreSQL)
- API Base Path: `/api/v1/incidents`
- Integration: Directory Services (policyholders, insurers, experts), Notification Service

> **For detailed implementation and API usage, see [microservices/incident/CLAUDE.md](../microservices/incident/CLAUDE.md)**

### State Machine Architecture

The Incident Service implements a finite state machine for incident lifecycle management with enforced transition rules.

**Incident Lifecycle:**

```
┌─────────┐
│ DECLARED│ ──────────────────────────┐
└────┬────┘                           │
     │                                │
     │ Qualify                         │ Abandon
     ▼                                │
┌─────────┐                           │
│QUALIFIED│ ──────────────┐           │
└────┬────┘               │           │
     │                    │           │
     │ Assign Expert       │ Abandon   │
     ▼                    │           │
┌────────────┐            │           │
│IN_PROGRESS │ ───────────┤           │
└─────┬──────┘            │           │
      │                   │           │
      │ Resolve            │           │
      ▼                   ▼           ▼
┌─────────┐         ┌─────────────────┐
│RESOLVED │         │   ABANDONED     │
└─────────┘         └─────────────────┘
(terminal)              (terminal)
```

**Valid State Transitions:**

| From State | To States | Trigger |
|------------|-----------|---------|
| DECLARED | QUALIFIED, ABANDONED | Qualification review, Early abandonment |
| QUALIFIED | IN_PROGRESS, ABANDONED | Expert assignment, Qualification rejected |
| IN_PROGRESS | RESOLVED, ABANDONED | Completion, Process failure |
| RESOLVED | (none) | Terminal state |
| ABANDONED | (none) | Terminal state |

**Transition Enforcement:**
- Implemented via `IncidentStatus` enum with `isValidTransition()` method
- Invalid transitions throw `InvalidStateTransitionException` (HTTP 400)
- All state changes logged in `IncidentEvent` for audit trail

### Microservices Integration

**Directory Service Validation (Circuit Breaker Pattern):**

The Incident Service validates entity references against Directory Services before creating or updating incidents:

```
Incident Service                 Directory Service              PostgreSQL
      |                                  |                          |
      | 1. Create Incident               |                          |
      | {policyholderId, insurerId}      |                          |
      |                                  |                          |
      | 2. Validate Policyholder         |                          |
      | GET /api/policyholders/{id}      |                          |
      |--------------------------------->|                          |
      |                                  |                          |
      |                                  | 3. Query database        |
      |                                  |------------------------->|
      |                                  |                          |
      |                                  | 4. Return entity         |
      |                                  |<-------------------------|
      |                                  |                          |
      | 5. 200 OK {policyholder}         |                          |
      |<---------------------------------|                          |
      |                                  |                          |
      | 6. Validate Insurer              |                          |
      | (similar flow)                   |                          |
      |                                  |                          |
      | 7. Save incident                 |                          |
      |---------------------------------------------------------------->|
```

**Validated Entities:**
- **Policyholder**: Must exist in policyholders service before incident creation
- **Insurer**: Must exist in insurers service before incident creation
- **Expert**: Must exist in experts service before assignment
- **Provider**: Optional validation when provider assigned

**Circuit Breaker Configuration (Resilience4j):**
- Sliding window size: 10 calls
- Minimum calls for stats: 5
- Failure rate threshold: 50%
- Open state duration: 30 seconds
- On circuit open: Throws `DirectoryValidationException` with "service unavailable" message

**Notification Integration (Event Publishing):**

The Incident Service publishes notifications to the Notification Service for key lifecycle events:

```
Incident Service                Notification Service           External Webhook
      |                                  |                          |
      | 1. Status update (DECLARED)      |                          |
      |                                  |                          |
      | 2. POST /notifications           |                          |
      | {eventType: "INCIDENT_DECLARED"  |                          |
      |  webhookUrl, payload}            |                          |
      |--------------------------------->|                          |
      |                                  |                          |
      | 3. 201 Created {id, status}      |                          |
      |<---------------------------------|                          |
      |                                  |                          |
      |                                  | 4. Dispatch webhook      |
      |                                  | POST {payload}           |
      |                                  |------------------------->|
      |                                  |                          |
      |                                  |  5. 200 OK               |
      |                                  |<-------------------------|
```

**Published Events:**
- `INCIDENT_DECLARED` - New incident created
- `INCIDENT_QUALIFIED` - Incident qualified for processing
- `INCIDENT_ABANDONED` - Incident abandoned
- `EXPERT_ASSIGNED` - Expert assigned to incident
- `STATUS_CHANGED` - Any status transition

### Data Model

**Incident Entity (PostgreSQL JSONB):**

```sql
CREATE TABLE incidents (
    id UUID PRIMARY KEY,
    reference_number VARCHAR(255) UNIQUE NOT NULL,  -- INC-YYYY-NNNNNN
    policyholder_id UUID NOT NULL,
    insurer_id UUID NOT NULL,
    status VARCHAR(50) NOT NULL,
    type VARCHAR(100) NOT NULL,
    description TEXT,
    incident_date TIMESTAMP NOT NULL,
    estimated_damage NUMERIC(15,2),
    currency VARCHAR(3) DEFAULT 'EUR',
    location JSONB,  -- {address, latitude, longitude}
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE expert_assignments (
    id UUID PRIMARY KEY,
    incident_id UUID REFERENCES incidents(id),
    expert_id UUID NOT NULL,
    scheduled_date TIMESTAMP,
    completed_date TIMESTAMP,
    notes TEXT,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE comments (
    id UUID PRIMARY KEY,
    incident_id UUID REFERENCES incidents(id),
    content TEXT NOT NULL,
    author_id UUID NOT NULL,
    author_type VARCHAR(50),  -- POLICYHOLDER, EXPERT, INSURER, ADMIN
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE incident_events (
    id UUID PRIMARY KEY,
    incident_id UUID REFERENCES incidents(id),
    event_type VARCHAR(100) NOT NULL,  -- STATUS_CHANGED, EXPERT_ASSIGNED, etc.
    previous_value VARCHAR(255),
    new_value VARCHAR(255),
    description TEXT,
    created_at TIMESTAMP NOT NULL
);
```

**Reference Number Generation:**
- Format: `INC-YYYY-NNNNNN` (e.g., `INC-2026-000001`)
- Year: Current year (4 digits)
- Sequence: Zero-padded 6-digit number, auto-incremented per year
- Uniqueness: Enforced by database unique constraint
- Implementation: `ReferenceNumberGenerator` service with database sequence

### Design Rationale

**Why State Machine over Free-Form Status?**

**Advantages:**
- **Data Integrity**: Invalid transitions prevented at application level
- **Business Rules**: Lifecycle rules enforced by code, not training
- **Audit Trail**: Every transition logged with context
- **Predictability**: Clear understanding of allowed operations
- **Testing**: Finite states enable comprehensive test coverage

**Trade-offs:**
- **Flexibility**: Adding new states requires code changes
- **Complexity**: More complex than simple status field
- **Learning Curve**: Developers must understand state machine semantics

**Why Circuit Breaker for Directory Validation?**

**Advantages:**
- **Resilience**: Incident service remains responsive when directory services fail
- **Fast Failure**: Fails quickly instead of waiting for timeout
- **Service Protection**: Prevents cascade failures across services
- **Automatic Recovery**: Circuit reopens after cooldown period

**Trade-offs:**
- **Eventual Consistency**: Circuit open means validation skipped (in this implementation, fails instead)
- **Complexity**: Additional configuration and monitoring required

**Why Synchronous Notifications over Event Bus?**

**Advantages:**
- **Simplicity**: No message broker infrastructure required
- **Immediate Feedback**: Know if notification accepted or rejected
- **Debugging**: Easier to trace request flow
- **Cost**: No Kafka/RabbitMQ infrastructure costs

**Trade-offs:**
- **Coupling**: Incident service depends on Notification service availability
- **Scalability**: Synchronous calls add latency to incident operations
- **Reliability**: If notification fails, incident operation succeeds but notification lost (in current implementation)

## Notification Service Architecture

### Overview

The Notification Service provides webhook-based notification dispatch with retry logic and comprehensive audit trails. It decouples notification delivery from business logic, allowing services to fire-and-forget while ensuring reliable webhook delivery.

**Service Details:**
- Port: 8086 (host), 8080 (internal)
- Database: `notifications_db` (PostgreSQL)
- API Base Path: `/api/v1/notifications`
- Pattern: Asynchronous webhook dispatch with exponential backoff

> **For detailed implementation and API usage, see [microservices/notification/CLAUDE.md](../microservices/notification/CLAUDE.md)**

### Webhook Dispatch Architecture

**Notification Lifecycle:**

```
┌─────────┐   Immediate     ┌──────┐   HTTP 200   ┌──────┐
│ PENDING ├───────────────>│ SENT │──────────────>│(Done)│
└────┬────┘   dispatch      └──────┘              └──────┘
     │
     │ HTTP 4xx/5xx
     │ Network timeout
     ▼
┌─────────┐   Retry 1       ┌──────┐
│ PENDING ├───(1s delay)───>│ SENT │
└────┬────┘                 └──────┘
     │
     │ Failure
     ▼
┌─────────┐   Retry 2       ┌──────┐
│ PENDING ├───(2s delay)───>│ SENT │
└────┬────┘                 └──────┘
     │
     │ Failure
     ▼
┌─────────┐   Retry 3       ┌──────┐
│ PENDING ├───(4s delay)───>│ SENT │
└────┬────┘                 └──────┘
     │
     │ All retries exhausted
     ▼
┌────────┐
│ FAILED │
└────────┘
```

**Exponential Backoff Strategy:**

| Attempt | Delay | Cumulative Time |
|---------|-------|-----------------|
| 1 (initial) | 0s | 0s |
| 2 (retry 1) | 1s | 1s |
| 3 (retry 2) | 2s | 3s |
| 4 (retry 3) | 4s | 7s |
| 5 (retry 4)* | 8s | 15s |

*If `max-retries` configured > 3

**Configuration:**
- `webhook.initial-retry-delay`: Initial delay (default: 1000ms)
- `webhook.retry-multiplier`: Multiplier for each retry (default: 2.0)
- `webhook.max-retry-delay`: Cap on delay (default: 60000ms)
- `webhook.max-retries`: Maximum attempts (default: 3)

### HTTP Client Configuration

**RestTemplate Configuration:**

```java
@Bean
public RestTemplate restTemplate(NotificationProperties props) {
    HttpComponentsClientHttpRequestFactory factory =
        new HttpComponentsClientHttpRequestFactory();
    factory.setConnectTimeout(props.getWebhook().getConnectTimeout());
    factory.setReadTimeout(props.getWebhook().getReadTimeout());
    return new RestTemplate(factory);
}
```

**Timeouts:**
- **Connect timeout**: 5000ms (time to establish TCP connection)
- **Read timeout**: 10000ms (time to receive response after sending request)
- **Total request timeout**: 15000ms (connect + read)

**Failure Scenarios:**
- **HTTP 4xx/5xx**: Stored as failure, triggers retry
- **Network timeout**: Counted as failure, triggers retry
- **Connection refused**: Counted as failure, triggers retry
- **SSL errors**: Counted as failure, triggers retry

### Data Model

**Notification Entity:**

```sql
CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    webhook_url VARCHAR(2048) NOT NULL,
    payload JSONB NOT NULL,
    event_type VARCHAR(100),
    event_id UUID,
    incident_id UUID,
    status VARCHAR(50) NOT NULL,  -- PENDING, SENT, FAILED, CANCELLED
    retry_count INT DEFAULT 0,
    max_retries INT DEFAULT 3,
    next_retry_at TIMESTAMP,
    last_attempt_at TIMESTAMP,
    response_status INT,
    response_body TEXT,
    failure_reason TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_notifications_status ON notifications(status);
CREATE INDEX idx_notifications_incident_id ON notifications(incident_id);
CREATE INDEX idx_notifications_next_retry ON notifications(next_retry_at) WHERE status = 'PENDING';
```

**Payload Format (JSONB):**

```json
{
  "eventType": "INCIDENT_DECLARED",
  "eventId": "550e8400-e29b-41d4-a716-446655440000",
  "timestamp": "2026-01-18T10:30:00Z",
  "incident": {
    "id": "550e8400-e29b-41d4-a716-446655440001",
    "referenceNumber": "INC-2026-000001",
    "status": "DECLARED",
    "type": "VEHICLE_ACCIDENT",
    "policyholderId": "550e8400-e29b-41d4-a716-446655440002",
    "insurerId": "550e8400-e29b-41d4-a716-446655440003",
    "estimatedDamage": 5000.00,
    "currency": "EUR"
  }
}
```

### Manual Retry Workflow

**Retry Failed Notifications:**

When a notification reaches FAILED status (all retries exhausted), it can be manually retried:

```bash
# Get failed notifications
curl http://localhost:8086/api/v1/notifications/status/FAILED

# Retry specific notification
curl -X POST http://localhost:8086/api/v1/notifications/{id}/retry
```

**Retry Behavior:**
- Resets `retry_count` to 0
- Sets `status` back to PENDING
- Schedules immediate dispatch
- Clears previous `failure_reason`
- Follows same exponential backoff on subsequent failures

### Design Rationale

**Why Separate Notification Service?**

**Advantages:**
- **Separation of Concerns**: Business logic services don't handle webhook complexity
- **Reliability**: Retry logic centralized, not duplicated across services
- **Audit Trail**: All notifications stored in single database
- **Monitoring**: Single service to monitor webhook health
- **Scalability**: Can scale notification dispatch independently

**Trade-offs:**
- **Additional Service**: More infrastructure to manage
- **Network Hop**: Extra latency for notification dispatch
- **Eventual Consistency**: Notifications not guaranteed to send in exact order

**Why Synchronous REST over Message Queue?**

**Advantages:**
- **Simplicity**: No Kafka/RabbitMQ infrastructure
- **Immediate Feedback**: Caller knows notification was accepted
- **Deployment**: Fewer moving parts
- **Cost**: Lower infrastructure costs

**Trade-offs:**
- **Scalability**: Limited to REST throughput
- **Back pressure**: Slow webhook endpoints can slow calling service
- **Reliability**: Lost if notification service down (mitigated by immediate retry on caller side)

**Why In-Process Retry over Scheduled Job?**

**Current Implementation**: Retry logic executed in same API call as creation

**Advantages:**
- **Simplicity**: No scheduler infrastructure (cron, Quartz)
- **Immediate**: Retries happen quickly (seconds, not minutes)
- **Low Latency**: Most notifications succeed on first attempt

**Trade-offs:**
- **Blocking**: Caller waits for all retries (max ~15s with default config)
- **Resource Usage**: Thread held during retry delays

**Future Enhancement**: Background job for retry execution
- Spring `@Scheduled` for polling PENDING notifications
- Allows caller to return immediately
- Requires job orchestration (single instance execution)

## Portal BFF Architecture

### Overview

The Portal BFF (Backend-for-Frontend) provides an aggregation layer that combines data from multiple backend services, optimized for the Angular frontend. It resolves actor IDs to names and provides dashboard KPIs.

**Service Details:**
- Port: 8090 (host), 8080 (internal)
- API Base Path: `/api/portal/v1`
- Pattern: Aggregation, name resolution, circuit breaker protection

> **For detailed feature documentation, see [features/portal-dashboard.md](features/portal-dashboard.md) and [features/claims-management-ui.md](features/claims-management-ui.md)**

### Aggregation Pattern

```
Angular Frontend
       │
       │ GET /api/portal/v1/claims/{id}
       ▼
┌──────────────────┐
│   Portal BFF     │
│     :8090        │
└────────┬─────────┘
         │
    ┌────┴────┬────────────┬────────────┐
    │         │            │            │
    ▼         ▼            ▼            ▼
┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐
│Incident│ │Policy- │ │Insurers│ │Experts │
│Service │ │holders │ │ :8084  │ │ :8082  │
│ :8085  │ │ :8081  │ │        │ │        │
└────────┘ └────────┘ └────────┘ └────────┘
```

### Key Capabilities

**Dashboard Aggregation:**
- Total claims, pending, in-progress, closed this month
- Status distribution (count per status)
- Claims by type (count per incident type)
- Recent activity (last 10 events)

**Name Resolution:**
- Converts policyholderId → policyholderName
- Converts insurerId → insurerName
- Converts expertId → expertName
- Fallback: Returns "Unknown" if directory service unavailable

**Circuit Breaker Protection:**
- Resilience4j for all inter-service calls
- Sliding window: 10 calls
- Failure threshold: 50%
- Open state: 30 seconds

### Design Rationale

**Why BFF Pattern?**

**Advantages:**
- **Reduced Round Trips**: Frontend makes one call instead of multiple
- **Optimized Payload**: Response shaped for frontend needs
- **Backend Flexibility**: Backend services can evolve independently
- **Caching**: BFF can cache directory lookups

**Trade-offs:**
- **Additional Service**: One more service to manage
- **Coupling**: BFF changes needed for new frontend requirements
- **Latency**: Extra hop between frontend and backend services

## Vault SSH Certificate Authority Architecture

### Overview

The platform integrates HashiCorp Vault SSH Secrets Engine in CA (Certificate Authority) mode for dynamic, short-lived SSH certificates. This provides enhanced security over static key management with forward secrecy and centralized credential management.

> **For detailed implementation and operations, see [topics/vault-ssh-ca.md](topics/vault-ssh-ca.md)**

### Certificate-Based Authentication Flow

```
Policyholders Service                    Vault SSH CA                    SFTP Server
        |                                     |                               |
        |  1. Generate ephemeral RSA-4096     |                               |
        |     key pair in memory              |                               |
        |                                     |                               |
        |  2. POST /sign/directory-service    |                               |
        |     {public_key, principal, ttl}    |                               |
        |------------------------------------>|                               |
        |                                     |                               |
        |  3. Return signed certificate       |                               |
        |     {signed_key, serial}            |                               |
        |<------------------------------------|                               |
        |                                     |                               |
        |  4. SSH connection with certificate |                               |
        |------------------------------------------------------------------>|
        |                                     |                               |
        |                                     |  5. Verify certificate        |
        |                                     |     - CA signature            |
        |                                     |     - Validity period         |
        |                                     |     - Principal match         |
        |                                     |<------------------------------|
        |                                     |                               |
        |  6. Session established             |                               |
        |<------------------------------------------------------------------|
```

### Key Components

**Certificate Client (Policyholders Service):**
- `EphemeralKeyPairGenerator`: Generates RSA-4096 key pairs in memory (forward secrecy)
- `VaultSshCertificateSigner`: Requests certificates from Vault SSH CA
- `SshCertificateManager`: Manages certificate lifecycle (caching, renewal)
- `MinaSftpClient`: Apache MINA SSHD client with certificate authentication

**Certificate Server (SFTP Server):**
- `CertificateAuthenticator`: Five-step certificate verification
- `VaultCaTrustProvider`: Loads and caches CA public key from Vault
- `CertificateAuditLogger`: Structured audit logging for authentication events

### Security Properties

| Property | Implementation |
|----------|----------------|
| **Forward Secrecy** | Each connection uses new ephemeral RSA-4096 key pair |
| **Short-Lived Credentials** | 15-minute certificate TTL (configurable) |
| **Centralized Trust** | Single CA managed by Vault |
| **Audit Trail** | All auth events logged with certificate serial |
| **Least Privilege** | Services have minimal Vault permissions |
| **Graceful Degradation** | Falls back to static keys when Vault unavailable |

### Certificate Verification (Five Steps)

1. **Certificate Type**: Must be `OpenSshCertificate` (raw keys rejected)
2. **CA Signature**: Certificate signed by trusted Vault CA
3. **Validity Period**: Within `validAfter` and `validBefore` timestamps
4. **Principal Match**: Username matches certificate principal
5. **Certificate Type**: Must be USER certificate (not HOST)

### Vault Configuration

**SSH Secrets Engine Role:**
```
Path: ssh-client-signer/roles/directory-service
- key_type: ca
- algorithm_signer: rsa-sha2-256
- allowed_users: policyholder-importer
- ttl: 15m
- max_ttl: 1h
```

**Service Policies:**
- Directory Service: Can sign certificates, read CA public key
- SFTP Server: Can read CA public key only (cannot sign)

### Design Rationale

**Why Certificate-Based Auth over Static Keys?**

**Advantages:**
- **Limited Exposure**: Compromised certificate expires in 15 minutes
- **Forward Secrecy**: Each session uses different keys
- **Centralized Management**: Single CA for all certificate operations
- **Audit Trail**: Certificate serials enable tracking
- **No Key Distribution**: Services request certificates dynamically

**Trade-offs:**
- **Vault Dependency**: Requires running Vault infrastructure
- **Complexity**: More components than static key auth
- **Network Calls**: Certificate requests add latency (mitigated by caching)

## Docker Multi-Stage Build Strategy

### Build Architecture

All services use a consistent multi-stage Dockerfile pattern optimized for dependency layer caching.

**Two-Stage Build:**

**Stage 1: Build (Maven + JDK 21)**
```dockerfile
FROM maven:3.9-amazoncorretto-21 AS build
WORKDIR /app

# Layer 1: POM files only (rarely changes)
COPY pom.xml .
COPY microservices/directory/pom.xml microservices/directory/

# Layer 2: Download dependencies (reused across builds)
RUN mvn dependency:resolve dependency:resolve-plugins

# Layer 3: Source code (changes frequently)
COPY microservices/directory/src microservices/directory/src

# Layer 4: Compile and package
RUN mvn -f microservices/directory/pom.xml clean package -DskipTests
```

**Stage 2: Runtime (JRE 21 Alpine)**
```dockerfile
FROM amazoncorretto:21-alpine
WORKDIR /app

# Copy executable JAR from build stage
COPY --from=build /app/microservices/directory/target/directory-1.0.0-exec.jar app.jar

# Inject configuration file based on build arg
ARG APP_YML
COPY microservices/directory/configs/${APP_YML} config/application.yml

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Dependency Layer Caching

**Caching Strategy:**

1. **POM Layer**: Copied first, changes rarely (only on dependency updates)
2. **Dependency Resolution Layer**: `mvn dependency:resolve` downloads all dependencies
   - **Critical**: This layer is cached and reused across all service builds
   - Only invalidates when POM files change
   - Saves ~60-70 seconds per build after first build
3. **Source Code Layer**: Changes frequently, but doesn't invalidate dependency cache
4. **Compilation Layer**: Fast because dependencies already downloaded

**Benefits:**
- **First Build**: ~3-4 minutes (downloads all dependencies)
- **Subsequent Builds** (source changes): ~30-40 seconds (compilation only)
- **Cross-Service Efficiency**: All three directory instances share the same dependency layer
- **CI/CD Speed**: Dramatic improvement in build pipeline times

### Configuration Injection

**APP_YML Build Argument:**

Docker Compose (in docker-compose.directory.yml) specifies which configuration file to use:

```yaml
policyholders-svc:
  build:
    dockerfile: microservices/directory/Dockerfile
    args:
      APP_YML: policyholders.yml

experts:
  build:
    dockerfile: microservices/directory/Dockerfile
    args:
      APP_YML: experts.yml
```

Dockerfile copies the specified file into the container:

```dockerfile
ARG APP_YML
COPY microservices/directory/configs/${APP_YML} config/application.yml
```

At runtime, Spring Boot reads `config/application.yml` which contains the instance-specific configuration.

### Volume Management

**Named Volumes (Data Persistence):**
- `postgres-data`: PostgreSQL database files
- Managed by Docker, persists across container lifecycle
- Survives `docker compose down` (destroyed only with `docker compose down -v`)

**Bind Mounts (Configuration & Keys):**
- `./keys`: SSH keys for SFTP (read-write)
- `./data`: CSV files exposed via SFTP (read-only mount)
- `./scripts`: Database initialization scripts (read-only)

### Design Rationale

**Why Multi-Stage Builds?**

**Advantages:**
- **Small Runtime Image**: Final image only contains JRE + JAR (~200MB vs ~600MB with Maven)
- **Security**: No build tools in production image
- **Fast Deployments**: Smaller images transfer faster
- **Clear Separation**: Build environment vs runtime environment

**Why Dependency Layer Caching?**

**Advantages:**
- **Development Speed**: Incremental builds are fast
- **CI/CD Efficiency**: Pull requests build quickly
- **Cost Savings**: Shorter CI/CD pipeline execution time
- **Developer Experience**: Fast local builds encourage iteration

## DTO and MapStruct Architecture

### Overview

The Directory Service uses a Data Transfer Object (DTO) layer to separate API contracts from internal entity models, with MapStruct providing automatic mapping between layers.

**Architecture Layers:**

```
REST Controller (DirectoryEntryController)
    ↓ (receives DirectoryEntryDTO)
MapStruct Mapper (DirectoryEntryMapper)
    ↓ (converts to/from DirectoryEntry entity)
Service Layer (DirectoryEntryService)
    ↓ (processes DirectoryEntry)
Repository (DirectoryEntryRepository)
    ↓ (persists DirectoryEntry)
PostgreSQL Database
```

### DTO Design

**DirectoryEntryDTO:**
```java
public class DirectoryEntryDTO {
    private UUID id;

    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Type is required")
    private String type;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Phone is required")
    private String phone;

    private String address;
    private String additionalInfo;
}
```

**Bean Validation:**
- `@NotBlank`: Required fields (name, type, email, phone)
- `@Email`: Email format validation
- Validation triggered by `@Valid` annotation in controller
- Returns HTTP 400 with validation errors on failure

### MapStruct Mapping

**Mapper Interface:**
```java
@Mapper(componentModel = "spring")
public interface DirectoryEntryMapper {
    DirectoryEntryDTO toDto(DirectoryEntry entity);
    DirectoryEntry toEntity(DirectoryEntryDTO dto);
    List<DirectoryEntryDTO> toDtoList(List<DirectoryEntry> entities);
}
```

**Code Generation:**
- MapStruct annotation processor generates implementation at compile time
- No reflection, no runtime overhead
- Type-safe mapping with compile-time validation

**Configuration:**
```xml
<dependency>
    <groupId>org.mapstruct</groupId>
    <artifactId>mapstruct</artifactId>
    <version>1.5.5.Final</version>
</dependency>
<dependency>
    <groupId>org.mapstruct</groupId>
    <artifactId>mapstruct-processor</artifactId>
    <version>1.5.5.Final</version>
    <scope>provided</scope>
</dependency>
```

### Design Rationale

**Why DTOs instead of exposing entities directly?**

**Advantages:**
- **API Stability**: Internal entity changes don't break API contracts
- **Validation Control**: Different validation rules for API vs persistence
- **Security**: Hide internal details (e.g., audit fields, relationships)
- **Versioning**: Multiple DTO versions for API versioning
- **Clean Contracts**: API models optimized for client needs

**Why MapStruct instead of manual mapping?**

**Advantages:**
- **Performance**: Generated code, no reflection
- **Type Safety**: Compile-time checking catches mapping errors
- **Maintainability**: Automatic updates when fields added
- **Consistency**: Same mapping logic everywhere
- **Reduced Boilerplate**: No manual getters/setters

**Trade-offs:**
- **Build Complexity**: Annotation processing in Maven
- **Learning Curve**: Developers must learn MapStruct annotations
- **Debugging**: Generated code can be harder to debug (though usually straightforward)

## Technology Stack Rationale

### Core Technologies

| Technology | Version | Rationale |
|------------|---------|-----------|
| **Java** | 21 | LTS release, virtual threads (future), pattern matching, records |
| **Spring Boot** | 3.5.0 | Latest stable, Jakarta EE 10, native compilation support |
| **PostgreSQL** | 16 | Latest stable, UUID native type, JSON support, reliability |
| **Maven** | 3.9+ | Standard build tool, plugin ecosystem, multi-module support |
| **Docker** | Latest | Containerization standard, multi-stage builds, compose orchestration |

### Spring Boot Libraries

| Library | Purpose | Rationale |
|---------|---------|-----------|
| **Spring Data JPA** | Database access | Reduces boilerplate, query derivation, transaction management |
| **Hibernate** | ORM implementation | Mature, feature-complete, @PrePersist for UUID generation |
| **Spring Boot Actuator** | Monitoring | Production-ready endpoints (health, metrics, info) |
| **Spring Integration** | SFTP polling | Enterprise integration patterns, SFTP adapter built-in |
| **Resilience4j** | Circuit breaker | Fault tolerance for inter-service calls, Spring Boot integration |
| **Hypersistence Utils** | JSONB support | First-class JSONB mapping for Hibernate 6.x, type-safe |
| **MapStruct** | DTO mapping | Type-safe, performant, compile-time generation |
| **Lombok** | Boilerplate reduction | @Data, @Builder reduce verbosity |

### SFTP Implementation

| Technology | Version | Rationale |
|------------|---------|-----------|
| **Apache MINA SSHD** | 2.12.0 | Pure Java SSH/SFTP server, embedded, customizable |
| **Alternative Considered** | Apache Commons VFS | Rejected: Client library, not server |
| **Alternative Considered** | JSch | Rejected: Unmaintained since 2018 |

### Build & Development

| Tool | Purpose | Rationale |
|------|---------|-----------|
| **Spotless** | Code formatting | Google Java Format, automated, enforced consistency |
| **HikariCP** | Connection pooling | Default in Spring Boot, fastest Java connection pool |
| **SLF4J + Logback** | Logging | Spring Boot default, structured logging, performance |

### Design Decisions

**Why Spring Boot over Micronaut or Quarkus?**
- **Maturity**: Largest ecosystem, most third-party integrations
- **Team Familiarity**: Most Java developers know Spring
- **Spring Integration**: No equivalent in Micronaut/Quarkus for SFTP
- **Actuator**: Production-ready monitoring out of the box
- **Trade-off**: Slower startup time (not critical for long-running services)

**Why PostgreSQL over MySQL?**
- **UUID Native Type**: Better performance for UUID primary keys
- **JSON Support**: Future flexibility for semi-structured data
- **Standards Compliance**: Better SQL standard conformance
- **Advanced Features**: Window functions, CTEs, full-text search

**Why Apache MINA SSHD over alternatives?**
- **Pure Java**: No native dependencies, cross-platform
- **Embeddable**: Runs inside Spring Boot application
- **Customizable**: Full control over authentication, file system
- **Active Maintenance**: Regular releases, security updates

## Design Principles and Trade-offs

### Design Principles

**1. Configuration over Code:**
- Multi-instance pattern uses configuration to differentiate services
- Enables runtime flexibility without recompilation

**2. Data Isolation:**
- Separate databases per service instance
- Prevents cross-contamination, enables independent scaling

**3. API-First Design:**
- DTO layer provides stable API contracts
- Internal implementation can evolve independently

**4. Intelligent Change Detection:**
- Two-level detection (file + row) minimizes unnecessary work
- Import results accurately reflect actual changes

**5. Operational Excellence:**
- Spring Boot Actuator provides observability
- Health checks enable automated monitoring
- Structured logging facilitates troubleshooting

**6. Security by Design:**
- SSH public key authentication only (no passwords)
- Read-only SFTP file system prevents tampering
- Database credentials externalized (environment variables)

### Trade-offs

**Multi-Instance Pattern:**
- ✅ Code reuse, consistent behavior
- ❌ Coupled evolution, limited specialization

**Single PostgreSQL Container:**
- ✅ Resource efficiency, operational simplicity
- ❌ Single point of failure, scaling limits

**In-Memory Metadata Store:**
- ✅ Simple implementation, no external dependencies
- ❌ Metadata lost on restart, reprocesses all files

**SFTP Polling:**
- ✅ Standard protocol, decoupled integration
- ❌ Polling overhead, latency (up to 2 minutes)

**UUID Primary Keys:**
- ✅ Global uniqueness, merge-friendly, no coordination
- ❌ Larger storage (128-bit vs 64-bit), slightly slower indexes

### Scaling Considerations

**Current Capacity:**
- Directory Services: ~10,000 requests/minute per instance
- Incident Service: ~5,000 incident operations/minute
- Notification Service: ~2,000 webhook dispatches/minute
- Database: ~100,000 entries per database
- SFTP Import: ~1,000 rows/second

**Scaling Strategies:**

**Horizontal Scaling (Future):**
- Run multiple instances of each service behind load balancer
- Directory Services: Stateless, can scale linearly
- Incident Service: Requires distributed transaction coordination
- Notification Service: Requires job scheduler for retry queue
- Requires: Persistent metadata store (not in-memory)
- Requires: Distributed locking for SFTP import

**Database Scaling (Future):**
- Separate PostgreSQL instances per service
- Read replicas for reporting queries
- Connection pooling at application level
- Incidents database: Partition by year (reference number)
- Notifications database: Archive old notifications to separate storage

**SFTP Scaling (Future):**
- Multiple SFTP servers with shared storage
- Load balancer for SFTP connections
- Distributed file system (NFS, S3)

**Notification Scaling (Future):**
- Move retry logic to background jobs (async processing)
- Use message queue (Kafka, RabbitMQ) for webhook dispatch
- Separate webhook dispatcher service for horizontal scaling

## Related Documentation

### Product & Features

- [PRD.md](PRD.md) - Product requirements document with feature inventory
- [features/directory-management.md](features/directory-management.md) - Directory CRUD and CSV import
- [features/incident-lifecycle.md](features/incident-lifecycle.md) - Incident state machine details
- [features/webhook-notifications.md](features/webhook-notifications.md) - Notification dispatch and retry
- [features/sftp-data-import.md](features/sftp-data-import.md) - SFTP polling and change detection
- [features/portal-dashboard.md](features/portal-dashboard.md) - Dashboard KPIs and aggregation
- [features/claims-management-ui.md](features/claims-management-ui.md) - Frontend claims pages

### Service-Specific Documentation

- [microservices/directory/CLAUDE.md](../microservices/directory/CLAUDE.md) - Directory Service implementation and usage
- [microservices/incident/CLAUDE.md](../microservices/incident/CLAUDE.md) - Incident Service implementation and usage
- [microservices/notification/CLAUDE.md](../microservices/notification/CLAUDE.md) - Notification Service implementation and usage
- [microservices/portal/CLAUDE.md](../microservices/portal/CLAUDE.md) - Portal BFF implementation and usage
- [microservices/sftp-server/CLAUDE.md](../microservices/sftp-server/CLAUDE.md) - SFTP Server implementation and usage
- [utilities/directory-data-generator/CLAUDE.md](../utilities/directory-data-generator/CLAUDE.md) - Data Generator usage

### Architecture Topics

- [USER_GUIDE.md](USER_GUIDE.md) - Operational procedures and step-by-step guides
- [topics/vault-ssh-ca.md](topics/vault-ssh-ca.md) - Vault SSH Certificate Authority implementation
- [topics/ssh-keys.md](topics/ssh-keys.md) - Static SSH key management (fallback mode)
- [topics/database.md](topics/database.md) - PostgreSQL technical deep-dive
- [topics/docker.md](topics/docker.md) - Docker and containerization details
- [topics/sftp-import.md](topics/sftp-import.md) - SFTP import system architecture
- [topics/configuration.md](topics/configuration.md) - Configuration management
- [migrations/uuid-migration.md](migrations/uuid-migration.md) - UUID migration details
- [INDEX.md](INDEX.md) - Complete documentation index

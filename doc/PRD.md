# Product Requirements Document (PRD)

## IRD0 Insurance Platform

**Version:** 1.0
**Last Updated:** 2026-01-22
**Status:** Active Development

---

## 1. Executive Summary

### 1.1 Product Overview

IRD0 is an insurance claim management platform designed to streamline the end-to-end lifecycle of insurance incidents. The platform provides:

- **Directory Management** for all stakeholders (policyholders, insurers, experts, providers)
- **Incident Lifecycle Management** with state machine enforcement
- **Automated Data Import** via SFTP with intelligent change detection
- **Webhook Notifications** for real-time event dispatch to external systems
- **Portal Dashboard** aggregating claim data for operational visibility

### 1.2 Key Value Proposition

| Capability | Business Value |
|------------|----------------|
| Centralized Directory | Single source of truth for all actors in the claims process |
| State Machine Workflow | Enforced business rules prevent invalid claim transitions |
| Automated Data Sync | Reduced manual data entry via SFTP polling |
| Real-time Notifications | External systems stay synchronized via webhooks |
| Unified Dashboard | Operational visibility across all claims |

### 1.3 Target Users

- **Insurance Operations Teams** - Manage claims lifecycle
- **External Partners** - Receive webhook notifications
- **System Administrators** - Configure and monitor the platform
- **Integration Systems** - Consume REST APIs and SFTP data

---

## 2. Product Vision and Goals

### 2.1 Vision Statement

Create a modular, resilient insurance claims management platform that enables insurance companies to efficiently track incidents from initial declaration through resolution while maintaining data integrity and providing real-time visibility.

### 2.2 Business Goals

| Goal | Description | Success Metric |
|------|-------------|----------------|
| **Automate Claims Processing** | Reduce manual intervention in claim workflows | <5 minutes average claim state transition |
| **Data Accuracy** | Maintain accurate directory information | 99.9% data consistency across services |
| **Integration Flexibility** | Enable external system integration | Webhook delivery success rate >99% |
| **Operational Visibility** | Provide real-time claim status tracking | Dashboard refresh <30 seconds |
| **Audit Compliance** | Track all claim changes for compliance | 100% event logging coverage |

### 2.3 Non-Goals (Out of Scope)

- Payment processing and billing
- Document management and file storage
- Customer-facing mobile applications
- Multi-tenancy (currently single-tenant)
- Real-time chat or messaging

---

## 3. User Personas and Actors

### 3.1 Primary Actors

| Actor | Description | Key Interactions |
|-------|-------------|------------------|
| **Policyholder** | Individual or entity with an insurance policy | Subject of incidents, referenced in claims |
| **Insurer** | Insurance company handling the claim | Owns incidents, manages claim lifecycle |
| **Expert** | Professional assigned to assess claims | Assigned to incidents, provides assessments |
| **Provider** | Service provider for repairs/services | Referenced in directory, may receive notifications |
| **System Administrator** | Platform operator | Monitors health, manages configuration |

### 3.2 System Actors

| Actor | Description | Key Interactions |
|-------|-------------|------------------|
| **SFTP Data Source** | External system providing CSV data | Uploads policyholder data files |
| **Webhook Consumer** | External system receiving notifications | Receives incident lifecycle events |
| **Directory Importer** | Scheduled job polling SFTP | Imports and synchronizes directory data |
| **Notification Dispatcher** | Background process | Delivers webhooks with retry logic |

### 3.3 Actor Relationships

```
                    ┌─────────────┐
                    │ Policyholder│
                    └──────┬──────┘
                           │ subject of
                           ▼
┌─────────┐  manages  ┌──────────┐  assigned to  ┌────────┐
│ Insurer ├──────────>│ Incident │<─────────────┤ Expert │
└─────────┘           └────┬─────┘              └────────┘
                           │
                           │ triggers
                           ▼
                    ┌─────────────┐
                    │Notification │
                    └──────┬──────┘
                           │ delivers to
                           ▼
                    ┌──────────────┐
                    │Webhook       │
                    │Consumer      │
                    └──────────────┘
```

---

## 4. Feature Inventory

### 4.1 Directory Management

| Feature ID | Feature | Description | Status |
|------------|---------|-------------|--------|
| DIR-001 | CRUD Operations | Create, read, update, delete directory entries | Complete |
| DIR-002 | Multi-Instance Deployment | Single codebase serving policyholders, experts, providers, insurers | Complete |
| DIR-003 | CSV Bulk Import | Import directory entries from CSV files | Complete |
| DIR-004 | Email Uniqueness | Enforce unique email addresses per directory | Complete |
| DIR-005 | Webhook URL Storage | Store webhook endpoints for notifications | Complete |
| DIR-006 | Entry Types | Support individual, family, corporate types | Complete |

### 4.2 SFTP Data Import

| Feature ID | Feature | Description | Status |
|------------|---------|-------------|--------|
| SFTP-001 | Automated Polling | Poll SFTP server every 2 minutes for new files | Complete |
| SFTP-002 | File Change Detection | Skip unchanged files based on timestamp | Complete |
| SFTP-003 | Row Change Detection | Detect new, updated, unchanged rows | Complete |
| SFTP-004 | Batch Processing | Process CSV rows in batches of 500 | Complete |
| SFTP-005 | Import Metrics | Track totalRows, newRows, updatedRows, failedRows | Complete |
| SFTP-006 | Read-Only SFTP Server | Expose files via SFTP with read-only access | Complete |
| SFTP-007 | SSH Key Authentication | Public key authentication only (no passwords) | Complete |
| SFTP-008 | Vault SSH CA | Certificate-based authentication with short-lived certs | Complete |

### 4.3 Incident Management

| Feature ID | Feature | Description | Status |
|------------|---------|-------------|--------|
| INC-001 | Incident Creation | Create new incidents with policyholder and insurer | Complete |
| INC-002 | State Machine | Enforce valid status transitions | Complete |
| INC-003 | Reference Numbers | Auto-generate INC-YYYY-NNNNNN format | Complete |
| INC-004 | Expert Assignment | Assign experts with scheduled dates | Complete |
| INC-005 | Comments | Add comments from various actor types | Complete |
| INC-006 | Event History | Track all changes with timestamps | Complete |
| INC-007 | Location Support | Store incident location with coordinates | Complete |
| INC-008 | Filtering & Pagination | List incidents with filters and pagination | Complete |
| INC-009 | Directory Validation | Validate actors exist before operations | Complete |

### 4.4 Notification Service

| Feature ID | Feature | Description | Status |
|------------|---------|-------------|--------|
| NOT-001 | Webhook Creation | Create notification with webhook URL and payload | Complete |
| NOT-002 | Immediate Dispatch | Attempt delivery immediately on creation | Complete |
| NOT-003 | Exponential Backoff | Retry with 1s, 2s, 4s delays | Complete |
| NOT-004 | Response Logging | Store HTTP status and response body | Complete |
| NOT-005 | Manual Retry | Retry failed notifications on demand | Complete |
| NOT-006 | Notification Status | Track PENDING, SENT, DELIVERED, FAILED states | Complete |

### 4.5 Portal/BFF Service

| Feature ID | Feature | Description | Status |
|------------|---------|-------------|--------|
| POR-001 | Dashboard KPIs | Total claims, pending, in-progress, closed counts | Complete |
| POR-002 | Status Distribution | Claims by status breakdown | Complete |
| POR-003 | Claims by Type | Claims grouped by incident type | Complete |
| POR-004 | Recent Activity | Last 10 claim events | Complete |
| POR-005 | Claims List | Paginated list with resolved actor names | Complete |
| POR-006 | Claim Detail | Full claim details with history | Complete |
| POR-007 | Actor Resolution | Convert IDs to names from directory services | Complete |

### 4.6 Frontend Portal

| Feature ID | Feature | Description | Status |
|------------|---------|-------------|--------|
| UI-001 | Dashboard Page | KPI cards, charts, recent activity | Complete |
| UI-002 | Claims List Page | Filterable, sortable claims table | Complete |
| UI-003 | Claim Detail Page | Full claim view with actions | Complete |
| UI-004 | Claim Create Page | Form to create new claims | Complete |
| UI-005 | Status Updates | Change claim status from detail page | Complete |
| UI-006 | Expert Assignment | Assign experts from claim detail | Complete |
| UI-007 | Comments | View and add comments | Complete |

---

## 5. Functional Requirements

### 5.1 Directory Service Requirements

#### FR-DIR-001: Directory Entry CRUD
- **Description**: System shall support create, read, update, delete operations for directory entries
- **Acceptance Criteria**:
  - POST creates entry, returns 201 with UUID
  - GET by ID returns entry or 404
  - PUT updates entry, returns 200
  - DELETE removes entry, returns 204
  - List returns paginated results

#### FR-DIR-002: Email Validation
- **Description**: System shall enforce unique, valid email addresses
- **Acceptance Criteria**:
  - Reject invalid email formats (400 Bad Request)
  - Reject duplicate emails (409 Conflict)
  - Email is required field

#### FR-DIR-003: CSV Import
- **Description**: System shall import directory entries from CSV files
- **Acceptance Criteria**:
  - Accept multipart/form-data file upload
  - Parse CSV with headers: name, type, email, phone, address, additionalInfo
  - Insert new entries (email not exists)
  - Update existing entries (email exists, fields changed)
  - Skip unchanged entries
  - Return import summary (new, updated, unchanged, failed counts)

#### FR-DIR-004: SFTP Polling Import
- **Description**: System shall automatically poll SFTP for new data files
- **Acceptance Criteria**:
  - Poll every 2 minutes (configurable)
  - Skip files with unchanged timestamp
  - Process all *.csv files in root directory
  - Log import results

### 5.2 Incident Service Requirements

#### FR-INC-001: Incident Creation
- **Description**: System shall create incidents with required fields
- **Acceptance Criteria**:
  - Required: policyholderId, insurerId, type, incidentDate
  - Validate policyholder exists in directory
  - Validate insurer exists in directory
  - Generate unique reference number (INC-YYYY-NNNNNN)
  - Initial status: DECLARED
  - Return 201 with full incident details

#### FR-INC-002: State Machine Transitions
- **Description**: System shall enforce valid status transitions
- **Acceptance Criteria**:
  - DECLARED → QUALIFIED, ABANDONED
  - QUALIFIED → IN_PROGRESS, ABANDONED
  - IN_PROGRESS → RESOLVED, ABANDONED
  - RESOLVED → (terminal)
  - ABANDONED → (terminal)
  - Reject invalid transitions (400 Bad Request)
  - Log transition in event history

#### FR-INC-003: Expert Assignment
- **Description**: System shall support assigning experts to incidents
- **Acceptance Criteria**:
  - Validate expert exists in directory
  - Require incident status: QUALIFIED or IN_PROGRESS
  - Accept optional scheduledDate and notes
  - Log assignment in event history

#### FR-INC-004: Comments
- **Description**: System shall support adding comments to incidents
- **Acceptance Criteria**:
  - Accept content, authorId, authorType
  - AuthorType: POLICYHOLDER, INSURER, EXPERT, SYSTEM
  - Return comments ordered by createdAt DESC
  - Log comment in event history

#### FR-INC-005: Event History
- **Description**: System shall track all incident changes
- **Acceptance Criteria**:
  - Record: eventType, previousValue, newValue, timestamp, triggeredBy
  - Event types: STATUS_CHANGE, EXPERT_ASSIGNED, COMMENT_ADDED, INSURER_UPDATED
  - Return history ordered by occurredAt DESC

### 5.3 Notification Service Requirements

#### FR-NOT-001: Webhook Notification Creation
- **Description**: System shall create and dispatch webhook notifications
- **Acceptance Criteria**:
  - Accept: webhookUrl, payload (JSON)
  - Attempt immediate HTTP POST
  - Set status based on response
  - Return notification details with ID

#### FR-NOT-002: Retry Logic
- **Description**: System shall retry failed notifications
- **Acceptance Criteria**:
  - Retry on HTTP 5xx, timeout, connection error
  - Do not retry on HTTP 4xx (client error)
  - Exponential backoff: 1s, 2s, 4s (configurable)
  - Max retries: 3 (configurable)
  - Mark FAILED after max retries exhausted

#### FR-NOT-003: Manual Retry
- **Description**: System shall support manual retry of failed notifications
- **Acceptance Criteria**:
  - POST /notifications/{id}/retry resets retry count
  - Re-attempts dispatch immediately
  - Only available for FAILED notifications

### 5.4 Portal Service Requirements

#### FR-POR-001: Dashboard Data
- **Description**: System shall provide aggregated dashboard data
- **Acceptance Criteria**:
  - KPIs: totalClaims, pendingCount, inProgressCount, closedThisMonth
  - Status distribution: count per status
  - Claims by type: count per incident type
  - Recent activity: last 10 events

#### FR-POR-002: Claims List
- **Description**: System shall provide paginated claims list with resolved names
- **Acceptance Criteria**:
  - Pagination: page, size, sort parameters
  - Filters: policyholderId, insurerId, status, type, fromDate, toDate
  - Resolve policyholderName and insurerName from directory
  - Return ClaimSummaryDTO with resolved names

#### FR-POR-003: Claim Detail
- **Description**: System shall provide full claim details with actor names
- **Acceptance Criteria**:
  - Include all incident fields
  - Resolve policyholder, insurer, expert names
  - Include availableTransitions based on current status
  - Include comments with authorName resolved
  - Include history with actorName resolved

---

## 6. Non-Functional Requirements

### 6.1 Performance Requirements

| Requirement ID | Description | Target |
|----------------|-------------|--------|
| NFR-PERF-001 | API response time (p95) | <200ms for CRUD operations |
| NFR-PERF-002 | Dashboard load time | <1 second |
| NFR-PERF-003 | CSV import throughput | >500 rows/second |
| NFR-PERF-004 | SFTP file transfer | >10 MB/s |
| NFR-PERF-005 | Webhook dispatch time | <5 seconds including retries |
| NFR-PERF-006 | Concurrent users | Support 100 concurrent API clients |

### 6.2 Scalability Requirements

| Requirement ID | Description | Target |
|----------------|-------------|--------|
| NFR-SCALE-001 | Directory entries | 100,000 per instance |
| NFR-SCALE-002 | Active incidents | 50,000 concurrent |
| NFR-SCALE-003 | Notifications | 10,000 pending notifications |
| NFR-SCALE-004 | API throughput | 1,000 requests/minute per service |

### 6.3 Availability Requirements

| Requirement ID | Description | Target |
|----------------|-------------|--------|
| NFR-AVAIL-001 | Service uptime | 99.9% availability |
| NFR-AVAIL-002 | Planned maintenance window | <1 hour/month |
| NFR-AVAIL-003 | Circuit breaker recovery | <30 seconds open state |
| NFR-AVAIL-004 | Database failover | Manual intervention acceptable |

### 6.4 Security Requirements

| Requirement ID | Description | Implementation |
|----------------|-------------|----------------|
| NFR-SEC-001 | SFTP authentication | SSH public key only (RSA 2048+) |
| NFR-SEC-002 | Certificate authentication | Vault SSH CA with 15-minute TTL |
| NFR-SEC-003 | File system access | Read-only SFTP file system |
| NFR-SEC-004 | Input validation | Bean validation on all API inputs |
| NFR-SEC-005 | UUID identifiers | Non-sequential, non-guessable IDs |
| NFR-SEC-006 | Credential storage | Environment variables (production: secrets manager) |

### 6.5 Observability Requirements

| Requirement ID | Description | Implementation |
|----------------|-------------|----------------|
| NFR-OBS-001 | Health endpoints | Spring Boot Actuator /actuator/health |
| NFR-OBS-002 | Metrics collection | JVM and custom metrics via /actuator/metrics |
| NFR-OBS-003 | Structured logging | JSON logging for production |
| NFR-OBS-004 | Audit trail | IncidentEvent and notification logs |
| NFR-OBS-005 | Certificate audit | Authentication events logged with serial numbers |

---

## 7. Technical Constraints

### 7.1 Technology Stack

| Layer | Technology | Version |
|-------|------------|---------|
| Runtime | Java | 21 (LTS) |
| Framework | Spring Boot | 3.5.0 |
| Database | PostgreSQL | 16 |
| ORM | Hibernate | 6.x |
| SFTP Server | Apache MINA SSHD | 2.12.0 |
| Secrets | HashiCorp Vault | Latest |
| Container | Docker | Latest |
| Build | Maven | 3.9+ |
| Frontend | Angular | 20 |

### 7.2 Architectural Constraints

| Constraint | Description | Rationale |
|------------|-------------|-----------|
| Multi-instance pattern | Single codebase, multiple deployments | Code reuse, consistent behavior |
| Synchronous REST | REST API for inter-service communication | Simplicity, no message broker |
| PostgreSQL per service | Separate databases per service | Data isolation, independent scaling |
| Docker deployment | All services containerized | Consistent deployment, portability |
| UUID primary keys | UUID type for all entity IDs | Global uniqueness, no coordination |

### 7.3 Integration Constraints

| Constraint | Description |
|------------|-------------|
| SSH key auth only | SFTP does not support password authentication |
| RSA keys only | Apache MINA SSHD 2.12 limitation (Ed25519 not supported) |
| No shared state | Services communicate only via REST APIs |
| Circuit breaker | Inter-service calls protected by Resilience4j |

---

## 8. API Specifications Summary

### 8.1 Service Endpoints

| Service | Base Path | Port | Description |
|---------|-----------|------|-------------|
| Policyholders | `/api/policyholders` | 8081 | Policyholder directory |
| Experts | `/api/experts` | 8082 | Expert directory |
| Providers | `/api/providers` | 8083 | Provider directory |
| Insurers | `/api/insurers` | 8084 | Insurer directory |
| Incidents | `/api/v1/incidents` | 8085 | Incident management |
| Notifications | `/api/v1/notifications` | 8086 | Webhook notifications |
| Portal | `/api/portal/v1` | 8090 | BFF for frontend |

### 8.2 Common Response Codes

| Code | Meaning | Usage |
|------|---------|-------|
| 200 | OK | Successful GET, PUT |
| 201 | Created | Successful POST |
| 204 | No Content | Successful DELETE |
| 400 | Bad Request | Validation error, invalid state transition |
| 404 | Not Found | Resource not found |
| 409 | Conflict | Duplicate email, constraint violation |
| 500 | Server Error | Unexpected error |

### 8.3 OpenAPI Documentation

OpenAPI specifications available at runtime:
- Directory: `http://localhost:808X/v3/api-docs`
- Incident: `http://localhost:8085/v3/api-docs`
- Notification: `http://localhost:8086/v3/api-docs`
- Portal: `http://localhost:8090/v3/api-docs`

---

## 9. Data Models Summary

### 9.1 DirectoryEntry

```
DirectoryEntry
├── id: UUID (PK, auto-generated)
├── name: String (required)
├── type: String (individual/family/corporate)
├── email: String (required, unique)
├── phone: String (required)
├── address: String (optional)
├── additionalInfo: String (optional)
└── webhookUrl: String (optional)
```

### 9.2 Incident

```
Incident
├── id: UUID (PK)
├── referenceNumber: String (unique, INC-YYYY-NNNNNN)
├── policyholderId: UUID (FK to policyholders)
├── insurerId: UUID (FK to insurers)
├── status: Enum (DECLARED, QUALIFIED, IN_PROGRESS, RESOLVED, ABANDONED)
├── type: String (WATER_DAMAGE, FIRE, THEFT, etc.)
├── description: String
├── incidentDate: Instant
├── location: JSONB {address, latitude, longitude}
├── estimatedDamage: BigDecimal
├── currency: String (default: EUR)
├── createdAt: Instant
├── updatedAt: Instant
├── expertAssignments: List<ExpertAssignment>
├── comments: List<Comment>
└── events: List<IncidentEvent>
```

### 9.3 Notification

```
Notification
├── id: UUID (PK)
├── eventId: UUID
├── eventType: String
├── incidentId: UUID (optional)
├── recipientId: UUID (optional)
├── webhookUrl: String
├── status: Enum (PENDING, SENT, DELIVERED, FAILED, CANCELLED)
├── payload: JSONB
├── sentAt: Instant
├── responseCode: Integer
├── responseBody: String (truncated to 2000 chars)
├── retryCount: Integer
├── nextRetryAt: Instant
├── failureReason: String
└── createdAt: Instant
```

---

## 10. Incident State Machine

### 10.1 State Diagram

```
                    ┌──────────────────────────────────────────┐
                    │                                          │
                    ▼                                          │
              ┌─────────┐                                      │
         ┌────│ DECLARED│────┐                                 │
         │    └────┬────┘    │                                 │
         │         │         │ abandon                         │
         │ qualify │         │                                 │
         │         ▼         │                                 │
         │    ┌─────────┐    │                                 │
         │    │QUALIFIED│────┼─────────────────────────────────┤
         │    └────┬────┘    │                                 │
         │         │         │                                 │
         │ assign  │         │                                 │
         │         ▼         │                                 │
         │  ┌────────────┐   │                                 │
         │  │IN_PROGRESS │───┼─────────────────────────────────┤
         │  └─────┬──────┘   │                                 │
         │        │          │                                 │
         │ resolve│          │                                 │
         │        ▼          ▼                                 │
         │   ┌─────────┐ ┌─────────┐                           │
         │   │RESOLVED │ │ABANDONED│ ◄─────────────────────────┘
         │   └─────────┘ └─────────┘
         │   (terminal)   (terminal)
         │
         └── (happy path: DECLARED → QUALIFIED → IN_PROGRESS → RESOLVED)
```

### 10.2 Transition Rules

| From State | To State | Trigger | Validation |
|------------|----------|---------|------------|
| DECLARED | QUALIFIED | Qualification review | None |
| DECLARED | ABANDONED | Early abandonment | Reason required |
| QUALIFIED | IN_PROGRESS | Expert assignment | Expert must be assigned |
| QUALIFIED | ABANDONED | Qualification rejected | Reason required |
| IN_PROGRESS | RESOLVED | Completion | Assessment complete |
| IN_PROGRESS | ABANDONED | Process failure | Reason required |

### 10.3 Events Generated

| Transition | Event Type | Notification |
|------------|------------|--------------|
| → DECLARED | INCIDENT_DECLARED | Yes |
| → QUALIFIED | INCIDENT_QUALIFIED | Yes |
| → IN_PROGRESS | STATUS_CHANGED | Yes |
| → RESOLVED | INCIDENT_RESOLVED | Yes |
| → ABANDONED | INCIDENT_ABANDONED | Yes |
| Expert assigned | EXPERT_ASSIGNED | Yes |

---

## 11. Port Reference

| Service | Local Dev | Docker Internal | Docker Host | Purpose |
|---------|-----------|-----------------|-------------|---------|
| Policyholders | 8081 | 8080 | 8081 | Policyholder directory |
| Experts | 8082 | 8080 | 8082 | Expert directory |
| Providers | 8083 | 8080 | 8083 | Provider directory |
| Insurers | 8084 | 8080 | 8084 | Insurer directory |
| Incident | 8085 | 8080 | 8085 | Incident management |
| Notification | 8086 | 8080 | 8086 | Webhook dispatch |
| Portal BFF | 8090 | 8080 | 8090 | Frontend aggregation |
| SFTP | 2222 | 2222 | 2222 | File transfer |
| SFTP Actuator | 9090 | 8080 | 9090 | SFTP monitoring |
| PostgreSQL | 5432 | 5432 | 5432 | Database |
| Vault | 8200 | 8200 | 8200 | Secrets/SSH CA |

---

## 12. Appendices

### Appendix A: Incident Types

| Type | Description |
|------|-------------|
| WATER_DAMAGE | Flooding, pipe burst, water infiltration |
| FIRE | Fire damage to property |
| THEFT | Burglary, robbery, theft |
| LIABILITY | Third-party liability claims |
| PROPERTY_DAMAGE | General property damage |
| NATURAL_DISASTER | Storm, earthquake, flood |
| VEHICLE_ACCIDENT | Motor vehicle incidents |
| OTHER | Uncategorized incidents |

### Appendix B: Actor Types (Comments)

| Type | Description |
|------|-------------|
| POLICYHOLDER | Comment from the insured party |
| INSURER | Comment from insurance company |
| EXPERT | Comment from assigned expert |
| SYSTEM | Automated system comment |

### Appendix C: Notification Event Types

| Event Type | Trigger |
|------------|---------|
| INCIDENT_DECLARED | New incident created |
| INCIDENT_QUALIFIED | Incident qualified for processing |
| INCIDENT_ABANDONED | Incident abandoned |
| INCIDENT_RESOLVED | Incident resolved |
| EXPERT_ASSIGNED | Expert assigned to incident |
| STATUS_CHANGED | Any status transition |
| INSURER_UPDATED | Insurer changed on incident |

---

## Related Documentation

- [ARCHITECTURE.md](ARCHITECTURE.md) - Technical architecture deep-dive
- [features/](features/) - Detailed feature documentation
- [USER_GUIDE.md](USER_GUIDE.md) - Operations manual
- [topics/](topics/) - Topic-based technical guides

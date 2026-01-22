# Directory Management

## Overview

The Directory Management feature provides centralized management of all actors in the insurance claims process: policyholders, experts, providers, and insurers. It uses a multi-instance microservice pattern where a single codebase serves four separate directory instances, each with its own database.

### Business Value

- **Single Source of Truth**: All actor data managed in dedicated directories
- **Data Isolation**: Separate databases prevent cross-contamination
- **Consistent API**: Same contract for all directory types
- **Webhook Integration**: Store webhook URLs for notification dispatch

---

## User Stories

### US-DIR-001: Create Directory Entry
**As an** administrator
**I want to** add a new entry to a directory
**So that** the actor can be referenced in incidents

**Acceptance Criteria**:
- Form accepts name, type, email, phone, address, additionalInfo
- Email must be unique within the directory
- System generates UUID for the entry
- Entry is immediately available via GET

### US-DIR-002: Update Directory Entry
**As an** administrator
**I want to** update an existing directory entry
**So that** actor information stays current

**Acceptance Criteria**:
- All fields except ID can be modified
- Email uniqueness enforced on update
- Update returns the modified entry

### US-DIR-003: List Directory Entries
**As a** system user
**I want to** view all entries in a directory
**So that** I can find actors for reference

**Acceptance Criteria**:
- Returns paginated list of entries
- Supports sorting by name, email
- Returns count of total entries

### US-DIR-004: Import Directory Data
**As an** administrator
**I want to** bulk import directory entries from CSV
**So that** I can efficiently populate the directory

**Acceptance Criteria**:
- Upload CSV file via REST endpoint
- New entries inserted, existing entries updated
- Unchanged entries skipped
- Import summary returned

---

## Business Rules

| Rule ID | Description | Enforcement |
|---------|-------------|-------------|
| BR-DIR-001 | Email must be unique within each directory | Database unique constraint + validation |
| BR-DIR-002 | Name, type, email, phone are required | Bean validation (@NotBlank) |
| BR-DIR-003 | Email must be valid format | Bean validation (@Email) |
| BR-DIR-004 | Type must be: individual, family, or corporate | String validation |
| BR-DIR-005 | UUID generated at entity creation | @PrePersist lifecycle hook |

---

## API Endpoints

### Base Paths

| Directory | Base Path | Port |
|-----------|-----------|------|
| Policyholders | `/api/policyholders` | 8081 |
| Experts | `/api/experts` | 8082 |
| Providers | `/api/providers` | 8083 |
| Insurers | `/api/insurers` | 8084 |

### Endpoints

| Method | Path | Description | Request Body | Response |
|--------|------|-------------|--------------|----------|
| GET | `/{base}` | List all entries | - | `List<DirectoryEntryDTO>` |
| GET | `/{base}/{id}` | Get by ID | - | `DirectoryEntryDTO` or 404 |
| POST | `/{base}` | Create entry | `DirectoryEntryDTO` | 201 + `DirectoryEntryDTO` |
| PUT | `/{base}/{id}` | Update entry | `DirectoryEntryDTO` | `DirectoryEntryDTO` or 404 |
| DELETE | `/{base}/{id}` | Delete entry | - | 204 or 404 |
| POST | `/{base}/import` | CSV import | `multipart/form-data` | `ImportResultDTO` |

### Request/Response Examples

**Create Entry**:
```http
POST /api/policyholders
Content-Type: application/json

{
  "name": "John Doe",
  "type": "individual",
  "email": "john.doe@example.com",
  "phone": "+33612345678",
  "address": "123 Main St, Paris",
  "additionalInfo": "Premium customer",
  "webhookUrl": "https://hooks.example.com/notify"
}
```

**Response**:
```json
{
  "id": "c9088e6f-86a4-4001-9a6a-554510787dd9",
  "name": "John Doe",
  "type": "individual",
  "email": "john.doe@example.com",
  "phone": "+33612345678",
  "address": "123 Main St, Paris",
  "additionalInfo": "Premium customer",
  "webhookUrl": "https://hooks.example.com/notify"
}
```

**Import Result**:
```json
{
  "totalRows": 1000,
  "newRows": 150,
  "updatedRows": 45,
  "unchangedRows": 800,
  "failedRows": 5,
  "errors": [
    "Row 23: Invalid email format",
    "Row 156: Duplicate email"
  ]
}
```

---

## Data Model

### DirectoryEntry

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| id | UUID | PK, auto-generated | Unique identifier |
| name | String | NOT NULL | Full name |
| type | String | NOT NULL | individual/family/corporate |
| email | String | NOT NULL, UNIQUE | Email address |
| phone | String | NOT NULL | Phone number |
| address | String | nullable | Physical address |
| additionalInfo | String | nullable | Free-form metadata |
| webhookUrl | String | nullable | Webhook endpoint for notifications |

### Database Schema

```sql
CREATE TABLE directory_entry (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    phone VARCHAR(50) NOT NULL,
    address TEXT,
    additional_info TEXT,
    webhook_url VARCHAR(2048)
);

CREATE INDEX idx_directory_entry_email ON directory_entry(email);
CREATE INDEX idx_directory_entry_type ON directory_entry(type);
```

---

## Multi-Instance Architecture

### How It Works

A single Directory Service codebase is deployed four times with different configurations:

```
                ┌─────────────────┐
                │ Directory       │
                │ Service Code    │
                └────────┬────────┘
                         │
        ┌────────────────┼────────────────┐
        │                │                │
        ▼                ▼                ▼
┌───────────────┐ ┌───────────────┐ ┌───────────────┐
│ Policyholders │ │   Experts     │ │  Providers    │
│   :8081       │ │   :8082       │ │   :8083       │
│ policyholders │ │  experts_db   │ │ providers_db  │
│      _db      │ │               │ │               │
└───────────────┘ └───────────────┘ └───────────────┘
```

### Configuration Files

**Common** (`application.yml`):
```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: update
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,info
```

**Instance-Specific** (`policyholders.yml`):
```yaml
server:
  port: 8081
app:
  base-path: /api/policyholders
spring:
  datasource:
    url: jdbc:postgresql://${POSTGRES_HOST:localhost}:5432/policyholders_db
```

---

## Validation

### Input Validation

Validation is enforced via Bean Validation annotations on the DTO:

```java
public class DirectoryEntryDTO {
    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Type is required")
    private String type;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Phone is required")
    private String phone;
}
```

### Error Responses

**Validation Error (400)**:
```json
{
  "timestamp": "2026-01-22T10:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "errors": [
    "email: Invalid email format",
    "phone: Phone is required"
  ]
}
```

**Conflict Error (409)**:
```json
{
  "timestamp": "2026-01-22T10:30:00Z",
  "status": 409,
  "error": "Conflict",
  "message": "Email already exists: john@example.com"
}
```

---

## Testing

### Health Check
```bash
curl http://localhost:8081/actuator/health
```

### CRUD Operations
```bash
# Create
curl -X POST http://localhost:8081/api/policyholders \
  -H "Content-Type: application/json" \
  -d '{"name":"Test User","type":"individual","email":"test@example.com","phone":"123456"}'

# List
curl http://localhost:8081/api/policyholders

# Get by ID
curl http://localhost:8081/api/policyholders/{id}

# Update
curl -X PUT http://localhost:8081/api/policyholders/{id} \
  -H "Content-Type: application/json" \
  -d '{"name":"Updated Name","type":"individual","email":"test@example.com","phone":"123456"}'

# Delete
curl -X DELETE http://localhost:8081/api/policyholders/{id}
```

### CSV Import
```bash
curl -X POST http://localhost:8081/api/policyholders/import \
  -F "file=@policyholders.csv"
```

---

## Related Documentation

- [PRD.md](../PRD.md) - Product requirements (FR-DIR-xxx)
- [ARCHITECTURE.md](../ARCHITECTURE.md) - Multi-instance pattern details
- [sftp-data-import.md](sftp-data-import.md) - Automated SFTP import
- [topics/database.md](../topics/database.md) - Database configuration

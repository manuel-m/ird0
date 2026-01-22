# Incident Lifecycle Management

## Overview

The Incident Lifecycle feature manages insurance claims through a defined state machine, from initial declaration to resolution or abandonment. It enforces business rules for valid state transitions and maintains a complete audit trail of all changes.

### Business Value

- **Workflow Enforcement**: Invalid state transitions prevented
- **Complete Audit Trail**: Every change tracked with timestamp and actor
- **Actor Validation**: References validated against directory services
- **Notification Integration**: Events trigger webhook notifications

---

## User Stories

### US-INC-001: Declare Incident
**As an** insurance operator
**I want to** create a new incident
**So that** I can begin processing a claim

**Acceptance Criteria**:
- Policyholder must exist in directory
- Insurer must exist in directory
- Unique reference number generated (INC-YYYY-NNNNNN)
- Initial status set to DECLARED
- INCIDENT_DECLARED event recorded

### US-INC-002: Qualify Incident
**As an** insurance operator
**I want to** qualify an incident for processing
**So that** experts can be assigned

**Acceptance Criteria**:
- Only DECLARED incidents can be qualified
- Status changes to QUALIFIED
- Reason optionally recorded
- STATUS_CHANGE event recorded

### US-INC-003: Assign Expert
**As an** insurance operator
**I want to** assign an expert to an incident
**So that** assessment can begin

**Acceptance Criteria**:
- Expert must exist in experts directory
- Incident must be QUALIFIED or IN_PROGRESS
- Scheduled date and notes optional
- EXPERT_ASSIGNED event recorded
- Status automatically transitions to IN_PROGRESS (if QUALIFIED)

### US-INC-004: Resolve Incident
**As an** insurance operator
**I want to** mark an incident as resolved
**So that** the claim is closed

**Acceptance Criteria**:
- Only IN_PROGRESS incidents can be resolved
- Status changes to RESOLVED (terminal)
- Resolution notes recorded
- INCIDENT_RESOLVED event recorded

### US-INC-005: Abandon Incident
**As an** insurance operator
**I want to** abandon an incident
**So that** invalid or withdrawn claims are tracked

**Acceptance Criteria**:
- Can abandon from DECLARED, QUALIFIED, or IN_PROGRESS
- Reason required
- Status changes to ABANDONED (terminal)
- INCIDENT_ABANDONED event recorded

### US-INC-006: View Incident History
**As an** insurance operator
**I want to** see all changes to an incident
**So that** I have complete audit visibility

**Acceptance Criteria**:
- All events listed chronologically
- Each event shows: type, old value, new value, actor, timestamp
- No events can be deleted or modified

---

## State Machine

### State Diagram

```
                         ┌───────────────────────────────────────┐
                         │                                       │
                         ▼                                       │
                   ┌─────────┐                                   │
              ┌────│ DECLARED│────────┐                          │
              │    └────┬────┘        │                          │
              │         │             │                          │
              │ qualify │             │ abandon                  │
              │         ▼             │                          │
              │    ┌─────────┐        │                          │
              │    │QUALIFIED│────────┼──────────────────────────┤
              │    └────┬────┘        │                          │
              │         │             │                          │
              │ assign  │             │                          │
              │ expert  │             │                          │
              │         ▼             │                          │
              │  ┌────────────┐       │                          │
              │  │IN_PROGRESS │───────┼──────────────────────────┤
              │  └─────┬──────┘       │                          │
              │        │              │                          │
              │resolve │              │                          │
              │        ▼              ▼                          │
              │   ┌─────────┐   ┌─────────┐                      │
              │   │RESOLVED │   │ABANDONED│◄─────────────────────┘
              │   └─────────┘   └─────────┘
              │   (terminal)    (terminal)
              │
              └── Happy path: DECLARED → QUALIFIED → IN_PROGRESS → RESOLVED
```

### Status Definitions

| Status | Description | Terminal | Next States |
|--------|-------------|----------|-------------|
| DECLARED | Incident reported, awaiting qualification | No | QUALIFIED, ABANDONED |
| QUALIFIED | Validated for processing, ready for expert | No | IN_PROGRESS, ABANDONED |
| IN_PROGRESS | Expert assigned, assessment underway | No | RESOLVED, ABANDONED |
| RESOLVED | Claim processed and closed | Yes | None |
| ABANDONED | Claim withdrawn or invalid | Yes | None |

### Transition Rules

| From | To | Trigger | Requirements |
|------|-----|---------|--------------|
| DECLARED | QUALIFIED | Qualification review | None |
| DECLARED | ABANDONED | Early abandonment | Reason required |
| QUALIFIED | IN_PROGRESS | Expert assignment | Expert must be valid |
| QUALIFIED | ABANDONED | Qualification failed | Reason required |
| IN_PROGRESS | RESOLVED | Assessment complete | None |
| IN_PROGRESS | ABANDONED | Process failure | Reason required |

### Transition Enforcement

Invalid transitions return HTTP 400:

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Invalid state transition: Cannot transition from RESOLVED to IN_PROGRESS"
}
```

---

## API Endpoints

### Base Path: `/api/v1/incidents`

| Method | Path | Description |
|--------|------|-------------|
| POST | `/` | Create incident |
| GET | `/` | List incidents (filtered) |
| GET | `/{id}` | Get by ID |
| GET | `/reference/{ref}` | Get by reference number |
| PUT | `/{id}/status` | Update status |
| PUT | `/{id}/insurer` | Change insurer |
| POST | `/{id}/expert` | Assign expert |
| POST | `/{id}/comments` | Add comment |
| GET | `/{id}/comments` | List comments |
| GET | `/{id}/history` | Get event history |
| DELETE | `/{id}` | Delete incident |

### Create Incident

```http
POST /api/v1/incidents
Content-Type: application/json

{
  "policyholderId": "c9088e6f-86a4-4001-9a6a-554510787dd9",
  "insurerId": "a7b5c3d1-2e4f-6789-abcd-ef0123456789",
  "type": "WATER_DAMAGE",
  "description": "Pipe burst in basement causing flooding",
  "incidentDate": "2026-01-20T08:30:00Z",
  "location": {
    "address": "123 Main St, Paris",
    "latitude": 48.8566,
    "longitude": 2.3522
  },
  "estimatedDamage": 5000.00,
  "currency": "EUR"
}
```

**Response (201 Created)**:
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "referenceNumber": "INC-2026-000001",
  "status": "DECLARED",
  "type": "WATER_DAMAGE",
  "description": "Pipe burst in basement causing flooding",
  "policyholderId": "c9088e6f-86a4-4001-9a6a-554510787dd9",
  "insurerId": "a7b5c3d1-2e4f-6789-abcd-ef0123456789",
  "incidentDate": "2026-01-20T08:30:00Z",
  "location": {
    "address": "123 Main St, Paris",
    "latitude": 48.8566,
    "longitude": 2.3522
  },
  "estimatedDamage": 5000.00,
  "currency": "EUR",
  "createdAt": "2026-01-20T09:00:00Z",
  "updatedAt": "2026-01-20T09:00:00Z"
}
```

### Update Status

```http
PUT /api/v1/incidents/{id}/status
Content-Type: application/json

{
  "status": "QUALIFIED",
  "reason": "Valid claim documentation received"
}
```

### Assign Expert

```http
POST /api/v1/incidents/{id}/expert
Content-Type: application/json

{
  "expertId": "e5f6g7h8-1234-5678-90ab-cdef01234567",
  "scheduledDate": "2026-01-25T10:00:00Z",
  "notes": "On-site assessment required"
}
```

### List with Filters

```http
GET /api/v1/incidents?status=DECLARED&type=WATER_DAMAGE&page=0&size=20&sort=createdAt,desc
```

---

## Data Model

### Incident Entity

| Field | Type | Description |
|-------|------|-------------|
| id | UUID | Primary key |
| referenceNumber | String | Unique reference (INC-YYYY-NNNNNN) |
| policyholderId | UUID | FK to policyholders directory |
| insurerId | UUID | FK to insurers directory |
| status | Enum | Current lifecycle status |
| type | String | Incident type |
| description | String | Detailed description |
| incidentDate | Instant | When incident occurred |
| location | JSONB | Address and coordinates |
| estimatedDamage | BigDecimal | Damage estimate |
| currency | String | Currency code (EUR) |
| createdAt | Instant | Creation timestamp |
| updatedAt | Instant | Last update timestamp |
| createdBy | UUID | User who created |

### ExpertAssignment

| Field | Type | Description |
|-------|------|-------------|
| id | UUID | Primary key |
| incidentId | UUID | FK to incident |
| expertId | UUID | FK to experts directory |
| assignedAt | Instant | Assignment timestamp |
| assignedBy | UUID | User who assigned |
| scheduledDate | Instant | Scheduled visit date |
| status | Enum | PENDING, ACCEPTED, COMPLETED |
| notes | String | Assignment notes |

### Comment

| Field | Type | Description |
|-------|------|-------------|
| id | UUID | Primary key |
| incidentId | UUID | FK to incident |
| authorId | UUID | Author ID |
| authorType | Enum | POLICYHOLDER, INSURER, EXPERT, SYSTEM |
| content | String | Comment text |
| createdAt | Instant | Creation timestamp |

### IncidentEvent (Audit)

| Field | Type | Description |
|-------|------|-------------|
| id | UUID | Primary key |
| incidentId | UUID | FK to incident |
| eventType | String | Event type |
| previousStatus | Enum | Previous status (for transitions) |
| newStatus | Enum | New status (for transitions) |
| description | String | Human-readable description |
| payload | JSONB | Additional event data |
| triggeredBy | UUID | User who triggered |
| occurredAt | Instant | Event timestamp |

---

## Event Types

| Event Type | Trigger | Payload |
|------------|---------|---------|
| INCIDENT_DECLARED | Incident created | Full incident details |
| STATUS_CHANGED | Status transition | Old status, new status, reason |
| INCIDENT_QUALIFIED | Qualified for processing | Qualification notes |
| INCIDENT_RESOLVED | Claim closed | Resolution details |
| INCIDENT_ABANDONED | Claim abandoned | Abandonment reason |
| EXPERT_ASSIGNED | Expert added | Expert ID, scheduled date |
| COMMENT_ADDED | Comment posted | Comment ID, author |
| INSURER_UPDATED | Insurer changed | Old insurer, new insurer |

---

## Directory Validation

### Validation Flow

```
Create Incident Request
         │
         ▼
┌─────────────────────┐
│ Validate            │
│ Policyholder ID     │──── 404 if not found
└─────────┬───────────┘
         │
         ▼
┌─────────────────────┐
│ Validate            │
│ Insurer ID          │──── 404 if not found
└─────────┬───────────┘
         │
         ▼
┌─────────────────────┐
│ Create Incident     │
└─────────────────────┘
```

### Circuit Breaker

Directory service calls protected by Resilience4j:

| Parameter | Value |
|-----------|-------|
| Sliding window size | 10 calls |
| Failure threshold | 50% |
| Open state duration | 30 seconds |
| Half-open calls | 3 |

**On circuit open**: Returns HTTP 503 with message "Directory service unavailable"

---

## Reference Number Generation

### Format

```
INC-YYYY-NNNNNN

INC     - Prefix (Incident)
YYYY    - 4-digit year
NNNNNN  - 6-digit sequence (zero-padded)
```

### Examples

- `INC-2026-000001` - First incident of 2026
- `INC-2026-000150` - 150th incident of 2026
- `INC-2027-000001` - First incident of 2027 (resets each year)

### Implementation

- Database sequence per year
- Atomic increment on insert
- Unique constraint prevents duplicates

---

## Notification Integration

### Published Events

When status changes or significant events occur, notifications are sent:

```
Incident Service                     Notification Service
        │                                    │
        │ 1. Status update                   │
        │                                    │
        │ 2. Create notification             │
        │    POST /api/v1/notifications      │
        │    {eventType, webhookUrl, payload}│
        │──────────────────────────────────►│
        │                                    │
        │ 3. 201 Created                     │
        │◄──────────────────────────────────│
        │                                    │
        │                                    │ 4. Dispatch webhook
        │                                    │────────────────────►
```

### Notification Payload

```json
{
  "eventType": "INCIDENT_QUALIFIED",
  "eventId": "550e8400-e29b-41d4-a716-446655440000",
  "timestamp": "2026-01-22T10:30:00Z",
  "incident": {
    "id": "550e8400-e29b-41d4-a716-446655440001",
    "referenceNumber": "INC-2026-000001",
    "status": "QUALIFIED",
    "type": "WATER_DAMAGE",
    "policyholderId": "c9088e6f-86a4-4001-9a6a-554510787dd9",
    "insurerId": "a7b5c3d1-2e4f-6789-abcd-ef0123456789"
  }
}
```

---

## Testing

### Create and Progress Incident

```bash
# Create incident
INCIDENT_ID=$(curl -s -X POST http://localhost:8085/api/v1/incidents \
  -H "Content-Type: application/json" \
  -d '{
    "policyholderId": "c9088e6f-86a4-4001-9a6a-554510787dd9",
    "insurerId": "a7b5c3d1-2e4f-6789-abcd-ef0123456789",
    "type": "WATER_DAMAGE",
    "incidentDate": "2026-01-20T08:30:00Z"
  }' | jq -r '.id')

# Qualify
curl -X PUT "http://localhost:8085/api/v1/incidents/$INCIDENT_ID/status" \
  -H "Content-Type: application/json" \
  -d '{"status": "QUALIFIED"}'

# Assign expert
curl -X POST "http://localhost:8085/api/v1/incidents/$INCIDENT_ID/expert" \
  -H "Content-Type: application/json" \
  -d '{"expertId": "e5f6g7h8-1234-5678-90ab-cdef01234567"}'

# View history
curl "http://localhost:8085/api/v1/incidents/$INCIDENT_ID/history"
```

### Test Invalid Transition

```bash
# Try to resolve a DECLARED incident (should fail)
curl -X PUT "http://localhost:8085/api/v1/incidents/$INCIDENT_ID/status" \
  -H "Content-Type: application/json" \
  -d '{"status": "RESOLVED"}'
# Returns 400 Bad Request
```

---

## Related Documentation

- [PRD.md](../PRD.md) - Product requirements (FR-INC-xxx)
- [ARCHITECTURE.md](../ARCHITECTURE.md) - State machine architecture
- [expert-assignment.md](expert-assignment.md) - Expert assignment details
- [webhook-notifications.md](webhook-notifications.md) - Notification dispatch
- [microservices/incident/CLAUDE.md](../../microservices/incident/CLAUDE.md) - Service implementation

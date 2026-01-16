# Incident Service

This document provides detailed guidance for working with the Incident Service microservice.

## Overview

The Incident Service manages insurance claim incidents through their complete lifecycle. It provides REST APIs for creating, tracking, and resolving incidents, with integration to directory services for validation and notification services for alerting.

**Key Features:**
- RESTful CRUD operations for incidents
- State machine for incident lifecycle management
- Integration with Directory Services (policyholder, insurer, expert validation)
- Integration with Notification Service (webhook notifications)
- Circuit breaker pattern for resilient inter-service communication
- Expert assignment workflow
- Comment system for incident discussions
- Event history tracking

## Architecture

### Components

**`IncidentApplication.java`** - Main Spring Boot entry point

**`controller/IncidentController.java`** - REST API controller with endpoints:
- `POST /api/v1/incidents` - Create new incident
- `GET /api/v1/incidents/{id}` - Get incident by ID
- `GET /api/v1/incidents/reference/{referenceNumber}` - Get by reference
- `GET /api/v1/incidents` - List with filters and pagination
- `PUT /api/v1/incidents/{id}/status` - Update status
- `POST /api/v1/incidents/{id}/expert` - Assign expert
- `POST /api/v1/incidents/{id}/comments` - Add comment
- `GET /api/v1/incidents/{id}/comments` - Get comments
- `GET /api/v1/incidents/{id}/history` - Get event history
- `DELETE /api/v1/incidents/{id}` - Delete incident

**`service/IncidentService.java`** - Business logic layer

**`service/DirectoryValidationService.java`** - Validates entities against Directory Services with circuit breaker

**`service/NotificationClient.java`** - Sends notifications to Notification Service

**`model/Incident.java`** - JPA entity with state machine

**`model/IncidentStatus.java`** - Enum defining incident lifecycle states

### Incident Lifecycle State Machine

```
DECLARED --> QUALIFIED --> IN_PROGRESS --> RESOLVED
    |           |              |
    v           v              v
ABANDONED   ABANDONED      ABANDONED
```

**Valid Transitions:**
- `DECLARED` -> `QUALIFIED`, `ABANDONED`
- `QUALIFIED` -> `IN_PROGRESS`, `ABANDONED`
- `IN_PROGRESS` -> `RESOLVED`, `ABANDONED`
- `RESOLVED` -> (terminal)
- `ABANDONED` -> (terminal)

## Configuration

### `configs/incident.yml`

```yaml
server:
  port: ${SERVER_PORT:8085}

incident:
  api:
    base-path: /api/v1/incidents
  directory:
    policyholders-url: ${POLICYHOLDERS_SERVICE_URL:http://localhost:8081}
    insurers-url: ${INSURERS_SERVICE_URL:http://localhost:8084}
    experts-url: ${EXPERTS_SERVICE_URL:http://localhost:8082}
    providers-url: ${PROVIDERS_SERVICE_URL:http://localhost:8083}
  notification:
    url: ${NOTIFICATION_SERVICE_URL:http://localhost:8086}
    enabled: ${NOTIFICATION_ENABLED:true}

spring:
  datasource:
    url: jdbc:postgresql://${POSTGRES_HOST:localhost}:${POSTGRES_PORT:5432}/incidents_db

resilience4j:
  circuitbreaker:
    instances:
      directoryService:
        slidingWindowSize: 10
        minimumNumberOfCalls: 5
        failureRateThreshold: 50
        waitDurationInOpenState: 30s
```

## Running Locally

**Prerequisites:**
- PostgreSQL running with `incidents_db` database
- Directory services running (optional, for validation)
- Notification service running (optional, for notifications)

**Run the service:**
```bash
cd microservices/incident
mvn spring-boot:run \
  -Dspring-boot.run.arguments="--spring.config.location=file:configs/application.yml,file:configs/incident.yml"
```

**With Docker:**
```bash
docker compose up incident-svc
```

## API Examples

### Create Incident

```bash
curl -X POST http://localhost:8085/api/v1/incidents \
  -H "Content-Type: application/json" \
  -d '{
    "policyholderId": "550e8400-e29b-41d4-a716-446655440000",
    "insurerId": "550e8400-e29b-41d4-a716-446655440001",
    "type": "VEHICLE_ACCIDENT",
    "description": "Car accident on highway",
    "incidentDate": "2026-01-15T10:30:00Z",
    "estimatedDamage": 5000.00,
    "location": {
      "address": "123 Main St, Paris",
      "latitude": 48.8566,
      "longitude": 2.3522
    }
  }'
```

### Update Status

```bash
curl -X PUT http://localhost:8085/api/v1/incidents/{id}/status \
  -H "Content-Type: application/json" \
  -d '{
    "status": "QUALIFIED",
    "reason": "All documents verified"
  }'
```

### Assign Expert

```bash
curl -X POST http://localhost:8085/api/v1/incidents/{id}/expert \
  -H "Content-Type: application/json" \
  -d '{
    "expertId": "550e8400-e29b-41d4-a716-446655440002",
    "scheduledDate": "2026-01-20T09:00:00Z",
    "notes": "Vehicle damage assessment"
  }'
```

### Add Comment

```bash
curl -X POST http://localhost:8085/api/v1/incidents/{id}/comments \
  -H "Content-Type: application/json" \
  -d '{
    "content": "Initial assessment complete",
    "authorId": "550e8400-e29b-41d4-a716-446655440003",
    "authorType": "EXPERT"
  }'
```

## Circuit Breaker

The service uses Resilience4j circuit breaker for inter-service calls to Directory Services:

- **Sliding window**: 10 calls
- **Failure threshold**: 50%
- **Open state duration**: 30 seconds
- **Fallback**: Throws `DirectoryValidationException` with service unavailable message

When the circuit breaker opens, validation calls fail fast with a clear error message instead of timing out.

## Data Model

### Incident Entity

| Field | Type | Description |
|-------|------|-------------|
| id | UUID | Primary key |
| referenceNumber | String | Unique reference (INC-YYYY-NNNNNN) |
| policyholderId | UUID | Reference to policyholder |
| insurerId | UUID | Reference to insurer |
| status | IncidentStatus | Current lifecycle status |
| type | String | Incident type (VEHICLE_ACCIDENT, etc.) |
| description | String | Detailed description |
| incidentDate | Instant | When incident occurred |
| estimatedDamage | BigDecimal | Estimated damage amount |
| currency | String | Currency code (default: EUR) |
| location | Location | JSONB embedded location data |
| comments | List<Comment> | Associated comments |
| expertAssignments | List<ExpertAssignment> | Assigned experts |
| events | List<IncidentEvent> | Event history |
| createdAt | Instant | Creation timestamp |
| updatedAt | Instant | Last update timestamp |

## Testing

**Run tests:**
```bash
mvn -f microservices/incident/pom.xml test
```

**Test coverage report:**
```bash
mvn -f microservices/incident/pom.xml verify
# Report at target/site/jacoco/index.html
```

## File Paths

Key source files in `src/main/java/com/ird0/incident/`:

- `IncidentApplication.java` - Main entry point
- `controller/IncidentController.java` - REST endpoints
- `service/IncidentService.java` - Business logic
- `service/DirectoryValidationService.java` - Directory validation with circuit breaker
- `service/NotificationClient.java` - Notification integration
- `model/Incident.java` - Main entity
- `model/IncidentStatus.java` - Status enum with transitions
- `dto/CreateIncidentRequest.java` - Request DTO
- `dto/IncidentResponse.java` - Response DTO
- `mapper/IncidentMapper.java` - MapStruct mapper
- `exception/IncidentNotFoundException.java` - Not found exception
- `exception/InvalidStateTransitionException.java` - Invalid transition exception

## Dependencies

Key dependencies:

- `spring-boot-starter-web` - REST API
- `spring-boot-starter-data-jpa` - Database access
- `spring-boot-starter-validation` - Request validation
- `resilience4j-spring-boot3` - Circuit breaker
- `hypersistence-utils-hibernate-63` - JSONB support
- `mapstruct` - DTO mapping
- `lombok` - Boilerplate reduction

## Troubleshooting

| Issue | Solution |
|-------|----------|
| Directory validation fails | Check directory services are running and URLs are correct |
| Circuit breaker open | Wait 30s or check directory service health |
| Invalid state transition | Check current status allows the requested transition |
| Notification not sent | Verify notification service is running and enabled |

# Portal BFF Service

This document provides detailed guidance for working with the Portal Backend-For-Frontend (BFF) Service.

## Overview

The Portal BFF is an aggregation layer that serves the Insurance Portal frontend. It combines data from multiple microservices (Incident, Directory) and transforms it into frontend-optimized responses.

**Key Features:**
- Aggregates data from Incident and Directory services
- Transforms microservice responses into frontend-friendly DTOs
- Resolves actor names (policyholder, insurer, expert) from IDs
- Circuit breaker pattern for resilient inter-service communication
- Simple in-memory caching for directory lookups
- CORS configuration for Angular development

## Architecture

### Components

**`PortalBffApplication.java`** - Main Spring Boot entry point

**`controller/DashboardController.java`** - Dashboard endpoint:
- `GET /api/portal/v1/dashboard` - KPIs and overview

**`controller/ClaimsController.java`** - Claims endpoints:
- `GET /api/portal/v1/claims` - Paginated list with filters and resolved names
- `POST /api/portal/v1/claims` - Create new claim
- `GET /api/portal/v1/claims/{id}` - Full claim detail
- `PUT /api/portal/v1/claims/{id}/status` - Update status
- `POST /api/portal/v1/claims/{id}/expert` - Assign expert
- `GET /api/portal/v1/claims/{id}/comments` - Get comments
- `POST /api/portal/v1/claims/{id}/comments` - Add comment
- `GET /api/portal/v1/claims/{id}/history` - Event history
- `GET /api/portal/v1/experts` - Available experts
- `GET /api/portal/v1/policyholders` - Policyholders list
- `GET /api/portal/v1/insurers` - Insurers list

**`service/ClaimsAggregationService.java`** - Aggregates claim data with resolved actors

**`service/DashboardService.java`** - Computes KPIs and dashboard statistics

**`client/IncidentClient.java`** - RestClient for Incident service

**`client/DirectoryClient.java`** - RestClient for Directory services (policyholders, experts, insurers)

### Data Flow

```
Frontend Request
       ↓
    [BFF Controller]
       ↓
    [Aggregation Service]
       ↓
    ┌─────┴─────┐
    ↓           ↓
[Incident     [Directory
 Client]       Client]
    ↓           ↓
Incident     Directory
Service      Services
```

## Configuration

### `configs/application.yml`

Common configuration:

```yaml
spring:
  application:
    name: portal-bff

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics

resilience4j:
  circuitbreaker:
    instances:
      incidentService:
        slidingWindowSize: 10
        failureRateThreshold: 50
        waitDurationInOpenState: 30s
      directoryService:
        slidingWindowSize: 10
        failureRateThreshold: 50
        waitDurationInOpenState: 30s
```

### `configs/portal-bff.yml`

Instance-specific configuration:

```yaml
server:
  port: ${SERVER_PORT:8090}

portal:
  api:
    base-path: /api/portal/v1
  services:
    incident-url: ${INCIDENT_SERVICE_URL:http://localhost:8085}
    policyholders-url: ${POLICYHOLDERS_SERVICE_URL:http://localhost:8081}
    experts-url: ${EXPERTS_SERVICE_URL:http://localhost:8082}
    insurers-url: ${INSURERS_SERVICE_URL:http://localhost:8084}
```

## Running Locally

**Prerequisites:**
- Incident service running (port 8085)
- Directory services running (policyholders 8081, experts 8082, insurers 8084)

**Run the service:**
```bash
cd microservices/portal-bff
mvn spring-boot:run \
  -Dspring-boot.run.arguments="--spring.config.location=file:configs/application.yml,file:configs/portal-bff.yml"
```

**With Docker:**
```bash
docker compose up portal-bff
```

## API Examples

### Dashboard

```bash
curl http://localhost:8090/api/portal/v1/dashboard
```

### List Claims

```bash
curl "http://localhost:8090/api/portal/v1/claims?page=0&size=20&status=DECLARED"
```

### Create Claim

```bash
curl -X POST http://localhost:8090/api/portal/v1/claims \
  -H "Content-Type: application/json" \
  -d '{
    "policyholderId": "550e8400-e29b-41d4-a716-446655440000",
    "insurerId": "550e8400-e29b-41d4-a716-446655440001",
    "type": "WATER_DAMAGE",
    "description": "Pipe burst in basement",
    "incidentDate": "2026-01-15T10:30:00Z",
    "estimatedDamage": 5000.00,
    "location": {
      "address": "123 Main St, Paris"
    }
  }'
```

### Get Claim Detail

```bash
curl http://localhost:8090/api/portal/v1/claims/{id}
```

### Update Status

```bash
curl -X PUT http://localhost:8090/api/portal/v1/claims/{id}/status \
  -H "Content-Type: application/json" \
  -d '{
    "status": "UNDER_REVIEW",
    "reason": "Starting review process"
  }'
```

### Assign Expert

```bash
curl -X POST http://localhost:8090/api/portal/v1/claims/{id}/expert \
  -H "Content-Type: application/json" \
  -d '{
    "expertId": "550e8400-e29b-41d4-a716-446655440002",
    "scheduledDate": "2026-01-20T09:00:00Z",
    "notes": "Water damage assessment"
  }'
```

## Response DTOs

### ClaimSummaryDTO (for list)

```json
{
  "id": "uuid",
  "referenceNumber": "INC-2026-000001",
  "status": "DECLARED",
  "type": "WATER_DAMAGE",
  "policyholderName": "John Doe",
  "insurerName": "Acme Insurance",
  "estimatedDamage": 5000.00,
  "currency": "EUR",
  "incidentDate": "2026-01-15T10:30:00Z",
  "createdAt": "2026-01-15T11:00:00Z"
}
```

### ClaimDetailDTO (for detail)

```json
{
  "id": "uuid",
  "referenceNumber": "INC-2026-000001",
  "status": "DECLARED",
  "availableTransitions": ["UNDER_REVIEW"],
  "type": "WATER_DAMAGE",
  "description": "Pipe burst in basement",
  "policyholder": {
    "id": "uuid",
    "name": "John Doe",
    "email": "john@example.com",
    "phone": "555-1234"
  },
  "insurer": {
    "id": "uuid",
    "name": "Acme Insurance"
  },
  "expertAssignments": [],
  "comments": [],
  "history": []
}
```

## Circuit Breaker

The service uses Resilience4j circuit breaker for inter-service calls:

- **Sliding window**: 10 calls
- **Failure threshold**: 50%
- **Open state duration**: 30 seconds

**Fallback behavior:**
- Incident service: Throws `ServiceUnavailableException`
- Directory service: Returns placeholder actor with "Unknown" name

## File Paths

Key source files in `src/main/java/com/ird0/portal/`:

- `PortalBffApplication.java` - Main entry point
- `config/PortalProperties.java` - Configuration properties
- `config/RestClientConfig.java` - RestClient configuration
- `config/WebConfig.java` - CORS configuration
- `controller/DashboardController.java` - Dashboard endpoint
- `controller/ClaimsController.java` - Claims endpoints
- `service/DashboardService.java` - Dashboard logic
- `service/ClaimsAggregationService.java` - Claims aggregation
- `client/IncidentClient.java` - Incident service client
- `client/DirectoryClient.java` - Directory services client
- `dto/request/*.java` - Request DTOs
- `dto/response/*.java` - Response DTOs
- `exception/GlobalExceptionHandler.java` - Error handling

## Dependencies

Key dependencies:

- `spring-boot-starter-web` - REST API
- `spring-boot-starter-validation` - Request validation
- `spring-data-commons` - Page/Pageable support
- `resilience4j-spring-boot3` - Circuit breaker
- `spring-boot-starter-actuator` - Health monitoring

## Troubleshooting

| Issue | Solution |
|-------|----------|
| Service unavailable | Check backend services are running |
| CORS errors | Verify frontend origin in WebConfig |
| Circuit breaker open | Wait 30s or check backend service health |
| Empty actor names | Directory service may be down (fallback returns "Unknown") |

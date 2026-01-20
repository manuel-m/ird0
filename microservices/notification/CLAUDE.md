# Notification Service

This document provides detailed guidance for working with the Notification Service microservice.

## Overview

The Notification Service manages webhook-based notifications for the insurance platform. It receives notification requests, stores them for audit purposes, and dispatches webhooks to configured endpoints with retry logic.

**Key Features:**
- Webhook dispatch to external endpoints
- Notification persistence for audit trail
- Retry logic with exponential backoff
- Status tracking (pending, sent, failed, cancelled)
- Incident-linked notifications
- Configurable timeouts and retry policies

## Architecture

### Components

**`NotificationApplication.java`** - Main Spring Boot entry point

**`controller/NotificationController.java`** - REST API controller with endpoints:
- `POST /api/v1/notifications` - Create and optionally dispatch notification
- `GET /api/v1/notifications/{id}` - Get notification by ID
- `GET /api/v1/notifications/incident/{incidentId}` - Get notifications for incident
- `GET /api/v1/notifications/status/{status}` - Get notifications by status
- `POST /api/v1/notifications/{id}/retry` - Retry failed notification
- `DELETE /api/v1/notifications/{id}` - Cancel pending notification

**`service/NotificationService.java`** - Business logic for notification management

**`service/WebhookDispatcher.java`** - Handles HTTP webhook dispatch with retry

**`model/Notification.java`** - JPA entity storing notification details

**`model/NotificationStatus.java`** - Status enum (PENDING, SENT, FAILED, CANCELLED)

### Notification Flow

```
Incident Service                  Notification Service               External Webhook
      |                                    |                              |
      | POST /notifications                |                              |
      | {webhookUrl, payload}              |                              |
      |------------------------------------>                              |
      |                                    |                              |
      |  201 Created {id, status=PENDING}  |                              |
      |<------------------------------------                              |
      |                                    |                              |
      |                                    |  POST {payload}              |
      |                                    |----------------------------->|
      |                                    |                              |
      |                                    |  200 OK                      |
      |                                    |<-----------------------------|
      |                                    |                              |
      |                                    | Update status=SENT           |
```

## Configuration

### `configs/notification.yml`

```yaml
server:
  port: ${SERVER_PORT:8086}

notification:
  api:
    base-path: /api/v1/notifications
  webhook:
    connect-timeout: ${WEBHOOK_CONNECT_TIMEOUT:5000}
    read-timeout: ${WEBHOOK_READ_TIMEOUT:10000}
    max-retries: ${WEBHOOK_MAX_RETRIES:3}
    initial-retry-delay: ${WEBHOOK_INITIAL_RETRY_DELAY:1000}
    max-retry-delay: ${WEBHOOK_MAX_RETRY_DELAY:60000}
    retry-multiplier: ${WEBHOOK_RETRY_MULTIPLIER:2.0}

spring:
  datasource:
    url: jdbc:postgresql://${POSTGRES_HOST:localhost}:${POSTGRES_PORT:5432}/notifications_db
```

### Configuration Properties

| Property | Default | Description |
|----------|---------|-------------|
| `webhook.connect-timeout` | 5000ms | HTTP connection timeout |
| `webhook.read-timeout` | 10000ms | HTTP read timeout |
| `webhook.max-retries` | 3 | Maximum retry attempts |
| `webhook.initial-retry-delay` | 1000ms | Initial delay between retries |
| `webhook.max-retry-delay` | 60000ms | Maximum delay between retries |
| `webhook.retry-multiplier` | 2.0 | Exponential backoff multiplier |

## Running Locally

**Prerequisites:**
- PostgreSQL running with `notifications_db` database

**Run the service:**
```bash
cd microservices/notification
./mvnw spring-boot:run \
  -Dspring-boot.run.arguments="--spring.config.location=file:configs/application.yml,file:configs/notification.yml"
```

**With Docker:**
```bash
docker compose up notification-svc
```

## API Examples

### Create Notification

```bash
curl -X POST http://localhost:8086/api/v1/notifications \
  -H "Content-Type: application/json" \
  -d '{
    "webhookUrl": "https://example.com/webhook",
    "payload": {
      "eventType": "INCIDENT_DECLARED",
      "eventId": "550e8400-e29b-41d4-a716-446655440000",
      "incident": {
        "id": "550e8400-e29b-41d4-a716-446655440001",
        "referenceNumber": "INC-2026-000001",
        "status": "DECLARED"
      }
    }
  }'
```

### Get Notification

```bash
curl http://localhost:8086/api/v1/notifications/{id}
```

### Get Notifications for Incident

```bash
curl http://localhost:8086/api/v1/notifications/incident/{incidentId}
```

### Get Notifications by Status

```bash
curl http://localhost:8086/api/v1/notifications/status/PENDING
```

### Retry Failed Notification

```bash
curl -X POST http://localhost:8086/api/v1/notifications/{id}/retry
```

### Cancel Notification

```bash
curl -X DELETE http://localhost:8086/api/v1/notifications/{id}
```

## Data Model

### Notification Entity

| Field | Type | Description |
|-------|------|-------------|
| id | UUID | Primary key |
| webhookUrl | String | Target webhook URL |
| payload | Map<String, Object> | JSONB payload data |
| eventType | String | Type of event (INCIDENT_DECLARED, etc.) |
| eventId | UUID | Source event ID |
| incidentId | UUID | Related incident ID |
| status | NotificationStatus | Current status |
| retryCount | int | Number of retry attempts |
| maxRetries | int | Maximum retries allowed |
| nextRetryAt | Instant | Scheduled next retry time |
| lastAttemptAt | Instant | Last dispatch attempt time |
| responseStatus | Integer | HTTP response status code |
| responseBody | String | HTTP response body (truncated) |
| failureReason | String | Failure description |
| createdAt | Instant | Creation timestamp |
| updatedAt | Instant | Last update timestamp |

### Notification Status

| Status | Description |
|--------|-------------|
| PENDING | Awaiting dispatch or retry |
| SENT | Successfully delivered |
| FAILED | All retries exhausted |
| CANCELLED | Manually cancelled |

## Retry Logic

The webhook dispatcher implements exponential backoff:

1. **First attempt**: Immediate dispatch
2. **On failure**: Schedule retry after `initial-retry-delay` (1s)
3. **Subsequent retries**: Delay doubles each time (2s, 4s, 8s...)
4. **Maximum delay**: Capped at `max-retry-delay` (60s)
5. **Maximum retries**: After 3 failures, status becomes FAILED

```
Attempt 1: Immediate
Attempt 2: 1s delay
Attempt 3: 2s delay
Attempt 4: 4s delay (if max-retries > 3)
...
```

## Integration with Incident Service

The Incident Service calls Notification Service for these events:

- `INCIDENT_DECLARED` - New incident created
- `INCIDENT_QUALIFIED` - Incident qualified for processing
- `INCIDENT_ABANDONED` - Incident abandoned
- `EXPERT_ASSIGNED` - Expert assigned to incident

Each notification includes the full incident details in the payload.

## Testing

**Run tests:**
```bash
./mvnw -f microservices/notification/pom.xml test
```

**Test coverage report:**
```bash
./mvnw -f microservices/notification/pom.xml verify
# Report at target/site/jacoco/index.html
```

## File Paths

Key source files in `src/main/java/com/ird0/notification/`:

- `NotificationApplication.java` - Main entry point
- `controller/NotificationController.java` - REST endpoints
- `service/NotificationService.java` - Business logic
- `service/WebhookDispatcher.java` - HTTP dispatch with retry
- `model/Notification.java` - Main entity
- `model/NotificationStatus.java` - Status enum
- `dto/WebhookRequest.java` - Request DTO
- `config/NotificationProperties.java` - Configuration properties
- `config/RestTemplateConfig.java` - HTTP client configuration
- `exception/NotificationNotFoundException.java` - Not found exception

## Dependencies

Key dependencies:

- `spring-boot-starter-web` - REST API
- `spring-boot-starter-data-jpa` - Database access
- `spring-boot-starter-validation` - Request validation
- `hypersistence-utils-hibernate-63` - JSONB support
- `commons` - Shared exceptions and utilities
- `lombok` - Boilerplate reduction

## Troubleshooting

| Issue | Solution |
|-------|----------|
| Webhook fails | Check target URL is reachable, review response status/body |
| All retries exhausted | Status becomes FAILED, use retry endpoint after fixing issue |
| Notification not created | Verify request payload format and required fields |
| Timeout errors | Increase connect-timeout or read-timeout in configuration |

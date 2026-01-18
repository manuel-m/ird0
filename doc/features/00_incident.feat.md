# Incident

```
┌──────────────────────┬──────┬──────────────────┬──────────────────────────────────────────────┐
│       Service        │ Port │     Database     │                 Description                  │
├──────────────────────┼──────┼──────────────────┼──────────────────────────────────────────────┤
│ Insurer Directory    │ 8084 │ insurers_db      │ 4th directory instance with webhookUrl field │
├──────────────────────┼──────┼──────────────────┼──────────────────────────────────────────────┤
│ Incident Service     │ 8085 │ incidents_db     │ Incident lifecycle management                │
├──────────────────────┼──────┼──────────────────┼──────────────────────────────────────────────┤
│ Notification Service │ 8086 │ notifications_db │ Webhook dispatch with retry                  │
└──────────────────────┴──────┴──────────────────┴──────────────────────────────────────────────┘
```

### Incident Lifecycle

```
DECLARED --> UNDER_REVIEW --> QUALIFIED --> IN_PROGRESS --> CLOSED
|               |
v               v
ABANDONED       ABANDONED
```

### Test flow
```
# 1. Create an insurer with webhook
curl -X POST http://localhost:8084/api/insurers \
-H "Content-Type: application/json" \
-d '{"name":"Test Insurer","type":"insurer","email":"test@insurer.com","phone":"+33123456789","webhookUrl":"https://webhook.site/YOUR-ID"}'

# 2. Create a policyholder
curl -X POST http://localhost:8081/api/policyholders \
-H "Content-Type: application/json" \
-d '{"name":"John Doe","type":"individual","email":"john@example.com","phone":"+33987654321"}'

# 3. Declare an incident
curl -X POST http://localhost:8085/api/v1/incidents \
-H "Content-Type: application/json" \
-d '{"policyholderId":"<UUID>","insurerId":"<UUID>","type":"AUTO_ACCIDENT","description":"Test","incidentDate":"2026-01-14T14:30:00Z"}'
```
### Dev notes

Phase 1 - Insurer Directory:
- microservices/directory/configs/insurers.yml
- microservices/directory/src/main/java/com/ird0/directory/model/DirectoryEntry.java (added webhookUrl)
- docker-compose.yml (added insurers service)

Phase 2 - Incident Service (microservices/incident/):
- Entity models: Incident.java, IncidentStatus.java, ExpertAssignment.java, Comment.java, IncidentEvent.java
- Repositories: IncidentRepository.java, IncidentEventRepository.java
- Service: IncidentService.java, ReferenceNumberGenerator.java
- Controller: IncidentController.java
- DTOs: CreateIncidentRequest.java, StatusUpdateRequest.java, IncidentResponse.java, etc.
- Exception handling: GlobalExceptionHandler.java, InvalidStateTransitionException.java

Phase 3 - Directory Validation:
- DirectoryValidationService.java - HTTP client for validating policyholders, insurers, experts
- NotificationClient.java - Sends notifications to Notification Service

Phase 4 - Notification Service (microservices/notification/):
- Model: Notification.java, NotificationStatus.java
- Repository: NotificationRepository.java
- Services: WebhookDispatcher.java (with exponential backoff retry), NotificationService.java
- Controller: NotificationController.java




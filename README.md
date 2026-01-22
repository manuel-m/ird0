# IRD0

Insurance Request Directory - A microservices platform for insurance claim management.

## Overview

IRD0 is an insurance platform built with Spring Boot 3.5.0 and Java 21, featuring:

- **Directory Services** - Manage policyholders, experts, providers, and insurers
- **Incident Service** - Insurance claim lifecycle management with state machine
- **Notification Service** - Webhook dispatch with retry logic
- **Portal BFF** - Backend-for-frontend aggregation layer
- **SFTP Server** - Secure file transfer for data imports
- **Angular Frontend** - Claims dashboard and management UI

## Quick Start

**Prerequisites:** Java 21, Maven 3.9+, Docker, Docker Compose

**Run all services:**
```bash
docker compose up --build
```

**Verify services are running:**
```bash
# Directory services
curl http://localhost:8081/actuator/health  # Policyholders
curl http://localhost:8082/actuator/health  # Experts
curl http://localhost:8083/actuator/health  # Providers
curl http://localhost:8084/actuator/health  # Insurers

# Application services
curl http://localhost:8085/actuator/health  # Incident
curl http://localhost:8086/actuator/health  # Notification
curl http://localhost:8090/actuator/health  # Portal BFF
curl http://localhost:9090/actuator/health  # SFTP Server
```

**Test the APIs:**
```bash
# Create a policyholder
curl -X POST http://localhost:8081/api/policyholders \
  -H "Content-Type: application/json" \
  -d '{"name": "John Doe", "type": "individual", "phone": "555-1234", "email": "john@example.com"}'

# Create an incident
curl -X POST http://localhost:8085/api/v1/incidents \
  -H "Content-Type: application/json" \
  -d '{"policyholderId": "<uuid>", "insurerId": "<uuid>", "type": "WATER_DAMAGE", "incidentDate": "2026-01-20T08:00:00Z"}'
```

## Service Ports

| Service | Port | Description |
|---------|------|-------------|
| Policyholders | 8081 | Policyholder directory |
| Experts | 8082 | Expert directory |
| Providers | 8083 | Provider directory |
| Insurers | 8084 | Insurer directory |
| Incident | 8085 | Claim management |
| Notification | 8086 | Webhook dispatch |
| Portal BFF | 8090 | Frontend API |
| SFTP | 2222 | File transfer |
| PostgreSQL | 5432 | Database |

## Documentation

| Document | Description |
|----------|-------------|
| [doc/INDEX.md](doc/INDEX.md) | Documentation index |
| [doc/PRD.md](doc/PRD.md) | Product requirements |
| [doc/ARCHITECTURE.md](doc/ARCHITECTURE.md) | System architecture |
| [doc/USER_GUIDE.md](doc/USER_GUIDE.md) | Operations manual |
| [doc/features/](doc/features/) | Feature documentation |

## Development

**Build all modules:**
```bash
./mvnw clean package
```

**Format code:**
```bash
./mvnw spotless:apply
```

**Run specific service:**
```bash
docker compose up policyholders-svc
docker compose up incident-svc
```



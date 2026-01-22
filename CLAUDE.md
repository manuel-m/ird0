# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is an insurance platform microservices architecture built with Spring Boot 3.5.0 and Java 21. The project includes:

1. **Directory Service** - Multi-instance REST API for managing policyholders, experts, providers, insurers (PostgreSQL)
2. **Incident Service** - Insurance claim lifecycle management with state machine
3. **Notification Service** - Webhook dispatch with exponential backoff retry
4. **Portal BFF** - Backend-for-frontend aggregation for the Angular dashboard
5. **SFTP Server** - Secure file transfer service for CSV data import
6. **Data Generator Utility** - CLI tool for generating realistic test data

## Key Documentation

| Document | Purpose |
|----------|---------|
| [doc/PRD.md](doc/PRD.md) | Product requirements, feature inventory, functional requirements |
| [doc/ARCHITECTURE.md](doc/ARCHITECTURE.md) | System design, multi-instance pattern, database architecture |
| [doc/features/](doc/features/) | Detailed feature documentation |
| [doc/topics/](doc/topics/) | Technical deep-dives (database, docker, configuration, etc.) |
| [doc/INDEX.md](doc/INDEX.md) | Complete documentation index |

## Architecture Overview

The project uses a multi-instance microservice pattern where the Directory Service codebase is deployed four times with different configurations. See [doc/ARCHITECTURE.md](doc/ARCHITECTURE.md) for details.

**Services:**

| Service | Port | API Base Path | Database |
|---------|------|---------------|----------|
| Policyholders | 8081 | `/api/policyholders` | `policyholders_db` |
| Experts | 8082 | `/api/experts` | `experts_db` |
| Providers | 8083 | `/api/providers` | `providers_db` |
| Insurers | 8084 | `/api/insurers` | `insurers_db` |
| Incident | 8085 | `/api/v1/incidents` | `incidents_db` |
| Notification | 8086 | `/api/v1/notifications` | `notifications_db` |
| Portal BFF | 8090 | `/api/portal/v1` | - |
| SFTP Server | 2222 | - | - |

## Technology Stack

- Java 21
- Spring Boot 3.5.0
- Spring Data JPA with Hibernate
- MapStruct 1.5.5 (DTO mapping)
- Resilience4j (circuit breaker)
- Apache MINA SSHD 2.12.0 (SFTP server)
- PostgreSQL 16 with UUID primary keys
- Angular 20 (frontend)
- Docker multi-stage builds

## Project Structure

```
ird0/
├── pom.xml                           # Root POM (parent)
├── docker-compose.yml                # Main orchestration
├── docker-compose.infrastructure.yml # Postgres, Vault
├── docker-compose.directory.yml      # Directory services
├── docker-compose.apps.yml           # App services
├── microservices/
│   ├── directory/                    # Multi-instance directory service
│   ├── incident/                     # Incident lifecycle management
│   ├── notification/                 # Webhook notifications
│   ├── portal/                       # BFF service
│   └── sftp-server/                  # SFTP server
├── frontend/
│   └── portal/                       # Angular dashboard
├── utilities/
│   └── directory-data-generator/     # Test data CLI
└── doc/
    ├── PRD.md                        # Product requirements
    ├── ARCHITECTURE.md               # System architecture
    ├── features/                     # Feature documentation
    └── topics/                       # Technical topics
```

## Module-Specific Documentation

Each microservice has its own CLAUDE.md file:

- **[Directory Service](microservices/directory/CLAUDE.md)** - Multi-instance setup, API testing
- **[Incident Service](microservices/incident/CLAUDE.md)** - State machine, lifecycle events
- **[Notification Service](microservices/notification/CLAUDE.md)** - Webhook dispatch, retry logic
- **[Portal BFF](microservices/portal/CLAUDE.md)** - Aggregation, name resolution
- **[SFTP Server](microservices/sftp-server/CLAUDE.md)** - SSH key setup, read-only access
- **[Data Generator](utilities/directory-data-generator/CLAUDE.md)** - Test data generation

## Common Development Commands

### Build All Modules

```bash
./mvnw clean package           # Build all
./mvnw clean package -DskipTests  # Skip tests
```

### Code Formatting with Spotless

```bash
./mvnw spotless:check    # Check formatting
./mvnw spotless:apply    # Auto-format code
```

**Configuration:** Google Java Format (style: GOOGLE), removes unused imports, trims trailing whitespace.

### Docker Operations

```bash
docker compose up --build     # Build and run all
docker compose up -d          # Run detached
docker compose down           # Stop all
docker compose up policyholders-svc  # Run specific service
docker compose up incident-svc
```

### SonarQube (On-Demand)

```bash
make sonar-start    # Start SonarQube
make sonar          # Run analysis
make sonar-stop     # Stop SonarQube
```

Access: http://localhost:9000 (admin/admin)

### Running Locally

Each service can run locally without Docker:

```bash
# Directory service (from microservices/directory/)
./mvnw spring-boot:run -Dspring-boot.run.arguments="--spring.config.location=configs/policyholders.yml"

# Incident service (from microservices/incident/)
./mvnw spring-boot:run
```

## Configuration

Configuration uses a layered approach. See [doc/topics/configuration.md](doc/topics/configuration.md) for details:

1. **Java defaults** - Local development values
2. **YAML files** - Per-instance configuration with `${VAR:default}` syntax
3. **.env file** - Docker deployment values
4. **docker-compose** - Constructs URLs from components (DRY principle)

## Database

PostgreSQL 16 with six databases in a single container. See [doc/topics/database.md](doc/topics/database.md):

- Databases: `policyholders_db`, `experts_db`, `providers_db`, `insurers_db`, `incidents_db`, `notifications_db`
- User: `directory_user` / Password: `directory_pass`
- Volume: `postgres-data`

## Testing APIs

```bash
# Create policyholder
curl -X POST http://localhost:8081/api/policyholders \
  -H "Content-Type: application/json" \
  -d '{"name":"John Doe","type":"individual","email":"john@example.com","phone":"555-1234"}'

# Create incident
curl -X POST http://localhost:8085/api/v1/incidents \
  -H "Content-Type: application/json" \
  -d '{"policyholderId":"<uuid>","insurerId":"<uuid>","type":"WATER_DAMAGE","incidentDate":"2026-01-20T08:00:00Z"}'

# Health checks
curl http://localhost:8081/actuator/health
curl http://localhost:8085/actuator/health
```

## Key Patterns

- **Multi-instance microservice**: Single codebase, multiple deployments with different configs
- **State machine**: Incident lifecycle with enforced transitions (DECLARED → QUALIFIED → IN_PROGRESS → RESOLVED)
- **Circuit breaker**: Resilience4j for inter-service calls
- **Exponential backoff**: Notification retry with 1s, 2s, 4s delays
- **Two-level change detection**: SFTP import with file-level and row-level detection
- **DTO mapping**: MapStruct for type-safe entity-to-DTO conversion
- **UUID primary keys**: Global uniqueness, no coordination needed

## Related Documentation

- [doc/PRD.md](doc/PRD.md) - Full product requirements
- [doc/features/incident-lifecycle.md](doc/features/incident-lifecycle.md) - State machine details
- [doc/features/webhook-notifications.md](doc/features/webhook-notifications.md) - Notification dispatch
- [doc/features/sftp-data-import.md](doc/features/sftp-data-import.md) - SFTP import flow
- [doc/topics/docker.md](doc/topics/docker.md) - Docker multi-stage builds

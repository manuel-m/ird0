# Directory Service

This document provides detailed guidance for working with the Directory Service microservice.

## Overview

The Directory Service is a multi-instance REST API for managing directory entries (policyholders, experts, and providers). It uses Spring Boot 3.5.0, Spring Data JPA with Hibernate, and PostgreSQL for data persistence.

**Key Features:**
- Multi-instance deployment (same codebase, different configurations)
- RESTful CRUD operations
- PostgreSQL database (separate database per instance)
- Spring Boot Actuator for health monitoring
- Configuration-driven API paths and database locations

## Multi-Instance Microservice Pattern

This service demonstrates a unique architecture where a single microservice is deployed **three times** with different configurations:

| Instance | Port | API Path | Database |
|----------|------|----------|----------|
| Policyholders | 8081 | `/api/policyholders` | `policyholders_db` (PostgreSQL) |
| Experts | 8082 | `/api/experts` | `experts_db` (PostgreSQL) |
| Providers | 8083 | `/api/providers` | `providers_db` (PostgreSQL) |

Each instance:
- Runs the same application code (`DirectoryApplication.java`)
- Uses the same controller, service, and repository classes
- Loads different configuration files at startup
- Operates independently with its own database

This pattern eliminates code duplication while maintaining service isolation.

## Configuration Files

Configuration files are located in `configs/`:

### `application.yml` (Common Configuration)

Shared configuration applied to all instances:

```yaml
spring:
  application:
    name: directory-service

  datasource:
    driver-class-name: org.postgresql.Driver
    username: ${POSTGRES_USER:directory_user}
    password: ${POSTGRES_PASSWORD:directory_pass}

  jpa:
    hibernate:
      ddl-auto: update                                    # Auto-update schema
    show-sql: true                                         # Log SQL statements
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics                      # Actuator endpoints
```

**Key Settings:**
- `hibernate.ddl-auto: update` - Automatically creates/updates database schema
- `show-sql: true` - Logs all SQL queries for debugging
- PostgreSQL dialect from Hibernate core (no additional dependency required)
- Credentials from environment variables with defaults for local development
- Actuator endpoints enabled for monitoring

### Instance-Specific Configuration Files

Each instance has its own YAML file that overrides specific settings:

**`policyholders.yml`:**
```yaml
server:
  port: 8081

directory:
  api:
    base-path: /api/policyholders
  sftp-import:
    enabled: true
    host: ${SFTP_HOST:localhost}
    port: ${SFTP_PORT:2222}
    username: ${SFTP_USERNAME:policyholder-importer}
    private-key-path: ${SFTP_PRIVATE_KEY_PATH:./keys/sftp_client_key}
    remote-file-path: policyholders.csv
    connection-timeout: 10000

spring:
  datasource:
    url: jdbc:postgresql://${POSTGRES_HOST:localhost}:${POSTGRES_PORT:5432}/policyholders_db
```

**`experts.yml`:**
```yaml
server:
  port: 8082

directory:
  api:
    base-path: /api/experts

spring:
  datasource:
    url: jdbc:postgresql://${POSTGRES_HOST:localhost}:${POSTGRES_PORT:5432}/experts_db
```

**`providers.yml`:**
```yaml
server:
  port: 8083

directory:
  api:
    base-path: /api/providers

spring:
  datasource:
    url: jdbc:postgresql://${POSTGRES_HOST:localhost}:${POSTGRES_PORT:5432}/providers_db
```

**Configuration Loading:**
Instances load both files in order: `application.yml` (common) → instance-specific YAML (overrides).

**Environment Variables:**
- `POSTGRES_HOST`: Database host (default: `localhost` for local, `postgres` in Docker)
- `POSTGRES_PORT`: Database port (default: `5432`)
- `POSTGRES_USER`: Database username (default: `directory_user`)
- `POSTGRES_PASSWORD`: Database password (default: `directory_pass`)

## Building the Directory Service

**Build directory microservice only:**
```bash
mvn -f microservices/directory/pom.xml clean package
```

**Build from project root:**
```bash
mvn clean package
```

**Output:**
- `target/directory-1.0.0.jar` (~8KB) - Standard JAR with classes only (used as Maven dependency)
- `target/directory-1.0.0-exec.jar` (~65MB) - Executable Spring Boot JAR with all dependencies

**Note:** The standard JAR is used as a dependency by the `directory-data-generator` utility.

## Running Locally

Run a single instance locally for development and debugging:

**Policyholders service (port 8081):**
```bash
cd microservices/directory
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.config.location=file:configs/application.yml,file:configs/policyholders.yml"
```

**Experts service (port 8082):**
```bash
cd microservices/directory
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.config.location=file:configs/application.yml,file:configs/experts.yml"
```

**Providers service (port 8083):**
```bash
cd microservices/directory
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.config.location=file:configs/application.yml,file:configs/providers.yml"
```

**What Happens:**
- Service starts on the configured port
- Connects to PostgreSQL database (requires PostgreSQL to be running)
- Database schema is auto-created/updated based on JPA entities
- SQL logging is enabled (`show-sql: true`)
- Service is ready to accept HTTP requests

**To stop the service:**
Press `Ctrl+C`

### Running with Local PostgreSQL

**Option 1: Use Docker Compose PostgreSQL**
```bash
docker compose up postgres -d
```
Then run services locally (they will connect to Docker PostgreSQL on localhost:5432).

**Option 2: Local PostgreSQL Installation**

Create databases (one-time setup):
```bash
psql -U postgres -c "CREATE DATABASE policyholders_db;"
psql -U postgres -c "CREATE DATABASE experts_db;"
psql -U postgres -c "CREATE DATABASE providers_db;"
psql -U postgres -c "CREATE USER directory_user WITH PASSWORD 'directory_pass';"
psql -U postgres -c "GRANT ALL PRIVILEGES ON DATABASE policyholders_db TO directory_user;"
psql -U postgres -c "GRANT ALL PRIVILEGES ON DATABASE experts_db TO directory_user;"
psql -U postgres -c "GRANT ALL PRIVILEGES ON DATABASE providers_db TO directory_user;"
```

## Running with Docker

**Run specific instance:**
```bash
# From project root
docker compose up policyholders
docker compose up experts
docker compose up providers
```

**Run all instances:**
```bash
docker compose up policyholders experts providers
```

## Testing the API

### Basic CRUD Operations

Replace `8081` and `/api/policyholders` with the appropriate port and path for experts (8082, `/api/experts`) or providers (8083, `/api/providers`).

**List all entries (empty initially):**
```bash
curl http://localhost:8081/api/policyholders
```

**Create a new entry:**
```bash
curl -X POST http://localhost:8081/api/policyholders \
  -H "Content-Type: application/json" \
  -d '{"name":"John Doe","type":"individual","email":"john@example.com","phone":"555-1234"}'
```

**Get a specific entry:**
```bash
curl http://localhost:8081/api/policyholders/1
```

**Update an entry:**
```bash
curl -X PUT http://localhost:8081/api/policyholders/1 \
  -H "Content-Type: application/json" \
  -d '{"name":"John Doe","type":"individual","email":"john.doe@example.com","phone":"555-5678"}'
```

**Delete an entry:**
```bash
curl -X DELETE http://localhost:8081/api/policyholders/1
```

### DirectoryEntry Model

All endpoints accept/return the `DirectoryEntry` model:

```json
{
  "id": 1,
  "name": "John Doe",
  "type": "individual",
  "email": "john.doe@example.com",
  "phone": "555-1234",
  "address": "123 Main St, Springfield, IL",
  "additionalInfo": "Account since 1985-03-15"
}
```

**Fields:**
- `id` (Long) - Auto-generated primary key
- `name` (String) - Entry name
- `type` (String) - Entry type: `individual`, `family`, or `corporate`
- `email` (String) - Email address
- `phone` (String) - Phone number
- `address` (String, optional) - Physical address
- `additionalInfo` (String, optional) - Additional metadata

## Actuator Endpoints

Spring Boot Actuator provides health and metrics endpoints:

```bash
# Health check
curl http://localhost:8081/actuator/health

# Application info
curl http://localhost:8081/actuator/info

# Metrics
curl http://localhost:8081/actuator/metrics

# List all available endpoints
curl http://localhost:8081/actuator
```

Replace port `8081` with `8082` for experts or `8083` for providers.

## Running Tests

**Run tests for directory microservice:**
```bash
mvn -f microservices/directory/pom.xml test
```

**Run tests from project root:**
```bash
mvn test
```

## Key Implementation Notes

### Spring Boot Application Entry Point

`DirectoryApplication.java` is the main entry point with `@SpringBootApplication` annotation:

```java
@SpringBootApplication
public class DirectoryApplication {
    public static void main(String[] args) {
        SpringApplication.run(DirectoryApplication.class, args);
    }
}
```

This single application class is reused across all three service instances, with behavior controlled entirely by the injected YAML configuration.

### Configuration-Driven API Paths

The REST controller uses Spring's property placeholder to configure the base path dynamically:

```java
@RestController
@RequestMapping("${directory.api.base-path:/api/entries}")
public class DirectoryEntryController {
    // ...
}
```

The `directory.api.base-path` property is set in instance-specific YAML files:
- Policyholders: `/api/policyholders`
- Experts: `/api/experts`
- Providers: `/api/providers`

This allows the same controller code to serve different API paths based on configuration.

### PostgreSQL with Hibernate

Each service instance uses its own PostgreSQL database in a shared PostgreSQL container.

**Database Architecture:**
```
PostgreSQL Container (port 5432)
├── policyholders_db → Policyholders Service (port 8081)
├── experts_db → Experts Service (port 8082)
└── providers_db → Providers Service (port 8083)
```

**Key Features:**
- **Data Isolation**: Each database is completely isolated
- **Connection Pooling**: Each service maintains its own HikariCP connection pool
- **Schema Management**: Hibernate auto-creates/updates schema (`ddl-auto: update`)
- **Data Persistence**: Data persists in Docker volume `postgres-data`

**PostgreSQL Dialect:**
```yaml
spring.jpa.properties.hibernate.dialect: org.hibernate.dialect.PostgreSQLDialect
```
Included in Hibernate core (no additional dependencies required).

**Database Schema:**
```sql
CREATE TABLE directory_entry (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(50),
    email VARCHAR(255),
    phone VARCHAR(50),
    address TEXT,
    additional_info TEXT
);
```
Schema is automatically created by Hibernate based on the `DirectoryEntry` entity.

**SQL Logging:**
With `show-sql: true`, all SQL queries are logged to the console:
```
Hibernate: insert into directory_entry (address, additional_info, email, name, phone, type) values (?, ?, ?, ?, ?, ?) returning id
```

### Standard CRUD Operations

All services expose the same REST API structure:

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `{base-path}` | List all entries |
| GET | `{base-path}/{id}` | Get single entry by ID |
| POST | `{base-path}` | Create new entry |
| PUT | `{base-path}/{id}` | Update existing entry |
| DELETE | `{base-path}/{id}` | Delete entry by ID |

Implementation is handled by standard Spring Data JPA repository pattern:
- `DirectoryEntryController` - REST endpoints
- `DirectoryEntryService` - Business logic
- `DirectoryEntryRepository` - JPA repository interface

## File Paths

Key source files in `src/main/java/com/ird0/directory/`:

- `DirectoryApplication.java` - Main Spring Boot entry point
- `controller/DirectoryEntryController.java` - REST API controller
- `model/DirectoryEntry.java` - JPA entity model
- `repository/DirectoryEntryRepository.java` - Spring Data JPA repository
- `service/DirectoryEntryService.java` - Business logic layer

## Dependencies

Key dependencies (managed by parent POM):

- `spring-boot-starter-web` - REST API support
- `spring-boot-starter-data-jpa` - JPA/Hibernate support
- `spring-boot-starter-actuator` - Health and metrics
- `org.postgresql:postgresql` - PostgreSQL JDBC driver (runtime)
- `org.projectlombok:lombok` - Boilerplate reduction

## Docker Configuration

The service is deployed via Docker Compose with three instances:

```yaml
policyholders:
  build:
    context: .
    dockerfile: microservices/directory/Dockerfile
    args:
      APP_YML: policyholders.yml
  ports:
    - "8081:8080"
  volumes:
    - ./data:/app/data
```

The `APP_YML` build argument selects which instance-specific configuration file to embed in the Docker image.

## Troubleshooting

| Issue | Solution |
|-------|----------|
| Port already in use | Stop conflicting service or change port in instance-specific YAML |
| Cannot connect to database | Verify PostgreSQL running: `docker compose ps postgres` |
| Schema not created | Verify `ddl-auto: update` in application.yml |
| Wrong API path | Check `directory.api.base-path` in instance-specific YAML |
| Authentication failed | Check `POSTGRES_USER` and `POSTGRES_PASSWORD` environment variables |
| SQL not logging | Ensure `show-sql: true` in application.yml |
| Database does not exist | Check init script logs: `docker compose logs postgres` |
| Connection pool exhausted | Increase `hikari.maximum-pool-size` or reduce concurrent requests |

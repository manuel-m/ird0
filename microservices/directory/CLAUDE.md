# Directory Service

This document provides detailed guidance for working with the Directory Service microservice.

## Overview

The Directory Service is a multi-instance REST API for managing directory entries (policyholders, experts, and providers). It uses Spring Boot 3.5.0, Spring Data JPA with Hibernate, and SQLite for data persistence.

**Key Features:**
- Multi-instance deployment (same codebase, different configurations)
- RESTful CRUD operations
- SQLite database (separate database per instance)
- Spring Boot Actuator for health monitoring
- Configuration-driven API paths and database locations

## Multi-Instance Microservice Pattern

This service demonstrates a unique architecture where a single microservice is deployed **three times** with different configurations:

| Instance | Port | API Path | Database File |
|----------|------|----------|---------------|
| Policyholders | 8081 | `/api/policyholders` | `policyholders.sqlite` |
| Experts | 8082 | `/api/experts` | `experts.sqlite` |
| Providers | 8083 | `/api/providers` | `providers.sqlite` |

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

  jpa:
    hibernate:
      ddl-auto: update                                    # Auto-update schema
    show-sql: true                                         # Log SQL statements
    properties:
      hibernate:
        dialect: org.hibernate.community.dialect.SQLiteDialect  # SQLite dialect
        format_sql: true

  datasource:
    driver-class-name: org.sqlite.JDBC

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics                      # Actuator endpoints
```

**Key Settings:**
- `hibernate.ddl-auto: update` - Automatically creates/updates database schema
- `show-sql: true` - Logs all SQL queries for debugging
- SQLite dialect from `hibernate-community-dialects` dependency
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

spring:
  datasource:
    url: jdbc:sqlite:policyholders.sqlite
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
    url: jdbc:sqlite:experts.sqlite
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
    url: jdbc:sqlite:providers.sqlite
```

**Configuration Loading:**
Instances load both files in order: `application.yml` (common) → instance-specific YAML (overrides).

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
- SQLite database file is created in `microservices/directory/` folder (if it doesn't exist)
- Database schema is auto-created/updated based on JPA entities
- SQL logging is enabled (`show-sql: true`)
- Service is ready to accept HTTP requests

**To stop the service:**
Press `Ctrl+C`

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

### SQLite with Hibernate

Each service instance uses its own SQLite database file. The project uses the Hibernate community dialect for SQLite.

**Important:** The correct dialect class is:
```yaml
spring.jpa.properties.hibernate.dialect: org.hibernate.community.dialect.SQLiteDialect
```

This requires the `hibernate-community-dialects` dependency (already configured in pom.xml).

**Database Schema Management:**
- Schema is auto-created on first run (`ddl-auto: update`)
- Schema is automatically updated when entity changes are detected
- Database file is created in the working directory (`microservices/directory/`)

**SQL Logging:**
With `show-sql: true`, all SQL queries are logged to the console:
```
Hibernate: insert into directory_entry (address, additional_info, email, name, phone, type, id) values (?, ?, ?, ?, ?, ?, ?)
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
- `org.xerial:sqlite-jdbc` - SQLite JDBC driver
- `org.hibernate.orm:hibernate-community-dialects` - SQLite dialect
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
| Database locked | Ensure no other process is accessing the SQLite file |
| Schema not created | Verify `ddl-auto: update` in application.yml |
| Wrong API path | Check `directory.api.base-path` in instance-specific YAML |
| ClassNotFoundException for SQLite dialect | Verify `hibernate-community-dialects` dependency in pom.xml |
| SQL not logging | Ensure `show-sql: true` in application.yml |

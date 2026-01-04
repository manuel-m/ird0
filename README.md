# IRD0

IRD demo project

## Project Overview

This is an insurance platform microservices architecture built with Spring Boot 3.5.0 and Java 21. The project uses a single shared microservice codebase that deploys multiple instances with different configurations to create separate directory services for different entity types.

## Architecture

### Multi-Instance Microservice Pattern

The project uses a unique architecture where a single microservice (`microservices/directory/`) is deployed three times with different configurations:

- **Policyholders Service**: Port 8081, API path `/api/policyholders`, SQLite database `policyholders.sqlite`
- **Experts Service**: Port 8082, API path `/api/experts`, SQLite database `experts.sqlite`
- **Providers Service**: Port 8083, API path `/api/providers`, SQLite database `providers.sqlite`

Each instance is configured via YAML files in `microservices/directory/configs/`:
- `policyholders.yml`
- `experts.yml`
- `providers.yml`

The configuration files control:
- Server port (`server.port`)
- API base path (`directory.api.base-path`)
- SQLite database file path (`spring.datasource.url`)

### Technology Stack

- Java 21
- Spring Boot 3.5.0
- Spring Data JPA with Hibernate
- SQLite database (separate instance per service)
- Lombok for boilerplate reduction
- Maven for build management
- Docker multi-stage builds

### Project Structure

```
ird0/
├── pom.xml                                   # Root POM (parent)
├── docker-compose.yml                         # Multi-instance deployment
└── microservices/
    └── directory/
        ├── pom.xml                           # Directory microservice POM
        ├── Dockerfile                         # Multi-stage build
        ├── configs/                          # Instance-specific configs
        │   ├── policyholders.yml
        │   ├── experts.yml
        │   └── providers.yml
        └── src/main/java/com/ird0/directory/
            ├── DirectoryApplication.java          # Main Spring Boot entry point
            ├── controller/DirectoryEntryController.java
            ├── model/DirectoryEntry.java
            ├── repository/DirectoryEntryRepository.java
            └── service/DirectoryEntryService.java
```

## Common Development Commands

### Build

Build the entire project:
```bash
mvn clean package
```

Build directory microservice only:
```bash
mvn -f microservices/directory/pom.xml clean package
```

Build without tests:
```bash
mvn clean package -DskipTests
```

### Run Locally (Without Docker)

Run a single service instance locally for development and debugging:

**Policyholders service (port 8081):**
```bash
cd microservices/directory
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.config.location=file:configs/policyholders.yml"
```

**Experts service (port 8082):**
```bash
cd microservices/directory
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.config.location=file:configs/experts.yml"
```

**Providers service (port 8083):**
```bash
cd microservices/directory
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.config.location=file:configs/providers.yml"
```

The service will:
- Start on the port specified in the config file
- Create a SQLite database file in the microservices/directory folder
- Enable SQL logging (show-sql: true)
- Auto-create/update database schema

To stop the service, press `Ctrl+C`.

**Testing the API:**
```bash
# List all entries (should return empty array initially)
curl http://localhost:8081/api/policyholders

# Create a new entry
curl -X POST http://localhost:8081/api/policyholders \
  -H "Content-Type: application/json" \
  -d '{"name":"John Doe","type":"individual","email":"john@example.com","phone":"555-1234"}'

# Get specific entry
curl http://localhost:8081/api/policyholders/1

# Update entry
curl -X PUT http://localhost:8081/api/policyholders/1 \
  -H "Content-Type: application/json" \
  -d '{"name":"John Doe","type":"individual","email":"john.doe@example.com","phone":"555-5678"}'

# Delete entry
curl -X DELETE http://localhost:8081/api/policyholders/1
```

Replace port 8081 and path `/api/policyholders` with the appropriate values for experts (8082, `/api/experts`) or providers (8083, `/api/providers`).

### Run Tests

Run all tests:
```bash
mvn test
```

Run tests for directory microservice:
```bash
mvn -f microservices/directory/pom.xml test
```

### Code Formatting with Spotless

The project uses Spotless with Google Java Format for consistent code formatting. Spotless is configured in the parent POM's `pluginManagement` section, providing a centralized configuration for all modules.

**Check if code is properly formatted (from root):**
```bash
mvn spotless:check
```

**Check from a specific module:**
```bash
cd microservices/directory
mvn spotless:check
```

**Automatically format all code (from root):**
```bash
mvn spotless:apply
```

**Configuration (managed in parent POM):**
- Formatter: Google Java Format (style: GOOGLE)
- Version: 1.19.2
- Features enabled:
  - Remove unused imports
  - Trim trailing whitespace
  - End files with newline

**Best practices:**
- Run `mvn spotless:apply` before committing code
- CI/CD pipelines should run `mvn spotless:check` to enforce formatting
- Configuration is centralized in the root pom.xml, inherited by all modules

### Docker Operations

Build and run all services:
```bash
docker compose up --build
```

Run specific service:
```bash
docker compose up policyholders
docker compose up experts
docker compose up providers
```

Stop all services:
```bash
docker compose down
```

### Docker Build Details

The Dockerfile uses multi-stage builds with optimized layer caching:

**Build Stage:**
1. **Copy POM files only** - Creates a cacheable layer for dependency metadata
2. **Download dependencies** - `mvn dependency:resolve dependency:resolve-plugins`
   - This layer is cached and reused across all 3 service builds
   - Only invalidates when POM files change, not when source code changes
3. **Copy source code** - Changes here don't invalidate the dependency cache
4. **Build application** - Fast build since dependencies are already downloaded

**Runtime Stage:**
5. **Minimal Alpine-based JRE 21** - Small final image
6. **Config injection** - `APP_YML` build arg selects which config file to use

**Benefits of this approach:**
- Maven dependencies downloaded only once (first build or when POMs change)
- Subsequent builds reuse the cached dependency layer (~60-70 seconds saved per build)
- All 3 services share the same dependency cache
- Source code changes trigger fast rebuilds (only compilation, no downloads)

### Maven Build Configuration

**Parent POM Plugin Management:**

The root POM manages plugin versions and configurations centrally in `pluginManagement`:
```xml
<build>
    <pluginManagement>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <version>${spring.boot.version}</version>
            </plugin>

            <plugin>
                <groupId>com.diffplug.spotless</groupId>
                <artifactId>spotless-maven-plugin</artifactId>
                <version>2.43.0</version>
                <configuration>
                    <java>
                        <googleJavaFormat>
                            <version>1.19.2</version>
                            <style>GOOGLE</style>
                        </googleJavaFormat>
                        <removeUnusedImports />
                        <trimTrailingWhitespace />
                        <endWithNewline />
                    </java>
                </configuration>
            </plugin>
        </plugins>
    </pluginManagement>
</build>
```

This centralizes plugin versions and configurations, ensuring consistency across all modules and eliminating Maven warnings about missing plugin versions. Child modules can simply reference the plugins without repeating configuration.

**Spring Boot Maven Plugin:**

The Spring Boot Maven plugin is configured with the `repackage` execution goal:
```xml
<plugin>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-maven-plugin</artifactId>
    <executions>
        <execution>
            <goals>
                <goal>repackage</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

This configuration ensures that `mvn package` produces an executable "fat JAR" (~62MB) containing:
- Application classes
- All dependencies (Spring Boot, Hibernate, SQLite, etc.)
- Embedded Tomcat server
- Custom Spring Boot classloader

Without this configuration, Maven would only produce a standard JAR (~8KB) with compiled classes, which cannot run standalone.

## Key Implementation Notes

### Spring Boot Application Entry Point

The `DirectoryApplication.java` class is the main entry point with `@SpringBootApplication` annotation. This single application class is reused across all three service instances (policyholders, experts, providers), with behavior controlled entirely by the injected YAML configuration.

### Configuration-Driven API Paths

The REST controller uses Spring's property placeholder to configure the base path dynamically:

```java
@RequestMapping("${directory.api.base-path:/api/entries}")
```

This allows the same controller code to serve different API paths based on the YAML configuration.

### SQLite with Hibernate

Each service instance uses its own SQLite database file. The project uses the Hibernate community dialect for SQLite:

**Important:** The correct dialect class is:
```yaml
spring.jpa.properties.hibernate.dialect: org.hibernate.community.dialect.SQLiteDialect
```

This requires the `hibernate-community-dialects` dependency (already configured in pom.xml).

Database schema is auto-updated via `spring.jpa.hibernate.ddl-auto: update`.

### Standard CRUD Operations

All services expose the same REST API structure:
- `GET {base-path}` - List all entries
- `GET {base-path}/{id}` - Get single entry
- `POST {base-path}` - Create entry
- `PUT {base-path}/{id}` - Update entry
- `DELETE {base-path}/{id}` - Delete entry

The `DirectoryEntry` model is generic with fields: `id`, `name`, `type`, `email`, `phone`, `address`, `additionalInfo`.



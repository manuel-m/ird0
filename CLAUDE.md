# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is an insurance platform microservices architecture built with Spring Boot 3.5.0 and Java 21. The project includes:
1. **Directory Service** - Multi-instance REST API for managing policyholders, experts, and providers (PostgreSQL database)
2. **SFTP Server** - Secure file transfer service for exposing policyholder CSV files via SFTP protocol
3. **Data Generator Utility** - CLI tool for generating realistic test data

## Architecture Overview

### Microservices

#### Directory Service (Multi-Instance)
- **Ports**: 8081-8084 (host access), 8080 (internal in Docker)
- **Protocol**: REST API (HTTP)
- **Database**: PostgreSQL (separate database per instance in single PostgreSQL container)
- **Purpose**: CRUD operations for directory entries

##### Multi-Instance Microservice Pattern

The project uses a unique architecture where a single microservice (`microservices/directory/`) is deployed three times with different configurations:

- **Policyholders Service**: Port 8081 (host), 8080 (internal), API path `/api/policyholders`, PostgreSQL database `policyholders_db`
- **Experts Service**: Port 8082 (host), 8080 (internal), API path `/api/experts`, PostgreSQL database `experts_db`
- **Providers Service**: Port 8083 (host), 8080 (internal), API path `/api/providers`, PostgreSQL database `providers_db`
- **Insurers Service**: Port 8084 (host), 8080 (internal), API path `/api/insurers`, PostgreSQL database `insurers_db`

Each instance is configured via YAML files in `microservices/directory/configs/`. Common configuration (`application.yml`) is shared, while instance-specific files (`policyholders.yml`, `experts.yml`, `providers.yml`) provide overrides for port, database path, and API base path.

See [Directory Service Documentation](microservices/directory/CLAUDE.md) for detailed configuration and usage.

#### SFTP Server
- **Ports**: 2222 (SFTP), 9090 (actuator host), 8080 (actuator internal)
- **Protocol**: SFTP (SSH File Transfer Protocol)
- **Authentication**: SSH public key only
- **Access**: Read-only file system
- **Purpose**: Expose policyholder CSV files to external consumers

See [SFTP Server Documentation](microservices/sftp-server/CLAUDE.md) for SSH key setup, configuration, and usage.

#### Data Generator Utility
- **Type**: CLI tool
- **Purpose**: Generate realistic fake policyholder data for testing
- **Output**: CSV files compatible with Directory Service

See [Data Generator Documentation](utilities/directory-data-generator/CLAUDE.md) for usage and examples.

### Technology Stack

- Java 21
- Spring Boot 3.5.0
- Spring Data JPA with Hibernate (directory service)
- MapStruct 1.5.5 (DTO mapping in directory service)
- Apache MINA SSHD 2.12.0 (SFTP server)
- Spring Boot Actuator (health, metrics, info endpoints)
- PostgreSQL 16 database with UUID primary keys (directory service)
- Lombok for boilerplate reduction
- Maven for build management
- Docker multi-stage builds

### Project Structure

```
ird0/
├── pom.xml                                   # Root POM (parent)
├── docker-compose.yml                         # Multi-service deployment
├── microservices/
│   ├── directory/
│   │   ├── pom.xml                           # Directory microservice POM
│   │   ├── Dockerfile                         # Multi-stage build
│   │   ├── CLAUDE.md                          # Directory service documentation
│   │   ├── configs/                          # Configuration files
│   │   │   ├── application.yml                # Common shared configuration
│   │   │   ├── policyholders.yml              # Instance-specific overrides
│   │   │   ├── experts.yml                    # Instance-specific overrides
│   │   │   └── providers.yml                  # Instance-specific overrides
│   │   └── src/main/java/com/ird0/directory/
│   │       ├── DirectoryApplication.java          # Main Spring Boot entry point
│   │       ├── controller/DirectoryEntryController.java
│   │       ├── model/DirectoryEntry.java
│   │       ├── repository/DirectoryEntryRepository.java
│   │       └── service/DirectoryEntryService.java
│   └── sftp-server/
│       ├── pom.xml                           # SFTP server module POM
│       ├── Dockerfile                         # Multi-stage build
│       ├── CLAUDE.md                          # SFTP server documentation
│       ├── configs/                          # Configuration files
│       │   ├── application.yml                # Common configuration
│       │   └── sftp.yml                       # SFTP-specific configuration
│       └── src/main/java/com/ird0/sftp/
│           ├── SftpServerApplication.java         # Main Spring Boot entry point
│           ├── config/
│           │   ├── SftpProperties.java            # Configuration properties
│           │   └── SftpServerConfig.java          # Apache MINA SSHD setup
│           ├── auth/
│           │   └── PublicKeyAuthenticator.java    # SSH key authentication
│           ├── filesystem/
│           │   ├── ReadOnlyFileSystemFactory.java # File system factory
│           │   └── CsvVirtualFileSystemView.java  # Read-only wrapper
│           └── lifecycle/
│               └── SftpServerLifecycle.java       # Server lifecycle
└── utilities/
    └── directory-data-generator/             # Test data generator CLI
        ├── pom.xml                           # Data generator module POM
        ├── CLAUDE.md                          # Data generator documentation
        └── src/main/java/com/ird0/utilities/datagen/
            ├── DataGeneratorCLI.java          # CLI entry point
            └── PolicyholderDataGenerator.java # Data generation logic
```

### PostgreSQL Database

All Directory Service instances connect to a shared PostgreSQL 16 container with separate databases:

- **Container**: `postgres` (port 5432)
- **Version**: PostgreSQL 16 Alpine
- **Databases**:
  - `policyholders_db` - Policyholders service data
  - `experts_db` - Experts service data
  - `providers_db` - Providers service data
- **User**: `directory_user`
- **Password**: `directory_pass`
- **Persistence**: Data stored in named volume `postgres-data`

**Connection Details:**
- Host: `postgres` (Docker network) or `localhost` (local development)
- Port: `5432`
- JDBC URL format: `jdbc:postgresql://[host]:5432/[database_name]`

**Data Persistence:**
Database data persists in the `postgres-data` Docker volume, surviving container restarts and rebuilds.

**Health Checks:**
Directory services wait for PostgreSQL health check before starting, ensuring database availability.

## Configuration Management

The project uses a layered configuration approach that supports both local development and Docker deployment:

1. **Java defaults** - Suitable for local development (localhost:808X)
2. **YAML files** - Support environment variable overrides with `${VAR:default}` syntax
3. **.env file** - Single source of truth for Docker deployments (DRY: hostnames and port stored separately)
4. **docker-compose.yml** - Constructs URLs by combining `${SERVICE_HOST}:${SERVICE_INTERNAL_PORT}`

**DRY Principle:**
Service URLs are not stored as complete URLs in .env. Instead:
- Service hostnames are defined once (e.g., `POLICYHOLDERS_SERVICE_HOST=policyholders`)
- Internal port is defined once (`SERVICE_INTERNAL_PORT=8080`)
- docker-compose.yml constructs full URLs: `http://${POLICYHOLDERS_SERVICE_HOST}:${SERVICE_INTERNAL_PORT}`
- This ensures the port is never duplicated - change it once and it applies everywhere

### Port Configuration Strategy

The architecture uses different port schemes for local development vs Docker deployment:

**Docker deployment (inside containers):**
- All Spring Boot services run on port **8080** internally
- External access via host port mapping (8081, 8082, 8083, etc.)
- Service-to-service communication uses Docker DNS names: `http://servicename:8080`

**Local development (without Docker):**
- Services run on different ports (8081, 8082, 8083, etc.) to avoid conflicts
- No Docker required
- Service URLs use localhost: `http://localhost:8081`

### Environment Variables

All configuration is externalized via environment variables. See `.env.example` for complete reference.

**Key variables:**
- `SERVICE_INTERNAL_PORT` - Internal port for all Spring Boot services (8080 in Docker)
- `{SERVICE}_HOST_PORT` - External port mapping for each service
- `{SERVICE}_SERVICE_HOST` - Service hostnames for Docker DNS (e.g., `POLICYHOLDERS_SERVICE_HOST=policyholders`)
- URLs are constructed as: `http://${SERVICE_HOST}:${SERVICE_INTERNAL_PORT}` (DRY principle)
- `POSTGRES_HOST`, `POSTGRES_PORT`, `POSTGRES_USER`, `POSTGRES_PASSWORD` - Database configuration
- `VAULT_ENABLED`, `VAULT_ADDR`, `VAULT_DEV_TOKEN` - Vault configuration

### Port Reference

| Service | Local Dev Port | Docker Internal | Docker Host | Inter-Service URL |
|---------|---------------|-----------------|-------------|-------------------|
| Policyholders | 8081 | 8080 | 8081 | http://policyholders:8080 |
| Experts | 8082 | 8080 | 8082 | http://experts:8080 |
| Providers | 8083 | 8080 | 8083 | http://providers:8080 |
| Insurers | 8084 | 8080 | 8084 | http://insurers:8080 |
| Incident | 8085 | 8080 | 8085 | http://incident-service:8080 |
| Notification | 8086 | 8080 | 8086 | http://notification-service:8080 |
| SFTP | 2222 | 2222 | 2222 | http://sftp-server:2222 |
| SFTP Actuator | 9090 | 8080 | 9090 | http://sftp-server:8080 |
| PostgreSQL | 5432 | 5432 | 5432 | postgres:5432 |
| Vault | 8200 | 8200 | 8200 | http://vault:8200 |

## Module-Specific Documentation

Each microservice has its own detailed CLAUDE.md file:

- **[Directory Service](microservices/directory/CLAUDE.md)** - Configuration, multi-instance setup, running locally, API testing, implementation details
- **[SFTP Server](microservices/sftp-server/CLAUDE.md)** - SSH key setup, configuration, testing, security features, troubleshooting
- **[Data Generator](utilities/directory-data-generator/CLAUDE.md)** - Building, usage, data format, importing strategies

## Common Development Commands

### Build All Modules

Build the entire project:
```bash
mvn clean package
```

Build without tests:
```bash
mvn clean package -DskipTests
```

### Code Formatting with Spotless

The project uses Spotless with Google Java Format for consistent code formatting. Spotless is configured in the parent POM's `pluginManagement` section, providing a centralized configuration for all modules.

**Check if code is properly formatted (from root):**
```bash
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
docker compose up sftp-server
```

Stop all services:
```bash
docker compose down
```

## Cross-Cutting Concerns

### Docker Multi-Stage Build Strategy

All services use multi-stage Dockerfiles with optimized layer caching:

**Build Stage:**
1. **Copy POM files only** - Creates a cacheable layer for dependency metadata
2. **Download dependencies** - `mvn dependency:resolve dependency:resolve-plugins`
   - This layer is cached and reused across all service builds
   - Only invalidates when POM files change, not when source code changes
3. **Copy source code** - Changes here don't invalidate the dependency cache
4. **Build application** - Fast build since dependencies are already downloaded

**Runtime Stage:**
5. **Minimal Alpine-based JRE 21** - Small final image
6. **Config injection** - `APP_YML` build arg selects which config file to use

**Benefits:**
- Maven dependencies downloaded only once (first build or when POMs change)
- Subsequent builds reuse the cached dependency layer (~60-70 seconds saved per build)
- All services share the same dependency cache
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

The Spring Boot Maven plugin is configured with the `repackage` execution goal and a classifier:
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
    <configuration>
        <classifier>exec</classifier>
    </configuration>
</plugin>
```

This configuration produces **two JAR files**:
1. **`{module}-1.0.0.jar`** - Standard JAR with compiled classes only
   - Used as a Maven dependency by other modules
   - Contains only application code, not dependencies
2. **`{module}-1.0.0-exec.jar`** - Executable Spring Boot "fat JAR"
   - Contains all dependencies
   - Contains embedded server (if applicable)
   - Can be run standalone with `java -jar`

The `classifier` configuration is crucial for allowing modules to be used as dependencies while still producing executable JARs.

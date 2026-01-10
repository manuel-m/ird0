# Configuration Management

## Overview

The IRD0 system uses YAML-based layered configuration with Spring Boot's externalized configuration support. The multi-instance pattern relies on configuration inheritance and overrides to deploy the same codebase with different runtime behavior.

**Key Features:**
- YAML configuration files (human-readable, hierarchical)
- Layered configuration (common + instance-specific)
- Environment variable overrides
- @ConfigurationProperties pattern (type-safe binding)
- Docker APP_YML build arg for configuration selection

## Configuration Layering

### Three-Layer Configuration

```
Layer 1: application.yml (Common)
         ↓ (shared settings)
Layer 2: instance.yml (Instance-Specific)
         ↓ (overrides)
Layer 3: Environment Variables (Runtime)
         ↓ (final overrides)
Final Configuration
```

**Priority (highest to lowest):**
1. Command-line arguments
2. Environment variables
3. Instance-specific YAML (policyholders.yml, experts.yml, providers.yml)
4. Common YAML (application.yml)
5. Spring Boot defaults

### Directory Service Configuration

**Common Configuration:** `microservices/directory/configs/application.yml`

```yaml
spring:
  datasource:
    driver-class-name: org.postgresql.Driver
    username: ${POSTGRES_USER:directory_user}
    password: ${POSTGRES_PASSWORD:directory_pass}

  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      show-details: when-authorized

logging:
  level:
    com.ird0.directory: INFO
    org.springframework.integration: INFO
```

**Instance-Specific:** `microservices/directory/configs/policyholders.yml`

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
    polling:
      fixed-delay: 120000
      initial-delay: 5000
      batch-size: 500

spring:
  datasource:
    url: jdbc:postgresql://${POSTGRES_HOST:localhost}:${POSTGRES_PORT:5432}/policyholders_db
```

### SFTP Server Configuration

**Common:** `microservices/sftp-server/configs/application.yml`

```yaml
spring:
  application:
    name: sftp-server
  main:
    web-application-type: none

management:
  server:
    port: 9090
  endpoints:
    web:
      exposure:
        include: health,info,metrics

logging:
  level:
    org.apache.sshd: INFO
    com.ird0.sftp: DEBUG
```

**SFTP-Specific:** `microservices/sftp-server/configs/sftp.yml`

```yaml
sftp:
  server:
    port: 2222
    data-directory: ${SFTP_DATA_DIR:./data}
    host-key-path: ${SFTP_HOST_KEY_PATH:./keys/hostkey.pem}
    authorized-keys-path: ${SFTP_AUTHORIZED_KEYS_PATH:./keys/authorized_keys}
    max-sessions: 10
    session-timeout: 900000
```

## Spring Boot @ConfigurationProperties Pattern

### Type-Safe Configuration Binding

**Component:** `DirectoryProperties.java`

```java
@ConfigurationProperties(prefix = "directory")
@Data
public class DirectoryProperties {
    private Api api = new Api();
    private SftpImport sftpImport = new SftpImport();

    @Data
    public static class Api {
        private String basePath = "/api/entries";
    }

    @Data
    public static class SftpImport {
        private boolean enabled = false;
        private String host = "localhost";
        private int port = 2222;
        private String username;
        private String privateKeyPath;
        private Polling polling = new Polling();

        @Data
        public static class Polling {
            private long fixedDelay = 120000;
            private long initialDelay = 5000;
            private int batchSize = 500;
        }
    }
}
```

**Enabling:**
```java
@Configuration
@EnableConfigurationProperties(DirectoryProperties.class)
public class AppConfig {
}
```

**Usage:**
```java
@Service
public class SomeService {
    private final DirectoryProperties properties;

    public SomeService(DirectoryProperties properties) {
        this.properties = properties;
    }

    public void doSomething() {
        String basePath = properties.getApi().getBasePath();
        boolean importEnabled = properties.getSftpImport().isEnabled();
    }
}
```

**Benefits:**
- Type-safe (compile-time checking)
- IDE autocomplete support
- Default values in code
- Validation via Bean Validation (@NotNull, @Min, etc.)
- Nested configuration structures

### SFTP Server Properties

**Component:** `SftpProperties.java`

```java
@ConfigurationProperties(prefix = "sftp")
@Data
public class SftpProperties {
    private Server server = new Server();

    @Data
    public static class Server {
        private int port = 2222;
        private String dataDirectory = "./data";
        private String hostKeyPath = "./keys/hostkey.pem";
        private String authorizedKeysPath = "./keys/authorized_keys";
        private int maxSessions = 10;
        private int sessionTimeout = 900000;
    }
}
```

## Multi-Instance Configuration Strategy

### Same Codebase, Different Configs

**Goal:** Deploy single microservice three times with different behavior

**Implementation:**

**1. Common Configuration (application.yml):**
- Database driver (PostgreSQL)
- JPA/Hibernate settings
- Actuator endpoints
- Logging levels
- Connection pooling defaults

**2. Instance-Specific Overrides:**

| Property | Policyholders | Experts | Providers |
|----------|---------------|---------|-----------|
| `server.port` | 8081 | 8082 | 8083 |
| `directory.api.base-path` | /api/policyholders | /api/experts | /api/providers |
| `spring.datasource.url` | ...policyholders_db | ...experts_db | ...providers_db |
| `directory.sftp-import.enabled` | true | false | false |

**3. Configuration File Selection:**

Dockerfile injects configuration at build time:

```dockerfile
ARG APP_YML=application.yml
COPY microservices/directory/configs/${APP_YML} instance.yml
ENTRYPOINT ["java", "-jar", "app.jar", "--spring.config.location=file:/app/application.yml,file:/app/instance.yml"]
```

Docker Compose specifies which config:

```yaml
policyholders:
  build:
    args:
      APP_YML: policyholders.yml

experts:
  build:
    args:
      APP_YML: experts.yml
```

### Adding a New Instance Type

**Steps:**

1. Create new YAML file: `configs/new-instance.yml`
   ```yaml
   server:
     port: 8084

   directory:
     api:
       base-path: /api/new-instance

   spring:
     datasource:
       url: jdbc:postgresql://${POSTGRES_HOST:localhost}:${POSTGRES_PORT:5432}/new_instance_db
   ```

2. Add to docker-compose.yml:
   ```yaml
   new-instance:
     build:
       args:
         APP_YML: new-instance.yml
     ports:
       - "8084:8084"
   ```

3. Update PostgreSQL init script:
   ```yaml
   POSTGRES_MULTIPLE_DATABASES: policyholders_db,experts_db,providers_db,new_instance_db
   ```

4. Build and run:
   ```bash
   docker compose up --build new-instance
   ```

## Docker APP_YML Build Arg

### Configuration Injection at Build Time

**Purpose:** Select configuration file during Docker image build

**Dockerfile Implementation:**

```dockerfile
# Stage 2: Runtime
FROM eclipse-temurin:21-jdk-alpine

ARG APP_YML=application.yml    # Build argument with default
WORKDIR /app

# Copy common configuration
COPY microservices/directory/configs/application.yml application.yml

# Copy instance-specific configuration (selected by APP_YML)
COPY microservices/directory/configs/${APP_YML} instance.yml

# Load both configs in order
ENTRYPOINT ["java", "-jar", "app.jar", \
    "--spring.config.location=file:/app/application.yml,file:/app/instance.yml"]
```

**Docker Compose Usage:**

```yaml
services:
  policyholders:
    build:
      context: .
      dockerfile: microservices/directory/Dockerfile
      args:
        APP_YML: policyholders.yml    # Inject at build time
```

**Benefits:**
- Configuration baked into image (immutable)
- No runtime configuration selection needed
- Single Dockerfile for all instances
- Clear separation of concerns

## Environment Variable Syntax

### Spring Boot Property Placeholders

**Syntax:** `${ENV_VAR:default_value}`

**Examples:**

```yaml
spring:
  datasource:
    url: jdbc:postgresql://${POSTGRES_HOST:localhost}:${POSTGRES_PORT:5432}/policyholders_db
    username: ${POSTGRES_USER:directory_user}
    password: ${POSTGRES_PASSWORD:directory_pass}

directory:
  sftp-import:
    host: ${SFTP_HOST:localhost}
    port: ${SFTP_PORT:2222}
    private-key-path: ${SFTP_PRIVATE_KEY_PATH:./keys/sftp_client_key}
```

**Explanation:**
- `${POSTGRES_HOST:localhost}`: Use env var `POSTGRES_HOST` if set, otherwise `localhost`
- `${POSTGRES_PORT:5432}`: Use env var `POSTGRES_PORT` if set, otherwise `5432`

### Docker Compose Environment Variables

**Setting Environment Variables:**

```yaml
services:
  policyholders:
    environment:
      POSTGRES_HOST: postgres
      POSTGRES_PORT: 5432
      POSTGRES_USER: directory_user
      POSTGRES_PASSWORD: directory_pass
      SFTP_HOST: sftp-server
      SFTP_PORT: 2222
```

**Override Precedence:**

1. Docker Compose `environment:` (highest)
2. `.env` file in project root
3. Shell environment variables
4. YAML default values (lowest)

## Configuration Properties Reference

### Directory Service Properties

**Server:**
- `server.port`: HTTP server port (8081, 8082, 8083)

**API:**
- `directory.api.base-path`: REST API base path (/api/policyholders, /api/experts, /api/providers)

**Database:**
- `spring.datasource.url`: JDBC connection URL
- `spring.datasource.username`: Database username
- `spring.datasource.password`: Database password
- `spring.datasource.driver-class-name`: JDBC driver class

**JPA/Hibernate:**
- `spring.jpa.hibernate.ddl-auto`: Schema management (update, validate, none)
- `spring.jpa.show-sql`: Log SQL statements (true/false)
- `spring.jpa.properties.hibernate.dialect`: SQL dialect
- `spring.jpa.properties.hibernate.format_sql`: Format SQL logs (true/false)

**SFTP Import:**
- `directory.sftp-import.enabled`: Enable SFTP polling (true/false)
- `directory.sftp-import.host`: SFTP server hostname
- `directory.sftp-import.port`: SFTP server port
- `directory.sftp-import.username`: SFTP username
- `directory.sftp-import.private-key-path`: SSH private key file path
- `directory.sftp-import.connection-timeout`: Connection timeout (milliseconds)
- `directory.sftp-import.polling.fixed-delay`: Polling interval (milliseconds)
- `directory.sftp-import.polling.initial-delay`: Delay before first poll (milliseconds)
- `directory.sftp-import.polling.batch-size`: Rows per transaction

**Actuator:**
- `management.endpoints.web.exposure.include`: Exposed endpoints (health,info,metrics)
- `management.endpoint.health.show-details`: Health detail visibility (always, when-authorized, never)

**Logging:**
- `logging.level.<package>`: Log level (TRACE, DEBUG, INFO, WARN, ERROR)

### SFTP Server Properties

**Server:**
- `sftp.server.port`: SFTP server port (2222)
- `sftp.server.data-directory`: CSV files directory
- `sftp.server.host-key-path`: Server host key file path
- `sftp.server.authorized-keys-path`: Authorized public keys file path
- `sftp.server.max-sessions`: Max concurrent SFTP sessions
- `sftp.server.session-timeout`: Session timeout (milliseconds)

**Management:**
- `management.server.port`: Actuator port (9090)
- `management.endpoints.web.exposure.include`: Exposed endpoints

## Configuration Changes Workflow

### Modifying Shared Configuration

**File:** `microservices/directory/configs/application.yml`

**Example:** Change log level

```yaml
logging:
  level:
    com.ird0.directory: DEBUG    # Change from INFO to DEBUG
```

**Impact:** Affects all three directory service instances

**Deployment:**
```bash
docker compose build policyholders experts providers
docker compose up policyholders experts providers
```

### Modifying Instance Configuration

**File:** `microservices/directory/configs/policyholders.yml`

**Example:** Change polling interval

```yaml
directory:
  sftp-import:
    polling:
      fixed-delay: 300000    # Change from 120000 (2 min) to 300000 (5 min)
```

**Impact:** Only affects policyholders service

**Deployment:**
```bash
docker compose build policyholders
docker compose up policyholders
```

### Rebuild vs Restart Requirements

**Rebuild Required (configuration baked into image):**
- Changes to `application.yml` or instance YAML files
- Changes to APP_YML build arg
- Changes to Dockerfile

**Restart Sufficient (environment variables):**
- Changes to environment variables in docker-compose.yml
- Changes to database credentials
- Changes to external service URLs

**Example (restart only):**
```yaml
# Change database password in docker-compose.yml
policyholders:
  environment:
    POSTGRES_PASSWORD: new_password    # Changed
```

```bash
docker compose up policyholders    # No rebuild needed
```

## Best Practices

### Externalized Configuration

**Principle:** Configuration should be external to application code

**Benefits:**
- Same artifact deployed to all environments
- No code changes for environment differences
- Easier to audit configuration changes
- Supports twelve-factor app methodology

**Implementation:**
- YAML files for structure and defaults
- Environment variables for sensitive values
- Docker secrets for production credentials

### Secrets Management

**Development:**
```yaml
spring:
  datasource:
    password: directory_pass    # Plain text (dev only)
```

**Production:**
```yaml
spring:
  datasource:
    password: ${DB_PASSWORD}    # From secrets management
```

**Docker Secrets:**
```yaml
services:
  policyholders:
    secrets:
      - db_password
    environment:
      DB_PASSWORD_FILE: /run/secrets/db_password

secrets:
  db_password:
    external: true
```

### Configuration Validation

**@Validated + Bean Validation:**

```java
@ConfigurationProperties(prefix = "directory.sftp-import")
@Validated
@Data
public class SftpImportProperties {
    @NotBlank
    private String host;

    @Min(1)
    @Max(65535)
    private int port = 2222;

    @NotBlank
    private String username;

    @NotBlank
    private String privateKeyPath;
}
```

**Startup Validation:**
- Application fails to start if validation fails
- Clear error messages for configuration issues
- Prevents runtime errors from misconfiguration

## Related Topics

- [USER_GUIDE.md#configuration-management](../USER_GUIDE.md#configuration-management) - Operational procedures
- [docker.md](docker.md) - APP_YML build arg details
- [sftp-import.md](sftp-import.md) - SFTP import configuration
- [database.md](database.md) - Database configuration

## References

- [Spring Boot Externalized Configuration](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.external-config)
- [Spring Boot Configuration Properties](https://docs.spring.io/spring-boot/docs/current/reference/html/configuration-metadata.html)
- [Twelve-Factor App: Config](https://12factor.net/config)

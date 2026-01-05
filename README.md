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
- `application.yml` - Common configuration shared across all instances (JPA, SQLite dialect, Actuator)
- `policyholders.yml` - Instance-specific overrides (port 8081, database file, API path)
- `experts.yml` - Instance-specific overrides (port 8082, database file, API path)
- `providers.yml` - Instance-specific overrides (port 8083, database file, API path)

The instance-specific configuration files control:
- Server port (`server.port`)
- API base path (`directory.api.base-path`)
- SQLite database file path (`spring.datasource.url`)

Common configuration in `application.yml` includes:
- JPA/Hibernate settings (DDL auto-update, SQL logging, SQLite dialect)
- Spring Boot Actuator endpoints (health, info, metrics)

### Technology Stack

- Java 21
- Spring Boot 3.5.0
- Spring Data JPA with Hibernate
- Spring Boot Actuator (health, metrics, info endpoints)
- SQLite database (separate instance per service)
- Lombok for boilerplate reduction
- Maven for build management
- Docker multi-stage builds

### Project Structure

```
ird0/
├── pom.xml                                   # Root POM (parent)
├── docker-compose.yml                         # Multi-instance deployment
├── microservices/
│   └── directory/
│       ├── pom.xml                           # Directory microservice POM
│       ├── Dockerfile                         # Multi-stage build
│       ├── configs/                          # Configuration files
│       │   ├── application.yml                # Common shared configuration
│       │   ├── policyholders.yml              # Instance-specific overrides
│       │   ├── experts.yml                    # Instance-specific overrides
│       │   └── providers.yml                  # Instance-specific overrides
│       └── src/main/java/com/ird0/directory/
│           ├── DirectoryApplication.java          # Main Spring Boot entry point
│           ├── controller/DirectoryEntryController.java
│           ├── model/DirectoryEntry.java
│           ├── repository/DirectoryEntryRepository.java
│           └── service/DirectoryEntryService.java
└── utilities/
    └── directory-data-generator/             # Test data generator CLI
        ├── pom.xml                           # Data generator module POM
        └── src/main/java/com/ird0/utilities/datagen/
            ├── DataGeneratorCLI.java          # CLI entry point
            └── PolicyholderDataGenerator.java # Data generation logic
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

**Testing Actuator endpoints:**
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

Replace port 8081 with 8082 for experts or 8083 for providers.

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

## Test Data Generator Utility

The project includes a CLI utility (`utilities/directory-data-generator`) to generate realistic fake policyholder data for testing and development purposes.

### Overview

The directory-data-generator is a standalone Java CLI application that:
- Generates realistic fake data using the Datafaker library
- Uses the actual `DirectoryEntry` model from the directory service (no duplication)
- Outputs CSV format compatible with the directory service
- Supports configurable record count with sensible defaults

### Building the Data Generator

Build the utility along with the entire project:
```bash
mvn clean package
```

Or build only the data generator module:
```bash
mvn -f utilities/directory-data-generator/pom.xml clean package
```

**Output:** `utilities/directory-data-generator/target/directory-data-generator.jar` (69MB fat JAR)

### Using the Data Generator

**Basic usage (generates 100 records by default):**
```bash
java -jar utilities/directory-data-generator/target/directory-data-generator.jar
```

**Generate specific number of records:**
```bash
java -jar utilities/directory-data-generator/target/directory-data-generator.jar 500
```

**Custom output file:**
```bash
java -jar utilities/directory-data-generator/target/directory-data-generator.jar 200 -o custom-data.csv
```

**Show help:**
```bash
java -jar utilities/directory-data-generator/target/directory-data-generator.jar --help
```

### Generated Data Format

The utility generates CSV files with the following structure:
```csv
name,type,email,phone,address,additionalInfo
"John Doe",individual,john.doe@example.com,555-1234,"123 Main St, Springfield, IL",Account since 1985-03-15
"Smith Family",family,smith@example.com,555-5678,"456 Oak Ave, Portland, OR",Members: 4
"Acme Corporation",corporate,acme.corporation@example.com,555-9012,"789 Business Blvd, NY","Industry: Technology, Employees: 250"
```

**Field Details:**
- **name**: Type-specific realistic names (person, family, or company)
- **type**: Randomly selected from `individual`, `family`, `corporate`
- **email**: Generated based on the name
- **phone**: Realistic phone numbers
- **address**: Full street addresses
- **additionalInfo**: Type-specific metadata:
  - `individual`: Account creation date
  - `family`: Number of family members
  - `corporate`: Industry and employee count

**Note:** The CSV does not include the `id` field as it's auto-generated by the database.

### Importing Generated Data

The CSV can be imported using various methods:

**Option 1: Manual API calls (for small datasets)**
```bash
# Skip the header line and POST each record
tail -n +2 policyholders.csv | while IFS=, read -r name type email phone address additionalInfo; do
  curl -X POST http://localhost:8081/api/policyholders \
    -H "Content-Type: application/json" \
    -d "{\"name\":$name,\"type\":$type,\"email\":$email,\"phone\":$phone,\"address\":$address,\"additionalInfo\":$additionalInfo}"
done
```

**Option 2: Direct SQLite import**
```bash
sqlite3 microservices/directory/policyholders.sqlite <<EOF
.mode csv
.import policyholders.csv directory_entry
EOF
```

**Option 3: Create a bulk import endpoint** (recommended for large datasets)
Consider adding a POST endpoint that accepts CSV files for bulk import.

### Technical Details

**Architecture:**
- Plain Java CLI application (not Spring Boot)
- Uses Picocli for CLI argument parsing with built-in help
- Uses Apache Commons CSV for robust CSV writing (handles special characters)
- Depends on the `directory` module to use `DirectoryEntry` model directly

**Dependencies:**
- `net.datafaker:datafaker:2.1.0` - Fake data generation
- `org.apache.commons:commons-csv:1.10.0` - CSV writing
- `info.picocli:picocli:4.7.5` - CLI parsing
- `com.ird0:directory:1.0.0` - DirectoryEntry model

**Build Configuration:**
- Maven Shade Plugin creates an executable fat JAR with all dependencies
- Final JAR includes Spring Boot libraries (needed for DirectoryEntry model)
- JAR size: ~69MB (due to transitive dependencies from directory module)

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
1. **`directory-1.0.0.jar`** (~8KB) - Standard JAR with compiled classes only
   - Used as a Maven dependency by other modules (e.g., directory-data-generator)
   - Contains only application code, not dependencies
2. **`directory-1.0.0-exec.jar`** (~65MB) - Executable Spring Boot "fat JAR"
   - Contains all dependencies (Spring Boot, Hibernate, SQLite, etc.)
   - Contains embedded Tomcat server
   - Can be run standalone with `java -jar`

The `classifier` configuration is crucial for allowing the directory module to be used as a dependency while still producing an executable JAR.

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



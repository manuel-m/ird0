# IRD0

IRD demo project

## Project Overview

This is an insurance platform microservices architecture built with Spring Boot 3.5.0 and Java 21. The project includes:
1. **Directory Service** - Multi-instance REST API for managing policyholders, experts, and providers
2. **SFTP Server** - Secure file transfer service for exposing policyholder CSV files via SFTP protocol
3. **Data Generator Utility** - CLI tool for generating realistic test data

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

### Microservices

#### Directory Service (Multi-Instance)
- **Ports**: 8081 (policyholders), 8082 (experts), 8083 (providers)
- **Protocol**: REST API (HTTP)
- **Database**: SQLite (separate database per instance)
- **Purpose**: CRUD operations for directory entries

#### SFTP Server
- **Port**: 2222 (SFTP), 9090 (management/actuator)
- **Protocol**: SFTP (SSH File Transfer Protocol)
- **Authentication**: SSH public key only
- **Access**: Read-only file system
- **Purpose**: Expose policyholder CSV files to external consumers

### Technology Stack

- Java 21
- Spring Boot 3.5.0
- Spring Data JPA with Hibernate (directory service)
- Apache MINA SSHD 2.12.0 (SFTP server)
- Spring Boot Actuator (health, metrics, info endpoints)
- SQLite database (directory service only)
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

## SFTP Server

The project includes an SFTP server microservice (`microservices/sftp-server`) that exposes CSV files via SFTP protocol for external consumers.

### Overview

The SFTP server is a Spring Boot application that:
- Exposes files via SFTP protocol on port 2222
- Provides read-only access using SSH public key authentication
- Serves CSV files from a configured data directory (provided via volume mount)
- Includes health monitoring via Spring Boot Actuator on port 9090
- Uses Apache MINA SSHD 2.12.0 for embedded SFTP server functionality

### Building the SFTP Server

Build the entire project including SFTP server:
```bash
mvn clean package
```

Build SFTP server only:
```bash
mvn -f microservices/sftp-server/pom.xml clean package
```

**Output:**
- `microservices/sftp-server/target/sftp-server-1.0.0.jar` (~9KB - normal JAR)
- `microservices/sftp-server/target/sftp-server-1.0.0-exec.jar` (~35MB - executable JAR)

### Configuration

The SFTP server uses two configuration files:

**`configs/application.yml`** (common settings):
- Application name and logging levels
- Actuator endpoints configuration (health, info, metrics)
- Management server port (9090)
- Keep-alive setting to prevent application shutdown

**`configs/sftp.yml`** (SFTP-specific with environment variable support):
```yaml
sftp:
  server:
    port: 2222                                      # SFTP server port
    data-directory: ${SFTP_DATA_DIR:./data}         # Directory containing CSV files
    host-key-path: ${SFTP_HOST_KEY_PATH:./keys/hostkey.pem} # Auto-generated RSA host key
    authorized-keys-path: ${SFTP_AUTHORIZED_KEYS_PATH:./keys/authorized_keys} # SSH public keys file
    max-sessions: 10                                # Max concurrent connections
    session-timeout: 900000                         # 15 minutes
```

**Environment Variable Support:**
- `SFTP_DATA_DIR`: Defaults to `./data` for local development, set to `/app/data` in Docker
- `SFTP_HOST_KEY_PATH`: Defaults to `./keys/hostkey.pem` for local development, set to `/app/keys/hostkey.pem` in Docker
- `SFTP_AUTHORIZED_KEYS_PATH`: Defaults to `./keys/authorized_keys` for local development, set to `/app/keys/authorized_keys` in Docker

This unified configuration works for both local development (using defaults) and Docker (using environment variables).

### Setting Up SSH Keys

Before running the SFTP server, you need to configure SSH public key authentication using the `authorized_keys` file:

**Important:** Currently, only RSA keys are fully supported. Ed25519 keys fail with "No decoder available" error due to a limitation in Apache SSHD 2.12.0.

**Generate SSH key pair:**
```bash
# Generate RSA key (recommended, fully supported)
ssh-keygen -t rsa -b 2048 -f ~/.ssh/sftp_test_key -N ""

# The key will be generated without a comment, you'll add the username manually
```

**Create authorized_keys file:**
```bash
# Create keys directory if it doesn't exist
mkdir -p keys

# Format the public key with username in the third field
awk '{print $1" "$2" your-username"}' ~/.ssh/sftp_test_key.pub > keys/authorized_keys

# Or create manually by editing the file
# Format: <key-type> <key-data> <username>
```

**File format:**
The `authorized_keys` file format is: `<key-type> <key-data> <username>`

Each line contains three space-separated fields:
```
ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQ... policyholder-consumer
ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQDiff... data-analyst
ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQCano... backup-service
```

**Important notes:**
- The third field is the username (not user@host format)
- Comments start with `#`
- Empty lines are ignored
- Multiple users can be added, one per line

**Set proper permissions (recommended):**
```bash
chmod 600 keys/authorized_keys
```

See `keys/authorized_keys.example` for a documented example file.

### Running the SFTP Server

**Run with Docker Compose:**
```bash
# Prepare data directory
mkdir -p data keys

# Place CSV files in data directory
cp policyholders.csv data/

# Build and start SFTP server
docker compose up --build sftp-server

# Run in background
docker compose up -d sftp-server

# View logs
docker compose logs -f sftp-server
```

**Run locally (development):**
```bash
# Option 1: Run from project root using environment variables (recommended)
SFTP_DATA_DIR=./data \
SFTP_HOST_KEY_PATH=./keys/hostkey.pem \
SFTP_AUTHORIZED_KEYS_PATH=./keys/authorized_keys \
mvn -f microservices/sftp-server/pom.xml spring-boot:run \
  -Dspring-boot.run.arguments="--spring.config.location=file:microservices/sftp-server/configs/application.yml,file:microservices/sftp-server/configs/sftp.yml"

# Option 2: Run from microservices/sftp-server using relative paths to root
cd microservices/sftp-server
SFTP_DATA_DIR=../../data \
SFTP_HOST_KEY_PATH=../../keys/hostkey.pem \
SFTP_AUTHORIZED_KEYS_PATH=../../keys/authorized_keys \
mvn spring-boot:run \
  -Dspring-boot.run.arguments="--spring.config.location=file:configs/application.yml,file:configs/sftp.yml"
```

**Note:** Environment variables are required for local development because the application's working directory differs from where files are located.

### Testing the SFTP Server

**Connect via SFTP client:**
```bash
# Connect to SFTP server (replace 'your-username' with the username in authorized_keys)
sftp -P 2222 -i ~/.ssh/sftp_test_key your-username@localhost

# SFTP commands:
sftp> ls                           # List files
sftp> get policyholders.csv        # Download file
sftp> put test.txt                 # Should fail (read-only)
sftp> rm policyholders.csv         # Should fail (read-only)
sftp> quit                         # Disconnect
```

**Download with SCP:**
```bash
# Replace 'your-username' with the username from authorized_keys
scp -P 2222 -i ~/.ssh/sftp_test_key \
  your-username@localhost:policyholders.csv \
  ./downloaded.csv
```

**Note:** The username must match the third field in your `keys/authorized_keys` file.

**Check health:**
```bash
# Health check
curl http://localhost:9090/actuator/health

# Metrics
curl http://localhost:9090/actuator/metrics

# List all actuator endpoints
curl http://localhost:9090/actuator
```

### Architecture Details

**Authentication:**
- SSH public key authentication only (no passwords)
- Multiple users supported with different public keys
- Keys stored in `keys/authorized_keys` file (OpenSSH format)
- Currently supports RSA keys only (Ed25519 not supported in Apache SSHD 2.12.0)

**File System:**
- Read-only virtual file system prevents uploads, modifications, deletions
- Root directory configured via `data-directory` setting
- Normalized paths prevent directory traversal attacks

**Security Features:**
1. Public key authentication only
2. Read-only access enforced at file system level
3. Persistent RSA host key for server verification
4. Configurable session limits and timeouts
5. Path normalization prevents traversal attacks
6. Comprehensive logging of authentication attempts and file access
7. Authorized keys file can have restricted permissions (chmod 600)

**Technical Implementation:**
- Uses Apache MINA SSHD 2.12.0 for embedded SFTP server
- SSH public keys loaded from file at startup (no hot-reload)
- Manual parsing of authorized_keys file using `PublicKeyEntry` API
- Custom `FileSystemFactory` provides read-only file system per session
- `PublickeyAuthenticator` validates SSH keys against configured users
- Server lifecycle managed via `@PostConstruct` and `@PreDestroy`
- No web server (uses `spring-boot-starter`, not `spring-boot-starter-web`)

### Docker Configuration

The SFTP server is integrated into docker-compose.yml:

```yaml
sftp-server:
  build:
    context: .
    dockerfile: microservices/sftp-server/Dockerfile
    args:
      APP_YML: sftp.yml
  image: sftp-server
  ports:
    - "2222:2222"  # SFTP port
    - "9090:9090"  # Management/actuator port
  environment:
    - SFTP_DATA_DIR=/app/data
    - SFTP_HOST_KEY_PATH=/app/keys/hostkey.pem
    - SFTP_AUTHORIZED_KEYS_PATH=/app/keys/authorized_keys
  volumes:
    - ./data:/app/data:ro   # Read-only CSV files
    - ./keys:/app/keys       # Persistent host key and authorized_keys
```

**Environment Variables:**
- `SFTP_DATA_DIR=/app/data` - Override default data directory for Docker
- `SFTP_HOST_KEY_PATH=/app/keys/hostkey.pem` - Override default host key path for Docker
- `SFTP_AUTHORIZED_KEYS_PATH=/app/keys/authorized_keys` - Override default authorized keys file path for Docker

**Volume Mounts:**
- `./data:/app/data:ro` - Mounts data directory as read-only, containing CSV files
- `./keys:/app/keys` - Persistent storage for auto-generated RSA host key and authorized_keys file

### Troubleshooting

| Issue | Solution |
|-------|----------|
| Connection refused | Verify server started: `docker compose ps`, check logs with `docker compose logs sftp-server` |
| Authentication failed | Verify public key in `keys/authorized_keys` matches your SSH key. Ensure format is: `<key-type> <key-data> <username>` (three fields, no @ symbols). |
| Authorized keys file not found | Create `keys/authorized_keys` file with your SSH public key. See `keys/authorized_keys.example` for format. |
| "No decoder available for key type=ssh-ed25519" | Use RSA keys instead. Ed25519 keys are not supported in Apache SSHD 2.12.0. Generate with: `ssh-keygen -t rsa -b 2048 -f ~/.ssh/sftp_key -N ""` |
| "Invalid format" or "Missing username" | Ensure authorized_keys file format is correct: `ssh-rsa <key-data> username`. Username should be in the third field, not in `user@host` format. |
| "No valid authorized keys found" | Check that authorized_keys file has at least one valid entry. Ensure lines are not commented out and format is correct. |
| Server fails to start (local dev) | Ensure environment variables are set: `SFTP_DATA_DIR`, `SFTP_HOST_KEY_PATH`, `SFTP_AUTHORIZED_KEYS_PATH`. Paths must be relative to where you run the command from. |
| File not found | Check data volume mount: `docker compose exec sftp-server ls /app/data` |
| Permission denied (read) | Check file permissions in data directory |
| Permission denied (write) | Expected - server is read-only by design |
| Host key changed warning | Host key was regenerated. Remove old key from `~/.ssh/known_hosts` |

### Data Flow

```
External System → [Generate CSV] → data/policyholders.csv
                                           ↓
                                    SFTP Server (port 2222)
                                           ↓
                              External Consumers (SFTP clients)
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



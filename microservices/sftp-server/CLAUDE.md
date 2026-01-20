# SFTP Server

This document provides detailed guidance for working with the SFTP Server microservice.

## Overview

The SFTP server is a Spring Boot application that exposes CSV files via SFTP protocol for external consumers. It provides read-only access using SSH public key authentication.

**Key Features:**
- SFTP protocol on port 2222
- SSH public key authentication (RSA keys)
- Read-only file system
- Spring Boot Actuator for health monitoring (port 9090 host, 8080 internal in Docker)
- Apache MINA SSHD 2.12.0 for embedded SFTP server
- No web server (non-reactive Spring Boot application)

**Use Case:**
External systems can securely download policyholder CSV files via SFTP without write access, ensuring data integrity and controlled distribution.

## Architecture

### Components

The SFTP server consists of several key components:

**`SftpServerApplication.java`** - Main Spring Boot entry point
- Configures non-web application context
- Enables Spring Boot Actuator
- No HTTP server, only SFTP and management ports

**`config/SftpProperties.java`** - Configuration properties
- Binds YAML configuration to Java objects
- Supports environment variable overrides
- Validates configuration at startup

**`config/SftpServerConfig.java`** - Apache MINA SSHD setup
- Configures SSH server (port, host keys, authentication)
- Sets up file system factory
- Configures session limits and timeouts

**`auth/PublicKeyAuthenticator.java`** - SSH key authentication
- Loads public keys from `authorized_keys` file
- Validates SSH key pairs during connection
- Supports multiple users with different keys

**`filesystem/ReadOnlyFileSystemFactory.java`** - File system factory
- Creates read-only file system for each session
- Prevents write operations
- Normalizes paths to prevent traversal attacks

**`filesystem/CsvVirtualFileSystemView.java`** - Read-only wrapper
- Wraps physical file system with read-only restrictions
- Blocks uploads, modifications, and deletions
- Allows only reading and listing files

**`lifecycle/SftpServerLifecycle.java`** - Server lifecycle
- Starts SFTP server via `@PostConstruct`
- Stops SFTP server via `@PreDestroy`
- Ensures clean shutdown

## Configuration Files

Configuration files are located in `configs/`:

### `application.yml` (Common Configuration)

```yaml
spring:
  application:
    name: sftp-server
  main:
    web-application-type: none               # No web server

logging:
  level:
    org.apache.sshd: INFO
    com.ird0.sftp: DEBUG

management:
  server:
    port: 9090                               # Actuator management port
  endpoints:
    web:
      exposure:
        include: health,info,metrics         # Actuator endpoints
```

**Key Settings:**
- `web-application-type: none` - Disables embedded web server (Tomcat)
- Logging levels for SSHD and application
- Separate management port (9090) for Actuator

### `sftp.yml` (SFTP-Specific Configuration)

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
- `SFTP_HOST_KEY_PATH`: Defaults to `./keys/hostkey.pem`, set to `/app/keys/hostkey.pem` in Docker
- `SFTP_AUTHORIZED_KEYS_PATH`: Defaults to `./keys/authorized_keys`, set to `/app/keys/authorized_keys` in Docker

This unified configuration works for both local development (using defaults) and Docker (using environment variables).

## Setting Up SSH Keys

Before running the SFTP server, you need to configure SSH public key authentication using the `authorized_keys` file.

### Important Limitation

**Currently, only RSA keys are fully supported.** Ed25519 keys fail with "No decoder available" error due to a limitation in Apache SSHD 2.12.0.

### Generate SSH Key Pair

```bash
# Generate RSA key (recommended, fully supported)
ssh-keygen -t rsa -b 2048 -f ~/.ssh/sftp_test_key -N ""

# The key will be generated without a comment, you'll add the username manually
```

This creates:
- `~/.ssh/sftp_test_key` - Private key (keep secure, never commit)
- `~/.ssh/sftp_test_key.pub` - Public key (add to authorized_keys)

### Create authorized_keys File

```bash
# Create keys directory if it doesn't exist
mkdir -p keys

# Format the public key with username in the third field
awk '{print $1" "$2" your-username"}' ~/.ssh/sftp_test_key.pub > keys/authorized_keys

# Or create manually by editing the file
# Format: <key-type> <key-data> <username>
```

### File Format

The `authorized_keys` file format is: `<key-type> <key-data> <username>`

Each line contains three space-separated fields:

```
ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQ... policyholder-consumer
ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQDiff... data-analyst
ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQCano... backup-service
```

**Important notes:**
- The third field is the **username** (not user@host format)
- Comments start with `#`
- Empty lines are ignored
- Multiple users can be added, one per line

### Set Proper Permissions

```bash
chmod 600 keys/authorized_keys
```

### Example File

See `keys/authorized_keys.example` for a documented example file.

## Building the SFTP Server

**Build SFTP server only:**
```bash
./mvnw -f microservices/sftp-server/pom.xml clean package
```

**Build from project root:**
```bash
./mvnw clean package
```

**Output:**
- `target/sftp-server-1.0.0.jar` (~9KB) - Standard JAR with classes only
- `target/sftp-server-1.0.0-exec.jar` (~35MB) - Executable Spring Boot JAR with all dependencies

## Running Locally

Environment variables are required for local development because the application's working directory differs from where files are located.

**Option 1: Run from project root using environment variables (recommended):**
```bash
SFTP_DATA_DIR=./data \
SFTP_HOST_KEY_PATH=./keys/hostkey.pem \
SFTP_AUTHORIZED_KEYS_PATH=./keys/authorized_keys \
./mvnw -f microservices/sftp-server/pom.xml spring-boot:run \
  -Dspring-boot.run.arguments="--spring.config.location=file:microservices/sftp-server/configs/application.yml,file:microservices/sftp-server/configs/sftp.yml"
```

**Option 2: Run from microservices/sftp-server using relative paths to root:**
```bash
cd microservices/sftp-server

SFTP_DATA_DIR=../../data \
SFTP_HOST_KEY_PATH=../../keys/hostkey.pem \
SFTP_AUTHORIZED_KEYS_PATH=../../keys/authorized_keys \
./mvnw spring-boot:run \
  -Dspring-boot.run.arguments="--spring.config.location=file:configs/application.yml,file:configs/sftp.yml"
```

**What Happens:**
- SFTP server starts on port 2222
- Host key is auto-generated if it doesn't exist (`keys/hostkey.pem`)
- Authorized keys are loaded from `keys/authorized_keys`
- CSV files from `data/` directory are exposed via SFTP
- Actuator endpoints available on port 9090
- Application remains running (keep-alive)

**Note:** The Actuator port starts on 9090 by default for local development. In Docker, the `SERVER_PORT` environment variable overrides this to 8080 (mapped to host port 9090), ensuring uniform internal ports across all Spring Boot services.

**To stop the server:**
Press `Ctrl+C`

## Running with Docker

**Prepare directories:**
```bash
mkdir -p data keys

# Place CSV files in data directory
cp policyholders.csv data/
```

**Build and start SFTP server:**
```bash
docker compose up --build sftp-server
```

**Run in background:**
```bash
docker compose up -d sftp-server
```

**View logs:**
```bash
docker compose logs -f sftp-server
```

## Testing the SFTP Server

### Connect via SFTP Client

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

**Note:** The username must match the third field in your `keys/authorized_keys` file.

### Download with SCP

```bash
# Replace 'your-username' with the username from authorized_keys
scp -P 2222 -i ~/.ssh/sftp_test_key \
  your-username@localhost:policyholders.csv \
  ./downloaded.csv
```

### Check Health

```bash
# Health check
curl http://localhost:9090/actuator/health

# Metrics
curl http://localhost:9090/actuator/metrics

# List all actuator endpoints
curl http://localhost:9090/actuator
```

## Security Features

The SFTP server implements multiple security layers:

1. **Public key authentication only** - No password authentication, reducing brute-force risk
2. **Read-only access enforced at file system level** - Write operations blocked by virtual file system
3. **Persistent RSA host key** - Server identity verification across restarts
4. **Configurable session limits** - Max 10 concurrent sessions by default
5. **Session timeouts** - 15-minute timeout prevents abandoned sessions
6. **Path normalization** - Prevents directory traversal attacks
7. **Comprehensive logging** - All authentication attempts and file access logged
8. **Authorized keys file permissions** - Can be restricted with chmod 600

## Architecture Details

### Authentication Flow

1. Client connects to SFTP server on port 2222
2. Server presents host key for verification
3. Client provides public key for authentication
4. `PublicKeyAuthenticator` validates key against `authorized_keys`
5. If valid, username is extracted (third field in authorized_keys)
6. Session is created with read-only file system

### File System

- **Read-only virtual file system** prevents uploads, modifications, deletions
- **Root directory** configured via `data-directory` setting
- **Normalized paths** prevent traversal attacks (e.g., `../../etc/passwd`)
- Files and directories can be listed and read, but not modified

### Technical Implementation

- **Apache MINA SSHD 2.12.0** - Embedded SFTP server library
- **SSH public keys** loaded from file at startup (no hot-reload)
- **Manual parsing** of authorized_keys file using `PublicKeyEntry` API
- **Custom `FileSystemFactory`** provides read-only file system per session
- **`PublickeyAuthenticator`** validates SSH keys against configured users
- **Server lifecycle** managed via `@PostConstruct` and `@PreDestroy`
- **No web server** - Uses `spring-boot-starter`, not `spring-boot-starter-web`

## Docker Configuration

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

## Troubleshooting

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

## Data Flow

```
External System → [Generate CSV] → data/policyholders.csv
                                           ↓
                                    SFTP Server (port 2222)
                                           ↓
                              External Consumers (SFTP clients)
```

## File Paths

Key source files in `src/main/java/com/ird0/sftp/`:

- `SftpServerApplication.java` - Main Spring Boot entry point
- `config/SftpProperties.java` - Configuration properties
- `config/SftpServerConfig.java` - Apache MINA SSHD setup
- `auth/PublicKeyAuthenticator.java` - SSH key authentication
- `filesystem/ReadOnlyFileSystemFactory.java` - File system factory
- `filesystem/CsvVirtualFileSystemView.java` - Read-only wrapper
- `lifecycle/SftpServerLifecycle.java` - Server lifecycle

## Dependencies

Key dependencies (managed by parent POM):

- `spring-boot-starter` - Core Spring Boot (no web server)
- `spring-boot-starter-actuator` - Health and metrics
- `org.apache.sshd:sshd-core` - SFTP server core
- `org.apache.sshd:sshd-sftp` - SFTP protocol implementation
- `org.projectlombok:lombok` - Boilerplate reduction

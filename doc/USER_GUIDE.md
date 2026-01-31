# IRD0 User Guide - Operations Manual

This guide provides comprehensive operational procedures for administrators, DevOps engineers, and system operators managing the IRD0 insurance platform.

## Table of Contents

1. [System Overview](#1-system-overview)
2. [Prerequisites and Installation](#2-prerequisites-and-installation)
3. [Service Operations](#3-service-operations)
4. [Database Management](#4-database-management)
5. [SFTP Import Operations](#5-sftp-import-operations)
6. [SSH Authentication](#6-ssh-authentication)
   - [Vault SSH CA (Primary)](#61-vault-ssh-ca-primary)
   - [Static Key Fallback](#62-static-key-fallback)
7. [Keycloak Identity Management](#7-keycloak-identity-management)
8. [Configuration Management](#8-configuration-management)
9. [Monitoring and Health Checks](#9-monitoring-and-health-checks)
10. [Backup and Restore](#10-backup-and-restore)
11. [Troubleshooting](#11-troubleshooting)
12. [Security Hardening](#12-security-hardening)
13. [Maintenance Procedures](#13-maintenance-procedures)
14. [Appendices](#14-appendices)

---

## 1. System Overview

The IRD0 platform consists of multiple microservices deployed as Docker containers:

**Directory Services (Multi-Instance Pattern):**
- **Policyholders** (port 8081) - REST API for policyholder data with SFTP import
- **Experts** (port 8082) - REST API for expert data
- **Providers** (port 8083) - REST API for provider data
- **Insurers** (port 8084) - REST API for insurer data

**Application Services:**
- **Incident** (port 8085) - Insurance claim lifecycle management with state machine
- **Notification** (port 8086) - Webhook dispatch with exponential backoff retry
- **Portal BFF** (port 7777) - Backend-for-frontend aggregation for Angular dashboard
- **SFTP Server** (port 2222) - Secure file transfer for CSV data import

**Infrastructure Services:**
- **PostgreSQL** (port 5432) - Database for all services (7 databases)
- **Vault** (port 8200) - Secrets management and SSH CA (optional)
- **Keycloak** (port 8180) - Identity and access management (OAuth 2.0/OIDC)

**Key Directories:**
- `./data/` - SFTP server data files (CSV)
- `./temp/sftp-downloads/` - Local SFTP download directory
- `./data/sftp-metadata/` - Import timestamp metadata
- `./data/sftp-errors/` - Failed imports (retry queue)
- `./data/sftp-failed/` - Exhausted retries (dead letter queue)
- `postgres-data` volume - PostgreSQL persistent data

**Architecture:**
For detailed architecture, see [ARCHITECTURE.md](ARCHITECTURE.md)

---

## 2. Prerequisites and Installation

### System Requirements

- **Docker** 24+ and Docker Compose v2
- **RAM**: 4GB minimum (8GB recommended)
- **Disk Space**: 10GB for containers and data
- **Ports**: The following ports must be available:
  - 5432 (PostgreSQL)
  - 7777 (Portal BFF)
  - 8081-8086 (Directory and Application services)
  - 8180 (Keycloak)
  - 8200 (Vault)
  - 2222, 9090 (SFTP server and actuator)

### Initial Setup

**linux / wsl**

```bash
# Clone repository
git clone <repository-url>
cd ird0

# Create .env file with required variables
cp .env.example .env

# Build and start all services (includes Vault)
make

# Verify services
docker compose ps
```

**Note:** SSH authentication now uses ephemeral certificates from Vault SSH CA. No manual SSH key generation is required.

### Verification

Check all services are healthy:

```bash
# Directory Services
curl http://localhost:8081/actuator/health  # Policyholders
curl http://localhost:8082/actuator/health  # Experts
curl http://localhost:8083/actuator/health  # Providers
curl http://localhost:8084/actuator/health  # Insurers

# Application Services
curl http://localhost:8085/actuator/health  # Incident
curl http://localhost:8086/actuator/health  # Notification
curl http://localhost:7777/actuator/health  # Portal BFF
curl http://localhost:9090/actuator/health  # SFTP Server

# Infrastructure Services
docker compose exec postgres psql -U directory_user -d policyholders_db -c "SELECT 1"  # PostgreSQL
curl http://localhost:8200/v1/sys/health    # Vault
curl http://localhost:8180/health           # Keycloak
```

If `scripts/verify-services.sh` exists:
```bash
./scripts/verify-services.sh
```

---

## 3. Service Operations

> **For Docker architecture and containerization details, see [topics/docker.md](topics/docker.md)**

### Starting Services

The project uses a modular Docker Compose configuration split across multiple files for easier maintenance:
- `docker-compose.infrastructure.yml` - Core infrastructure (postgres, vault)
- `docker-compose.directory.yml` - Directory microservices (policyholders, experts, providers, insurers)
- `docker-compose.apps.yml` - Application services (incident, notification, sftp)
- `docker-compose.yml` - Main file that includes all service groups

**Start all services:**
```bash
# Start all services (uses main docker-compose.yml with includes)
docker compose up -d

# Start with rebuild
docker compose up --build -d
```

**Start specific service groups:**
```bash
# Infrastructure only (postgres + vault)
docker compose -f docker-compose.infrastructure.yml up -d

# Infrastructure + directory services
docker compose -f docker-compose.infrastructure.yml -f docker-compose.directory.yml up -d

# Infrastructure + directory + apps (same as docker compose up -d)
docker compose up -d
```

**Start specific service:**
```bash
# Start individual service
docker compose up -d policyholders-svc
docker compose up -d incident-svc
docker compose up -d sftp-server

# Watch logs
docker compose logs -f policyholders-svc
```

### Stopping Services

```bash
# Graceful shutdown (all services)
docker compose down

# Stop specific service
docker compose stop policyholders-svc

# Restart service
docker compose restart policyholders-svc
```

### Viewing Configuration

```bash
# View the merged configuration from all files
docker compose config

# List all services
docker compose config --services

# View specific service configuration
docker compose config policyholders-svc
```

### Service Dependencies

Docker Compose automatically resolves dependencies across the split files when using the main docker-compose.yml or when combining files with multiple `-f` flags.

**Startup Order:**
1. PostgreSQL and Vault containers start (infrastructure layer)
2. Directory services wait for PostgreSQL to be healthy
3. Policyholders service additionally waits for SFTP server and Vault
4. SFTP server waits for Vault to be healthy
5. Application services (incident, notification) wait for their dependencies

**Health Checks:**
- PostgreSQL: `pg_isready -U directory_user` every 10s, 5 retries, 5s timeout
- Directory services: Spring Boot Actuator `/actuator/health`
- SFTP server: Spring Boot Actuator on port 9090

### Viewing Logs

```bash
# All services
docker compose logs -f

# Specific service
docker compose logs -f policyholders

# Last 100 lines
docker compose logs --tail=100 policyholders

# Filter for errors
docker compose logs policyholders | grep ERROR

# Filter for import activity
docker compose logs policyholders | grep "Import completed"
```

---

## 4. Database Management

> **For PostgreSQL architecture and design decisions, see [topics/database.md](topics/database.md)**

### Connection Details

- **Host**: `postgres` (Docker network) / `localhost` (external)
- **Port**: 5432
- **User**: `directory_user`
- **Password**: `directory_pass` (change in production!)

**Databases:**
- `policyholders_db` - Policyholders service data
- `experts_db` - Experts service data
- `providers_db` - Providers service data

### Accessing PostgreSQL

**Via Docker:**
```bash
docker compose exec postgres psql -U directory_user -d policyholders_db
```

**Via local psql client:**
```bash
psql -h localhost -p 5432 -U directory_user -d policyholders_db
```

### Schema Management

Schemas are auto-created/updated by Hibernate (`ddl-auto: update`).

**Manual schema inspection:**
```sql
-- Connect to database
\c policyholders_db

-- List tables
\dt

-- Describe directory_entry table
\d directory_entry

-- Check constraints
\d+ directory_entry

-- Count records
SELECT COUNT(*) FROM directory_entry;
```

### Backup Procedures

**Single database backup:**
```bash
docker compose exec postgres pg_dump -U directory_user policyholders_db > backup-policyholders-$(date +%Y%m%d).sql
```

**All databases backup:**
```bash
docker compose exec postgres pg_dumpall -U postgres > backup-all-$(date +%Y%m%d).sql
```

**Volume backup:**
```bash
docker run --rm \
  -v ird0_postgres-data:/data \
  -v $(pwd)/backups:/backup \
  alpine tar czf /backup/postgres-data-$(date +%Y%m%d).tar.gz /data
```

### Restore Procedures

**Restore single database:**
```bash
# Option 1: Restore to existing database
cat backup-policyholders.sql | docker compose exec -T postgres psql -U directory_user -d policyholders_db

# Option 2: Drop and recreate first (DESTRUCTIVE)
docker compose exec postgres psql -U postgres -c "DROP DATABASE policyholders_db;"
docker compose exec postgres psql -U postgres -c "CREATE DATABASE policyholders_db OWNER directory_user;"
cat backup-policyholders.sql | docker compose exec -T postgres psql -U directory_user -d policyholders_db
```

**Restore volume:**
```bash
# Stop services first
docker compose down

# Restore volume
docker run --rm \
  -v ird0_postgres-data:/data \
  -v $(pwd)/backups:/backup \
  alpine sh -c "cd / && tar xzf /backup/postgres-data-YYYYMMDD.tar.gz"

# Start services
docker compose up -d
```

### Data Persistence

Database data persists in Docker volume `ird0_postgres-data`:

```bash
# List volumes
docker volume ls | grep postgres-data

# Inspect volume
docker volume inspect ird0_postgres-data

# Remove volume (DESTRUCTIVE - all data lost!)
docker compose down -v
```

---

## 5. SFTP Import Operations

### How SFTP Import Works

**Policyholders service only** has SFTP polling enabled.

**Process:**
1. Polls SFTP server every 2 minutes
2. Downloads `*.csv` files to `./temp/sftp-downloads`
3. Checks file timestamp against metadata store
4. If unchanged: Skips processing and deletes local file
5. If changed: Parses CSV and imports to database with change detection
6. Stores file timestamp in metadata store
7. Deletes local file after processing

**For technical architecture, see [topics/sftp-import.md](topics/sftp-import.md)**

### Configuration

**File:** `microservices/directory/configs/policyholders.yml`

```yaml
directory:
  sftp-import:
    enabled: true
    host: localhost
    port: 2222
    username: policyholder-importer
    private-key-path: ./keys/sftp_client_key
    polling:
      fixed-delay: 120000      # 2 minutes
      initial-delay: 5000      # 5 seconds
      batch-size: 500          # Rows per transaction
```

### Monitoring SFTP Import

**Check logs:**
```bash
docker compose logs -f policyholders | grep "Import completed"
```

**Expected output:**
```
INFO Import completed for policyholders.csv: 100 total, 5 new, 10 updated, 85 unchanged, 0 failed
```

**Metrics meaning:**
- `totalRows`: Total CSV rows processed
- `newRows`: New inserts (email not in database)
- `updatedRows`: Updates (email exists, data changed)
- `unchangedRows`: Skipped (email exists, data identical)
- `failedRows`: Validation or processing failures

### Manual CSV Import

Alternative to SFTP polling - upload via REST API:

```bash
curl -X POST http://localhost:8081/api/policyholders/import \
  -F "file=@policyholders.csv"
```

**Response:**
```json
{
  "totalRows": 100,
  "newRows": 50,
  "updatedRows": 30,
  "unchangedRows": 18,
  "failedRows": 2
}
```

### CSV File Requirements

**Format:**
```csv
name,type,email,phone,address,additionalInfo
John Doe,individual,john@example.com,555-1234,123 Main St,Notes here
```

**Required Fields:** name, type, email, phone
**Optional Fields:** address, additionalInfo
**Unique Key:** email (used for upsert)

### Error Handling

**Failed imports:**
- Moved to `./data/sftp-errors/` (retry queue)
- Retried with exponential backoff (3 attempts max)
- After 3 failures: Moved to `./data/sftp-failed/` (dead letter queue)

**Check error queue:**
```bash
ls -la ./data/sftp-errors/
ls -la ./data/sftp-failed/
```

---

## 6. SSH Authentication

The platform uses **Vault SSH CA** as the primary authentication method for SFTP connections, with automatic fallback to static keys when Vault is unavailable.

| Mode | Default | Authentication Method | Use Case |
|------|---------|----------------------|----------|
| **Vault SSH CA** | Yes (`VAULT_SSH_CA_ENABLED=true`) | Dynamic certificates (15-min TTL) | Production, CI/CD |
| **Static Keys** | Fallback | File-based RSA keys | Vault unavailable, debugging |

> **For detailed architecture, see [topics/vault-ssh-ca.md](topics/vault-ssh-ca.md)** and **[topics/ssh-keys.md](topics/ssh-keys.md)**

---

## 6.1. Vault SSH CA (Primary)

The default authentication method uses HashiCorp Vault's SSH Certificate Authority to issue short-lived certificates for SFTP connections.

### Key Capabilities

- **Dynamic Certificates**: Short-lived (15-minute TTL) certificates issued on-demand
- **Ephemeral Keys**: Per-connection RSA-4096 key pairs with forward secrecy
- **Centralized Management**: All secrets and CA configuration in Vault
- **Audit Trail**: Complete logging of certificate issuance and authentication events
- **Zero Key Distribution**: No static keys to manage or rotate

### How It Works

1. Policyholders service requests a certificate from Vault SSH CA
2. Vault signs an ephemeral public key with 15-minute validity
3. Service authenticates to SFTP server using the certificate
4. SFTP server validates certificate against Vault CA public key
5. Certificate expires automatically; new one issued for next connection

### Quick Start

```bash
# Start all services (Vault SSH CA enabled by default)
docker compose up -d

# Verify certificate-based authentication
docker compose logs policyholders | grep "\[AUDIT\]"
```

### Configuration

**Default (enabled):**
```bash
# In docker-compose or .env
VAULT_SSH_CA_ENABLED=true   # Default
```

**Disable (use static keys instead):**
```bash
VAULT_SSH_CA_ENABLED=false
```

### Vault Secrets and Paths

| Path | Purpose | Used By |
|------|---------|---------|
| `ssh-client-signer/config/ca` | CA public key | SFTP server (validation) |
| `ssh-client-signer/sign/directory-service` | Certificate signing | Policyholders (auth) |
| `secret/ird0/sftp/host-key` | SFTP server host key | SFTP server |
| `secret/ird0/database/postgres` | Database credentials | Directory services |

### Monitoring

```bash
# Watch authentication events
docker compose logs -f sftp-server | grep "\[AUDIT\]"

# Check certificate renewals
docker compose logs policyholders | grep "CERT_RENEWED"

# Check for authentication failures
docker compose logs sftp-server | grep "AUTH_REJECTED"
```

**Expected log output:**
```
[AUDIT] AUTH_SUCCESS username=policyholder-importer clientAddress=/172.18.0.4:54321 certificateSerial=1234567890 validUntil=2026-01-15T10:30:00Z
```

### Verifying Vault Status

```bash
# Check Vault health
curl http://localhost:8200/v1/sys/health

# Check SSH CA role
docker compose exec -e VAULT_TOKEN=dev-root-token vault \
    vault read ssh-client-signer/roles/directory-service

# Read CA public key
docker compose exec -e VAULT_TOKEN=dev-root-token vault \
    vault read ssh-client-signer/config/ca
```

### Vault UI Access

Access the Vault web interface at `http://localhost:8200`:
- Token: `dev-root-token` (or value of `VAULT_DEV_TOKEN`)
- Navigate to: Secrets > ssh-client-signer > config/ca

### Troubleshooting

**Certificate not obtained:**
```bash
# Check Vault connectivity
docker compose exec policyholders wget -qO- http://vault:8200/v1/sys/health

# Check environment variables
docker compose exec policyholders env | grep VAULT
```

**Authentication rejected:**
```bash
# Check rejection reason
docker compose logs sftp-server | grep "AUTH_REJECTED"
# Common reasons: CERTIFICATE_EXPIRED, INVALID_CA_SIGNATURE, PRINCIPAL_MISMATCH
```

**Re-initialize Vault:**
```bash
./scripts/vault-init.sh
docker compose restart policyholders sftp-server
```

### Production Considerations

- Use Vault in server mode (not dev mode)
- Enable TLS for Vault communication
- Use AppRole or Kubernetes authentication instead of tokens
- Store Vault unseal keys securely using Shamir's Secret Sharing
- Enable audit logging for compliance
- Implement certificate TTL based on security requirements (5-60 minutes)
- Use separate Vault clusters per environment

---

## 6.2. Static Key Fallback

When Vault is unavailable or disabled, the system automatically falls back to file-based SSH key authentication.

### When Fallback Occurs

| Scenario | Behavior |
|----------|----------|
| Vault unreachable | Automatic fallback with warning log |
| `VAULT_SSH_CA_ENABLED=false` | Static keys used exclusively |
| Certificate signing fails | Fallback for that connection |

**Fallback sources:**

| Component | Fallback File | Purpose |
|-----------|---------------|---------|
| Client Auth | `./keys/sftp_client_key` | Policyholders → SFTP |
| Known Hosts | `./keys/known_hosts` | Server verification |
| Authorized Keys | `./keys/authorized_keys` | Client verification |
| Host Key | `./keys/hostkey.pem` | SFTP server identity |

### Generating Static Keys (If Needed)

Only required if running without Vault or for local debugging:

```bash
# Generate RSA key pair (4096-bit)
ssh-keygen -t rsa -b 4096 -f ./keys/sftp_client_key -N "" -C "policyholder-importer"

# Add public key to authorized keys
cat ./keys/sftp_client_key.pub >> ./keys/authorized_keys

# Set permissions (644 for Docker - containers run as non-root)
chmod 644 ./keys/sftp_client_key
chmod 644 ./keys/sftp_client_key.pub
chmod 644 ./keys/authorized_keys

# Generate known_hosts (after SFTP server is running)
ssh-keyscan -p 2222 localhost 2>/dev/null | sed 's/\[localhost\]:2222/[sftp-server]:2222/' > ./keys/known_hosts
ssh-keyscan -p 2222 localhost 2>/dev/null >> ./keys/known_hosts
chmod 644 ./keys/known_hosts
```

**Important notes:**
- Only RSA keys are supported (Ed25519 will fail)
- Use **644 permissions** for Docker (not 600) - containers need read access
- The `known_hosts` file needs entries for both `[sftp-server]:2222` and `[localhost]:2222`

### Authorized Keys Format

```
ssh-rsa AAAAB3NzaC1yc2EAAA... policyholder-importer
```

The comment field (third field) must match the SFTP username.

### Key Rotation (Zero Downtime)

If using static keys long-term, rotate every 90 days:

1. **Generate new key pair:**
   ```bash
   ssh-keygen -t rsa -b 4096 -f ./keys/sftp_client_key_new -N "" -C "policyholder-importer"
   chmod 644 ./keys/sftp_client_key_new ./keys/sftp_client_key_new.pub
   ```

2. **Add new public key (keep old for transition):**
   ```bash
   cat ./keys/sftp_client_key_new.pub >> ./keys/authorized_keys
   docker compose restart sftp-server
   ```

3. **Switch to new key:**
   ```bash
   mv ./keys/sftp_client_key_new ./keys/sftp_client_key
   mv ./keys/sftp_client_key_new.pub ./keys/sftp_client_key.pub
   docker compose restart policyholders
   ```

4. **Remove old key and clean up:**
   ```bash
   # Edit authorized_keys to remove old public key
   docker compose restart sftp-server
   shred -vfz ./keys/sftp_client_key.old  # Secure delete
   ```

### Security Best Practices (Static Keys)

- Never commit private keys to Git (use `.gitignore`)
- Use separate keys per environment
- Rotate keys every 90 days
- Monitor `authorized_keys` for unauthorized additions
- Consider migrating to Vault SSH CA for production

---

## 7. Keycloak Identity Management

The platform uses Keycloak for identity and access management, providing OAuth 2.0/OIDC authentication for the Portal BFF and frontend.

> **For authentication architecture details, see [features/user-authentification.refined.md](features/user-authentification.refined.md)**

### Overview

**Components:**
- **Keycloak Server** (port 8180) - Identity provider with OIDC support
- **ird0-portal** client - Public client for Angular SPA (PKCE flow)
- **ird0-portal-bff** client - Confidential client for service-to-service auth

**Roles (hierarchical):**
- `claims-viewer` - View claims and dashboard
- `claims-manager` - Manage claims (includes viewer permissions)
- `claims-admin` - Full administrative access (includes manager permissions)

### Operation Modes

| Mode | `KEYCLOAK_DEV_MODE` | Test Users | Use Case |
|------|---------------------|------------|----------|
| Development | `true` (default) | Auto-created | Local development, CI/CD |
| Production | `false` | Manual creation | Staging, production |

### Development Mode (Default)

In development mode, test users are automatically created on startup.

**How it works:**
1. Keycloak imports the realm from `keycloak/realm-export.json` (clients, roles only)
2. The `keycloak-init` container waits for Keycloak to be healthy
3. The init script creates test users via Keycloak Admin CLI
4. The init container exits after user creation

**Test accounts created:**

| Username | Password | Role | Permissions |
|----------|----------|------|-------------|
| `viewer` | `viewer` | claims-viewer | View claims, dashboard |
| `manager` | `manager` | claims-manager | View + manage claims |
| `admin` | `admin` | claims-admin | Full access |

**Start in development mode:**
```bash
# Default - KEYCLOAK_DEV_MODE=true
docker compose up -d

# Explicit
KEYCLOAK_DEV_MODE=true docker compose up -d
```

**Verify test users were created:**
```bash
# Check init container logs
docker logs keycloak-init

# Expected output:
# [init-dev-users] Development mode enabled, creating test users...
# [init-dev-users] Created user 'viewer' with role 'claims-viewer'
# [init-dev-users] Created user 'manager' with role 'claims-manager'
# [init-dev-users] Created user 'admin' with role 'claims-admin'
```

### Production Mode Setup

In production mode, no test users are created. You must create users manually.

**Step 1: Disable development mode**

In your `.env` file or environment:
```bash
KEYCLOAK_DEV_MODE=false
```

**Step 2: Set secure credentials**

```bash
# .env file
KEYCLOAK_ADMIN_USER=admin
KEYCLOAK_ADMIN_PASSWORD=<strong-random-password>
KEYCLOAK_CLIENT_SECRET=<generate-with-openssl-rand-base64-32>
```

**Step 3: Start Keycloak**

```bash
docker compose up -d keycloak
# Note: keycloak-init will exit immediately when KEYCLOAK_DEV_MODE=false
```

**Step 4: Create users via Admin Console**

1. Access Keycloak Admin Console: `http://localhost:8180/admin`
2. Log in with admin credentials
3. Select the `ird0` realm
4. Navigate to **Users** → **Add user**
5. Fill in user details:
   - Username (required)
   - Email (required, used for login)
   - First Name, Last Name
   - Email Verified: ON
   - Enabled: ON
6. Click **Create**
7. Go to **Credentials** tab → **Set password**
   - Set a strong password
   - Temporary: OFF (unless you want forced password change)
8. Go to **Role mapping** tab → **Assign role**
   - Filter by clients: `ird0-portal-bff`
   - Select appropriate role: `claims-viewer`, `claims-manager`, or `claims-admin`

**Alternative: Create users via Admin CLI**

```bash
# Connect to Keycloak container
docker exec -it keycloak /bin/bash

# Authenticate with admin credentials
/opt/keycloak/bin/kcadm.sh config credentials \
    --server http://localhost:8080 \
    --realm master \
    --user admin \
    --password <admin-password>

# Create a user
/opt/keycloak/bin/kcadm.sh create users -r ird0 \
    -s username=john.doe \
    -s email=john.doe@company.com \
    -s emailVerified=true \
    -s enabled=true \
    -s firstName=John \
    -s lastName=Doe

# Set password
/opt/keycloak/bin/kcadm.sh set-password -r ird0 \
    --username john.doe \
    --new-password "<secure-password>"

# Get client UUID
CLIENT_UUID=$(/opt/keycloak/bin/kcadm.sh get clients -r ird0 \
    -q clientId=ird0-portal-bff --fields id | grep '"id"' | sed 's/.*: "\(.*\)".*/\1/')

# Get user ID
USER_ID=$(/opt/keycloak/bin/kcadm.sh get users -r ird0 \
    -q username=john.doe --fields id | grep '"id"' | sed 's/.*: "\(.*\)".*/\1/')

# Get role ID
ROLE_ID=$(/opt/keycloak/bin/kcadm.sh get clients/$CLIENT_UUID/roles/claims-manager \
    -r ird0 --fields id | grep '"id"' | sed 's/.*: "\(.*\)".*/\1/')

# Assign role
/opt/keycloak/bin/kcadm.sh create users/$USER_ID/role-mappings/clients/$CLIENT_UUID \
    -r ird0 -s "id=$ROLE_ID" -s "name=claims-manager"
```

### Client Secret Configuration

The BFF service needs a client secret to authenticate with Keycloak.

**Development (default):**
```yaml
# microservices/portal-bff/configs/portal-bff.yml
client-secret: ${KEYCLOAK_CLIENT_SECRET:CHANGE_ME_IN_PRODUCTION}
```

**Production:**
```bash
# Generate a strong secret
openssl rand -base64 32

# Set in .env
KEYCLOAK_CLIENT_SECRET=<generated-secret>

# Update in Keycloak Admin Console:
# Clients → ird0-portal-bff → Credentials → Regenerate Secret
# (or set the secret to match your generated value)
```

### Environment Variables Reference

| Variable | Default | Description |
|----------|---------|-------------|
| `KEYCLOAK_ADMIN_USER` | `admin` | Admin console username |
| `KEYCLOAK_ADMIN_PASSWORD` | `admin` | Admin console password |
| `KEYCLOAK_HOST_PORT` | `8180` | External port mapping |
| `KEYCLOAK_DEV_MODE` | `true` | Enable test user creation |
| `KEYCLOAK_CLIENT_SECRET` | `CHANGE_ME_IN_PRODUCTION` | BFF client secret (must rotate for production) |

### Verifying Keycloak Setup

**Check Keycloak health:**
```bash
curl http://localhost:8180/health
```

**Check realm exists:**
```bash
curl http://localhost:8180/realms/ird0/.well-known/openid-configuration
```

**Test user authentication:**
```bash
# Get token (direct grant - only for testing)
curl -X POST http://localhost:8180/realms/ird0/protocol/openid-connect/token \
    -d "grant_type=password" \
    -d "client_id=ird0-portal" \
    -d "username=viewer" \
    -d "password=viewer"
```

**Decode JWT to verify roles:**
```bash
# Use jwt.io or:
TOKEN="<access_token_from_above>"
echo $TOKEN | cut -d'.' -f2 | base64 -d 2>/dev/null | jq .
```

### Troubleshooting

**Test users not created:**
```bash
# Check if dev mode is enabled
docker exec keycloak env | grep KEYCLOAK_DEV_MODE

# Check init container logs
docker logs keycloak-init

# Re-run init script manually
docker exec -e KEYCLOAK_DEV_MODE=true keycloak /scripts/init-dev-users.sh
```

**User cannot log in:**
```bash
# Verify user exists
docker exec keycloak /opt/keycloak/bin/kcadm.sh get users -r ird0

# Check user is enabled
docker exec keycloak /opt/keycloak/bin/kcadm.sh get users -r ird0 -q username=viewer
```

**BFF authentication fails:**
```bash
# Check client secret matches
docker exec portal-bff env | grep KEYCLOAK_CLIENT_SECRET

# Test client credentials flow
curl -X POST http://localhost:8180/realms/ird0/protocol/openid-connect/token \
    -d "grant_type=client_credentials" \
    -d "client_id=ird0-portal-bff" \
    -d "client_secret=CHANGE_ME_IN_PRODUCTION"
```

### Production Security Checklist

- [ ] Set `KEYCLOAK_DEV_MODE=false`
- [ ] Change `KEYCLOAK_ADMIN_PASSWORD` to a strong password
- [ ] **Rotate the `KEYCLOAK_CLIENT_SECRET`** (see detailed instructions below)
- [ ] Enable HTTPS for Keycloak (reverse proxy or KC_HOSTNAME settings)
- [ ] Configure proper redirect URIs for production domain
- [ ] Enable brute force protection (already enabled in realm)
- [ ] Set up user federation (LDAP/AD) if applicable
- [ ] Configure password policies in Keycloak
- [ ] Enable audit logging
- [ ] Regular backup of Keycloak database (`keycloak_db`)

#### Rotating the Portal BFF Client Secret

**CRITICAL SECURITY REQUIREMENT:** The Keycloak realm export (`keycloak/realm-export.json`) contains a placeholder client secret (`CHANGE_ME_IN_PRODUCTION`) for the `ird0-portal-bff` client. This placeholder is **insecure** and **MUST** be rotated before production deployment.

**Why this is important:**
- The placeholder secret is committed to version control
- Anyone with repository access knows the default value
- Using default secrets in production is a critical security vulnerability

**Step-by-step rotation process:**

**Option 1: Using Keycloak Admin Console (Recommended)**

1. Access the Keycloak Admin Console:
   ```
   URL: http://localhost:8180/admin
   Realm: ird0
   Credentials: admin / <your-admin-password>
   ```

2. Navigate to the client:
   - Left sidebar: **Clients**
   - Select: **ird0-portal-bff**
   - Tab: **Credentials**

3. Generate a new secret:
   - Click **Regenerate secret** button
   - Or click **Edit** and enter a custom secret (see generation method below)
   - Copy the new secret value

4. Update the environment variable:
   ```bash
   # Edit .env file
   KEYCLOAK_CLIENT_SECRET=<your-new-secret>
   ```

5. Restart the Portal BFF service:
   ```bash
   docker compose restart portal-bff-svc
   ```

6. Verify the change:
   ```bash
   # Test client credentials flow with new secret
   curl -X POST http://localhost:8180/realms/ird0/protocol/openid-connect/token \
       -d "grant_type=client_credentials" \
       -d "client_id=ird0-portal-bff" \
       -d "client_secret=<your-new-secret>"
   ```

**Option 2: Using Keycloak Admin REST API**

1. Get admin access token:
   ```bash
   ADMIN_TOKEN=$(curl -X POST http://localhost:8180/realms/master/protocol/openid-connect/token \
       -d "grant_type=password" \
       -d "client_id=admin-cli" \
       -d "username=admin" \
       -d "password=<admin-password>" | jq -r '.access_token')
   ```

2. Get the client UUID:
   ```bash
   CLIENT_UUID=$(curl -X GET http://localhost:8180/admin/realms/ird0/clients \
       -H "Authorization: Bearer $ADMIN_TOKEN" | jq -r '.[] | select(.clientId=="ird0-portal-bff") | .id')
   ```

3. Generate a strong secret:
   ```bash
   NEW_SECRET=$(openssl rand -base64 32)
   echo "Generated secret: $NEW_SECRET"
   ```

4. Update the client secret:
   ```bash
   curl -X POST "http://localhost:8180/admin/realms/ird0/clients/$CLIENT_UUID/client-secret" \
       -H "Authorization: Bearer $ADMIN_TOKEN" \
       -H "Content-Type: application/json" \
       -d "{\"value\": \"$NEW_SECRET\"}"
   ```

5. Update `.env` and restart service (same as Option 1 steps 4-6)

**Secret generation best practices:**
```bash
# Generate a strong 32-byte secret (recommended)
openssl rand -base64 32

# Or use uuidgen for a UUID-based secret
uuidgen

# Minimum length: 16 characters
# Use only alphanumeric characters and safe symbols: -_+=
```

**Development vs Production:**
- **Development:** The placeholder `CHANGE_ME_IN_PRODUCTION` is acceptable for local testing
- **Staging/Production:** Always rotate to a unique, strong, randomly-generated secret
- **CI/CD:** Store secrets in secure vaults (HashiCorp Vault, AWS Secrets Manager, etc.)
- **Never commit** production secrets to version control

---

## 8. Configuration Management

### Configuration Files

**Root:**
- `docker-compose.yml` - Service orchestration

**Directory Service:**
- `microservices/directory/configs/application.yml` - Common config
- `microservices/directory/configs/policyholders.yml` - Instance-specific
- `microservices/directory/configs/experts.yml` - Instance-specific
- `microservices/directory/configs/providers.yml` - Instance-specific

**SFTP Server:**
- `microservices/sftp-server/configs/application.yml` - Common config
- `microservices/sftp-server/configs/sftp.yml` - SFTP-specific

**For detailed configuration guide, see [topics/configuration.md](topics/configuration.md)**

### Environment Variables

**Common environment variables:**
```yaml
environment:
  POSTGRES_HOST: postgres
  POSTGRES_PORT: 5432
  POSTGRES_USER: directory_user
  POSTGRES_PASSWORD: directory_pass  # Change in production!
  SFTP_HOST: sftp-server
  SFTP_PORT: 2222
  SFTP_USERNAME: policyholder-importer
  SFTP_PRIVATE_KEY_PATH: /app/keys/sftp_client_key
```

**Production overrides:**
Create `.env` file in project root:
```bash
POSTGRES_PASSWORD=secure_production_password
SFTP_HOST=sftp.production.com
```

### Changing Configuration

**YAML changes (requires rebuild):**
```bash
# Edit YAML file
vim microservices/directory/configs/policyholders.yml

# Rebuild and restart service
docker compose build policyholders
docker compose up -d policyholders
```

**Environment variable changes (no rebuild needed):**
```bash
# Edit docker-compose.yml or .env
vim docker-compose.yml

# Restart service (picks up new env vars)
docker compose up -d policyholders
```

---

## 9. Monitoring and Health Checks

> **For comprehensive monitoring architecture and metrics details, see [topics/monitoring.md](topics/monitoring.md)**

### Health Endpoints

**Directory Services:**
```bash
curl http://localhost:8081/actuator/health  # Policyholders
curl http://localhost:8082/actuator/health  # Experts
curl http://localhost:8083/actuator/health  # Providers
curl http://localhost:8084/actuator/health  # Insurers
```

**SFTP Server:**
```bash
curl http://localhost:9090/actuator/health
```

**Expected response:**
```json
{
  "status": "UP"
}
```

### Metrics Endpoints

```bash
# List all metrics
curl http://localhost:8081/actuator/metrics

# Specific metric
curl http://localhost:8081/actuator/metrics/jvm.memory.used

# Database connection pool
curl http://localhost:8081/actuator/metrics/hikaricp.connections.active
```

### Application Info

```bash
curl http://localhost:8081/actuator/info
```

### Log Monitoring

**Real-time logs:**
```bash
# All services
docker compose logs -f

# Specific service with timestamps
docker compose logs -f --timestamps policyholders

# Filter for errors
docker compose logs policyholders | grep -E "ERROR|WARN"

# Search for specific import file
docker compose logs policyholders | grep "policyholders.csv"
```

### Setting Up Alerts

**Health check script (cron job):**
```bash
#!/bin/bash
# check-health.sh
for service in policyholders:8081 experts:8082 providers:8083; do
  name=${service%%:*}
  port=${service##*:}
  status=$(curl -s http://localhost:$port/actuator/health | jq -r '.status')
  if [ "$status" != "UP" ]; then
    echo "ALERT: $name is $status"
    # Send alert (email, Slack, PagerDuty, etc.)
  fi
done
```

**For detailed monitoring setup, see [topics/monitoring.md](topics/monitoring.md)**

---

## 10. Backup and Restore

### What to Back Up

1. **PostgreSQL databases** (critical) - All application data
2. **SFTP metadata** (`./data/sftp-metadata/`) - Import timestamps
3. **Configuration files** (in Git, but verify) - Service configuration
4. **SSH keys** (`./keys/`, secure storage!) - Authentication credentials

### Backup Strategy

**Daily automated backup script:**
```bash
#!/bin/bash
# backup-daily.sh
BACKUP_DIR=/backups
DATE=$(date +%Y%m%d_%H%M%S)

# Database backup
docker compose exec -T postgres pg_dumpall -U postgres > ${BACKUP_DIR}/db-${DATE}.sql

# Metadata backup
tar czf ${BACKUP_DIR}/sftp-metadata-${DATE}.tar.gz ./data/sftp-metadata

# Configuration backup (optional if using Git)
tar czf ${BACKUP_DIR}/configs-${DATE}.tar.gz microservices/*/configs

# Upload to remote storage (S3, network drive, etc.)
# aws s3 cp ${BACKUP_DIR}/db-${DATE}.sql s3://my-bucket/backups/
```

**Add to crontab:**
```bash
# Run daily at 2 AM
0 2 * * * /path/to/backup-daily.sh
```

### Restore Procedures

**Database restore:**
```bash
# Stop services
docker compose down

# Restore databases
docker compose up -d postgres
sleep 10  # Wait for PostgreSQL to be ready
cat /backups/db-20260110.sql | docker compose exec -T postgres psql -U postgres

# Start all services
docker compose up -d
```

**Metadata restore:**
```bash
tar xzf /backups/sftp-metadata-20260110.tar.gz
```

**Full system restore:**
```bash
# Stop everything
docker compose down -v  # Removes volumes!

# Restore PostgreSQL volume
docker run --rm \
  -v ird0_postgres-data:/data \
  -v /backups:/backup \
  alpine sh -c "cd / && tar xzf /backup/postgres-data-20260110.tar.gz"

# Restore metadata
tar xzf /backups/sftp-metadata-20260110.tar.gz

# Restore SSH keys (from secure storage)
tar xzf /secure-backup/keys-20260110.tar.gz

# Start services
docker compose up -d
```

### Disaster Recovery

**Recovery Time Objective (RTO):** Estimated 30-60 minutes
**Recovery Point Objective (RPO):** 24 hours (daily backups)

**Disaster recovery steps:**
1. Provision new infrastructure
2. Clone repository
3. Restore SSH keys from secure backup
4. Restore configuration files (or use Git)
5. Start PostgreSQL: `docker compose up -d postgres`
6. Restore databases from backup
7. Restore SFTP metadata
8. Start all services: `docker compose up -d`
9. Verify health endpoints
10. Test SFTP import

---

## 11. Troubleshooting

> **For comprehensive diagnostic procedures, see [topics/troubleshooting.md](topics/troubleshooting.md)**

### Service Won't Start

**Symptoms:** Container exits immediately or fails to start

**Diagnosis:**
```bash
# Check container status
docker compose ps

# View startup logs
docker compose logs <service>

# Check for port conflicts
netstat -tuln | grep -E "8081|8082|8083|2222|5432|9090"
```

**Common causes:**
- Port already in use (another process on same port)
- Database not ready (check PostgreSQL health)
- Configuration error (check YAML syntax)
- Missing SSH key (check key paths in config)

### Database Connection Failed

**Symptoms:** Service logs show "Connection refused" or "Unknown host"

**Diagnosis:**
```bash
# Check PostgreSQL running
docker compose ps postgres

# Check database exists
docker compose exec postgres psql -U postgres -l

# Test connection
docker compose exec postgres psql -U directory_user -d policyholders_db -c "SELECT 1"

# Check credentials
docker compose exec policyholders env | grep POSTGRES
```



### CSV Import Errors

**Symptoms:** High `failedRows` count in import results

**Diagnosis:**
```bash
# Check recent import results
docker compose logs policyholders | grep "Import completed"

# Look for validation errors
docker compose logs policyholders | grep -E "ERROR|validation"

# Check CSV format
head ./data/policyholders.csv
```

**Common causes:**
- Missing required fields (name, type, email, phone)
- Invalid email format
- Duplicate emails in CSV
- CSV encoding issues (use UTF-8)
- Extra commas or malformed CSV

**For comprehensive troubleshooting, see [topics/troubleshooting.md](topics/troubleshooting.md)**

---

## 12. Security Hardening

### For Production Deployment

**Credentials:**
- ✅ Change all default passwords (`POSTGRES_PASSWORD`)
- ✅ Use secrets management (Vault, AWS Secrets Manager, Docker secrets)
- ✅ Enable SSL/TLS for database connections
- ✅ Use strong SSH keys (4096-bit RSA minimum)
- ✅ Rotate credentials regularly (90 days)

**Network:**
- ✅ Restrict port access with firewall rules
- ✅ Use internal Docker networks for service communication
- ✅ Enable HTTPS for actuator endpoints
- ✅ Disable unnecessary actuator endpoints
- ✅ Use reverse proxy (nginx) for external access
- ✅ Implement rate limiting on APIs

**SSH Keys:**
- ✅ Remove private keys from Docker images
- ✅ Mount keys as Docker secrets or volumes
- ✅ Use different keys per environment
- ✅ Set file permissions: 600 for private, 644 for public
- ✅ Never commit keys to Git

**Database:**
- ✅ Create separate users with minimal privileges
- ✅ Enable SSL connections (`sslmode=require`)
- ✅ Regular security patches for PostgreSQL
- ✅ Enable audit logging
- ✅ Restrict access to localhost or private network

**Application:**
- ✅ Keep dependencies updated (Maven, Docker images)
- ✅ Scan images for vulnerabilities
- ✅ Run services as non-root user
- ✅ Enable Spring Security for APIs (add authentication)
- ✅ Validate all user input
- ✅ Implement proper error handling (don't leak stack traces)

---

## 13. Maintenance Procedures

### Weekly Tasks

- ✅ Review application logs for errors
- ✅ Check disk space usage
- ✅ Verify backups completed successfully
- ✅ Monitor service health endpoints

### Monthly Tasks

- ✅ Review import metrics (new/updated/unchanged ratios)
- ✅ Update dependencies (`mvn versions:display-dependency-updates`)
- ✅ Review security advisories for Java, Spring Boot, PostgreSQL
- ✅ Test backup restore procedure
- ✅ Clean up old log files and backups

### Quarterly Tasks

- ✅ Rotate SSH keys
- ✅ Review and update documentation
- ✅ Perform load testing
- ✅ Conduct disaster recovery drill
- ✅ Security audit
- ✅ Review and optimize database queries

### Annual Tasks

- ✅ Major dependency upgrades (Java, Spring Boot major versions)
- ✅ Review and update architecture
- ✅ Capacity planning
- ✅ Security penetration testing

---

## 14. Appendices

### A. Port Reference

| Service | Port | Protocol | Purpose |
|---------|------|----------|---------|
| Policyholders | 8081 | HTTP | REST API + Actuator |
| Experts | 8082 | HTTP | REST API + Actuator |
| Providers | 8083 | HTTP | REST API + Actuator |
| Insurers | 8084 | HTTP | REST API + Actuator |
| Incident | 8085 | HTTP | REST API + Actuator |
| Notification | 8086 | HTTP | REST API + Actuator |
| Portal BFF | 7777 | HTTP | REST API + Actuator |
| SFTP Server | 2222 | SFTP | File transfer |
| SFTP Actuator | 9090 | HTTP | Health and metrics |
| PostgreSQL | 5432 | PostgreSQL | Database |
| Vault | 8200 | HTTP | Secrets management (optional) |
| Keycloak | 8180 | HTTP | Identity provider (OIDC) |

### B. Directory Structure

```
/app/                          (Container paths)
├── data/
│   ├── sftp-metadata/         # Import timestamps (persistent)
│   ├── sftp-errors/           # Failed imports for retry
│   └── sftp-failed/           # Exhausted retry attempts
├── temp/
│   └── sftp-downloads/        # Downloaded CSV files (temporary)
└── config/
    └── application.yml        # Service configuration
```

### C. Useful Commands Cheat Sheet

```bash
# BUILD AND START
docker compose up --build -d                # Build and start all services
docker compose up --build -d policyholders  # Build and start specific service

# LOGS
docker compose logs -f policyholders        # Follow logs
docker compose logs --tail=100 policyholders # Last 100 lines
docker compose logs policyholders | grep ERROR # Filter errors

# HEALTH CHECKS
curl http://localhost:8081/actuator/health  # Policyholders health
curl http://localhost:8082/actuator/health  # Experts health
curl http://localhost:8083/actuator/health  # Providers health

# DATABASE
docker compose exec postgres psql -U directory_user -d policyholders_db  # Connect
docker compose exec postgres pg_dump -U directory_user policyholders_db > backup.sql  # Backup
cat backup.sql | docker compose exec -T postgres psql -U directory_user -d policyholders_db  # Restore

# SFTP
sftp -i ./keys/sftp_client_key -P 2222 policyholder-importer@localhost  # Test connection
docker compose logs sftp-server | grep Authentication  # Check auth logs

# MONITORING
docker compose logs -f policyholders | grep "Import completed"  # Watch imports
docker compose ps  # Service status
docker stats  # Resource usage

# RESTART
docker compose restart policyholders        # Restart specific service
docker compose restart                      # Restart all services

# CLEANUP
docker compose down                         # Stop all services
docker compose down -v                      # Stop and remove volumes (DATA LOSS!)
docker system prune -a                      # Clean up unused Docker resources
```

### D. Environment Variables Reference

| Variable | Default | Description |
|----------|---------|-------------|
| `POSTGRES_HOST` | postgres | PostgreSQL hostname |
| `POSTGRES_PORT` | 5432 | PostgreSQL port |
| `POSTGRES_USER` | directory_user | Database username |
| `POSTGRES_PASSWORD` | directory_pass | Database password |
| `SFTP_HOST` | localhost | SFTP server hostname |
| `SFTP_PORT` | 2222 | SFTP server port |
| `SFTP_USERNAME` | policyholder-importer | SFTP username |
| `SFTP_PRIVATE_KEY_PATH` | ./keys/sftp_client_key | Client private key path |
| `VAULT_ENABLED` | false | Enable Vault integration |
| `VAULT_ADDR` | http://vault:8200 | Vault server address |
| `VAULT_TOKEN` | (from VAULT_DEV_TOKEN) | Vault authentication token |
| `VAULT_DEV_TOKEN` | dev-root-token | Development root token |
| `KEYCLOAK_ADMIN_USER` | admin | Keycloak admin username |
| `KEYCLOAK_ADMIN_PASSWORD` | admin | Keycloak admin password |
| `KEYCLOAK_HOST_PORT` | 8180 | Keycloak external port |
| `KEYCLOAK_DEV_MODE` | true | Create test users on startup |
| `KEYCLOAK_CLIENT_SECRET` | CHANGE_ME_IN_PRODUCTION | BFF client secret (must rotate for production) |

---

**Document Version**: 1.4
**Last Updated**: 2026-01-29
**Maintainer**: DevOps Team

For technical architecture details, see [ARCHITECTURE.md](ARCHITECTURE.md)
For topic-specific guides, see [INDEX.md](INDEX.md)

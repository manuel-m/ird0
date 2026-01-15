# IRD0 User Guide - Operations Manual

This guide provides comprehensive operational procedures for administrators, DevOps engineers, and system operators managing the IRD0 insurance platform.

## Table of Contents

1. [System Overview](#1-system-overview)
2. [Prerequisites and Installation](#2-prerequisites-and-installation)
3. [Service Operations](#3-service-operations)
4. [Database Management](#4-database-management)
5. [SFTP Import Operations](#5-sftp-import-operations)
6. [SSH Key Management](#6-ssh-key-management)
   - [HashiCorp Vault Integration](#65-hashicorp-vault-integration) (SSH CA)
7. [Configuration Management](#7-configuration-management)
8. [Monitoring and Health Checks](#8-monitoring-and-health-checks)
9. [Backup and Restore](#9-backup-and-restore)
10. [Troubleshooting](#10-troubleshooting)
11. [Security Hardening](#11-security-hardening)
12. [Maintenance Procedures](#12-maintenance-procedures)
13. [Appendices](#13-appendices)

---

## 1. System Overview

The IRD0 platform consists of multiple microservices deployed as Docker containers:

**Services:**
- **Policyholders** (port 8081) - REST API for policyholder data with SFTP import
- **Experts** (port 8082) - REST API for expert data
- **Providers** (port 8083) - REST API for provider data
- **SFTP Server** (port 2222) - Secure file transfer for CSV files
- **PostgreSQL** (port 5432) - Database for all directory services
- **Vault** (port 8200) - Optional secrets management (SSH keys, credentials)

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
- **RAM**: 4GB minimum
- **Disk Space**: 10GB for containers and data
- **Ports**: 8081-8083, 2222, 5432, 8200, 9090 must be available

### Initial Setup

```bash
# Clone repository
git clone <repository-url>
cd ird0

# Create .env file with required variables
cp .env.example .env

# Build and start all services (includes Vault)
docker compose up --build -d

# Wait for Vault to be healthy
sleep 15

# Initialize Vault with SSH CA and secrets
./scripts/vault-init.sh

# Restart services to pick up Vault configuration
docker compose restart policyholders sftp-server

# Verify services
docker compose ps
```

**Note:** SSH authentication now uses ephemeral certificates from Vault SSH CA. No manual SSH key generation is required.

### Verification

Check all services are healthy:

```bash
# Policyholders
curl http://localhost:8081/actuator/health

# Experts
curl http://localhost:8082/actuator/health

# Providers
curl http://localhost:8083/actuator/health

# SFTP Server
curl http://localhost:9090/actuator/health

# PostgreSQL
docker compose exec postgres psql -U directory_user -d policyholders_db -c "SELECT 1"
```

If `scripts/verify-services.sh` exists:
```bash
./scripts/verify-services.sh
```

---

## 3. Service Operations

> **For Docker architecture and containerization details, see [topics/docker.md](topics/docker.md)**

### Starting Services

```bash
# Start all services
docker compose up -d

# Start specific service
docker compose up -d policyholders

# Start with rebuild
docker compose up --build -d

# Watch logs
docker compose logs -f policyholders
```

### Stopping Services

```bash
# Graceful shutdown (all services)
docker compose down

# Stop specific service
docker compose stop policyholders

# Restart service
docker compose restart policyholders
```

### Service Dependencies

**Startup Order:**
1. PostgreSQL container starts and runs health check
2. Directory services wait for PostgreSQL to be healthy
3. Policyholders service additionally waits for SFTP server
4. SFTP server can start independently

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
- **Password**: `directory_pass` **⚠️ Change in production!**

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

## 6. SSH Key Management

### Key Types

1. **SFTP Server Host Key** - Server identity (`./keys/hostkey.pem`)
2. **Client Private Key** - Policyholders service authentication (`./keys/sftp_client_key`)
3. **Client Public Key** - Stored in authorized keys (`./keys/sftp_client_key.pub`)
4. **Authorized Keys** - List of allowed public keys (`./keys/authorized_keys`)

**For detailed key management, see [topics/ssh-keys.md](topics/ssh-keys.md)**

### Generating New Client Keys

```bash
# Generate RSA key pair (4096-bit)
ssh-keygen -t rsa -b 4096 -f ./keys/sftp_client_key -N "" -C "policyholder-importer"

# Add public key to SFTP server
cat ./keys/sftp_client_key.pub >> ./keys/authorized_keys

# Set correct permissions (644 for Docker containers with non-root users)
chmod 644 ./keys/sftp_client_key
chmod 644 ./keys/sftp_client_key.pub
chmod 644 ./keys/authorized_keys

# Generate known_hosts file with SFTP server fingerprint
# (Do this after SFTP server is running)
ssh-keyscan -p 2222 localhost 2>/dev/null | sed 's/\[localhost\]:2222/[sftp-server]:2222/' > ./keys/known_hosts
ssh-keyscan -p 2222 localhost 2>/dev/null >> ./keys/known_hosts
chmod 644 ./keys/known_hosts
```

**⚠️ Important:**
- Only RSA keys are supported. Ed25519 keys will fail with "No decoder available".
- Use **644 permissions** (not 600) for Docker environments - containers run as non-root users and need read access.
- The `known_hosts` file must include entries for both `[sftp-server]:2222` (Docker network) and `[localhost]:2222` (local testing).

### Authorized Keys Format

**Correct format (3 fields):**
```
ssh-rsa AAAAB3NzaC1yc2EAAA... policyholder-importer
```

**Incorrect format (will fail):**
```
ssh-rsa AAAAB3NzaC1yc2EAAA... user@hostname
```

The third field must match the username in SFTP configuration.

### Key Rotation Procedure (Zero Downtime)

1. Generate new key pair with correct permissions
   ```bash
   ssh-keygen -t rsa -b 4096 -f ./keys/sftp_client_key_new -N "" -C "policyholder-importer"
   chmod 644 ./keys/sftp_client_key_new
   chmod 644 ./keys/sftp_client_key_new.pub
   ```
2. Add new public key to `authorized_keys` (keep old key for transition)
   ```bash
   cat ./keys/sftp_client_key_new.pub >> ./keys/authorized_keys
   chmod 644 ./keys/authorized_keys
   ```
3. Restart SFTP server to load new authorized key
   ```bash
   docker compose restart sftp-server
   ```
4. Update known_hosts with new server host key (if server regenerated)
   ```bash
   ssh-keyscan -p 2222 localhost 2>/dev/null | sed 's/\[localhost\]:2222/[sftp-server]:2222/' > ./keys/known_hosts
   ssh-keyscan -p 2222 localhost 2>/dev/null >> ./keys/known_hosts
   chmod 644 ./keys/known_hosts
   ```
5. Replace old key with new key
   ```bash
   mv ./keys/sftp_client_key_new ./keys/sftp_client_key
   mv ./keys/sftp_client_key_new.pub ./keys/sftp_client_key.pub
   ```
6. Restart policyholders service
   ```bash
   docker compose restart policyholders
   ```
7. Verify SFTP import works with new key
   ```bash
   docker compose logs -f policyholders | grep "Import completed"
   ```
8. Remove old public key from `authorized_keys` and restart SFTP server
9. Delete old private key securely
   ```bash
   shred -vfz ./keys/sftp_client_key.old
   ```

**Downtime:** Zero (both keys work during transition)

### Security Best Practices

- ✅ Never commit private keys to Git
- ✅ Use `.gitignore` for `keys/` directory
- ✅ Rotate keys every 90 days
- ✅ Use separate keys per environment (dev/staging/prod)
- ✅ Set correct permissions for **Docker environments**: 644 for all key files (private keys, public keys, authorized_keys, known_hosts)
  - **Why 644?** Containers run as non-root users who need read access. Traditional 600 permissions will cause "Permission denied" errors.
  - For non-Docker environments, use 600 for private keys and authorized_keys
- ✅ Monitor `authorized_keys` for unauthorized additions
- ✅ Regenerate `known_hosts` file when SFTP server host key changes

---

## 6.5. HashiCorp Vault Integration

The platform integrates with HashiCorp Vault for centralized secrets management and SSH Certificate Authority (CA) for dynamic certificate-based SFTP authentication.

> **For detailed architecture and implementation, see [topics/vault-ssh-ca.md](topics/vault-ssh-ca.md)**

### Overview

**Key Capabilities:**
- **SSH Certificate Authority**: Dynamic, short-lived certificates (15-minute TTL) for SFTP authentication
- **Ephemeral Keys**: Per-connection RSA-4096 key pairs with forward secrecy
- **Centralized Secrets**: Database credentials, host keys stored in Vault
- **Audit Trail**: Complete logging of certificate issuance and authentication events
- **Graceful Fallback**: Automatic fallback to static keys when Vault unavailable

**Operation Modes:**
- **Vault Disabled** (default): Services use file-based keys from `./keys/`
- **Vault Enabled**: Services use Vault SSH CA for certificate-based auth

### Quick Start

```bash
# Start the cluster with Vault
docker compose up -d

# Wait for Vault to be healthy
docker compose exec vault vault status

# Initialize Vault with SSH CA and secrets
./scripts/vault-init.sh

# Restart services to pick up Vault configuration
docker compose restart policyholders sftp-server

# Verify certificate-based authentication
docker compose logs policyholders | grep "\[AUDIT\]"
```

### Enabling/Disabling Vault

**Via Environment Variables:**
```bash
# Enable Vault with SSH CA
VAULT_ENABLED=true VAULT_SSH_CA_ENABLED=true docker compose up -d

# Disable Vault (use static keys)
VAULT_ENABLED=false docker compose up -d
```

**Via .env File:**
```bash
VAULT_ENABLED=true
VAULT_SSH_CA_ENABLED=true
```

### Vault Secrets and SSH CA Paths

| Path | Purpose | Used By |
|------|---------|---------|
| `secret/ird0/database/postgres` | Database credentials | Directory services |
| `secret/ird0/sftp/host-key` | SFTP server host key | SFTP server |
| `ssh-client-signer/config/ca` | CA public key | Both services |
| `ssh-client-signer/sign/directory-service` | Certificate signing | Policyholders |

### Monitoring Certificate Authentication

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
- Navigate to: Secrets > ssh-client-signer > config/ca (CA public key)

### Graceful Fallback Behavior

When Vault is unavailable, services automatically fall back to static keys:

| Component | Fallback Source | Logged As |
|-----------|-----------------|-----------|
| SFTP Client Auth | `./keys/sftp_client_key` | Warning |
| Known Hosts | `./keys/known_hosts` | Warning |
| Authorized Keys | `./keys/authorized_keys` | Warning |
| Host Key | `./keys/hostkey.pem` | Warning |

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

## 7. Configuration Management

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

## 8. Monitoring and Health Checks

> **For comprehensive monitoring architecture and metrics details, see [topics/monitoring.md](topics/monitoring.md)**

### Health Endpoints

**Directory Services:**
```bash
curl http://localhost:8081/actuator/health  # Policyholders
curl http://localhost:8082/actuator/health  # Experts
curl http://localhost:8083/actuator/health  # Providers
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

## 9. Backup and Restore

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

## 10. Troubleshooting

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

**Common causes:**
- PostgreSQL container not running
- Wrong credentials (check env vars)
- Database not created (check init script)
- Network issue (check Docker network)

### SFTP Import Not Working

**Symptoms:** No "Import completed" logs appear

**Diagnosis:**
```bash
# Check SFTP import enabled
docker compose exec policyholders env | grep SFTP_IMPORT

# Check SFTP server reachable
docker compose exec policyholders nc -zv sftp-server 2222

# Test SFTP authentication
sftp -i ./keys/sftp_client_key -P 2222 policyholder-importer@localhost

# Check metadata store
docker compose exec policyholders ls -la /app/data/sftp-metadata
```

**Common causes:**
- SFTP import disabled (only enabled on Policyholders)
- SSH key missing or wrong permissions
- SFTP server not running
- No CSV files on SFTP server
- File timestamp unchanged (metadata store says "no change")

### SFTP Authentication Failed

**Symptoms:** "Authentication failed" in logs or sftp connection rejected

**Diagnosis:**
```bash
# Check key format and permissions
ls -la ./keys/sftp_client_key*
ls -la ./keys/authorized_keys
ls -la ./keys/known_hosts

# Verify key is RSA (not Ed25519)
head -n1 ./keys/sftp_client_key.pub

# Check authorized_keys format (should have 3 fields)
cat ./keys/authorized_keys

# Test with verbose output
sftp -vvv -i ./keys/sftp_client_key -P 2222 policyholder-importer@localhost
```

**Common causes:**
- **Wrong key permissions** - In Docker, all key files must be **644** (readable by non-root container users)
- Using Ed25519 keys (only RSA supported)
- Wrong authorized_keys format (must have 3 fields: key-type, key-data, username)
- Username mismatch in authorized_keys (third field must match SFTP username)
- Key not mounted in container
- Missing or outdated known_hosts file
- Host key changed (regenerate known_hosts)

### SSH Key Permission Issues

**Symptoms:**
- SFTP server fails with "Authorized keys file is not readable"
- Policyholders service fails with "AccessDeniedException" for sftp_client_key
- "Server key did not validate" errors

**Solution:**
```bash
# Set correct permissions for Docker (644 for all key files)
chmod 644 ./keys/sftp_client_key
chmod 644 ./keys/sftp_client_key.pub
chmod 644 ./keys/authorized_keys
chmod 644 ./keys/known_hosts

# Regenerate known_hosts if host key changed
ssh-keyscan -p 2222 localhost 2>/dev/null | sed 's/\[localhost\]:2222/[sftp-server]:2222/' > ./keys/known_hosts
ssh-keyscan -p 2222 localhost 2>/dev/null >> ./keys/known_hosts
chmod 644 ./keys/known_hosts

# Restart services
docker compose restart sftp-server policyholders
```

**Why 644?** Docker containers run as non-root users (appuser) who need read access to key files. Traditional 600 permissions prevent the container user from reading the files.

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

## 11. Security Hardening

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

## 12. Maintenance Procedures

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

## 13. Appendices

### A. Port Reference

| Service | Port | Protocol | Purpose |
|---------|------|----------|---------|
| Policyholders | 8081 | HTTP | REST API + Actuator |
| Experts | 8082 | HTTP | REST API + Actuator |
| Providers | 8083 | HTTP | REST API + Actuator |
| SFTP Server | 2222 | SFTP | File transfer |
| SFTP Actuator | 9090 | HTTP | Health and metrics |
| PostgreSQL | 5432 | PostgreSQL | Database |
| Vault | 8200 | HTTP | Secrets management (optional) |

### B. Directory Structure

```
/app/                          (Container paths)
├── data/
│   ├── sftp-metadata/         # Import timestamps (persistent)
│   ├── sftp-errors/           # Failed imports for retry
│   └── sftp-failed/           # Exhausted retry attempts
├── keys/
│   ├── hostkey.pem            # SFTP server host key
│   ├── sftp_client_key        # Client private key
│   ├── sftp_client_key.pub    # Client public key
│   └── authorized_keys        # Allowed public keys
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

---

**Document Version**: 1.2
**Last Updated**: 2026-01-15
**Maintainer**: DevOps Team

For technical architecture details, see [ARCHITECTURE.md](ARCHITECTURE.md)
For topic-specific guides, see [INDEX.md](INDEX.md)

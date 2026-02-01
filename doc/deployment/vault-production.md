# HashiCorp Vault Production Setup

This guide covers migrating Vault from development mode to a production-ready configuration with proper security, persistence, and operational procedures.

## Current State Assessment

### Development Mode Issues

The current Vault setup uses development mode (`vault server -dev`), which has these limitations:

| Issue | Risk | Production Impact |
|-------|------|-------------------|
| In-memory storage | Data loss on restart | All secrets lost |
| Root token exposed | Security vulnerability | Unauthorized access |
| No TLS | Data in transit exposed | Secret interception |
| Auto-unsealed | No protection at rest | Compromised if server accessed |
| Single token auth | No audit trail | Cannot track who accessed what |

### Production Requirements

- Persistent storage (file or Consul backend)
- Manual or auto-unseal with secure key management
- TLS encryption for all communication
- Proper initialization with Shamir key sharing
- Audit logging enabled
- Least-privilege access policies

---

## Architecture

### Production Vault Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                      VPS / Production                        │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │                     Vault Server                       │   │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐   │   │
│  │  │   Secrets   │  │    Auth     │  │   Audit     │   │   │
│  │  │   Engine    │  │   Methods   │  │    Logs     │   │   │
│  │  └─────────────┘  └─────────────┘  └─────────────┘   │   │
│  │                                                        │   │
│  │  ┌─────────────────────────────────────────────────┐  │   │
│  │  │              Storage Backend (File)              │  │   │
│  │  │                /vault/data                       │  │   │
│  │  └─────────────────────────────────────────────────┘  │   │
│  └──────────────────────────────────────────────────────┘   │
│                              │                               │
│                              │ TLS (8200)                    │
│                              ▼                               │
│  ┌──────────────────────────────────────────────────────┐   │
│  │                  Application Services                 │   │
│  │   ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐ │   │
│  │   │Directory│  │Incident │  │Notif.   │  │Portal   │ │   │
│  │   │Services │  │Service  │  │Service  │  │BFF      │ │   │
│  │   └─────────┘  └─────────┘  └─────────┘  └─────────┘ │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

---

## Docker Compose Configuration

### Production Vault Service

```yaml
# deploy/docker-compose.vault.yml
version: '3.8'

services:
  vault:
    image: hashicorp/vault:1.15
    container_name: vault
    restart: unless-stopped
    cap_add:
      - IPC_LOCK  # Prevent memory from being swapped
    environment:
      VAULT_ADDR: "https://127.0.0.1:8200"
      VAULT_API_ADDR: "https://vault:8200"
      VAULT_CLUSTER_ADDR: "https://vault:8201"
    volumes:
      - vault_data:/vault/data
      - ./vault/config:/vault/config:ro
      - ./vault/certs:/vault/certs:ro
      - ./vault/policies:/vault/policies:ro
      - vault_logs:/vault/logs
    command: server
    healthcheck:
      test: ["CMD", "vault", "status", "-tls-skip-verify"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 10s
    networks:
      - internal
    # Note: No external ports - only internal network access

volumes:
  vault_data:
  vault_logs:

networks:
  internal:
    external: true
```

### Vault Configuration File

```hcl
# deploy/vault/config/vault.hcl

# Storage backend - file-based for single node
storage "file" {
  path = "/vault/data"
}

# Listener configuration with TLS
listener "tcp" {
  address       = "0.0.0.0:8200"
  tls_cert_file = "/vault/certs/vault.crt"
  tls_key_file  = "/vault/certs/vault.key"

  # Require TLS 1.2 minimum
  tls_min_version = "tls12"

  # Disable client certificate requirement
  tls_require_and_verify_client_cert = false
}

# API address for redirects
api_addr = "https://vault:8200"

# Cluster address (for future HA)
cluster_addr = "https://vault:8201"

# Enable UI
ui = true

# Disable memory locking warning (handled by IPC_LOCK cap)
disable_mlock = false

# Telemetry (optional)
telemetry {
  disable_hostname = true
  prometheus_retention_time = "30s"
}

# Audit logging
# Enabled after initialization via CLI
```

---

## TLS Certificate Generation

### Option 1: Self-Signed Certificates (Internal Only)

```bash
# Generate CA
openssl genrsa -out vault-ca.key 4096
openssl req -new -x509 -days 3650 -key vault-ca.key \
  -out vault-ca.crt \
  -subj "/CN=Vault CA/O=IRD0"

# Generate Vault certificate
openssl genrsa -out vault.key 2048

# Create certificate signing request
cat > vault-csr.conf << EOF
[req]
default_bits = 2048
prompt = no
default_md = sha256
distinguished_name = dn
req_extensions = req_ext

[dn]
CN = vault
O = IRD0

[req_ext]
subjectAltName = @alt_names

[alt_names]
DNS.1 = vault
DNS.2 = localhost
IP.1 = 127.0.0.1
EOF

openssl req -new -key vault.key -out vault.csr -config vault-csr.conf

# Sign with CA
openssl x509 -req -in vault.csr \
  -CA vault-ca.crt -CAkey vault-ca.key -CAcreateserial \
  -out vault.crt -days 365 \
  -extensions req_ext -extfile vault-csr.conf

# Copy to vault certs directory
cp vault.crt vault.key deploy/vault/certs/
```

### Option 2: Let's Encrypt via Traefik

If Vault is exposed through Traefik, certificates are managed automatically:

```yaml
# Traefik labels
labels:
  - traefik.enable=true
  - traefik.http.routers.vault.rule=Host(`vault.yourdomain.com`)
  - traefik.http.routers.vault.tls.certresolver=letsencrypt
```

---

## Initialization Process

### Step 1: Start Vault Container

```bash
docker compose -f deploy/docker-compose.vault.yml up -d
```

### Step 2: Initialize Vault

```bash
# Initialize with 5 key shares, requiring 3 to unseal
docker exec vault vault operator init \
  -key-shares=5 \
  -key-threshold=3 \
  -format=json > vault-init.json
```

**CRITICAL:** The `vault-init.json` file contains the unseal keys and initial root token. This file must be:
- Encrypted immediately
- Stored in multiple secure locations
- Never committed to version control
- Keys distributed to different team members

Example output:
```json
{
  "unseal_keys_b64": [
    "key1...",
    "key2...",
    "key3...",
    "key4...",
    "key5..."
  ],
  "unseal_keys_hex": [...],
  "unseal_shares": 5,
  "unseal_threshold": 3,
  "recovery_keys_b64": [],
  "initial_root_token": "hvs.xxxxxxxxxxxxx"
}
```

### Step 3: Unseal Vault

```bash
# Unseal using 3 of the 5 keys
docker exec vault vault operator unseal <key1>
docker exec vault vault operator unseal <key2>
docker exec vault vault operator unseal <key3>
```

Check status:
```bash
docker exec vault vault status
```

Expected output:
```
Key             Value
---             -----
Seal Type       shamir
Initialized     true
Sealed          false  # <-- Should be false after unsealing
...
```

### Step 4: Authenticate with Root Token

```bash
docker exec -it vault vault login
# Enter root token when prompted
```

---

## Secrets Configuration

### Enable KV Secrets Engine

```bash
# Enable KV v2 secrets engine
docker exec vault vault secrets enable -path=secret kv-v2
```

### Store Application Secrets

```bash
# PostgreSQL credentials
docker exec vault vault kv put secret/postgres \
  username="ird_production_user" \
  password="$(openssl rand -base64 32)"

# Keycloak credentials
docker exec vault vault kv put secret/keycloak \
  admin_password="$(openssl rand -base64 32)" \
  client_secret="$(openssl rand -base64 32)"

# SFTP SSH keys
docker exec vault vault kv put secret/sftp \
  host_key="$(cat /path/to/sftp_host_key)"
```

### Secrets Structure

```
secret/
├── postgres
│   ├── username
│   └── password
├── keycloak
│   ├── admin_password
│   └── client_secret
├── sftp
│   └── host_key
└── services
    ├── incident
    │   └── api_key
    └── notification
        └── webhook_secret
```

---

## Access Policies

### Create Application Policies

```hcl
# deploy/vault/policies/app-readonly.hcl

# Read-only access to application secrets
path "secret/data/postgres" {
  capabilities = ["read"]
}

path "secret/data/keycloak" {
  capabilities = ["read"]
}

path "secret/data/services/*" {
  capabilities = ["read"]
}
```

```hcl
# deploy/vault/policies/admin.hcl

# Full admin access
path "secret/*" {
  capabilities = ["create", "read", "update", "delete", "list"]
}

path "sys/policies/*" {
  capabilities = ["create", "read", "update", "delete", "list"]
}

path "auth/*" {
  capabilities = ["create", "read", "update", "delete", "list"]
}
```

### Apply Policies

```bash
docker exec vault vault policy write app-readonly /vault/policies/app-readonly.hcl
docker exec vault vault policy write admin /vault/policies/admin.hcl
```

---

## Authentication Methods

### AppRole for Services

AppRole provides machine authentication for services:

```bash
# Enable AppRole auth
docker exec vault vault auth enable approle

# Create role for applications
docker exec vault vault write auth/approle/role/app-services \
  token_policies="app-readonly" \
  token_ttl="1h" \
  token_max_ttl="4h" \
  secret_id_ttl="720h"

# Get RoleID (safe to expose)
docker exec vault vault read auth/approle/role/app-services/role-id

# Generate SecretID (keep secure)
docker exec vault vault write -f auth/approle/role/app-services/secret-id
```

### Service Authentication

Services authenticate using RoleID and SecretID:

```bash
# Login and get token
vault write auth/approle/login \
  role_id="<role_id>" \
  secret_id="<secret_id>"
```

### Token Authentication for CI/CD

Create a limited token for CI/CD pipelines:

```bash
docker exec vault vault token create \
  -policy="app-readonly" \
  -ttl="720h" \
  -display-name="gitlab-ci"
```

---

## Audit Logging

### Enable Audit Log

```bash
# File-based audit log
docker exec vault vault audit enable file file_path=/vault/logs/audit.log
```

### Log Format

Audit logs record all operations:
```json
{
  "time": "2026-01-20T10:00:00Z",
  "type": "request",
  "auth": {
    "token_type": "service",
    "policies": ["app-readonly"]
  },
  "request": {
    "path": "secret/data/postgres",
    "operation": "read"
  }
}
```

### Log Rotation

Configure logrotate:
```
# /etc/logrotate.d/vault
/vault/logs/audit.log {
    daily
    rotate 30
    compress
    delaycompress
    notifempty
    create 0640 vault vault
    postrotate
        docker exec vault vault audit disable file
        docker exec vault vault audit enable file file_path=/vault/logs/audit.log
    endscript
}
```

---

## Application Integration

### Spring Boot Configuration

```yaml
# application.yml
spring:
  cloud:
    vault:
      enabled: true
      uri: https://vault:8200
      authentication: APPROLE
      app-role:
        role-id: ${VAULT_ROLE_ID}
        secret-id: ${VAULT_SECRET_ID}
      kv:
        enabled: true
        backend: secret
```

### Docker Compose Service Configuration

```yaml
services:
  incident-svc:
    environment:
      SPRING_CLOUD_VAULT_URI: https://vault:8200
      VAULT_ROLE_ID: ${VAULT_ROLE_ID}
      VAULT_SECRET_ID: ${VAULT_SECRET_ID}
    depends_on:
      vault:
        condition: service_healthy
```

### Direct API Access

```bash
# Read secret via API
curl -s \
  -H "X-Vault-Token: ${VAULT_TOKEN}" \
  https://vault:8200/v1/secret/data/postgres | jq '.data.data'
```

---

## Operational Procedures

### Unsealing After Restart

Vault seals itself on restart. To unseal:

```bash
# Check if sealed
docker exec vault vault status | grep Sealed

# If sealed, unseal with 3 keys
docker exec vault vault operator unseal <key1>
docker exec vault vault operator unseal <key2>
docker exec vault vault operator unseal <key3>
```

### Auto-Unseal (Optional)

For automated unseal, consider:

1. **AWS KMS Auto-Unseal**
   ```hcl
   seal "awskms" {
     region     = "us-east-1"
     kms_key_id = "alias/vault-unseal"
   }
   ```

2. **GCP KMS Auto-Unseal**
   ```hcl
   seal "gcpckms" {
     project     = "my-project"
     region      = "global"
     key_ring    = "vault-keyring"
     crypto_key  = "vault-key"
   }
   ```

### Secret Rotation

```bash
# Rotate PostgreSQL password
NEW_PASS=$(openssl rand -base64 32)

# Update in Vault
docker exec vault vault kv put secret/postgres \
  username="ird_production_user" \
  password="$NEW_PASS"

# Update in PostgreSQL
docker exec postgres psql -U postgres -c \
  "ALTER USER ird_production_user PASSWORD '$NEW_PASS'"

# Restart services to pick up new secret
docker compose restart
```

### Backup Procedures

```bash
# Backup Vault data
docker exec vault vault operator raft snapshot save /vault/backup.snap
docker cp vault:/vault/backup.snap ./vault-backup-$(date +%Y%m%d).snap

# Encrypt backup
gpg --encrypt --recipient admin@company.com vault-backup-*.snap

# Store in secure location (S3, etc.)
```

---

## Troubleshooting

### Vault Won't Start

```bash
# Check logs
docker logs vault

# Common issues:
# - Permission denied on /vault/data
# - TLS certificate issues
# - Port already in use
```

### Cannot Authenticate

```bash
# Verify Vault is unsealed
docker exec vault vault status

# Test token
docker exec vault vault token lookup

# Check policies
docker exec vault vault token capabilities secret/data/postgres
```

### Services Can't Connect

```bash
# Test connectivity
docker exec incident-svc curl -k https://vault:8200/v1/sys/health

# Check DNS resolution
docker exec incident-svc nslookup vault

# Verify certificate
openssl s_client -connect vault:8200 -servername vault
```

---

## Security Checklist

- [ ] Root token revoked or secured
- [ ] Unseal keys distributed to multiple people
- [ ] TLS enabled and verified
- [ ] Audit logging enabled
- [ ] Policies follow least-privilege
- [ ] AppRole SecretIDs rotated regularly
- [ ] Backup procedures tested
- [ ] No secrets in environment variables
- [ ] Vault container has limited capabilities

---

## Next Steps

1. Generate TLS certificates
2. Initialize Vault with key shares
3. Configure application policies
4. Set up AppRole authentication
5. Update application configurations
6. Test secret retrieval
7. Enable audit logging
8. Document unseal procedures for team

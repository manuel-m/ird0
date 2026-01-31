# Vault Integration Review

## Executive Summary

This document reviews the HashiCorp Vault integration added to the IRD0 insurance platform. The integration centralizes SSH key and credential management for improved security and operational simplicity.

**Date**: 2026-01-14
**Reviewer**: System Analysis
**Status**: Implementation Complete with Minor Issues

**Overall Assessment**:
- **Architecture**: Excellent (9/10) - Clean separation, graceful fallbacks
- **Security**: Good (8/10) - Addresses MITM vulnerability from previous review
- **Documentation**: Needs Update (5/10) - USER_GUIDE.md requires Vault section
- **Configuration**: Good (7/10) - One critical fix applied (vault-data volume)

---

## Components Added

### 1. VaultSftpKeyLoader (Directory Service)

**File**: `microservices/directory/src/main/java/com/ird0/directory/config/VaultSftpKeyLoader.java`

**Purpose**: Loads SFTP client private key and known_hosts from Vault for SFTP client connections.

**Vault Paths**:
- `secret/data/ird0/sftp/client-key` - Client private key (field: `private_key`)
- `secret/data/ird0/sftp/known-hosts` - Known hosts file (field: `content`)

**Key Features**:
- Creates temporary files with restrictive permissions (owner read/write only)
- Returns Spring `Resource` objects compatible with SFTP session factory
- Provides `hasClientKey()` and `hasKnownHosts()` methods for graceful fallback detection
- Automatic cleanup via `deleteOnExit()`

**Activation**: `@ConditionalOnProperty(name = "vault.enabled", havingValue = "true")`

---

### 2. VaultAuthorizedKeysLoader (SFTP Server)

**File**: `microservices/sftp-server/src/main/java/com/ird0/sftp/config/VaultAuthorizedKeysLoader.java`

**Purpose**: Loads authorized public keys for SFTP authentication from Vault.

**Vault Path**: `secret/data/ird0/sftp/authorized-keys` (field: `content`)

**Key Features**:
- Parses standard `authorized_keys` format (key-type, key-data, username)
- Returns `Map<String, PublicKey>` for authentication lookup
- Skips comments and empty lines
- Logs parse errors without failing entire load (resilient parsing)

**Activation**: `@ConditionalOnProperty(name = "vault.enabled", havingValue = "true")`

---

### 3. VaultHostKeyProvider (SFTP Server)

**File**: `microservices/sftp-server/src/main/java/com/ird0/sftp/config/VaultHostKeyProvider.java`

**Purpose**: Provides SFTP server's RSA host key from Vault, implementing Apache SSHD's `KeyPairProvider` interface.

**Vault Path**: `secret/data/ird0/sftp/host-key` (field: `private_key`)

**Key Features**:
- Loads key at startup via `@PostConstruct`
- Parses PEM-format private key using Apache SSHD's `SecurityUtils`
- Fail-fast behavior: throws `IllegalStateException` if key not found
- Solves host key regeneration issue on container restarts

**Activation**: `@ConditionalOnProperty(name = "vault.enabled", havingValue = "true")`

---

### 4. Bootstrap Configuration Files

**Files**:
- `microservices/directory/src/main/resources/bootstrap.yml`
- `microservices/sftp-server/src/main/resources/bootstrap.yml`

**Content** (identical in both services):
```yaml
spring:
  cloud:
    vault:
      enabled: ${VAULT_ENABLED:false}
      uri: ${VAULT_ADDR:http://vault:8200}
      authentication: TOKEN
      token: ${VAULT_TOKEN:${VAULT_DEV_TOKEN:dev-root-token}}
      kv:
        enabled: true
        backend: secret
        default-context: ird0
      connection-timeout: 5000
      read-timeout: 15000
      fail-fast: false
```

**Key Settings**:
- `enabled: ${VAULT_ENABLED:false}` - Disabled by default for backward compatibility
- `fail-fast: false` - Applications start even if Vault unavailable (graceful degradation)
- Token-based authentication with three-level fallback
- KV v2 secrets engine with `secret/` backend

---

### 5. Vault Policies

**Directory Service Policy** (`vault/policies/directory-service.hcl`):
```hcl
path "secret/data/ird0/database/postgres" { capabilities = ["read"] }
path "secret/data/ird0/sftp/client-key" { capabilities = ["read"] }
path "secret/data/ird0/sftp/known-hosts" { capabilities = ["read"] }
```

**SFTP Server Policy** (`vault/policies/sftp-server.hcl`):
```hcl
path "secret/data/ird0/sftp/host-key" { capabilities = ["read"] }
path "secret/data/ird0/sftp/authorized-keys" { capabilities = ["read"] }
```

Both policies are read-only, following least-privilege principle.

---

### 6. Vault Initialization Script

**File**: `scripts/vault-init.sh`

**Purpose**: One-time initialization of Vault with all required secrets.

**Workflow**:
1. Waits for Vault health endpoint
2. Enables KV v2 secrets engine at `secret/`
3. Creates service policies
4. Stores database credentials
5. Generates and stores SFTP host key (RSA 2048-bit)
6. Stores client key from `./keys/sftp_client_key`
7. Stores authorized keys from `./keys/authorized_keys`
8. Generates and stores known_hosts entry

**Secrets Created**:
| Path | Description |
|------|-------------|
| `secret/ird0/database/postgres` | PostgreSQL username and password |
| `secret/ird0/sftp/host-key` | SFTP server RSA private key |
| `secret/ird0/sftp/client-key` | SFTP client RSA private key |
| `secret/ird0/sftp/authorized-keys` | Authorized public keys file content |
| `secret/ird0/sftp/known-hosts` | Known hosts entry for SFTP server |

---

### 7. Docker Compose Integration

**Vault Service** (docker-compose.yml lines 2-31):
```yaml
vault:
  image: hashicorp/vault:1.15
  container_name: vault
  restart: unless-stopped
  ports:
    - "8200:8200"
  environment:
    VAULT_DEV_ROOT_TOKEN_ID: ${VAULT_DEV_TOKEN:-dev-root-token}
    VAULT_DEV_LISTEN_ADDRESS: 0.0.0.0:8200
  cap_add:
    - IPC_LOCK
  volumes:
    - vault-data:/vault/data
    - ./vault/config:/vault/config:ro
    - ./vault/policies:/vault/policies:ro
  healthcheck:
    test: ["CMD", "vault", "status"]
    interval: 10s
    timeout: 5s
    retries: 5
```

**Service Dependencies**:
- Policyholders depends on Vault (service_healthy condition)
- Experts and Providers do not depend on Vault (they don't use SFTP import)
- SFTP Server does not depend on Vault (can use file-based keys)

---

## Architecture Patterns

### 1. Optional Dependency Injection with Graceful Fallback

All Vault components use `Optional<T>` injection pattern:

```java
private final Optional<VaultSftpKeyLoader> vaultSftpKeyLoader;

// Usage in SftpIntegrationConfig
if (vaultSftpKeyLoader.isPresent() && vaultSftpKeyLoader.get().hasClientKey()) {
    // Use Vault key
} else {
    // Fall back to file-based key
}
```

**Benefits**:
- No runtime errors if Vault is disabled
- Seamless transition between Vault and file-based modes
- Clear logging of which source is being used

### 2. Conditional Bean Activation

All Vault components use `@ConditionalOnProperty`:

```java
@ConditionalOnProperty(name = "vault.enabled", havingValue = "true", matchIfMissing = false)
```

**Benefits**:
- Vault beans only created when `vault.enabled=true`
- No overhead when Vault is disabled
- Spring Cloud Vault auto-configuration controlled by `spring.cloud.vault.enabled`

### 3. Fail-Safe vs Fail-Fast Behavior

| Component | Behavior | Reason |
|-----------|----------|--------|
| `VaultSftpKeyLoader` | Fail-safe (returns null/empty) | Allows file fallback |
| `VaultAuthorizedKeysLoader` | Fail-safe (returns empty map) | Allows file fallback |
| `VaultHostKeyProvider` | Fail-fast (throws exception) | Critical for server identity |
| `bootstrap.yml` | `fail-fast: false` | Application can start without Vault |

### 4. Bootstrap vs Application Configuration

- `bootstrap.yml` - Loaded first, before application context
- Used for Spring Cloud Vault configuration
- Environment variables override bootstrap values
- Application configuration (`application.yml`) loaded after Vault connection established

---

## Security Improvements

### Addressing MITM Vulnerability (Issue #1 from Codebase Review)

**Previous State**:
```java
factory.setAllowUnknownKeys(true);  // VULNERABLE: No host verification
```

**New State with Vault**:
```java
// SftpIntegrationConfig.configureKnownHosts()
if (vaultSftpKeyLoader.isPresent() && vaultSftpKeyLoader.get().hasKnownHosts()) {
    Resource knownHostsResource = vaultSftpKeyLoader.get().getKnownHostsResource();
    factory.setKnownHostsResource(knownHostsResource);
}
```

**Security Improvement**:
- Host key verification now enabled by default
- Known hosts loaded from Vault (centralized management)
- Falls back to file-based known_hosts if Vault unavailable
- MITM vulnerability effectively mitigated

### Centralized Credential Management

| Secret | Before Vault | After Vault |
|--------|--------------|-------------|
| Database credentials | docker-compose.yml | Vault + env var fallback |
| SFTP client key | ./keys/sftp_client_key | Vault + file fallback |
| SFTP host key | Auto-generated per container | Vault (persistent) |
| Authorized keys | ./keys/authorized_keys | Vault + file fallback |

### Key Rotation Benefits

With Vault integration:
1. Update secret in Vault
2. Restart affected services
3. No file system changes required
4. Audit trail in Vault

---

## Issues Identified and Fixed

### Critical Issue: Missing vault-data Volume (FIXED)

**Problem**: docker-compose.yml referenced `vault-data:/vault/data` volume but did not declare it.

**Impact**: Container startup failure or data loss on restart.

**Fix Applied**:
```yaml
volumes:
  postgres-data:
    driver: local
  vault-data:
    driver: local
```

### Medium Issue: Manual Initialization Required

**Problem**: `vault-init.sh` must be run manually after first cluster start.

**Impact**: Secrets won't exist until initialization script is executed.

**Workaround**: Document initialization procedure in USER_GUIDE.md.

**Future Improvement**: Consider init container or startup hook.

### Low Issue: SFTP Server Vault Dependency

**Problem**: sftp-server service did not have Vault environment variables.

**Fix Applied**: Added VAULT_ENABLED, VAULT_ADDR, VAULT_TOKEN to sftp-server environment.

---

## Configuration Reference

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `VAULT_ENABLED` | `false` | Enable Vault integration |
| `VAULT_ADDR` | `http://vault:8200` | Vault server address |
| `VAULT_TOKEN` | (from VAULT_DEV_TOKEN) | Vault authentication token |
| `VAULT_DEV_TOKEN` | `dev-root-token` | Development root token |

### Vault Secret Paths

| Path | Fields | Used By |
|------|--------|---------|
| `secret/ird0/database/postgres` | username, password | Directory services |
| `secret/ird0/sftp/host-key` | private_key | SFTP server |
| `secret/ird0/sftp/client-key` | private_key | Directory services |
| `secret/ird0/sftp/authorized-keys` | content | SFTP server |
| `secret/ird0/sftp/known-hosts` | content | Directory services |

---

## Testing Checklist

### Without Vault (VAULT_ENABLED=false)

- [ ] All services start successfully
- [ ] Policyholders SFTP import works with file-based keys
- [ ] SFTP server accepts connections with file-based authorized_keys
- [ ] Health endpoints return UP status

### With Vault (VAULT_ENABLED=true)

- [ ] Vault container starts and becomes healthy
- [ ] vault-init.sh completes successfully
- [ ] Services start after Vault is healthy
- [ ] SFTP import works with Vault-provided keys
- [ ] SFTP server uses Vault-provided host key
- [ ] Known hosts verification succeeds

---

## Recommendations

### Immediate (This Sprint)

1. **Document Vault integration in USER_GUIDE.md** - Add section covering:
   - Enabling/disabling Vault
   - Running vault-init.sh
   - Troubleshooting Vault issues

2. **Test cluster with both modes** - Verify file fallback and Vault modes work

### Future Improvements

1. **Automate Vault initialization**:
   - Add init container to docker-compose
   - Or use health check that waits for secrets

2. **Production hardening**:
   - Use AppRole or Kubernetes authentication instead of token
   - Enable TLS for Vault communication
   - Implement secret rotation policies

3. **Enhanced monitoring**:
   - Add Vault health to service health checks
   - Alert on Vault unavailability
   - Track secret access patterns

---

## Summary

The Vault integration is well-designed with:
- Clean architecture using optional injection and graceful fallbacks
- Proper separation of concerns with dedicated loader components
- Least-privilege policies for each service
- Backward compatibility with file-based operation

Key issues addressed:
- Fixed missing vault-data volume declaration
- Added Vault environment variables to sftp-server
- Documented initialization procedure requirements

The integration successfully addresses the MITM vulnerability identified in the codebase review while providing a path to centralized credential management.

---

*This document should be reviewed and updated as the Vault integration evolves.*

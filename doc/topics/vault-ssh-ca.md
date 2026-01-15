# Vault SSH Certificate Authority

## Overview

The IRD0 platform integrates HashiCorp Vault SSH Secrets Engine in CA (Certificate Authority) mode to provide dynamic, short-lived SSH certificates for SFTP authentication. This replaces static SSH key management with a more secure, auditable approach.

**Key Features:**
- Ephemeral certificates with 15-minute TTL (configurable)
- Forward secrecy via per-connection key pair generation
- Centralized certificate authority managed by Vault
- Five-step certificate verification on SFTP server
- Complete audit trail for authentication events
- Automatic certificate renewal before expiration
- Graceful fallback to static keys when Vault unavailable

## Architecture

### Component Overview

```
                                   HashiCorp Vault
                                  +-----------------+
                                  | SSH CA Engine   |
                                  | (ssh-client-    |
                                  |  signer)        |
                                  +-----------------+
                                         ^
                                         | Sign certificate
                                         | (POST /sign/directory-service)
                                         |
+-----------------------+          +-----+-----+
| Policyholders Service |          |           |
|                       |--------->| Vault API |
| - SshCertificateManager         |           |
| - VaultSshCertificateSigner     +-----------+
| - EphemeralKeyPairGenerator           |
| - MinaSftpClient              Read CA |
+-----------------------+        public |
         |                        key   |
         | SSH with certificate         v
         |                    +-------------------+
         +------------------->| SFTP Server       |
                              |                   |
                              | - CertificateAuthenticator
                              | - VaultCaTrustProvider
                              | - CertificateAuditLogger
                              +-------------------+
```

### Certificate Flow

```
1. CERTIFICATE REQUEST (Policyholders Service)
   +---------------------------------------------------------+
   | EphemeralKeyPairGenerator                               |
   |   Generate RSA-4096 key pair in memory                  |
   +---------------------------------------------------------+
                            |
                            v
   +---------------------------------------------------------+
   | VaultSshCertificateSigner                               |
   |   POST vault/ssh-client-signer/sign/directory-service   |
   |   Body: {                                               |
   |     public_key: "ssh-rsa AAAA...",                      |
   |     valid_principals: "policyholder-importer",          |
   |     ttl: "900s",                                        |
   |     cert_type: "user"                                   |
   |   }                                                     |
   +---------------------------------------------------------+
                            |
                            v
   +---------------------------------------------------------+
   | Vault SSH CA                                            |
   |   Signs public key with CA private key                  |
   |   Returns: { signed_key: "...", serial_number: "..." }  |
   +---------------------------------------------------------+
                            |
                            v
   +---------------------------------------------------------+
   | SshCertificateManager                                   |
   |   Caches SignedCertificate (keyPair + signedKey)        |
   |   Schedules renewal when 5 min remaining                |
   +---------------------------------------------------------+

2. SFTP AUTHENTICATION
   +---------------------------------------------------------+
   | MinaSftpClient                                          |
   |   Creates SSH session with certificate                  |
   |   session.addPublicKeyIdentity(keyPair)                 |
   +---------------------------------------------------------+
                            |
                            v
   +---------------------------------------------------------+
   | CertificateAuthenticator (SFTP Server)                  |
   |   Step 1: Verify it's a certificate (not raw key)       |
   |   Step 2: Verify CA signature                           |
   |   Step 3: Verify validity period (not expired)          |
   |   Step 4: Verify principal matches username             |
   |   Step 5: Verify certificate type is USER               |
   +---------------------------------------------------------+
                            |
                            v
   +---------------------------------------------------------+
   | CertificateAuditLogger                                  |
   |   [AUDIT] AUTH_SUCCESS/AUTH_REJECTED with details       |
   +---------------------------------------------------------+
```

## Vault Configuration

### SSH Secrets Engine Setup

The `scripts/vault-init.sh` configures Vault SSH CA mode:

```bash
# Enable SSH secrets engine for client certificate signing
vault secrets enable -path=ssh-client-signer ssh

# Configure CA (generates signing key pair if needed)
vault write ssh-client-signer/config/ca generate_signing_key=true

# Create role for directory service with specific constraints
vault write ssh-client-signer/roles/directory-service \
    key_type=ca \
    algorithm_signer=rsa-sha2-256 \
    allow_user_certificates=true \
    allowed_users="policyholder-importer" \
    ttl=15m \
    max_ttl=1h \
    default_user="policyholder-importer"
```

**Role Parameters:**

| Parameter | Value | Purpose |
|-----------|-------|---------|
| `key_type` | ca | Enable CA signing mode |
| `algorithm_signer` | rsa-sha2-256 | Signature algorithm |
| `allow_user_certificates` | true | Allow user certificates (not host) |
| `allowed_users` | policyholder-importer | Principals allowed in certificates |
| `ttl` | 15m | Default certificate lifetime |
| `max_ttl` | 1h | Maximum certificate lifetime |
| `default_user` | policyholder-importer | Default principal if not specified |

### Vault Policies

**Directory Service Policy (`vault/policies/directory-service.hcl`):**

```hcl
# Read database credentials
path "secret/data/ird0/database/postgres" {
    capabilities = ["read"]
}

# Sign SSH certificates
path "ssh-client-signer/sign/directory-service" {
    capabilities = ["create", "update"]
}

# Read CA public key for verification
path "ssh-client-signer/config/ca" {
    capabilities = ["read"]
}
```

**SFTP Server Policy (`vault/policies/sftp-server.hcl`):**

```hcl
# Read host private key
path "secret/data/ird0/sftp/host-key" {
    capabilities = ["read"]
}

# Read CA public key for certificate verification
path "ssh-client-signer/config/ca" {
    capabilities = ["read"]
}
```

**Security Note:** The SFTP server cannot sign certificates (no access to `sign/` path). The directory service cannot read the host key. This implements least-privilege access.

### Secrets Stored in Vault

| Path | Fields | Purpose |
|------|--------|---------|
| `secret/ird0/database/postgres` | `username`, `password` | PostgreSQL credentials |
| `secret/ird0/sftp/host-key` | `private_key` | SFTP server RSA private key |
| `secret/ird0/sftp/known-hosts` | `content` | Known hosts entry |
| `secret/ird0/sftp/client-key` | `private_key` | Legacy client key (fallback) |
| `secret/ird0/sftp/authorized-keys` | `content` | Legacy authorized keys (fallback) |
| `ssh-client-signer/config/ca` | `public_key` | CA public key |

## Certificate Management

### Ephemeral Key Pair Generation

**File:** `EphemeralKeyPairGenerator.java`

Each SFTP connection uses a fresh RSA-4096 key pair generated in memory:

```java
public KeyPair generateKeyPair() {
    KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
    keyGen.initialize(4096, secureRandom);
    return keyGen.generateKeyPair();
}
```

**Security Properties:**
- Forward secrecy: Each connection uses different keys
- No disk persistence: Keys exist only in memory
- Strong cryptography: RSA-4096 bit keys

### Certificate Signing

**File:** `VaultSshCertificateSigner.java`

Requests certificate from Vault SSH CA:

```java
public SignedCertificate signPublicKey(KeyPair keyPair) {
    // Convert public key to OpenSSH format
    String publicKeyOpenSsh = convertToOpenSshFormat(keyPair.getPublic());

    // Request certificate from Vault
    Map<String, Object> request = Map.of(
        "public_key", publicKeyOpenSsh,
        "valid_principals", properties.getPrincipal(),
        "ttl", properties.getTtl().toSeconds() + "s",
        "cert_type", "user"
    );

    VaultResponse response = vaultTemplate.write(
        "ssh-client-signer/sign/directory-service",
        request
    );

    return SignedCertificate.builder()
        .signedPublicKey(response.getData().get("signed_key"))
        .serial(response.getData().get("serial_number"))
        .keyPair(keyPair)
        .expiresAt(Instant.now().plus(properties.getTtl()))
        .build();
}
```

### Certificate Lifecycle Management

**File:** `SshCertificateManager.java`

Manages certificate caching and renewal:

```java
public SignedCertificate getCurrentCertificate() {
    // Lazy initialization on first use
    if (!initialized && cert == null) {
        renewalLock.lock();
        try {
            obtainNewCertificate();
            initialized = true;
        } finally {
            renewalLock.unlock();
        }
    }

    // Check if renewal needed (within threshold)
    if (cert.needsRenewal(renewalThreshold)) {
        triggerAsyncRenewal();
    }

    return cert;
}

// Scheduled safety net (every 60 seconds)
@Scheduled(fixedRate = 60000)
public void checkAndRenew() {
    if (cert != null && cert.needsRenewal(renewalThreshold)) {
        obtainNewCertificate();
    }
}
```

**Renewal Strategy:**
- Lazy loading: First certificate obtained on first SFTP connection
- Proactive renewal: Renewed when 5 minutes remaining (out of 15 min TTL)
- Scheduled check: Every 60 seconds as safety net
- Thread-safe: Double-checked locking prevents race conditions

### SignedCertificate Data Structure

**File:** `SignedCertificate.java`

```java
@Builder
public class SignedCertificate {
    private final String signedPublicKey;  // Vault-signed certificate
    private final String serial;            // Certificate serial number
    private final String principal;         // "policyholder-importer"
    private final Instant issuedAt;         // When issued
    private final Instant expiresAt;        // When expires
    private final KeyPair keyPair;          // Ephemeral key pair

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean needsRenewal(Duration threshold) {
        return Instant.now().plus(threshold).isAfter(expiresAt);
    }

    public Duration getRemainingValidity() {
        return Duration.between(Instant.now(), expiresAt);
    }
}
```

## SFTP Server Certificate Verification

### Five-Step Verification Process

**File:** `CertificateAuthenticator.java`

```java
@Override
public boolean authenticate(String username, PublicKey key, ServerSession session) {
    String clientAddress = session.getClientAddress().toString();

    // Step 1: Verify it's a certificate, not raw public key
    if (!(key instanceof OpenSshCertificate)) {
        auditLogger.logRejection(username, clientAddress, "RAW_KEY_NOT_ALLOWED");
        return false;
    }

    OpenSshCertificate certificate = (OpenSshCertificate) key;

    // Step 2: Verify CA signature
    if (!verifyCaSignature(certificate)) {
        auditLogger.logRejection(username, clientAddress, "INVALID_CA_SIGNATURE");
        return false;
    }

    // Step 3: Verify validity period (not expired)
    if (!verifyCertificateValidity(certificate)) {
        auditLogger.logRejection(username, clientAddress, "CERTIFICATE_EXPIRED");
        return false;
    }

    // Step 4: Verify username matches certificate principal
    if (!verifyPrincipal(certificate, username)) {
        auditLogger.logRejection(username, clientAddress, "PRINCIPAL_MISMATCH");
        return false;
    }

    // Step 5: Verify it's a user certificate (not host)
    if (!verifyCertificateType(certificate)) {
        auditLogger.logRejection(username, clientAddress, "INVALID_CERT_TYPE");
        return false;
    }

    String certSerial = String.valueOf(certificate.getSerial());
    Instant validUntil = Instant.ofEpochSecond(certificate.getValidBefore());
    auditLogger.logSuccess(username, clientAddress, certSerial, validUntil);

    return true;
}
```

**Verification Details:**

| Step | Check | Rejection Reason |
|------|-------|------------------|
| 1 | Is OpenSshCertificate instance | RAW_KEY_NOT_ALLOWED |
| 2 | CA key matches trusted CA | INVALID_CA_SIGNATURE |
| 3 | Now within validAfter..validBefore | CERTIFICATE_EXPIRED |
| 4 | Username in certificate principals | PRINCIPAL_MISMATCH |
| 5 | Certificate type is USER | INVALID_CERT_TYPE |

### CA Trust Provider

**File:** `VaultCaTrustProvider.java`

Loads and caches the CA public key from Vault:

```java
public PublicKey getCaPublicKey() {
    if (!initialized) {
        synchronized (lock) {
            loadCaPublicKey();
            initialized = true;
        }
    }
    return caPublicKey;
}

private void loadCaPublicKey() {
    VaultResponse response = vaultTemplate.read("ssh-client-signer/config/ca");
    String publicKeyData = (String) response.getData().get("public_key");

    // Parse OpenSSH format public key
    PublicKeyEntry entry = PublicKeyEntry.parsePublicKeyEntry(publicKeyData);
    this.caPublicKey = entry.resolvePublicKey(null, null, null);
}
```

## Audit Logging

### CertificateAuditLogger

**File:** `CertificateAuditLogger.java`

Structured logging for security monitoring:

```java
// Successful authentication
public void logSuccess(String username, String clientAddress,
                       String certificateSerial, Instant validUntil) {
    log.info("[AUDIT] AUTH_SUCCESS username={} clientAddress={} " +
             "certificateSerial={} validUntil={}",
             username, clientAddress, certificateSerial, validUntil);
}

// Failed authentication
public void logRejection(String username, String clientAddress, String reason) {
    log.warn("[AUDIT] AUTH_REJECTED username={} clientAddress={} reason={}",
             username, clientAddress, reason);
}

// Certificate renewal
public void logRenewal(String username, String oldSerial,
                       String newSerial, Instant newValidUntil) {
    log.info("[AUDIT] CERT_RENEWED username={} oldSerial={} " +
             "newSerial={} newValidUntil={}",
             username, oldSerial, newSerial, newValidUntil);
}
```

**Audit Events:**

| Event | Level | Fields |
|-------|-------|--------|
| AUTH_SUCCESS | INFO | username, clientAddress, certificateSerial, validUntil |
| AUTH_REJECTED | WARN | username, clientAddress, reason |
| CERT_RENEWED | INFO | username, oldSerial, newSerial, newValidUntil |

**Rejection Reasons:**
- `RAW_KEY_NOT_ALLOWED` - Attempted with non-certificate key
- `INVALID_CA_SIGNATURE` - Certificate not signed by trusted CA
- `CERTIFICATE_EXPIRED` - Certificate validity period expired
- `PRINCIPAL_MISMATCH` - Username doesn't match certificate
- `INVALID_CERT_TYPE` - Not a user certificate

## Configuration

### Service Configuration

**Policyholders Service (`policyholders.yml`):**

```yaml
directory:
  sftp-import:
    enabled: true
    certificate:
      ttl: 15m                          # Certificate valid for 15 minutes
      renewal-threshold: 5m             # Renew when 5 minutes remaining
      principal: policyholder-importer  # Must match Vault role
      vault-role: directory-service     # Vault role name

vault:
  ssh:
    ca:
      enabled: ${VAULT_SSH_CA_ENABLED:true}
```

**Bootstrap Configuration (`bootstrap.yml`):**

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
      fail-fast: false  # Continue if Vault unavailable
```

### Docker Compose Configuration

```yaml
services:
  vault:
    image: hashicorp/vault:1.15
    ports:
      - "8200:8200"
    environment:
      VAULT_DEV_ROOT_TOKEN_ID: ${VAULT_DEV_TOKEN:-dev-root-token}
      VAULT_DEV_LISTEN_ADDRESS: 0.0.0.0:8200
    volumes:
      - ./vault/policies:/vault/policies:ro
      - vault-data:/vault/data
    healthcheck:
      test: ["CMD", "vault", "status"]
      interval: 10s
      timeout: 5s
      retries: 3

  policyholders:
    depends_on:
      vault:
        condition: service_healthy
    environment:
      - VAULT_ENABLED=true
      - VAULT_SSH_CA_ENABLED=true
      - VAULT_ADDR=http://vault:8200
      - VAULT_TOKEN=${VAULT_DEV_TOKEN:-dev-root-token}

  sftp-server:
    depends_on:
      vault:
        condition: service_healthy
    environment:
      - VAULT_ENABLED=true
      - VAULT_SSH_CA_ENABLED=true
      - VAULT_ADDR=http://vault:8200
      - VAULT_TOKEN=${VAULT_DEV_TOKEN:-dev-root-token}
```

### Environment Variables

| Variable | Default | Purpose |
|----------|---------|---------|
| `VAULT_ENABLED` | false | Enable Vault integration |
| `VAULT_SSH_CA_ENABLED` | true | Enable SSH CA mode (when Vault enabled) |
| `VAULT_ADDR` | http://vault:8200 | Vault server address |
| `VAULT_TOKEN` | dev-root-token | Vault authentication token |
| `VAULT_DEV_TOKEN` | dev-root-token | Development root token |

## Operations

### Initial Setup

```bash
# Start the cluster
docker compose up -d

# Wait for Vault to be healthy
docker compose exec vault vault status

# Initialize Vault with SSH CA
./scripts/vault-init.sh

# Restart services to pick up Vault configuration
docker compose restart policyholders sftp-server
```

### Verifying Vault Status

```bash
# Check Vault health
curl http://localhost:8200/v1/sys/health

# Check Vault status via CLI
docker compose exec vault vault status

# List SSH CA roles
docker compose exec -e VAULT_TOKEN=dev-root-token vault \
    vault list ssh-client-signer/roles

# Read CA public key
docker compose exec -e VAULT_TOKEN=dev-root-token vault \
    vault read ssh-client-signer/config/ca
```

### Monitoring Certificates

```bash
# Watch certificate authentication events
docker compose logs -f policyholders | grep "\[AUDIT\]"
docker compose logs -f sftp-server | grep "\[AUDIT\]"

# Check certificate renewal
docker compose logs policyholders | grep "CERT_RENEWED"

# Check authentication failures
docker compose logs sftp-server | grep "AUTH_REJECTED"
```

### Certificate Troubleshooting

**Certificate not obtained:**
```bash
# Check Vault connectivity
docker compose exec policyholders wget -qO- http://vault:8200/v1/sys/health

# Check Vault token validity
docker compose exec policyholders env | grep VAULT

# Check role exists
docker compose exec -e VAULT_TOKEN=dev-root-token vault \
    vault read ssh-client-signer/roles/directory-service
```

**Authentication rejected:**
```bash
# Check rejection reason in SFTP server logs
docker compose logs sftp-server | grep "AUTH_REJECTED"

# Verify CA public key is loaded
docker compose logs sftp-server | grep "CA public key"

# Check certificate validity
docker compose logs policyholders | grep "certificate.*expires"
```

## Security Properties

### Defense Against Threats

| Threat | Mitigation |
|--------|------------|
| Stolen private key | Certificate expires in 15 minutes; compromise window limited |
| MITM attack | Client verifies server via known_hosts; server verifies certificate |
| Replayed certificate | Timestamp-based expiration in certificate |
| Wrong user certificate | Principal verification ensures certificate matches username |
| Host certificate as client | Type validation rejects non-user certificates |
| Compromised CA | CA key stored in Vault (not in application); can be rotated |
| Static password guessing | No passwords; certificate-based only |

### Security Best Practices

**Certificate TTL:**
- Production: 15 minutes (current default)
- High-security: 5 minutes
- Lower-security: 1 hour (max_ttl)

**Key Rotation:**
- CA key: Rotate annually or on suspected compromise
- Service credentials: Rotate with Vault token rotation

**Monitoring:**
- Alert on AUTH_REJECTED events
- Track certificate renewal failures
- Monitor Vault health continuously

## Graceful Fallback

When Vault is unavailable, services fall back to static key authentication:

| Component | Vault Missing | Fallback Behavior |
|-----------|---------------|-------------------|
| SFTP Client Key | Falls back to `./keys/sftp_client_key` | Logged as warning |
| Known Hosts | Falls back to `./keys/known_hosts` | Logged as warning |
| Authorized Keys | Falls back to `./keys/authorized_keys` | Logged as warning |
| Host Key | Falls back to auto-generation | Uses `./keys/hostkey.pem` |

**Configuration for fallback:**
```yaml
spring:
  cloud:
    vault:
      fail-fast: false  # Don't fail startup if Vault unavailable
```

## Key Files Reference

| File | Purpose |
|------|---------|
| `scripts/vault-init.sh` | One-time Vault initialization |
| `vault/policies/directory-service.hcl` | Directory service policy |
| `vault/policies/sftp-server.hcl` | SFTP server policy |
| `EphemeralKeyPairGenerator.java` | RSA-4096 key pair generation |
| `VaultSshCertificateSigner.java` | Certificate signing requests |
| `SshCertificateManager.java` | Certificate lifecycle management |
| `SignedCertificate.java` | Certificate data structure |
| `MinaSftpClient.java` | SFTP client with certificate auth |
| `CertificateAuthenticator.java` | Five-step verification |
| `VaultCaTrustProvider.java` | CA public key management |
| `CertificateAuditLogger.java` | Audit logging |

## Related Topics

- [ssh-keys.md](ssh-keys.md) - Static SSH key management (fallback mode)
- [sftp-import.md](sftp-import.md) - SFTP import architecture
- [configuration.md](configuration.md) - Configuration management
- [USER_GUIDE.md](../USER_GUIDE.md#hashicorp-vault-integration) - Operational procedures

## References

- [HashiCorp Vault SSH Secrets Engine](https://developer.hashicorp.com/vault/docs/secrets/ssh)
- [SSH Certificate Authentication](https://smallstep.com/blog/ssh-certificates/)
- [Apache MINA SSHD](https://github.com/apache/mina-sshd)
- [Spring Cloud Vault](https://docs.spring.io/spring-cloud-vault/docs/current/reference/html/)

# SSH Key Management

## Overview

The SFTP server uses SSH public key authentication for secure, password-less access. The system manages three types of keys: host keys (server identity), client private keys (authentication), and authorized public keys (access control).

**Key Features:**
- SSH public key authentication only (no passwords)
- RSA 2048-bit keys (Ed25519 not supported in Apache SSHD 2.12.0)
- Persistent host key across restarts
- Multiple users with separate keys
- Three-field authorized_keys format

## Key Types and Purposes

### 1. Host Key (Server Identity)

**File:** `keys/hostkey.pem`

**Purpose:**
- Identifies the SFTP server to clients
- Prevents man-in-the-middle attacks
- Verified by clients during connection

**Generation:**
- Auto-generated on first server startup
- RSA 2048-bit default
- Persisted in Docker bind mount (`./keys:/app/keys`)

**Implementation:**
```java
@Bean
public SshServer sshServer() {
    SshServer server = SshServer.setUpDefaultServer();

    // SimpleGeneratorHostKeyProvider auto-generates and persists key
    server.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(
        Paths.get(properties.getServer().getHostKeyPath())
    ));

    return server;
}
```

**Location:**
- Local development: `./keys/hostkey.pem`
- Docker: `/app/keys/hostkey.pem` (mapped to `./keys`)

**Characteristics:**
- Generated once, reused on subsequent startups
- PEM format, readable by OpenSSH tools
- Should be backed up (losing it requires client known_hosts updates)

### 2. Client Private Key (Authentication)

**File:** `keys/sftp_client_key` (Policyholders service uses this)

**Purpose:**
- Authenticates client to SFTP server
- Corresponds to public key in authorized_keys
- Used by Policyholders service for automated imports

**Generation:**
```bash
# Generate RSA key pair
ssh-keygen -t rsa -b 2048 -f keys/sftp_client_key -N ""
```

**Output:**
- `keys/sftp_client_key` - Private key (keep secure, never commit)
- `keys/sftp_client_key.pub` - Public key (add to authorized_keys)

**Usage in Policyholders Service:**
```yaml
directory:
  sftp-import:
    username: policyholder-importer
    private-key-path: ./keys/sftp_client_key
```

**Security:**
- File permissions: `chmod 600 keys/sftp_client_key`
- Never commit to version control
- Rotate periodically (update authorized_keys)

### 3. Authorized Public Keys (Access Control)

**File:** `keys/authorized_keys`

**Purpose:**
- Lists public keys allowed to authenticate
- Maps public keys to usernames
- Loaded once at SFTP server startup

**Format:** Three space-separated fields per line
```
<key-type> <key-data> <username>
```

**Example:**
```
ssh-rsa AAAAB3NzaC1yc2EAAAADAQAB... policyholder-importer
ssh-rsa AAAAB3NzaC1yc2EAAAADAQAB... data-analyst
ssh-rsa AAAAB3NzaC1yc2EAAAADAQAB... backup-service
```

**Important Rules:**
- **Three fields required**: key-type, key-data, username
- **Username format**: Plain username (NOT `user@host` format)
- **One key per line**
- **Comments**: Lines starting with `#` are ignored
- **Empty lines**: Ignored

## Host Key Generation

### SimpleGeneratorHostKeyProvider

**Component:** Apache MINA SSHD utility class

**Configuration:**
```java
KeyPairProvider keyPairProvider = new SimpleGeneratorHostKeyProvider(
    Paths.get("./keys/hostkey.pem")
);
```

**Behavior:**
1. Check if host key file exists
2. If not exists: Generate RSA 2048-bit key pair, save to file
3. If exists: Load existing key from file
4. Use key for all SFTP sessions

**Generation Details:**
- Algorithm: RSA
- Key size: 2048 bits
- Format: PEM (Privacy Enhanced Mail)
- Compatible with OpenSSH

**File Contents (example):**
```
-----BEGIN RSA PRIVATE KEY-----
MIIEpAIBAAKCAQEAr2fTvBjFm3...
[base64-encoded key data]
-----END RSA PRIVATE KEY-----
```

### Persistence Strategy

**Docker Volume Mount:**
```yaml
sftp-server:
  volumes:
    - ./keys:/app/keys
```

**Benefits:**
- Key survives container restarts
- Key survives container rebuilds
- Consistent server identity for clients
- Avoids "host key changed" warnings

**Client Verification:**

First connection:
```bash
$ sftp -P 2222 user@localhost
The authenticity of host '[localhost]:2222' can't be established.
RSA key fingerprint is SHA256:abc123...
Are you sure you want to continue connecting (yes/no)? yes
```

Subsequent connections: Silent (key matches known_hosts)

## Client Key Generation

### RSA Keys (Supported)

**Generate RSA key pair:**
```bash
# Generate 2048-bit RSA key without passphrase
ssh-keygen -t rsa -b 2048 -f ~/.ssh/sftp_test_key -N ""
```

**Output:**
- `~/.ssh/sftp_test_key` - Private key (600 permissions)
- `~/.ssh/sftp_test_key.pub` - Public key

**Public key format:**
```
ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQCr2fTvBjFm... user@hostname
```

**Options:**
- `-t rsa`: Key type (RSA)
- `-b 2048`: Key size (2048 bits, 4096 also supported)
- `-f ~/.ssh/sftp_test_key`: Output file path
- `-N ""`: Empty passphrase (automated use cases)

### Ed25519 Keys (NOT Supported)

**Limitation:** Apache SSHD 2.12.0 lacks Ed25519 decoder

**Error message:**
```
No decoder available for key type=ssh-ed25519
```

**Workaround:** Use RSA keys instead

**Future:** Upgrade to Apache SSHD 2.13+ for Ed25519 support

## Authorized Keys File Format

### Three-Field Format

**Required Format:**
```
<key-type> <key-data> <username>
```

**Field Descriptions:**

| Field | Description | Example |
|-------|-------------|---------|
| key-type | SSH key algorithm | `ssh-rsa` |
| key-data | Base64-encoded public key | `AAAAB3NzaC1yc2EAAAA...` |
| username | Plain username (no @host) | `policyholder-importer` |

**Example File:**
```
# Production SFTP users
ssh-rsa AAAAB3NzaC1yc2EAAAADAQAB... policyholder-importer
ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQDiff... data-analyst

# Backup service
ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQCano... backup-service
```

### Creating authorized_keys

**From generated public key:**
```bash
# Extract key-type and key-data, add custom username
awk '{print $1" "$2" your-username"}' ~/.ssh/sftp_test_key.pub > keys/authorized_keys
```

**Manual creation:**
```bash
# Copy public key contents (first two fields)
cat ~/.ssh/sftp_test_key.pub
# ssh-rsa AAAAB3NzaC1yc2E... user@hostname

# Create authorized_keys with custom username
echo "ssh-rsa AAAAB3NzaC1yc2E... policyholder-importer" > keys/authorized_keys
```

**Set permissions:**
```bash
chmod 600 keys/authorized_keys
```

### Common Format Errors

**WRONG: User@host format**
```
ssh-rsa AAAAB3NzaC1yc2E... user@hostname
```
Username extracted: `user@hostname` (invalid, includes @)

**WRONG: Missing username**
```
ssh-rsa AAAAB3NzaC1yc2E...
```
Error: "Invalid format (expected 3 fields)"

**WRONG: Four fields**
```
ssh-rsa AAAAB3NzaC1yc2E... username extra-field
```
Username extracted: `username` (extra field ignored)

**CORRECT:**
```
ssh-rsa AAAAB3NzaC1yc2E... username
```

## PublicKeyAuthenticator Implementation

### Loading Keys at Startup

**Component:** `PublicKeyAuthenticator.java`

**Initialization (`@PostConstruct`):**
```java
@PostConstruct
public void init() throws Exception {
    Path keysFile = Paths.get(properties.getServer().getAuthorizedKeysPath());

    // Validate file exists and is readable
    if (!Files.exists(keysFile)) {
        throw new IllegalStateException("Authorized keys file not found");
    }

    // Parse file line by line
    for (String line : Files.readAllLines(keysFile)) {
        String trimmed = line.trim();

        // Skip empty lines and comments
        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
            continue;
        }

        // Split into three fields
        String[] parts = trimmed.split("\\s+");
        if (parts.length < 3) {
            log.warn("Invalid format, skipping");
            continue;
        }

        String keyType = parts[0];     // ssh-rsa
        String keyData = parts[1];     // AAAAB3Nz...
        String username = parts[2];    // policyholder-importer

        // Parse public key
        String keyEntry = keyType + " " + keyData;
        PublicKeyEntry entry = PublicKeyEntry.parsePublicKeyEntry(keyEntry);
        PublicKey publicKey = entry.resolvePublicKey(null, null, null);

        // Store in memory map
        authorizedKeys.put(username, publicKey);
        log.info("Loaded authorized key for user: {}", username);
    }

    // Require at least one valid key
    if (authorizedKeys.isEmpty()) {
        throw new IllegalStateException("No valid authorized keys found");
    }
}
```

**Key Points:**
- Keys loaded once at startup (no hot-reload)
- Invalid entries logged and skipped
- Duplicate usernames overwrite previous entry
- At least one valid key required

### Authentication Flow

**Method:** `authenticate(String username, PublicKey key, ServerSession session)`

**Process:**
1. Client provides username and public key
2. Lookup username in `authorizedKeys` map
3. Compare client's public key with stored key
4. Return true if match, false otherwise

**Implementation:**
```java
@Override
public boolean authenticate(String username, PublicKey key, ServerSession session) {
    PublicKey authorizedKey = authorizedKeys.get(username);

    if (authorizedKey == null) {
        log.warn("No authorized key found for user: {}", username);
        return false;
    }

    boolean authenticated = authorizedKey.equals(key);

    if (authenticated) {
        log.info("User {} authenticated successfully", username);
    } else {
        log.warn("Authentication failed for user: {}", username);
    }

    return authenticated;
}
```

**Username Extraction:**
- Username comes from authorized_keys third field
- NOT from client's key comment
- NOT from SSH connection username
- Client must authenticate as the username associated with their public key

## Key Rotation Procedures

### Rotating Host Key

**Impact:** All clients must update known_hosts

**Steps:**
1. Stop SFTP server
2. Delete old host key: `rm keys/hostkey.pem`
3. Start SFTP server (generates new key)
4. Notify all clients to update known_hosts

**Client Update:**
```bash
# Remove old key
ssh-keygen -R "[localhost]:2222"

# Connect again (adds new key)
sftp -P 2222 -i ~/.ssh/key user@localhost
```

**Recommendation:** Avoid unless compromised

### Rotating Client Keys

**Impact:** Minimal (update authorized_keys)

**Steps:**
1. Generate new client key pair
2. Update authorized_keys with new public key
3. Restart SFTP server (reload keys)
4. Test new key
5. Remove old private key

**Example:**
```bash
# Generate new key
ssh-keygen -t rsa -b 2048 -f ~/.ssh/sftp_new_key -N ""

# Update authorized_keys
awk '{print $1" "$2" policyholder-importer"}' ~/.ssh/sftp_new_key.pub > keys/authorized_keys

# Restart SFTP server
docker compose restart sftp-server

# Test new key
sftp -P 2222 -i ~/.ssh/sftp_new_key policyholder-importer@localhost

# Remove old key
rm ~/.ssh/sftp_client_key ~/.ssh/sftp_client_key.pub
```

### Zero-Downtime Key Rotation

**Strategy:** Temporarily allow both old and new keys

**Steps:**
1. Generate new client key pair
2. Add new public key to authorized_keys (keep old)
3. Restart SFTP server
4. Update client to use new key
5. Test new key authentication
6. Remove old public key from authorized_keys
7. Restart SFTP server

**authorized_keys During Transition:**
```
# Old key (will be removed)
ssh-rsa AAAAB3OLD... policyholder-importer-old

# New key (active)
ssh-rsa AAAAB3NEW... policyholder-importer
```

## Security Best Practices

### Key Length

**Recommendation:** RSA 2048-bit minimum, 4096-bit preferred

**Current:** RSA 2048-bit (adequate for most use cases)

**Comparison:**

| Key Size | Security | Performance | Compatibility |
|----------|----------|-------------|---------------|
| 1024-bit | Weak | Fast | Universal |
| 2048-bit | Good | Fast | Universal |
| 4096-bit | Excellent | Slower | Universal |

### File Permissions

**Host Key:**
```bash
chmod 600 keys/hostkey.pem
chown app:app keys/hostkey.pem  # In Docker
```

**Client Private Key:**
```bash
chmod 600 ~/.ssh/sftp_client_key
```

**authorized_keys:**
```bash
chmod 600 keys/authorized_keys
```

**Why 600?**
- Owner: read+write
- Group: no access
- Others: no access
- Prevents unauthorized access to keys

### Key Storage

**Development:**
- Keys in `./keys` directory (bind mount)
- Private keys excluded from git (`.gitignore`)

**Production:**
- Use Docker secrets or Kubernetes secrets
- Inject keys as environment variables or files
- Rotate keys periodically (90-180 days)
- Audit key access

**Example with Docker Secrets:**
```yaml
secrets:
  sftp_host_key:
    file: ./keys/hostkey.pem
  authorized_keys:
    file: ./keys/authorized_keys

services:
  sftp-server:
    secrets:
      - sftp_host_key
      - authorized_keys
```

### Monitoring

**Authentication Attempts:**
```bash
# View authentication logs
docker compose logs sftp-server | grep "authenticated"

# Failed attempts
docker compose logs sftp-server | grep "Authentication failed"

# No authorized key
docker compose logs sftp-server | grep "No authorized key found"
```

**Metrics:**
- Track successful vs failed authentication attempts
- Alert on repeated failures (potential brute-force)
- Monitor active SFTP sessions

## Troubleshooting

### Common Issues

**Issue: "Authorized keys file not found"**
- Create `keys/authorized_keys` file
- Ensure file exists before starting server
- Check file path configuration

**Issue: "No valid authorized keys found"**
- Verify file format (three fields per line)
- Check for parsing errors in logs
- Ensure at least one uncommented line

**Issue: "No authorized key found for user: X"**
- Username in authorized_keys doesn't match connection username
- Check third field in authorized_keys
- Client must authenticate as correct username

**Issue: "Authentication failed for user: X"**
- Public key doesn't match authorized key
- Verify client is using correct private key
- Check key fingerprints match

**Issue: "No decoder available for key type=ssh-ed25519"**
- Ed25519 keys not supported in Apache SSHD 2.12.0
- Generate RSA key instead: `ssh-keygen -t rsa -b 2048`

**Issue: "Host key verification failed"**
- Host key changed (regenerated)
- Remove old key: `ssh-keygen -R "[localhost]:2222"`
- Reconnect to add new key

## Related Topics

- [USER_GUIDE.md#ssh-key-management](../USER_GUIDE.md#ssh-key-management) - Operational procedures
- [sftp-import.md](sftp-import.md) - SFTP import system
- [configuration.md](configuration.md) - SFTP server configuration
- [USER_GUIDE.md#security-hardening](../USER_GUIDE.md#security-hardening) - Production security

## References

- [Apache SSHD Documentation](https://github.com/apache/mina-sshd)
- [OpenSSH Key Management](https://www.openssh.com/manual.html)
- [SSH Public Key Authentication](https://www.ssh.com/academy/ssh/public-key-authentication)

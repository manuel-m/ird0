#!/bin/bash
# Vault Initialization Script
# This script initializes Vault with secrets for the IRD0 project
# Run this after Vault container starts for the first time
#
# Requirements: docker compose (no local vault CLI needed)

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

VAULT_ADDR="${VAULT_ADDR:-http://localhost:8200}"
VAULT_TOKEN="${VAULT_TOKEN:-dev-root-token}"

echo "Vault initialization script"
echo "VAULT_ADDR: $VAULT_ADDR"
echo "PROJECT_DIR: $PROJECT_DIR"

# Helper function to run vault commands inside the container
vault_cmd() {
    docker compose exec -e VAULT_TOKEN="$VAULT_TOKEN" vault vault "$@"
}
. .env
# Wait for Vault to be ready
echo "Waiting for Vault to be ready..."
until curl -s "$VAULT_ADDR/v1/sys/health" > /dev/null 2>&1; do
    echo "Vault is not ready yet..."
    sleep 2
done
echo "Vault is ready!"

# Check if secrets engine is already enabled
if ! vault_cmd secrets list 2>/dev/null | grep -q "secret/"; then
    echo "Enabling KV v2 secrets engine..."
    vault_cmd secrets enable -path=secret kv-v2
fi

# Create policies (policies are mounted in /vault/policies inside container)
echo "Creating policies..."
vault_cmd policy write directory-service /vault/policies/directory-service.hcl
vault_cmd policy write sftp-server /vault/policies/sftp-server.hcl

echo "Policies created successfully"

# Store database credentials
echo "Storing database credentials..."
vault_cmd kv put secret/ird0/database/postgres \
    username="${POSTGRES_USER:-directory_user}" \
    password="${POSTGRES_PASSWORD:-directory_pass}"

# Generate SFTP host key on host machine (PEM format required by Apache SSHD)
echo "Generating and storing SFTP host key..."
TEMP_KEY=$(mktemp)
rm -f "$TEMP_KEY" "${TEMP_KEY}.pub"  # Clean up if exists
echo "Generating RSA key in PEM format..."
ssh-keygen -t rsa -b 2048 -m PEM -f "$TEMP_KEY" -N "" -q

if [ ! -f "$TEMP_KEY" ]; then
    echo "ERROR: Failed to generate host key"
    rm -f "$TEMP_KEY" "${TEMP_KEY}.pub"
    exit 1
fi

echo "Storing host key in Vault..."
docker compose cp "$TEMP_KEY" vault:/tmp/host_key
docker compose cp "${TEMP_KEY}.pub" vault:/tmp/host_key.pub
docker compose exec vault sh -c '
    VAULT_TOKEN="'"$VAULT_TOKEN"'" vault kv put secret/ird0/sftp/host-key \
        private_key=@/tmp/host_key
    echo "Host key stored successfully"
'

# Clean up temp files
rm -f "$TEMP_KEY" "${TEMP_KEY}.pub"

# Store SFTP client key (read from file)
KEYS_DIR="$PROJECT_DIR/keys"
if [ -f "$KEYS_DIR/sftp_client_key" ]; then
    echo "Storing SFTP client key from file..."
    # Copy key to container temp location and store in Vault
    docker compose cp "$KEYS_DIR/sftp_client_key" vault:/tmp/client_key
    docker compose exec vault sh -c '
        VAULT_TOKEN="'"$VAULT_TOKEN"'" vault kv put secret/ird0/sftp/client-key \
            private_key=@/tmp/client_key
        rm -f /tmp/client_key
    '
else
    echo "Warning: SFTP client key file not found at $KEYS_DIR/sftp_client_key"
    echo "You may need to store it manually."
fi

# Store authorized keys (read from file)
if [ -f "$KEYS_DIR/authorized_keys" ]; then
    echo "Storing authorized keys from file..."
    docker compose cp "$KEYS_DIR/authorized_keys" vault:/tmp/authorized_keys
    docker compose exec vault sh -c '
        VAULT_TOKEN="'"$VAULT_TOKEN"'" vault kv put secret/ird0/sftp/authorized-keys \
            content=@/tmp/authorized_keys
        rm -f /tmp/authorized_keys
    '
else
    echo "Warning: Authorized keys file not found at $KEYS_DIR/authorized_keys"
fi

# Generate and store known_hosts from the host key we generated
echo "Generating known_hosts entry..."
docker compose exec vault sh -c '
    if [ -f /tmp/host_key.pub ]; then
        HOST_KEY_PUB=$(cat /tmp/host_key.pub)
        KNOWN_HOSTS_ENTRY="[sftp-server]:2222 $HOST_KEY_PUB"
        echo "$KNOWN_HOSTS_ENTRY" > /tmp/known_hosts
        VAULT_TOKEN="'"$VAULT_TOKEN"'" vault kv put secret/ird0/sftp/known-hosts \
            content=@/tmp/known_hosts
        rm -f /tmp/host_key.pub /tmp/host_key /tmp/known_hosts
        echo "Known hosts entry stored successfully"
    else
        echo "Warning: Could not generate known_hosts entry - host_key.pub not found"
    fi
'

echo ""
# =============================================================================
# SSH Certificate Authority Setup (Vault SSH Secrets Engine - CA Mode)
# =============================================================================
echo ""
echo "Setting up SSH Certificate Authority..."

# Enable SSH secrets engine for client certificate signing
if ! vault_cmd secrets list 2>/dev/null | grep -q "ssh-client-signer/"; then
    echo "Enabling SSH secrets engine at ssh-client-signer/..."
    vault_cmd secrets enable -path=ssh-client-signer ssh
fi

# Generate CA signing key pair
echo "Configuring SSH CA..."
vault_cmd write ssh-client-signer/config/ca generate_signing_key=true 2>/dev/null || true

# Create role for directory service (15 min TTL for high security)
echo "Creating directory-service role for certificate signing..."
vault_cmd write ssh-client-signer/roles/directory-service \
    key_type=ca \
    algorithm_signer=rsa-sha2-256 \
    allow_user_certificates=true \
    allowed_users="policyholder-importer" \
    ttl=15m \
    max_ttl=1h \
    default_user="policyholder-importer"

# Create SSH CA policy for directory service
echo "Creating SSH CA policy..."
vault_cmd policy write directory-service-ssh /vault/policies/directory-service-ssh.hcl

echo "SSH Certificate Authority configured successfully!"
echo "  - CA path: ssh-client-signer"
echo "  - Role: directory-service (TTL: 15m, max: 1h)"
echo "  - Allowed principal: policyholder-importer"

echo ""
echo "Vault initialization complete!"
echo ""
echo "Secrets stored:"
echo "  - secret/ird0/database/postgres"
echo "  - secret/ird0/sftp/host-key"
echo "  - secret/ird0/sftp/client-key (legacy - to be removed)"
echo "  - secret/ird0/sftp/authorized-keys (legacy - to be removed)"
echo "  - secret/ird0/sftp/known-hosts"
echo ""
echo "SSH CA configured:"
echo "  - ssh-client-signer/config/ca (CA public key)"
echo "  - ssh-client-signer/roles/directory-service (signing role)"
echo ""
echo "To verify, run:"
echo "  docker compose exec vault vault kv list secret/ird0"
echo "  docker compose exec vault vault read ssh-client-signer/config/ca"

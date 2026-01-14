# Policy for Directory Services (Policyholders, Experts, Providers)
# Allows reading database credentials and SSH certificate signing

# Database credentials
path "secret/data/ird0/database/postgres" {
  capabilities = ["read"]
}

# Sign SSH public keys to get certificates (for SFTP authentication)
path "ssh-client-signer/sign/directory-service" {
  capabilities = ["create", "update"]
}

# Read CA public key (for verification)
path "ssh-client-signer/config/ca" {
  capabilities = ["read"]
}

# Policy for SFTP Server
# Allows reading host key and SSH CA public key for certificate verification

# SFTP server host key (RSA private key)
path "secret/data/ird0/sftp/host-key" {
  capabilities = ["read"]
}

# SSH CA public key (for verifying client certificates)
path "ssh-client-signer/config/ca" {
  capabilities = ["read"]
}

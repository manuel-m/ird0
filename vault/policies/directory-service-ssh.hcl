# Policy for Directory Service SSH Certificate Signing
# Allows requesting short-lived SSH certificates from Vault CA

# Sign SSH public keys to get certificates
path "ssh-client-signer/sign/directory-service" {
  capabilities = ["create", "update"]
}

# Read CA public key (for verification)
path "ssh-client-signer/config/ca" {
  capabilities = ["read"]
}

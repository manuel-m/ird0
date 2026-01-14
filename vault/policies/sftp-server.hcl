# Policy for SFTP Server
# Allows reading host key and authorized keys

# SFTP server host key (RSA private key)
path "secret/data/ird0/sftp/host-key" {
  capabilities = ["read"]
}

# Authorized public keys for SFTP authentication
path "secret/data/ird0/sftp/authorized-keys" {
  capabilities = ["read"]
}

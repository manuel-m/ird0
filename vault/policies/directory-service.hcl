# Policy for Directory Services (Policyholders, Experts, Providers)
# Allows reading database credentials and SFTP client keys

# Database credentials
path "secret/data/ird0/database/postgres" {
  capabilities = ["read"]
}

# SFTP client private key (for connecting to SFTP server)
path "secret/data/ird0/sftp/client-key" {
  capabilities = ["read"]
}

# Known hosts file (for SFTP server verification)
path "secret/data/ird0/sftp/known-hosts" {
  capabilities = ["read"]
}

# Vault Development Configuration
# This configuration is for development/local use only
# For production, use proper storage backend and enable TLS

storage "file" {
  path = "/vault/data"
}

listener "tcp" {
  address     = "0.0.0.0:8200"
  tls_disable = 1
}

api_addr = "http://vault:8200"
ui = true

# Disable mlock for Docker environments
disable_mlock = true

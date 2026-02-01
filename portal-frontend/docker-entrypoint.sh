#!/bin/sh
# =============================================================================
# Portal Frontend Entrypoint Script
# =============================================================================
# Configures nginx based on ENV_MODE:
# - dev: HTTP only (port 80)
# - production: HTTPS with HTTP redirect (ports 80, 443)
# =============================================================================

set -e

ENV_MODE="${ENV_MODE:-dev}"

echo "[portal-entrypoint] Starting portal frontend in ${ENV_MODE} mode..."

if [ "$ENV_MODE" = "production" ]; then
    echo "[portal-entrypoint] Production mode: Enabling HTTPS"

    # Check for SSL certificates in mounted volume
    MOUNTED_SSL_DIR="/etc/nginx/ssl"
    # Use a writable location for generated certs
    GENERATED_SSL_DIR="/tmp/ssl"
    mkdir -p "$GENERATED_SSL_DIR"

    if [ -f "$MOUNTED_SSL_DIR/cert.pem" ] && [ -f "$MOUNTED_SSL_DIR/key.pem" ]; then
        echo "[portal-entrypoint] Using provided SSL certificates from $MOUNTED_SSL_DIR"
        # Copy to writable location (nginx config points here)
        cp "$MOUNTED_SSL_DIR/cert.pem" "$GENERATED_SSL_DIR/cert.pem"
        cp "$MOUNTED_SSL_DIR/key.pem" "$GENERATED_SSL_DIR/key.pem"
    else
        echo "[portal-entrypoint] No SSL certificates found, generating self-signed certificates..."
        echo "[portal-entrypoint] WARNING: Self-signed certificates are for testing only!"
        echo "[portal-entrypoint] For production, mount real certificates to $MOUNTED_SSL_DIR"

        openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
            -keyout "$GENERATED_SSL_DIR/key.pem" \
            -out "$GENERATED_SSL_DIR/cert.pem" \
            -subj "/CN=localhost/O=IRD0/C=FR" \
            2>/dev/null

        echo "[portal-entrypoint] Self-signed certificates generated in $GENERATED_SSL_DIR"
    fi

    # Use production nginx config
    cp /etc/nginx/nginx-production.conf /etc/nginx/nginx.conf
    echo "[portal-entrypoint] HTTPS configuration enabled"
else
    echo "[portal-entrypoint] Development mode: HTTP only"
    # Dev config is already in place (copied during build)
fi

echo "[portal-entrypoint] Starting nginx..."
exec nginx -g "daemon off;"

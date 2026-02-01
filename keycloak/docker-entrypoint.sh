#!/bin/bash
# =============================================================================
# Keycloak Entrypoint Script
# =============================================================================
# Starts Keycloak in the appropriate mode based on ENV_MODE:
# - dev: Uses start-dev (relaxed security, HTTP allowed)
# - production: Uses start (strict security, expects reverse proxy)
# =============================================================================

set -e

ENV_MODE="${ENV_MODE:-dev}"

echo "[keycloak-entrypoint] Starting Keycloak in ${ENV_MODE} mode..."

if [ "$ENV_MODE" = "production" ]; then
    echo "[keycloak-entrypoint] Production mode: Using production startup"
    echo "[keycloak-entrypoint] Expecting reverse proxy for TLS termination"

    # Production mode settings - behind reverse proxy
    export KC_HOSTNAME_STRICT=true
    export KC_HTTP_ENABLED=true
    export KC_PROXY_HEADERS=xforwarded

    # Note: --optimized requires custom build, use standard start for official image
    exec /opt/keycloak/bin/kc.sh start --import-realm
else
    echo "[keycloak-entrypoint] Development mode: Using dev startup"

    # Dev mode - relaxed settings
    export KC_HOSTNAME_STRICT=false

    exec /opt/keycloak/bin/kc.sh start-dev --import-realm
fi

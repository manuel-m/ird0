#!/bin/bash
# =============================================================================
# Development Test Users Initialization Script
# =============================================================================
# Creates test users for local development and testing.
# This script is NOT meant for production use.
#
# Usage:
#   Automatic: Set ENV_MODE=dev in .env (KEYCLOAK_DEV_MODE is derived)
#   Manual:    docker exec -it keycloak /scripts/init-dev-users.sh
#
# Prerequisites:
#   - Keycloak must be running and healthy
#   - The 'ird0' realm must exist (imported via realm-export.json)
# =============================================================================

set -e

# Configuration
REALM="ird0"
CLIENT_ID="ird0-portal-bff"
KCADM="/opt/keycloak/bin/kcadm.sh"
KEYCLOAK_URL="${KEYCLOAK_INTERNAL_URL:-http://keycloak:8080}"

# Check if running in dev mode
if [ "${KEYCLOAK_DEV_MODE:-production}" != "dev" ]; then
    echo "[init-dev-users] KEYCLOAK_DEV_MODE is not 'dev', skipping test user creation"
    exit 0
fi

echo "[init-dev-users] Development mode enabled, creating test users..."

# Wait for Keycloak to be ready
echo "[init-dev-users] Waiting for Keycloak to be ready..."
max_attempts=30
attempt=0
while [ $attempt -lt $max_attempts ]; do
    if $KCADM config credentials --server "$KEYCLOAK_URL" \
        --realm master \
        --user "${KEYCLOAK_ADMIN:-admin}" \
        --password "${KEYCLOAK_ADMIN_PASSWORD:-admin}" 2>/dev/null; then
        echo "[init-dev-users] Keycloak is ready at $KEYCLOAK_URL"
        break
    fi
    attempt=$((attempt + 1))
    echo "[init-dev-users] Waiting for Keycloak... (attempt $attempt/$max_attempts)"
    sleep 2
done

if [ $attempt -eq $max_attempts ]; then
    echo "[init-dev-users] ERROR: Keycloak did not become ready in time"
    exit 1
fi

# Function to create a user with a client role
create_user() {
    local username=$1
    local email=$2
    local first_name=$3
    local last_name=$4
    local password=$5
    local role=$6

    echo "[init-dev-users] Processing user: $username"

    # Check if user already exists
    existing_user=$($KCADM get users -r "$REALM" -q "username=$username" 2>/dev/null | grep -c "\"username\"" || true)
    if [ "$existing_user" -gt 0 ]; then
        echo "[init-dev-users]   User '$username' already exists"
    else
        # Create user
        echo "[init-dev-users]   Creating user '$username'..."
        if ! $KCADM create users -r "$REALM" \
            -s username="$username" \
            -s email="$email" \
            -s emailVerified=true \
            -s enabled=true \
            -s firstName="$first_name" \
            -s lastName="$last_name"; then
            echo "[init-dev-users]   ERROR: Failed to create user '$username'"
            return 1
        fi

        # Set password
        echo "[init-dev-users]   Setting password for '$username'..."
        if ! $KCADM set-password -r "$REALM" \
            --username "$username" \
            --new-password "$password"; then
            echo "[init-dev-users]   ERROR: Failed to set password for '$username'"
            return 1
        fi
    fi

    # Always ensure role is assigned (idempotent)
    echo "[init-dev-users]   Assigning role '$role' to '$username'..."
    if ! $KCADM add-roles -r "$REALM" \
        --uusername "$username" \
        --cclientid "$CLIENT_ID" \
        --rolename "$role" 2>&1; then
        echo "[init-dev-users]   WARNING: Role assignment may have failed for '$username'"
    fi

    echo "[init-dev-users]   Done with user '$username'"
}

# Create test users
# Note: In development, passwords match usernames for convenience
create_user "viewer"  "viewer@example.com"  "View"    "User" "viewer"  "claims-viewer"
create_user "manager" "manager@example.com" "Manager" "User" "manager" "claims-manager"
create_user "admin"   "admin@example.com"   "Admin"   "User" "admin"   "claims-admin"

echo "[init-dev-users] Test users created successfully!"
echo "[init-dev-users] Available test accounts:"
echo "[init-dev-users]   - viewer/viewer   (claims-viewer role)"
echo "[init-dev-users]   - manager/manager (claims-manager role)"
echo "[init-dev-users]   - admin/admin     (claims-admin role)"

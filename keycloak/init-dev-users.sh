#!/bin/bash
# =============================================================================
# Development Test Users Initialization Script
# =============================================================================
# Creates test users for local development and testing.
# This script is NOT meant for production use.
#
# Usage:
#   Automatic: Set KEYCLOAK_DEV_MODE=true in docker-compose environment
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

# Check if running in dev mode
if [ "${KEYCLOAK_DEV_MODE:-false}" != "true" ]; then
    echo "[init-dev-users] KEYCLOAK_DEV_MODE is not 'true', skipping test user creation"
    exit 0
fi

echo "[init-dev-users] Development mode enabled, creating test users..."

# Wait for Keycloak to be ready
echo "[init-dev-users] Waiting for Keycloak to be ready..."
max_attempts=30
attempt=0
while [ $attempt -lt $max_attempts ]; do
    if $KCADM config credentials --server http://localhost:8080 \
        --realm master \
        --user "${KEYCLOAK_ADMIN:-admin}" \
        --password "${KEYCLOAK_ADMIN_PASSWORD:-admin}" 2>/dev/null; then
        echo "[init-dev-users] Keycloak is ready"
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

    echo "[init-dev-users] Creating user: $username"

    # Check if user already exists
    existing_user=$($KCADM get users -r "$REALM" -q "username=$username" 2>/dev/null | grep -c "\"username\"" || true)
    if [ "$existing_user" -gt 0 ]; then
        echo "[init-dev-users]   User '$username' already exists, skipping"
        return 0
    fi

    # Create user
    $KCADM create users -r "$REALM" \
        -s username="$username" \
        -s email="$email" \
        -s emailVerified=true \
        -s enabled=true \
        -s firstName="$first_name" \
        -s lastName="$last_name"

    # Set password
    $KCADM set-password -r "$REALM" \
        --username "$username" \
        --new-password "$password"

    # Get user ID
    user_id=$($KCADM get users -r "$REALM" -q "username=$username" --fields id | grep '"id"' | sed 's/.*: "\(.*\)".*/\1/')

    # Get client ID (internal UUID)
    client_uuid=$($KCADM get clients -r "$REALM" -q "clientId=$CLIENT_ID" --fields id | grep '"id"' | sed 's/.*: "\(.*\)".*/\1/')

    # Get role ID
    role_id=$($KCADM get clients/$client_uuid/roles/$role -r "$REALM" --fields id | grep '"id"' | sed 's/.*: "\(.*\)".*/\1/')

    # Assign client role to user
    $KCADM create users/$user_id/role-mappings/clients/$client_uuid -r "$REALM" \
        -s "id=$role_id" \
        -s "name=$role"

    echo "[init-dev-users]   Created user '$username' with role '$role'"
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

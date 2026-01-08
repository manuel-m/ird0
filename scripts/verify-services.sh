#!/bin/bash

# IRD0 Service Verification Script
# This script performs comprehensive health checks on all services in the IRD0 insurance platform

# Color codes for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Counters
TOTAL_CHECKS=0
PASSED_CHECKS=0
FAILED_CHECKS=0

# Helper functions
check_pass() {
  echo -e "${GREEN}[PASS]${NC} $1"
  ((PASSED_CHECKS++))
  ((TOTAL_CHECKS++))
}

check_fail() {
  echo -e "${RED}[FAIL]${NC} $1"
  if [ -n "$2" ]; then
    echo -e "  ${YELLOW}Diagnostic: $2${NC}"
  fi
  ((FAILED_CHECKS++))
  ((TOTAL_CHECKS++))
}

check_info() {
  echo -e "${BLUE}[INFO]${NC} $1"
}

section_header() {
  echo ""
  echo "=== $1 ==="
}

print_banner() {
  echo "================================"
  echo "IRD0 Service Verification"
  echo "================================"
}

print_summary() {
  echo ""
  echo "================================"
  echo "Verification Summary"
  echo "================================"
  echo "Total Checks: $TOTAL_CHECKS"
  echo -e "${GREEN}Passed: $PASSED_CHECKS${NC}"
  echo -e "${RED}Failed: $FAILED_CHECKS${NC}"
  echo ""

  if [ $FAILED_CHECKS -eq 0 ]; then
    echo -e "${GREEN}All checks passed!${NC}"
    return 0
  else
    echo -e "${RED}Some checks failed. Please review the output above.${NC}"
    return 1
  fi
}

# Check if Docker Compose is running
check_docker_compose() {
  if ! command -v docker &> /dev/null; then
    check_fail "Docker command not found" "Install Docker first"
    return 1
  fi

  if ! docker compose version &> /dev/null; then
    check_fail "Docker Compose not available" "Install Docker Compose plugin"
    return 1
  fi

  check_pass "Docker and Docker Compose are available"
}

# Check Docker containers
check_docker_containers() {
  section_header "Docker Container Status"

  local containers=("policyholders" "experts" "providers" "postgres" "sftp-server")

  for container in "${containers[@]}"; do
    if docker compose ps --status running 2>/dev/null | grep -q "$container"; then
      check_pass "Container $container is running"
    else
      local status=$(docker compose ps --all 2>/dev/null | grep "$container" | awk '{print $NF}')
      if [ -z "$status" ]; then
        check_fail "Container $container not found" "Run 'docker compose up $container'"
      else
        check_fail "Container $container is not running" "Status: $status. Run 'docker compose up $container'"
      fi
    fi
  done
}

# Check PostgreSQL
check_postgresql() {
  section_header "PostgreSQL Database"

  # Check if PostgreSQL container is accessible
  if ! docker compose exec -T postgres pg_isready -U directory_user &> /dev/null; then
    check_fail "PostgreSQL is not accepting connections" "Check container logs with 'docker compose logs postgres'"
    return 1
  fi
  check_pass "PostgreSQL is accepting connections"

  # Check databases exist
  local databases=("policyholders_db" "experts_db" "providers_db")

  for db in "${databases[@]}"; do
    if docker compose exec -T postgres psql -U directory_user -lqt 2>/dev/null | cut -d \| -f 1 | grep -qw "$db"; then
      check_pass "Database $db exists"

      # Check if directory_entry table exists
      if docker compose exec -T postgres psql -U directory_user -d "$db" -c "\dt directory_entry" 2>/dev/null | grep -q "directory_entry"; then
        check_pass "Table directory_entry exists in $db"

        # Optional: Check row count
        local count=$(docker compose exec -T postgres psql -U directory_user -d "$db" -t -c "SELECT COUNT(*) FROM directory_entry" 2>/dev/null | tr -d ' ')
        if [ -n "$count" ]; then
          check_info "  Row count in $db: $count"
        fi
      else
        check_fail "Table directory_entry not found in $db" "Schema may not be initialized"
      fi
    else
      check_fail "Database $db does not exist" "Check init script: scripts/init-multiple-databases.sh"
    fi
  done
}

# Check service health endpoints
check_service_health() {
  section_header "Service Health Checks"

  # Array of services with their ports and names
  # Note: SFTP server is not included as it has no web server (uses Apache MINA SSHD, not HTTP)
  declare -A services
  services["Policyholders"]=8081
  services["Experts"]=8082
  services["Providers"]=8083

  for service in "${!services[@]}"; do
    local port="${services[$service]}"
    local url="http://localhost:$port/actuator/health"

    # Make HTTP request with timeout
    local response=$(curl -s -m 5 "$url" 2>/dev/null)
    local status=$?

    if [ $status -eq 0 ]; then
      # Check if response contains "UP" status
      if echo "$response" | grep -q '"status":"UP"'; then
        check_pass "$service service is healthy (port $port)"
      else
        check_fail "$service service is not healthy (port $port)" "Status: $(echo $response | grep -o '"status":"[^"]*"')"
      fi
    else
      check_fail "$service service health endpoint not accessible (port $port)" "Check if container is running and port is exposed"
    fi
  done
}

# Check SFTP server connectivity
check_sftp_connectivity() {
  section_header "SFTP Server Connectivity"

  check_info "  Note: SFTP server has no HTTP management endpoint (non-web application)"

  # Check if port 2222 is listening
  if nc -z localhost 2222 2>/dev/null; then
    check_pass "SFTP server is listening on port 2222"
  else
    check_fail "SFTP server port 2222 is not accessible" "Check if sftp-server container is running"
    return 1
  fi

  # Optional: Try SFTP authentication if key exists
  # Try project key first (keys/sftp_client_key), then fallback to ~/.ssh/sftp_test_key
  local sftp_key=""
  local sftp_user=""

  if [ -f "keys/sftp_client_key" ]; then
    sftp_key="keys/sftp_client_key"
    sftp_user="policyholder-importer"
  elif [ -f "$HOME/.ssh/sftp_test_key" ]; then
    sftp_key="$HOME/.ssh/sftp_test_key"
    sftp_user="test-user"
  fi

  if [ -n "$sftp_key" ]; then
    check_info "  Testing SFTP authentication with key $sftp_key as user $sftp_user"

    # Try to connect and list files (timeout after 5 seconds)
    if timeout 5 sftp -P 2222 -i "$sftp_key" -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -b - "$sftp_user@localhost" <<< "ls" &>/dev/null; then
      check_pass "SFTP authentication successful"
    else
      check_fail "SFTP authentication failed" "Verify SSH key matches keys/authorized_keys and username is correct"
    fi
  else
    check_info "  Skipping SFTP authentication test (no key found at keys/sftp_client_key or ~/.ssh/sftp_test_key)"
  fi
}

# Optional: REST API smoke tests
check_rest_api() {
  section_header "REST API Smoke Tests (Optional)"

  check_info "Skipping REST API smoke tests (not implemented)"
  check_info "To enable, uncomment the smoke test section in this script"

  # Uncomment below to enable REST API testing
  # local test_email="test-$(date +%s)@example.com"
  #
  # # Create entry
  # local create_response=$(curl -s -X POST http://localhost:8081/api/policyholders \
  #   -H "Content-Type: application/json" \
  #   -d "{\"name\":\"Test User\",\"type\":\"individual\",\"email\":\"$test_email\",\"phone\":\"555-0000\"}")
  #
  # if echo "$create_response" | grep -q "\"id\""; then
  #   check_pass "REST API: Create entry successful"
  #
  #   local entry_id=$(echo "$create_response" | grep -o '"id":[0-9]*' | grep -o '[0-9]*')
  #
  #   # Get entry
  #   if curl -s "http://localhost:8081/api/policyholders/$entry_id" | grep -q "$test_email"; then
  #     check_pass "REST API: Get entry successful"
  #   else
  #     check_fail "REST API: Get entry failed"
  #   fi
  #
  #   # Delete entry (cleanup)
  #   curl -s -X DELETE "http://localhost:8081/api/policyholders/$entry_id" &>/dev/null
  #   check_pass "REST API: Delete entry successful (cleanup)"
  # else
  #   check_fail "REST API: Create entry failed" "Response: $create_response"
  # fi
}

# Main execution
main() {
  print_banner

  # Run all checks
  check_docker_compose
  check_docker_containers
  check_postgresql
  check_service_health
  check_sftp_connectivity
  check_rest_api

  # Print summary and exit
  print_summary
  exit $?
}

# Run main function
main

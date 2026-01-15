# Port refactor prompt

  Configuration Files (10 files modified):
  1. .env - Added comprehensive configuration with 40+ variables
  2. .env.example - Updated with full documentation
  3-6. Directory service configs (policyholders.yml, experts.yml, providers.yml, insurers.yml) - Added ${SERVER_PORT:808X} placeholders
  3. incident.yml - Added ${SERVER_PORT:8085} placeholder
  4. notification.yml - Added ${SERVER_PORT:8086} and directory.insurers-url config
  5. sftp.yml - Added ${SERVER_PORT:9090} placeholder
  6. policyholders.yml - Externalized SFTP import directory paths

  Java Code (1 file modified):
  11. NotificationProperties.java - Added Directory configuration class for insurers URL

  Docker Orchestration (1 file modified):
  12. docker-compose.yml - Updated all 6 Spring Boot services with:
      - Uniform internal port mapping (8080)
      - Environment variable references for all config
      - Updated healthchecks to use port 8080
      - Externalized service URLs

  Documentation (3 files modified):
  13. CLAUDE.md - Added comprehensive Configuration Management section with port reference table
  14. microservices/directory/CLAUDE.md - Added port configuration notes
  15. microservices/sftp-server/CLAUDE.md - Added port configuration notes

  Architecture After Refactoring

  Port Scheme:
  - Internal (Docker): All Spring Boot services run on port 8080
  - External (Host): Services accessible on original ports (8081-8086, 9090)
  - Service Discovery: Docker DNS names with uniform port (e.g., http://policyholders:8080)

  Configuration Hierarchy:
  1. Java defaults (localhost:808X) - for local development
  2. YAML files with ${VAR:default} - support both local and Docker
  3. .env file - single source of truth for Docker
  4. docker-compose.yml - references .env variables

  Testing & Verification

  The refactoring is backward compatible. You can test it with:

  Test 1: Docker Deployment
  docker compose up --build

  # Test external access
  curl http://localhost:8081/actuator/health  # Policyholders
  curl http://localhost:8082/actuator/health  # Experts
  curl http://localhost:8085/actuator/health  # Incident

  Test 2: Verify Internal Ports
  # From within containers
  docker compose exec policyholders wget -qO- http://localhost:8080/actuator/health
  docker compose exec incident-service wget -qO- http://localhost:8080/actuator/health

  Test 3: Inter-Service Communication
  The incident service should successfully call directory services using the new uniform ports.

  Test 4: Local Development (No Docker)
  cd microservices/directory
  mvn spring-boot:run -Dspring-boot.run.arguments="--spring.config.location=file:configs/application.yml,file:configs/policyholders.yml"
  # Should start on port 8081 (YAML default)

  Benefits Achieved

  1. Uniform internal ports - All services on 8080 in Docker
  2. Docker DNS-based discovery - Services communicate via servicename:8080
  3. Single source of truth - .env file contains all configuration
  4. Environment-driven config - No hardcoded ports in YAML files
  5. Kubernetes-ready - Standard port 8080 expected by K8s
  6. Better maintainability - Configuration changes in one place
  7. Backward compatible - Local development unchanged

  Next Steps

  1. Test the changes using the verification commands above
  2. Review the plan file at /home/gal/.claude/plans/starry-cooking-lemon.md for detailed testing scenarios
  3. Commit the changes when ready
  4. Update any external documentation or scripts that reference the old architecture

  All files have been updated and the refactoring is complete. The system is ready to test!
# Production Deployment Documentation

This directory contains comprehensive documentation for deploying the IRD0 Insurance Platform to production using Coolify and self-hosted GitLab.

## Overview

**Objective:** Deploy the insurance platform to production on a VPS using Coolify, with self-hosted GitLab for CI/CD.

**Architecture:**
- CI/CD: Self-hosted GitLab (Docker)
- Secrets Management: HashiCorp Vault (production mode)
- Deployment Platform: Coolify with Let's Encrypt SSL
- Reverse Proxy: Traefik (managed by Coolify)

## Documentation Index

### Infrastructure Setup

| Document | Description |
|----------|-------------|
| [gitlab-setup.md](gitlab-setup.md) | Self-hosted GitLab Docker installation |
| [gitlab-ci-guide.md](gitlab-ci-guide.md) | CI/CD pipeline configuration |
| [coolify-setup.md](coolify-setup.md) | Coolify installation and configuration |
| [coolify-app-deployment.md](coolify-app-deployment.md) | Application deployment to Coolify |
| [vault-production.md](vault-production.md) | HashiCorp Vault production setup |

### Testing & Quality

| Document | Description |
|----------|-------------|
| [testing-requirements.md](testing-requirements.md) | Test coverage requirements per service |
| [critical-tests-spec.md](critical-tests-spec.md) | Specifications for critical missing tests |
| [integration-tests.md](integration-tests.md) | End-to-end test scenarios |

### Deployment & Operations

| Document | Description |
|----------|-------------|
| [compose-coolify-notes.md](compose-coolify-notes.md) | Docker Compose adaptations for Coolify |
| [security-checklist.md](security-checklist.md) | Pre-deployment security verification |
| [runbook.md](runbook.md) | Operations guide and troubleshooting |

## Implementation Timeline

### Week 1: Documentation
1. Create all documentation files (this phase)
2. Review and validate documentation completeness

### Week 2: Testing
1. Implement critical tests for Notification service
2. Implement critical tests for Portal-BFF
3. Implement critical tests for SFTP-Server
4. Run full test suite, verify coverage

### Week 3: Infrastructure Setup
1. Deploy GitLab on VPS (following [gitlab-setup.md](gitlab-setup.md))
2. Configure GitLab Runner
3. Import repository from GitHub
4. Create `.gitlab-ci.yml`
5. Verify CI pipeline runs successfully

### Week 4: Production Deployment
1. Initialize Vault in production mode ([vault-production.md](vault-production.md))
2. Configure Coolify with secrets ([coolify-app-deployment.md](coolify-app-deployment.md))
3. Deploy application stack
4. Run security checklist verification ([security-checklist.md](security-checklist.md))
5. Monitor for 48 hours before announcing

## Quick Reference

### Service Ports (Internal)

| Service | Port | API Base Path |
|---------|------|---------------|
| Policyholders | 8080 | `/api/policyholders` |
| Experts | 8080 | `/api/experts` |
| Providers | 8080 | `/api/providers` |
| Insurers | 8080 | `/api/insurers` |
| Incident | 8080 | `/api/v1/incidents` |
| Notification | 8080 | `/api/v1/notifications` |
| Portal BFF | 8080 | `/api/portal/v1` |
| Keycloak | 8080 | `/realms/*` |

### Health Check Endpoints

All Spring Boot services expose: `/actuator/health`

### Required Secrets

- `POSTGRES_PASSWORD` - PostgreSQL database password
- `KEYCLOAK_ADMIN_PASSWORD` - Keycloak admin console password
- `KEYCLOAK_CLIENT_SECRET` - OAuth2 client secret
- `VAULT_TOKEN` - Vault access token (from initialization)

## Related Documentation

- [doc/ARCHITECTURE.md](../ARCHITECTURE.md) - System architecture overview
- [doc/PRD.md](../PRD.md) - Product requirements
- [doc/topics/docker.md](../topics/docker.md) - Docker configuration details
- [doc/topics/configuration.md](../topics/configuration.md) - Configuration layering

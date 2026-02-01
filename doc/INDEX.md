# IRD0 Documentation Index

Welcome to the IRD0 project documentation. This index helps you find the right document for your needs.

## Getting Started

- [README.md](../README.md) - Project overview and quick start
- [CLAUDE.md](../CLAUDE.md) - Comprehensive guide for Claude Code AI assistant
- [USER_GUIDE.md](USER_GUIDE.md) - Operations manual for administrators

## Product Requirements

- [PRD.md](PRD.md) - Complete product requirements document
  - Executive summary and business goals
  - User personas and actors
  - Feature inventory by domain
  - Functional requirements (FR-xxx)
  - Non-functional requirements
  - API specifications summary
  - Data models and state machine

## Feature Documentation

Detailed documentation for each major feature:

- [Directory Management](features/directory-management.md) - CRUD operations, multi-instance pattern, CSV import
- [SFTP Data Import](features/sftp-data-import.md) - Automated polling, change detection, batch processing
- [Incident Lifecycle](features/incident-lifecycle.md) - State machine, transitions, event history
- [Expert Assignment](features/expert-assignment.md) - Assignment workflow, scheduling, validation
- [Webhook Notifications](features/webhook-notifications.md) - Dispatch, exponential backoff, retry logic
- [Portal Dashboard](features/portal-dashboard.md) - KPIs, status charts, recent activity
- [Claims Management UI](features/claims-management-ui.md) - List, detail, create pages

## Architecture & Design

- [ARCHITECTURE.md](ARCHITECTURE.md) - System design deep-dive
  - Multi-instance microservice pattern
  - PostgreSQL multi-database architecture
  - SFTP integration overview
  - Docker multi-stage builds
  - DTO and MapStruct architecture
  - Technology choices and rationale

## Topic-Based Guides

### Core Infrastructure

- [Database Management](topics/database.md)
  - PostgreSQL setup and initialization
  - Multi-database strategy
  - Schema management with Hibernate
  - Connection pooling (HikariCP)
  - Backup and restore procedures
  - Data persistence with Docker volumes
  - Performance tuning
  - Security and credentials

- [Docker & Containerization](topics/docker.md)
  - Multi-stage build strategy
  - Dependency layer caching
  - Modular Docker Compose file organization (infrastructure, directory, apps)
  - Include directive and service group startup
  - Volume management (named volumes, bind mounts)
  - Network configuration and cross-file dependencies
  - Service dependencies and health checks
  - Optimization techniques

### SFTP System

- [SFTP Import Architecture](topics/sftp-import.md)
  - Spring Integration polling flow
  - SFTP session factory configuration
  - File-level change detection (timestamps)
  - Row-level change detection (field comparison)
  - CSV parsing and batch processing
  - PostgreSQL upsert (ON CONFLICT)
  - Transaction management
  - Performance optimizations
  - Troubleshooting SFTP import

- [Vault SSH Certificate Authority](topics/vault-ssh-ca.md)
  - SSH CA mode architecture
  - Ephemeral key pair generation (forward secrecy)
  - Certificate signing and lifecycle management
  - Five-step certificate verification
  - Audit logging for authentication events
  - Vault policies and role configuration
  - Security properties and threat mitigation
  - Graceful fallback to static keys

- [SSH Key Management](topics/ssh-keys.md) (Static Keys / Fallback Mode)
  - Key types (host key, client private key, authorized keys)
  - Host key generation (SimpleGeneratorHostKeyProvider)
  - Client key generation (ssh-keygen)
  - Authorized keys format (RSA only, 3-field format)
  - File permissions requirements
  - Key rotation procedures (zero-downtime)
  - Security best practices
  - Troubleshooting key issues

### Configuration & Operations

- [Configuration Management](topics/configuration.md)
  - YAML file structure
  - Configuration loading order (layered config)
  - Environment variable overrides
  - @ConfigurationProperties pattern
  - Instance-specific overrides (policyholders, experts, providers)
  - Docker configuration (APP_YML build arg)
  - Secrets management recommendations

- [Monitoring & Health Checks](topics/monitoring.md)
  - Spring Boot Actuator endpoints
  - Health check configuration
  - Custom health indicators
  - Metrics collection (JVM, custom)
  - Log monitoring and filtering
  - Setting up alerts (Prometheus/Grafana examples)
  - Import metrics tracking

- [Troubleshooting Guide](topics/troubleshooting.md)
  - Service won't start
  - Database connection failures
  - SFTP import not working
  - SFTP authentication failures
  - CSV import errors
  - File processing issues
  - Container and Docker issues
  - Performance problems
  - Error recovery and retry logic

## Production Deployment

Comprehensive guides for deploying the IRD0 platform to production:

- [Deployment Overview](deployment/README.md) - Index and implementation timeline
- [GitLab Setup](deployment/gitlab-setup.md) - Self-hosted GitLab installation
- [GitLab CI/CD](deployment/gitlab-ci-guide.md) - Pipeline configuration
- [Coolify Setup](deployment/coolify-setup.md) - Coolify installation and configuration
- [Coolify Deployment](deployment/coolify-app-deployment.md) - Deploying IRD0 to Coolify
- [Docker Compose Notes](deployment/compose-coolify-notes.md) - Compose file adaptations
- [Vault Production](deployment/vault-production.md) - Production Vault setup
- [Testing Requirements](deployment/testing-requirements.md) - Test coverage requirements
- [Critical Tests Spec](deployment/critical-tests-spec.md) - Test specifications
- [Integration Tests](deployment/integration-tests.md) - E2E test scenarios
- [Security Checklist](deployment/security-checklist.md) - Pre-deployment verification
- [Operations Runbook](deployment/runbook.md) - Production operations guide

## Migration Guides

- [UUID Migration](migrations/uuid-migration.md) - Long to UUID primary key migration
  - Migration overview and rationale
  - Breaking changes in API
  - UUID generation strategy (@PrePersist)
  - MapStruct configuration
  - Native SQL with UUID casting
  - Testing and rollback plan

## Reviews & Assessments

- [SFTP Import Review (2026-01-08)](reviews/sftp-import-review-2026-01-08.md)
  - Production readiness assessment
  - Performance characteristics
  - Security considerations
  - Critical findings and recommendations
  - Monitoring and observability gaps

## Module-Specific Documentation

- [Directory Service](../microservices/directory/CLAUDE.md)
  - Multi-instance pattern implementation
  - Configuration files (application.yml, instance YAMLs)
  - Building and running locally
  - API testing examples
  - DirectoryEntry model
  - CRUD operations

- [SFTP Server](../microservices/sftp-server/CLAUDE.md)
  - Apache MINA SSHD architecture
  - SSH key setup (detailed guide)
  - Building and running
  - Testing SFTP connections
  - Security features
  - Read-only file system implementation

- [Data Generator](../utilities/directory-data-generator/CLAUDE.md)
  - CLI usage and examples
  - Generated data format
  - Importing strategies
  - Technical architecture

## Deprecated Documentation

- [Spring Integration Examples](deprecated/SpringIntegration.md) - Historical SFTP configuration examples

## Quick Reference

### By Role

- **New Developer**: Start with [README.md](../README.md) → [PRD.md](PRD.md) → [ARCHITECTURE.md](ARCHITECTURE.md)
- **Product Owner**: Start with [PRD.md](PRD.md) → [features/](features/)
- **Operations Team**: Start with [USER_GUIDE.md](USER_GUIDE.md) → [troubleshooting.md](topics/troubleshooting.md)
- **Security Admin**: See [vault-ssh-ca.md](topics/vault-ssh-ca.md) → [ssh-keys.md](topics/ssh-keys.md) → [configuration.md](topics/configuration.md)
- **Database Admin**: See [database.md](topics/database.md) → [USER_GUIDE.md](USER_GUIDE.md)
- **DevOps Engineer**: See [docker.md](topics/docker.md) → [monitoring.md](topics/monitoring.md) → [vault-ssh-ca.md](topics/vault-ssh-ca.md) → [deployment/](deployment/)

### By Task

- **Setting up project**: [README.md](../README.md) Quick Start section
- **Understanding product features**: [PRD.md](PRD.md) → [features/](features/)
- **Understanding incident workflow**: [features/incident-lifecycle.md](features/incident-lifecycle.md)
- **Understanding SFTP import**: [features/sftp-data-import.md](features/sftp-data-import.md) + [topics/sftp-import.md](topics/sftp-import.md)
- **Setting up Vault SSH CA**: [topics/vault-ssh-ca.md](topics/vault-ssh-ca.md) + [USER_GUIDE.md](USER_GUIDE.md#hashicorp-vault-integration)
- **Managing SSH keys (static)**: [topics/ssh-keys.md](topics/ssh-keys.md)
- **Troubleshooting database issues**: [topics/database.md](topics/database.md) + [topics/troubleshooting.md](topics/troubleshooting.md)
- **Deploying to production**: [deployment/README.md](deployment/README.md) → [deployment/coolify-app-deployment.md](deployment/coolify-app-deployment.md) → [deployment/security-checklist.md](deployment/security-checklist.md)
- **Monitoring services**: [topics/monitoring.md](topics/monitoring.md)
- **Monitoring certificate auth**: [topics/vault-ssh-ca.md](topics/vault-ssh-ca.md#audit-logging)
- **Backing up data**: [USER_GUIDE.md](USER_GUIDE.md#backup-and-restore) + [topics/database.md](topics/database.md)
- **Rotating SSH keys**: [topics/ssh-keys.md](topics/ssh-keys.md) + [USER_GUIDE.md](USER_GUIDE.md#ssh-key-management)
- **Changing configuration**: [topics/configuration.md](topics/configuration.md)
- **Debugging import failures**: [topics/sftp-import.md](topics/sftp-import.md) + [topics/troubleshooting.md](topics/troubleshooting.md)

## Documentation Organization Principles

This documentation follows these organizational principles:

1. **One Source of Truth**: Each piece of technical information exists in exactly ONE authoritative location
2. **Topic-Based**: Each major topic has its own dedicated file
3. **Cross-Referenced**: Documents link to each other rather than repeating content
4. **Role-Oriented**: Navigation helps different roles (developer, operator, admin) find relevant content
5. **Task-Oriented**: Common tasks are mapped to relevant documentation

## Contributing to Documentation

When updating documentation:

- Update the authoritative source for each topic (see "One Source of Truth" table above)
- Update cross-references if file locations change
- Keep this INDEX.md synchronized when adding new topics
- Follow the existing structure and style
- Test all internal links after changes

Last Updated: 2026-02-01

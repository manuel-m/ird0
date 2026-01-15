# IRD0 Documentation Index

Welcome to the IRD0 project documentation. This index helps you find the right document for your needs.

## Getting Started

- [README.md](../README.md) - Project overview and quick start
- [CLAUDE.md](../CLAUDE.md) - Comprehensive guide for Claude Code AI assistant
- [USER_GUIDE.md](USER_GUIDE.md) - Operations manual for administrators

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
  - Volume management (named volumes, bind mounts)
  - Network configuration
  - Docker Compose orchestration
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

- **New Developer**: Start with [README.md](../README.md) → [CLAUDE.md](../CLAUDE.md) → [ARCHITECTURE.md](ARCHITECTURE.md)
- **Operations Team**: Start with [USER_GUIDE.md](USER_GUIDE.md) → [troubleshooting.md](topics/troubleshooting.md)
- **Security Admin**: See [vault-ssh-ca.md](topics/vault-ssh-ca.md) → [ssh-keys.md](topics/ssh-keys.md) → [configuration.md](topics/configuration.md)
- **Database Admin**: See [database.md](topics/database.md) → [USER_GUIDE.md](USER_GUIDE.md)
- **DevOps Engineer**: See [docker.md](topics/docker.md) → [monitoring.md](topics/monitoring.md) → [vault-ssh-ca.md](topics/vault-ssh-ca.md)

### By Task

- **Setting up project**: [README.md](../README.md) Quick Start section
- **Understanding SFTP import**: [topics/sftp-import.md](topics/sftp-import.md)
- **Setting up Vault SSH CA**: [topics/vault-ssh-ca.md](topics/vault-ssh-ca.md) + [USER_GUIDE.md](USER_GUIDE.md#hashicorp-vault-integration)
- **Managing SSH keys (static)**: [topics/ssh-keys.md](topics/ssh-keys.md)
- **Troubleshooting database issues**: [topics/database.md](topics/database.md) + [topics/troubleshooting.md](topics/troubleshooting.md)
- **Deploying to production**: [USER_GUIDE.md](USER_GUIDE.md) → [ARCHITECTURE.md](ARCHITECTURE.md)
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

Last Updated: 2026-01-15

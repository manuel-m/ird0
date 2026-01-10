# Codebase Review

## Executive Summary

This document provides a comprehensive review of the IRD0 insurance platform codebase, covering code quality, security, testing, architecture, and infrastructure. The review identifies areas for improvement and provides prioritized recommendations.

**Date**: 2026-01-10
**Reviewer**: System Analysis
**Status**: Good Foundation with Security and Testing Gaps

**Overall Assessment**:
- **Architecture**: Excellent (9/10) - Clean patterns, good separation of concerns
- **Security**: Needs Improvement (5/10) - Critical SFTP and credential issues
- **Testing**: Poor (3/10) - Only ~25% coverage, no integration tests
- **Infrastructure**: Good (7/10) - Proper Docker setup, missing CI/CD
- **Documentation**: Excellent (9/10) - Comprehensive CLAUDE.md files

---

## Critical Security Issues

These issues should be addressed immediately before any production deployment.

### Issue #1: SFTP Client Vulnerable to Man-in-the-Middle Attacks

**File**: `microservices/directory/src/main/java/com/ird0/directory/config/SftpIntegrationConfig.java:29`

**Current Code**:
```java
factory.setAllowUnknownKeys(true);
```

**Problem**: The SFTP client accepts any host key without verification. An attacker can intercept the connection (MITM attack) and:
- Steal SSH credentials
- Capture imported CSV data
- Inject malicious data into the import stream

**Impact**: **CRITICAL** - Data integrity and confidentiality compromised

**Recommendation**: Remove this line or implement proper host key verification:
```java
// Option 1: Use known hosts file
factory.setKnownHostsResource(new FileSystemResource("/path/to/known_hosts"));

// Option 2: Verify specific fingerprint
factory.setHostKeyAlias("sftp-server");
factory.setHostKeyVerifier((host, port, publicKey) -> {
    String fingerprint = KeyUtils.getFingerPrint(publicKey);
    return expectedFingerprint.equals(fingerprint);
});
```

**Effort**: 1-2 hours

---

### Issue #2: Database Credentials Exposed in Source Control

**File**: `docker-compose.yml:73-78`

**Current Code**:
```yaml
postgres:
  environment:
    POSTGRES_USER: directory_user
    POSTGRES_PASSWORD: directory_pass
```

**Problem**: Default credentials are visible in version control. Anyone with repository access can connect to production databases.

**Impact**: **CRITICAL** - Database access compromised

**Recommendation**:
1. Create `.env` file (add to `.gitignore`):
```
POSTGRES_USER=directory_user
POSTGRES_PASSWORD=<strong-random-password>
```

2. Update `docker-compose.yml`:
```yaml
postgres:
  env_file: .env
```

3. Create `.env.example` for documentation:
```
POSTGRES_USER=directory_user
POSTGRES_PASSWORD=change_this_password
```

**Effort**: 30 minutes

---

## High Priority Issues

### Issue #3: Insufficient Test Coverage

**Current State**:
- **Test Files**: 3
- **Source Files**: 23+
- **Coverage**: ~25% (by file count)

**Existing Tests** (all in Directory Service):

| Test File | Tests | Coverage |
|-----------|-------|----------|
| `DirectoryEntryTest.java` | 3 | UUID generation only |
| `DirectoryEntryMapperTest.java` | 6 | MapStruct mapping only |
| `DirectoryEntryDTOTest.java` | 8 | DTO validation only |

**Missing Test Coverage**:

| Component | Files | Risk |
|-----------|-------|------|
| REST Controllers | 1 | HIGH - API contracts untested |
| Service Layer | 4 | HIGH - Business logic untested |
| SFTP Server | 7 | HIGH - Security-critical code untested |
| Repository (custom queries) | 1 | MEDIUM - Upsert SQL untested |
| Spring Integration Flow | 3 | MEDIUM - SFTP polling untested |
| Data Generator | 2 | LOW - CLI tool untested |

**Recommendation**: Add tests in priority order:

1. **Controller Integration Tests** (WebMvcTest)
```java
@WebMvcTest(DirectoryEntryController.class)
class DirectoryEntryControllerTest {
    @Test void getAllEntries_returnsOk() { ... }
    @Test void createEntry_withValidData_returnsCreated() { ... }
    @Test void createEntry_withInvalidEmail_returnsBadRequest() { ... }
}
```

2. **Service Unit Tests** (Mockito)
```java
@ExtendWith(MockitoExtension.class)
class CsvImportServiceTest {
    @Test void importFromCsv_withValidData_returnsCorrectCounts() { ... }
    @Test void changeDetection_withIdenticalData_skipsUpdate() { ... }
}
```

3. **Repository Integration Tests** (DataJpaTest)
```java
@DataJpaTest
class DirectoryEntryRepositoryTest {
    @Test void upsertByEmail_insertsNewEntry() { ... }
    @Test void upsertByEmail_updatesExistingEntry() { ... }
}
```

**Effort**: 2-3 days for comprehensive coverage

---

### Issue #4: Generic Exception Handling

Multiple locations catch broad `Exception` class instead of specific types:

**Locations**:
- `CsvImportService.java:65` - CSV record parsing
- `CsvImportService.java:128` - Entry processing
- `CsvFileProcessor.java:75` - File processing
- `PublicKeyAuthenticator.java:86` - Key parsing

**Example** (`CsvImportService.java:65`):
```java
catch (Exception e) {
    log.warn("Failed to parse record {}: {}", record.getRecordNumber(), e.getMessage());
    failedRows++;
}
```

**Problems**:
- Masks programming errors (NullPointerException, IndexOutOfBoundsException)
- Makes debugging difficult
- Swallows unexpected exceptions

**Recommendation**: Catch specific exceptions:
```java
catch (IllegalArgumentException | NumberFormatException e) {
    log.warn("Failed to parse record {}: {}", record.getRecordNumber(), e.getMessage());
    failedRows++;
}
```

---

### Issue #5: Generic RuntimeException for Domain Errors

**File**: `microservices/directory/src/main/java/com/ird0/directory/service/DirectoryEntryService.java:25`

**Current Code**:
```java
return repository
    .findById(id)
    .orElseThrow(() -> new RuntimeException("Entry not found with id: " + id));
```

**Problem**: Generic exception makes it impossible to distinguish "not found" from other errors.

**Recommendation**: Create custom exception hierarchy:

```java
// Create in: com.ird0.directory.exception
public class EntityNotFoundException extends RuntimeException {
    public EntityNotFoundException(String message) {
        super(message);
    }
}

// Usage:
.orElseThrow(() -> new EntityNotFoundException("Entry not found with id: " + id));

// Global handler (add to controller or @ControllerAdvice):
@ExceptionHandler(EntityNotFoundException.class)
public ResponseEntity<ErrorResponse> handleNotFound(EntityNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(new ErrorResponse(ex.getMessage()));
}
```

**Effort**: 2 hours

---

### Issue #6: SQL Logging Enabled in All Environments

**File**: `microservices/directory/configs/application.yml:11`

**Current Code**:
```yaml
spring:
  jpa:
    show-sql: true
```

**Problem**:
- Performance overhead from logging every SQL statement
- Potential sensitive data exposure in logs
- Noise in production logs

**Recommendation**: Use environment-specific configuration:

```yaml
# application.yml (default: disabled)
spring:
  jpa:
    show-sql: false

# application-dev.yml (development only)
spring:
  jpa:
    show-sql: true
```

**Effort**: 15 minutes

---

## Medium Priority Issues

### Issue #7: Missing Email Validation in CSV Import

**File**: `microservices/directory/src/main/java/com/ird0/directory/service/CsvImportService.java:147-165`

**Problem**: CSV import checks if email is empty but doesn't validate format. Invalid emails like "notanemail" or "@example.com" are accepted.

**Note**: The DTO has `@Email` validation, but CSV import bypasses it.

**Recommendation**: Add email validation in `parseRecord()`:
```java
private static final String EMAIL_REGEX =
    "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";

private DirectoryEntry parseRecord(CSVRecord record) {
    String email = getField(record, "email");
    if (email != null && !email.matches(EMAIL_REGEX)) {
        log.warn("Invalid email format: {}", email);
        return null;
    }
    // ... rest of parsing
}
```

**Effort**: 30 minutes

---

### Issue #8: Sensitive Data Logged

**File**: `microservices/directory/src/main/java/com/ird0/directory/config/SftpIntegrationConfig.java:34-39`

**Current Code**:
```java
log.info(
    "SFTP session factory configured: host={}, port={}, user={}, privateKey={}",
    properties.getHost(),
    properties.getPort(),
    properties.getUsername(),
    properties.getPrivateKeyPath());
```

**Problem**: Logging SFTP credentials and key paths exposes security-sensitive information.

**Recommendation**: Remove sensitive fields from logs:
```java
log.info("SFTP session factory configured: host={}, port={}",
    properties.getHost(), properties.getPort());
```

**Effort**: 5 minutes

---

### Issue #9: Potential Path Traversal in SFTP Server

**File**: `microservices/sftp-server/src/main/java/com/ird0/sftp/filesystem/CsvVirtualFileSystemView.java:64`

**Current Code**:
```java
public Path getPath(String first, String... more) {
    Path requested = Paths.get(first, more);
    return rootPath.resolve(requested).normalize();
}
```

**Problem**: While `.normalize()` removes `..` sequences, no validation ensures the resolved path stays within `rootPath`.

**Recommendation**: Add containment check:
```java
public Path getPath(String first, String... more) {
    Path requested = Paths.get(first, more);
    Path resolved = rootPath.resolve(requested).normalize();

    if (!resolved.startsWith(rootPath)) {
        throw new SecurityException("Path traversal attempt blocked: " + requested);
    }
    return resolved;
}
```

**Effort**: 15 minutes

---

### Issue #10: Code Duplication in Import Error Handling

**File**: `microservices/directory/src/main/java/com/ird0/directory/service/ImportErrorHandler.java:65-94`

**Problem**: `moveToErrorDirectory()` and `moveToDeadLetterQueue()` have duplicated logic.

**Recommendation**: Extract common method:
```java
private void moveFile(File source, String targetDir, String prefix) throws IOException {
    Path targetPath = ensureDirectoryExists(targetDir);
    String filename = prefix + "_" + source.getName();
    Files.move(source.toPath(), targetPath.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
}
```

**Effort**: 30 minutes

---

### Issue #11: Missing Configuration Validation

**File**: `microservices/directory/src/main/java/com/ird0/directory/config/SftpImportProperties.java`

**Problem**: Configuration properties have no validation. Invalid values (negative timeout, zero batch size) cause runtime errors.

**Recommendation**: Add validation annotations:
```java
@Data
@Validated
@ConfigurationProperties(prefix = "directory.sftp-import")
public class SftpImportProperties {

    @Min(value = 1000, message = "Connection timeout must be at least 1 second")
    private int connectionTimeout = 10000;

    @Min(value = 1, message = "Batch size must be at least 1")
    private int batchSize = 500;

    @Min(value = 1, message = "Max retry attempts must be at least 1")
    private int maxAttempts = 3;
}
```

**Effort**: 30 minutes

---

### Issue #12: Missing Docker Infrastructure

**Multiple locations**

| Issue | Impact | Fix |
|-------|--------|-----|
| No `.dockerignore` | Larger images, slower builds | Create file |
| No restart policies | Services don't recover | Add `restart: unless-stopped` |
| No health checks | No automatic recovery | Add actuator health checks |
| No resource limits | OOM crashes | Add `deploy.resources` |

**Recommended `.dockerignore`**:
```
.git
.gitignore
.idea
*.md
target/
keys/*
!keys/.gitkeep
data/
temp/
```

**Recommended docker-compose additions**:
```yaml
policyholders:
  restart: unless-stopped
  healthcheck:
    test: ["CMD", "curl", "-f", "http://localhost:8081/actuator/health"]
    interval: 30s
    timeout: 10s
    retries: 3
  deploy:
    resources:
      limits:
        cpus: '1'
        memory: 512M
```

**Effort**: 1-2 hours

---

### Issue #13: No CI/CD Pipeline

**Problem**: No automated build, test, or deployment pipeline.

**Recommendation**: Create `.github/workflows/build.yml`:
```yaml
name: Build and Test
on: [push, pull_request]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
      - run: mvn clean verify
      - run: mvn spotless:check
```

**Effort**: 1-2 hours

---

## Low Priority Issues

### Issue #14: printStackTrace() Anti-Pattern

**File**: `utilities/directory-data-generator/src/main/java/com/ird0/utilities/datagen/DataGeneratorCLI.java:71`

**Current Code**:
```java
} catch (Exception e) {
    System.err.println("Unexpected error: " + e.getMessage());
    e.printStackTrace();
    return 1;
}
```

**Problem**: `printStackTrace()` uses `System.err` which can't be controlled. Inconsistent with rest of codebase using SLF4J.

**Recommendation**: Use logging framework:
```java
private static final Logger log = LoggerFactory.getLogger(DataGeneratorCLI.class);

} catch (Exception e) {
    log.error("Unexpected error", e);
    return 1;
}
```

**Effort**: 10 minutes

---

### Issue #15: Missing EXPOSE in Directory Dockerfile

**File**: `microservices/directory/Dockerfile`

**Problem**: No EXPOSE instruction documents which ports the container listens on.

**Recommendation**: Add before ENTRYPOINT:
```dockerfile
EXPOSE 8081
```

**Effort**: 1 minute

---

### Issue #16: Hardcoded Dependency Versions in Child POMs

**File**: `microservices/directory/pom.xml:65,70`

**Current Code**:
```xml
<version>1.5.5.Final</version>  <!-- MapStruct version -->
```

**Problem**: Version should be in parent POM properties for consistency.

**Recommendation**: Move to parent `pom.xml`:
```xml
<properties>
    <mapstruct.version>1.5.5.Final</mapstruct.version>
</properties>
```

**Effort**: 15 minutes

---

## Strengths

The codebase demonstrates several excellent practices worth highlighting:

### Architecture
- **Multi-instance microservice pattern**: Single codebase deployed three times with configuration overrides - elegant and maintainable
- **Clean MVC architecture**: Clear separation between Controller, Service, and Repository layers
- **Spring Integration for SFTP**: Sophisticated polling flow with proper thread management
- **DTO pattern with MapStruct**: Clean API contracts decoupled from entities

### Data Management
- **UUID primary keys**: Future-proof for distributed systems, no ID conflicts
- **Change detection**: Both file-level (timestamps) and row-level (field comparison) prevent unnecessary work
- **Batch processing**: 500-row batches with proper transaction boundaries
- **PostgreSQL upsert**: Efficient `INSERT ... ON CONFLICT` for bulk operations

### Infrastructure
- **Multi-stage Docker builds**: Optimized images with dependency caching
- **Non-root execution**: Both services properly configured with appuser
- **Proper volume management**: Data persistence with named volumes

### Documentation
- **Comprehensive CLAUDE.md files**: Detailed guidance for each module
- **Organized doc/ structure**: Topic-based documentation with index

### Code Quality
- **Constructor injection**: No field injection, easily testable
- **Lombok for boilerplate**: Consistent @Data, @RequiredArgsConstructor usage
- **Spotless code formatting**: Google Java Format enforced

---

## Recommendations Roadmap

### Phase 1: Critical Security (Do Immediately)

| Task | File | Effort |
|------|------|--------|
| Fix SFTP host key verification | `SftpIntegrationConfig.java` | 1-2 hours |
| Move credentials to .env file | `docker-compose.yml` | 30 minutes |
| Disable show-sql in production | `application.yml` | 15 minutes |

### Phase 2: High Priority (This Sprint)

| Task | Effort |
|------|--------|
| Add REST Controller integration tests | 4-6 hours |
| Add Service layer unit tests | 4-6 hours |
| Create custom exception hierarchy | 2 hours |
| Fix generic exception handling | 2 hours |

### Phase 3: Medium Priority (Next Sprint)

| Task | Effort |
|------|--------|
| Create .dockerignore | 15 minutes |
| Add restart policies and health checks | 1 hour |
| Add resource limits to containers | 30 minutes |
| Add configuration property validation | 30 minutes |
| Add CI/CD pipeline | 2 hours |
| Fix code duplication | 1 hour |
| Add email validation to CSV import | 30 minutes |
| Fix path traversal vulnerability | 15 minutes |

### Phase 4: Low Priority (Backlog)

| Task | Effort |
|------|--------|
| Replace printStackTrace with logging | 10 minutes |
| Add EXPOSE to Dockerfile | 1 minute |
| Centralize dependency versions | 15 minutes |
| Add SFTP server tests | 4-6 hours |

---

## Summary Table

| Category | Severity | Count | Key Issue |
|----------|----------|-------|-----------|
| Security | CRITICAL | 2 | SFTP MITM vulnerability, exposed credentials |
| Testing | HIGH | 1 | Only 25% test coverage |
| Error Handling | HIGH | 2 | Generic exceptions throughout |
| Configuration | HIGH | 1 | SQL logging in production |
| Code Quality | MEDIUM | 4 | Validation, logging, duplication, path traversal |
| Infrastructure | MEDIUM | 4 | Docker config, CI/CD |
| Build | LOW | 1 | Version management |
| Code Style | LOW | 2 | printStackTrace, EXPOSE |

---

## Appendix: File References

### Critical Files
- `microservices/directory/src/main/java/com/ird0/directory/config/SftpIntegrationConfig.java`
- `docker-compose.yml`

### Test Files (Existing)
- `microservices/directory/src/test/java/com/ird0/directory/model/DirectoryEntryTest.java`
- `microservices/directory/src/test/java/com/ird0/directory/mapper/DirectoryEntryMapperTest.java`
- `microservices/directory/src/test/java/com/ird0/directory/dto/DirectoryEntryDTOTest.java`

### Service Layer
- `microservices/directory/src/main/java/com/ird0/directory/service/DirectoryEntryService.java`
- `microservices/directory/src/main/java/com/ird0/directory/service/CsvImportService.java`
- `microservices/directory/src/main/java/com/ird0/directory/service/CsvFileProcessor.java`
- `microservices/directory/src/main/java/com/ird0/directory/service/ImportErrorHandler.java`

### SFTP Server
- `microservices/sftp-server/src/main/java/com/ird0/sftp/auth/PublicKeyAuthenticator.java`
- `microservices/sftp-server/src/main/java/com/ird0/sftp/filesystem/CsvVirtualFileSystemView.java`

### Configuration
- `microservices/directory/configs/application.yml`
- `microservices/directory/src/main/java/com/ird0/directory/config/SftpImportProperties.java`

### Infrastructure
- `microservices/directory/Dockerfile`
- `microservices/sftp-server/Dockerfile`
- `pom.xml`
- `microservices/directory/pom.xml`

---

*This document should be reviewed and updated as the system evolves or recommendations are implemented.*

# Testing Requirements for Production Deployment

This document outlines the test coverage requirements that must be met before deploying the IRD0 Insurance Platform to production.

## Current Test Coverage Status

### Summary

| Service | Test Files | Status | Risk Level |
|---------|------------|--------|------------|
| Directory | 7 files | Partial coverage | Medium |
| Incident | 1 file | Minimal coverage | High |
| Notification | 0 files | **No tests** | **Critical** |
| Portal-BFF | 0 files | **No tests** | **Critical** |
| SFTP-Server | 0 files | **No tests** | **Critical** |

### Directory Service

**Location:** `microservices/directory/src/test/java/`

**Existing Tests:**
- `DirectoryApplicationTests.java` - Context loading
- `DirectoryControllerTest.java` - Controller endpoints
- `DirectoryRepositoryTest.java` - Repository operations
- `DirectoryServiceTest.java` - Service layer logic
- `DirectoryMapperTest.java` - DTO mapping
- `DirectoryDtoTest.java` - DTO validation
- `DirectoryEntityTest.java` - Entity validation

**Gaps:**
- CSV import functionality (SFTP data ingestion)
- Bulk operations
- Pagination edge cases

### Incident Service

**Location:** `microservices/incident/src/test/java/`

**Existing Tests:**
- `IncidentApplicationTests.java` - Context loading only

**Gaps:**
- Controller endpoint tests
- State machine transition tests (DECLARED → QUALIFIED → IN_PROGRESS → RESOLVED)
- Invalid transition rejection tests
- Service layer logic

### Notification Service

**Location:** `microservices/notification/src/test/java/`

**Current Status:** No tests exist

**Required Tests:** See [critical-tests-spec.md](critical-tests-spec.md)

### Portal-BFF Service

**Location:** `microservices/portal-bff/src/test/java/`

**Current Status:** No tests exist

**Required Tests:** See [critical-tests-spec.md](critical-tests-spec.md)

### SFTP-Server Service

**Location:** `microservices/sftp-server/src/test/java/`

**Current Status:** No tests exist

**Required Tests:** See [critical-tests-spec.md](critical-tests-spec.md)

## Minimum Coverage Requirements

### Before Production Deployment

| Service | Required Coverage | Priority |
|---------|-------------------|----------|
| Notification | 70% line coverage | P0 - Critical |
| Portal-BFF | 70% line coverage | P0 - Critical |
| SFTP-Server | 60% line coverage | P0 - Critical |
| Incident | 60% line coverage | P1 - High |
| Directory | Maintain existing | P2 - Medium |

### Critical Paths That Must Be Tested

#### 1. Notification Service
- [ ] Webhook HTTP delivery (success/failure)
- [ ] Exponential backoff retry logic (1s, 2s, 4s)
- [ ] Maximum retry limit enforcement
- [ ] Circuit breaker state transitions
- [ ] Notification status persistence

#### 2. Portal-BFF Service
- [ ] Claims aggregation from multiple services
- [ ] Name resolution from directory services
- [ ] Circuit breaker fallback behavior
- [ ] Dashboard KPI calculations
- [ ] Authentication token validation

#### 3. SFTP-Server Service
- [ ] SSH public key authentication
- [ ] Read-only file system enforcement
- [ ] Directory listing permissions
- [ ] File download functionality
- [ ] Invalid key rejection

#### 4. Incident Service
- [ ] State machine transitions (all valid paths)
- [ ] Invalid transition rejection
- [ ] Incident creation with validation
- [ ] Incident querying and filtering

## Test Types Required

### Unit Tests

Test individual components in isolation:

```java
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {
    @Mock
    private WebhookClient webhookClient;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    void shouldDeliverWebhookSuccessfully() {
        // Given
        Notification notification = createTestNotification();
        when(webhookClient.send(any())).thenReturn(ResponseEntity.ok().build());

        // When
        DeliveryResult result = notificationService.deliver(notification);

        // Then
        assertThat(result.isSuccessful()).isTrue();
    }
}
```

### Integration Tests

Test component interactions with real dependencies:

```java
@SpringBootTest
@Testcontainers
class NotificationIntegrationTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Test
    void shouldPersistNotificationWithStatus() {
        // Test actual database operations
    }
}
```

### Controller Tests

Test REST API endpoints:

```java
@WebMvcTest(NotificationController.class)
class NotificationControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NotificationService notificationService;

    @Test
    void shouldCreateNotification() throws Exception {
        mockMvc.perform(post("/api/v1/notifications")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"webhookUrl": "https://example.com/webhook", "payload": {}}
                """))
            .andExpect(status().isCreated());
    }
}
```

## Test Infrastructure Requirements

### Dependencies to Add

```xml
<!-- pom.xml additions for test dependencies -->
<dependencies>
    <!-- JUnit 5 -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>

    <!-- Testcontainers for integration tests -->
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>postgresql</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>junit-jupiter</artifactId>
        <scope>test</scope>
    </dependency>

    <!-- WireMock for HTTP mocking -->
    <dependency>
        <groupId>org.wiremock</groupId>
        <artifactId>wiremock-standalone</artifactId>
        <version>3.5.4</version>
        <scope>test</scope>
    </dependency>

    <!-- AssertJ for fluent assertions -->
    <dependency>
        <groupId>org.assertj</groupId>
        <artifactId>assertj-core</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

### Test Configuration

Each service should have:

```yaml
# src/test/resources/application-test.yml
spring:
  datasource:
    url: jdbc:tc:postgresql:16:///test_db
    driver-class-name: org.testcontainers.jdbc.ContainerDatabaseDriver
  jpa:
    hibernate:
      ddl-auto: create-drop
```

## Running Tests

### Local Development

```bash
# Run all tests
./mvnw test

# Run tests for specific module
./mvnw test -pl microservices/notification

# Run with coverage report
./mvnw test jacoco:report

# View coverage report
open target/site/jacoco/index.html
```

### CI Pipeline

Tests run automatically in GitLab CI:

```yaml
test:notification:
  script:
    - ./mvnw test -pl microservices/notification -am
  artifacts:
    reports:
      junit: "**/target/surefire-reports/TEST-*.xml"
```

## Coverage Verification

### JaCoCo Configuration

Add to each module's `pom.xml`:

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.12</version>
    <executions>
        <execution>
            <goals>
                <goal>prepare-agent</goal>
            </goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>test</phase>
            <goals>
                <goal>report</goal>
            </goals>
        </execution>
        <execution>
            <id>check</id>
            <goals>
                <goal>check</goal>
            </goals>
            <configuration>
                <rules>
                    <rule>
                        <element>BUNDLE</element>
                        <limits>
                            <limit>
                                <counter>LINE</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>0.70</minimum>
                            </limit>
                        </limits>
                    </rule>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

### Enforcement

The CI pipeline will fail if coverage drops below thresholds:

```bash
./mvnw verify  # Includes coverage check
```

## Test Data Management

### Fixtures

Create shared test fixtures:

```java
public class TestFixtures {
    public static Notification createTestNotification() {
        return Notification.builder()
            .webhookUrl("https://example.com/webhook")
            .payload(Map.of("event", "test"))
            .status(NotificationStatus.PENDING)
            .build();
    }

    public static Incident createTestIncident() {
        return Incident.builder()
            .policyholderId(UUID.randomUUID())
            .insurerId(UUID.randomUUID())
            .type(IncidentType.WATER_DAMAGE)
            .status(IncidentStatus.DECLARED)
            .build();
    }
}
```

### Database Seeding

For integration tests:

```java
@Sql("/test-data/incidents.sql")
@Test
void shouldListIncidents() {
    // Test with pre-seeded data
}
```

## Acceptance Criteria

Before approving for production:

1. **All critical path tests pass** - No skipped or ignored tests for P0 functionality
2. **Coverage thresholds met** - As defined in JaCoCo configuration
3. **No critical security findings** - SAST/dependency scanning clean
4. **Integration tests green** - All services communicate correctly
5. **Performance acceptable** - Response times under SLA thresholds

## Next Steps

1. Review [critical-tests-spec.md](critical-tests-spec.md) for detailed test specifications
2. Implement tests in priority order (P0 → P1 → P2)
3. Run coverage report and verify thresholds
4. See [integration-tests.md](integration-tests.md) for E2E scenarios

# Integration Test Scenarios

This document defines end-to-end integration tests that verify the complete system behavior across multiple services.

## Overview

Integration tests validate:
- Service-to-service communication
- Data flow through the entire system
- Authentication and authorization
- Error handling and resilience

## Test Environment Setup

### Docker Compose Test Stack

```yaml
# docker-compose.test.yml
version: '3.8'

services:
  postgres-test:
    image: postgres:16-alpine
    environment:
      POSTGRES_USER: test_user
      POSTGRES_PASSWORD: test_pass
    ports:
      - "5433:5432"
    volumes:
      - ./init-test-dbs.sql:/docker-entrypoint-initdb.d/init.sql
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U test_user"]
      interval: 5s
      timeout: 5s
      retries: 5

  keycloak-test:
    image: quay.io/keycloak/keycloak:24.0
    command: start-dev --import-realm
    environment:
      KEYCLOAK_ADMIN: admin
      KEYCLOAK_ADMIN_PASSWORD: admin
    ports:
      - "8180:8080"
    volumes:
      - ./test-realm.json:/opt/keycloak/data/import/realm.json:ro
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/health"]
      interval: 10s
      timeout: 5s
      retries: 10

  wiremock:
    image: wiremock/wiremock:3.5.4
    ports:
      - "8089:8080"
    volumes:
      - ./wiremock:/home/wiremock
```

### Test Database Initialization

```sql
-- init-test-dbs.sql
CREATE DATABASE policyholders_test;
CREATE DATABASE experts_test;
CREATE DATABASE providers_test;
CREATE DATABASE insurers_test;
CREATE DATABASE incidents_test;
CREATE DATABASE notifications_test;

GRANT ALL PRIVILEGES ON DATABASE policyholders_test TO test_user;
GRANT ALL PRIVILEGES ON DATABASE experts_test TO test_user;
GRANT ALL PRIVILEGES ON DATABASE providers_test TO test_user;
GRANT ALL PRIVILEGES ON DATABASE insurers_test TO test_user;
GRANT ALL PRIVILEGES ON DATABASE incidents_test TO test_user;
GRANT ALL PRIVILEGES ON DATABASE notifications_test TO test_user;
```

---

## End-to-End Test Scenarios

### Scenario 1: Complete Incident Lifecycle

**Description:** Create a policyholder and insurer, then create and resolve an incident, verifying notifications are sent at each stage.

```java
@SpringBootTest
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IncidentLifecycleE2ETest {

    @Container
    static DockerComposeContainer<?> environment =
        new DockerComposeContainer<>(new File("docker-compose.test.yml"))
            .withExposedService("postgres-test", 5432)
            .withExposedService("keycloak-test", 8080)
            .withExposedService("wiremock", 8080)
            .waitingFor("postgres-test", Wait.forHealthcheck());

    @Autowired
    private TestRestTemplate restTemplate;

    private static UUID policyholderId;
    private static UUID insurerId;
    private static UUID incidentId;
    private static String authToken;

    @BeforeAll
    static void authenticate() {
        // Get OAuth2 token from Keycloak
        authToken = getAuthToken("test-user", "test-password");
    }

    @Test
    @Order(1)
    void step1_createPolicyholder() {
        // Given
        CreatePolicyholderRequest request = new CreatePolicyholderRequest(
            "John Doe", "individual", "john@example.com", "555-1234");

        HttpHeaders headers = authHeaders();

        // When
        ResponseEntity<PolicyholderResponse> response = restTemplate.exchange(
            "/api/policyholders",
            HttpMethod.POST,
            new HttpEntity<>(request, headers),
            PolicyholderResponse.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        policyholderId = response.getBody().getId();
    }

    @Test
    @Order(2)
    void step2_createInsurer() {
        // Given
        CreateInsurerRequest request = new CreateInsurerRequest(
            "Acme Insurance", "contact@acme.com", "555-5678");

        // When
        ResponseEntity<InsurerResponse> response = restTemplate.exchange(
            "/api/insurers",
            HttpMethod.POST,
            new HttpEntity<>(request, authHeaders()),
            InsurerResponse.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        insurerId = response.getBody().getId();
    }

    @Test
    @Order(3)
    void step3_createIncident() {
        // Given
        CreateIncidentRequest request = CreateIncidentRequest.builder()
            .policyholderId(policyholderId)
            .insurerId(insurerId)
            .type(IncidentType.WATER_DAMAGE)
            .description("Pipe burst in basement")
            .incidentDate(Instant.now().minus(1, ChronoUnit.DAYS))
            .build();

        // When
        ResponseEntity<IncidentResponse> response = restTemplate.exchange(
            "/api/v1/incidents",
            HttpMethod.POST,
            new HttpEntity<>(request, authHeaders()),
            IncidentResponse.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        incidentId = response.getBody().getId();
        assertThat(response.getBody().getStatus()).isEqualTo(IncidentStatus.DECLARED);
    }

    @Test
    @Order(4)
    void step4_verifyNotificationSent_IncidentCreated() throws Exception {
        // Wait for async notification
        Thread.sleep(1000);

        // Verify webhook was called
        WireMock.verify(postRequestedFor(urlEqualTo("/webhook/incidents"))
            .withRequestBody(matchingJsonPath("$.event", equalTo("incident.created")))
            .withRequestBody(matchingJsonPath("$.incidentId", equalTo(incidentId.toString()))));
    }

    @Test
    @Order(5)
    void step5_qualifyIncident() {
        // Given
        UpdateIncidentRequest request = UpdateIncidentRequest.builder()
            .status(IncidentStatus.QUALIFIED)
            .qualificationNotes("Verified by phone call")
            .build();

        // When
        ResponseEntity<IncidentResponse> response = restTemplate.exchange(
            "/api/v1/incidents/{id}",
            HttpMethod.PATCH,
            new HttpEntity<>(request, authHeaders()),
            IncidentResponse.class,
            incidentId);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getStatus()).isEqualTo(IncidentStatus.QUALIFIED);
    }

    @Test
    @Order(6)
    void step6_assignExpertAndStartProgress() {
        // First create an expert
        UUID expertId = createExpert();

        // Update incident to IN_PROGRESS
        UpdateIncidentRequest request = UpdateIncidentRequest.builder()
            .status(IncidentStatus.IN_PROGRESS)
            .assignedExpertId(expertId)
            .build();

        ResponseEntity<IncidentResponse> response = restTemplate.exchange(
            "/api/v1/incidents/{id}",
            HttpMethod.PATCH,
            new HttpEntity<>(request, authHeaders()),
            IncidentResponse.class,
            incidentId);

        assertThat(response.getBody().getStatus()).isEqualTo(IncidentStatus.IN_PROGRESS);
        assertThat(response.getBody().getAssignedExpertId()).isEqualTo(expertId);
    }

    @Test
    @Order(7)
    void step7_resolveIncident() {
        UpdateIncidentRequest request = UpdateIncidentRequest.builder()
            .status(IncidentStatus.RESOLVED)
            .resolutionNotes("Claim settled for $5,000")
            .build();

        ResponseEntity<IncidentResponse> response = restTemplate.exchange(
            "/api/v1/incidents/{id}",
            HttpMethod.PATCH,
            new HttpEntity<>(request, authHeaders()),
            IncidentResponse.class,
            incidentId);

        assertThat(response.getBody().getStatus()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(response.getBody().getResolvedAt()).isNotNull();
    }

    @Test
    @Order(8)
    void step8_verifyCompleteHistory() {
        // Get incident with full event history
        ResponseEntity<IncidentDetailResponse> response = restTemplate.exchange(
            "/api/v1/incidents/{id}?includeHistory=true",
            HttpMethod.GET,
            new HttpEntity<>(authHeaders()),
            IncidentDetailResponse.class,
            incidentId);

        List<IncidentEvent> history = response.getBody().getHistory();
        assertThat(history).hasSize(4);
        assertThat(history.get(0).getToStatus()).isEqualTo(IncidentStatus.DECLARED);
        assertThat(history.get(1).getToStatus()).isEqualTo(IncidentStatus.QUALIFIED);
        assertThat(history.get(2).getToStatus()).isEqualTo(IncidentStatus.IN_PROGRESS);
        assertThat(history.get(3).getToStatus()).isEqualTo(IncidentStatus.RESOLVED);
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(authToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
```

---

### Scenario 2: State Transition Enforcement

**Description:** Verify that invalid state transitions are rejected.

```java
@SpringBootTest
@Testcontainers
class StateTransitionEnforcementTest {

    @Test
    void cannotSkipFromDeclaredToResolved() {
        // Given: An incident in DECLARED state
        UUID incidentId = createIncidentInState(IncidentStatus.DECLARED);

        // When: Attempting to jump to RESOLVED
        UpdateIncidentRequest request = UpdateIncidentRequest.builder()
            .status(IncidentStatus.RESOLVED)
            .build();

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
            "/api/v1/incidents/{id}",
            HttpMethod.PATCH,
            new HttpEntity<>(request, authHeaders()),
            ErrorResponse.class,
            incidentId);

        // Then: Request is rejected
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getCode()).isEqualTo("INVALID_STATE_TRANSITION");
    }

    @Test
    void cannotTransitionBackwards() {
        // Given: An incident in QUALIFIED state
        UUID incidentId = createIncidentInState(IncidentStatus.QUALIFIED);

        // When: Attempting to go back to DECLARED
        UpdateIncidentRequest request = UpdateIncidentRequest.builder()
            .status(IncidentStatus.DECLARED)
            .build();

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
            "/api/v1/incidents/{id}",
            HttpMethod.PATCH,
            new HttpEntity<>(request, authHeaders()),
            ErrorResponse.class,
            incidentId);

        // Then: Request is rejected
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void cannotModifyResolvedIncident() {
        // Given: A resolved incident
        UUID incidentId = createIncidentInState(IncidentStatus.RESOLVED);

        // When: Attempting to update status
        UpdateIncidentRequest request = UpdateIncidentRequest.builder()
            .status(IncidentStatus.IN_PROGRESS)
            .build();

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
            "/api/v1/incidents/{id}",
            HttpMethod.PATCH,
            new HttpEntity<>(request, authHeaders()),
            ErrorResponse.class,
            incidentId);

        // Then: Request is rejected
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).contains("resolved");
    }
}
```

---

### Scenario 3: Portal Dashboard Aggregation

**Description:** Verify the Portal-BFF correctly aggregates data from all services.

```java
@SpringBootTest
@Testcontainers
class PortalDashboardE2ETest {

    @Test
    void dashboardShowsAggregatedData() {
        // Given: Several incidents in various states
        createIncidentsInAllStates();

        // When: Fetching dashboard
        ResponseEntity<DashboardResponse> response = restTemplate.exchange(
            "/api/portal/v1/dashboard",
            HttpMethod.GET,
            new HttpEntity<>(authHeaders()),
            DashboardResponse.class);

        // Then: Dashboard shows correct aggregation
        DashboardResponse dashboard = response.getBody();
        assertThat(dashboard.getTotalClaims()).isGreaterThan(0);
        assertThat(dashboard.getClaimsByStatus()).containsKeys(
            "DECLARED", "QUALIFIED", "IN_PROGRESS", "RESOLVED");
    }

    @Test
    void claimsListResolvesActorNames() {
        // Given: An incident with policyholder and insurer
        UUID policyholderId = createPolicyholder("John Smith");
        UUID insurerId = createInsurer("ABC Insurance");
        UUID incidentId = createIncident(policyholderId, insurerId);

        // When: Fetching claims list
        ResponseEntity<ClaimsListResponse> response = restTemplate.exchange(
            "/api/portal/v1/claims",
            HttpMethod.GET,
            new HttpEntity<>(authHeaders()),
            ClaimsListResponse.class);

        // Then: Names are resolved (not just UUIDs)
        ClaimSummary claim = findClaimById(response.getBody().getClaims(), incidentId);
        assertThat(claim.getPolicyholderName()).isEqualTo("John Smith");
        assertThat(claim.getInsurerName()).isEqualTo("ABC Insurance");
    }

    @Test
    void claimDetailIncludesAllRelatedInfo() {
        // Given: A complete incident with all actors
        UUID policyholderId = createPolicyholder("Jane Doe");
        UUID insurerId = createInsurer("XYZ Insurance");
        UUID expertId = createExpert("Expert Bob");
        UUID providerId = createProvider("RepairCo");

        UUID incidentId = createIncidentWithAssignments(
            policyholderId, insurerId, expertId, providerId);

        // When: Fetching claim detail
        ResponseEntity<ClaimDetailResponse> response = restTemplate.exchange(
            "/api/portal/v1/claims/{id}",
            HttpMethod.GET,
            new HttpEntity<>(authHeaders()),
            ClaimDetailResponse.class,
            incidentId);

        // Then: All actor names are resolved
        ClaimDetailResponse detail = response.getBody();
        assertThat(detail.getPolicyholderName()).isEqualTo("Jane Doe");
        assertThat(detail.getInsurerName()).isEqualTo("XYZ Insurance");
        assertThat(detail.getExpertName()).isEqualTo("Expert Bob");
        assertThat(detail.getProviderName()).isEqualTo("RepairCo");
    }
}
```

---

### Scenario 4: Keycloak Authentication Flow

**Description:** Verify authentication and authorization work correctly.

```java
@SpringBootTest
@Testcontainers
class AuthenticationE2ETest {

    @Test
    void unauthenticatedRequestRejected() {
        // When: Making request without auth token
        ResponseEntity<String> response = restTemplate.getForEntity(
            "/api/v1/incidents", String.class);

        // Then: 401 Unauthorized
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void expiredTokenRejected() {
        // Given: An expired token
        String expiredToken = generateExpiredToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(expiredToken);

        // When: Making request with expired token
        ResponseEntity<String> response = restTemplate.exchange(
            "/api/v1/incidents",
            HttpMethod.GET,
            new HttpEntity<>(headers),
            String.class);

        // Then: 401 Unauthorized
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void validTokenAccepted() {
        // Given: A valid token
        String token = getAuthToken("test-user", "test-password");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        // When: Making request with valid token
        ResponseEntity<String> response = restTemplate.exchange(
            "/api/v1/incidents",
            HttpMethod.GET,
            new HttpEntity<>(headers),
            String.class);

        // Then: 200 OK
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void tokenRefreshWorks() {
        // Given: Initial authentication
        TokenPair tokens = authenticate("test-user", "test-password");

        // When: Refreshing the token
        TokenPair newTokens = refreshToken(tokens.getRefreshToken());

        // Then: New access token works
        assertThat(newTokens.getAccessToken()).isNotEqualTo(tokens.getAccessToken());

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(newTokens.getAccessToken());

        ResponseEntity<String> response = restTemplate.exchange(
            "/api/v1/incidents",
            HttpMethod.GET,
            new HttpEntity<>(headers),
            String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void insufficientRoleRejected() {
        // Given: A user without admin role
        String token = getAuthToken("read-only-user", "password");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        // When: Attempting admin operation
        ResponseEntity<String> response = restTemplate.exchange(
            "/api/admin/users",
            HttpMethod.GET,
            new HttpEntity<>(headers),
            String.class);

        // Then: 403 Forbidden
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
```

---

### Scenario 5: Health Check Verification

**Description:** Verify all services report healthy status.

```java
@SpringBootTest
class HealthCheckE2ETest {

    private static final Map<String, Integer> SERVICE_PORTS = Map.of(
        "policyholders", 8081,
        "experts", 8082,
        "providers", 8083,
        "insurers", 8084,
        "incident", 8085,
        "notification", 8086,
        "portal-bff", 8090
    );

    @ParameterizedTest
    @MethodSource("serviceNames")
    void serviceIsHealthy(String serviceName) {
        int port = SERVICE_PORTS.get(serviceName);

        ResponseEntity<HealthResponse> response = restTemplate.getForEntity(
            "http://localhost:" + port + "/actuator/health",
            HealthResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getStatus()).isEqualTo("UP");
    }

    @Test
    void allServicesHealthy() {
        for (var entry : SERVICE_PORTS.entrySet()) {
            ResponseEntity<HealthResponse> response = restTemplate.getForEntity(
                "http://localhost:" + entry.getValue() + "/actuator/health",
                HealthResponse.class);

            assertThat(response.getBody().getStatus())
                .as("Service %s should be UP", entry.getKey())
                .isEqualTo("UP");
        }
    }

    @Test
    void keycloakIsHealthy() {
        ResponseEntity<String> response = restTemplate.getForEntity(
            "http://localhost:8180/health",
            String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    static Stream<String> serviceNames() {
        return SERVICE_PORTS.keySet().stream();
    }
}
```

---

### Scenario 6: Circuit Breaker Behavior

**Description:** Verify circuit breaker opens when services are unavailable.

```java
@SpringBootTest
class CircuitBreakerE2ETest {

    @Test
    void circuitBreakerOpensOnDirectoryFailure() {
        // Given: Directory service is down (simulated via WireMock)
        stubFor(get(urlMatching("/api/policyholders/.*"))
            .willReturn(aResponse()
                .withStatus(500)
                .withFixedDelay(5000)));

        // When: Making multiple requests that fail
        for (int i = 0; i < 10; i++) {
            try {
                restTemplate.exchange(
                    "/api/portal/v1/claims",
                    HttpMethod.GET,
                    new HttpEntity<>(authHeaders()),
                    ClaimsListResponse.class);
            } catch (Exception ignored) {}
        }

        // Then: Circuit breaker is open
        ResponseEntity<CircuitBreakerStatus> status = restTemplate.getForEntity(
            "/actuator/circuitbreakers/policyholders-client",
            CircuitBreakerStatus.class);

        assertThat(status.getBody().getState()).isEqualTo("OPEN");
    }

    @Test
    void fallbackReturnsGracefulDegradation() {
        // Given: Directory service is unavailable
        stubFor(get(urlMatching("/api/policyholders/.*"))
            .willReturn(aResponse().withStatus(503)));

        // When: Fetching claim detail
        ResponseEntity<ClaimDetailResponse> response = restTemplate.exchange(
            "/api/portal/v1/claims/{id}",
            HttpMethod.GET,
            new HttpEntity<>(authHeaders()),
            ClaimDetailResponse.class,
            existingIncidentId);

        // Then: Response includes fallback values
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getPolicyholderName()).isEqualTo("Unknown");
    }
}
```

---

## Running Integration Tests

### Locally

```bash
# Start test infrastructure
docker compose -f docker-compose.test.yml up -d

# Wait for services to be healthy
./scripts/wait-for-services.sh

# Run integration tests
./mvnw verify -Pintegration-tests

# Cleanup
docker compose -f docker-compose.test.yml down -v
```

### In CI Pipeline

See [gitlab-ci-guide.md](gitlab-ci-guide.md) for pipeline configuration.

```yaml
test:integration:
  services:
    - name: postgres:16-alpine
      alias: postgres
  script:
    - ./mvnw verify -Pintegration-tests
```

---

## Test Data Management

### Cleanup Between Tests

```java
@AfterEach
void cleanup() {
    // Reset WireMock
    WireMock.reset();

    // Clean test data
    testDataManager.cleanAll();
}
```

### Test Data Factory

```java
@Component
public class TestDataFactory {

    public UUID createPolicyholder(String name) {
        // Create via API and return ID
    }

    public UUID createInsurer(String name) {
        // Create via API and return ID
    }

    public UUID createIncident(UUID policyholderId, UUID insurerId) {
        // Create via API and return ID
    }

    public void advanceToState(UUID incidentId, IncidentStatus targetState) {
        // Transition incident through states to reach target
    }
}
```

---

## Expected Outcomes

All integration tests should:

1. **Pass consistently** - No flaky tests
2. **Be independent** - Can run in any order
3. **Clean up after themselves** - No leftover test data
4. **Complete in reasonable time** - Under 5 minutes total
5. **Provide clear failure messages** - Easy to diagnose issues

## Next Steps

1. Implement integration test suite
2. Add to CI pipeline
3. Run against staging environment
4. Document any issues found
5. Proceed to [security-checklist.md](security-checklist.md)

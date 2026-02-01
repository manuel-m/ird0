# Critical Tests Specification

This document provides detailed test specifications for the services currently lacking test coverage. These tests must be implemented before production deployment.

## Notification Service Tests

### Location
`microservices/notification/src/test/java/com/ird/notification/`

### NotificationDispatchServiceTest

Tests for the core webhook dispatch logic with retry handling.

```java
package com.ird.notification.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationDispatchServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationDispatchService dispatchService;

    @Test
    void testSuccessfulWebhookDelivery() {
        // Given: A pending notification
        Notification notification = createPendingNotification();
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
            .thenReturn(ResponseEntity.ok("received"));

        // When: Dispatch is attempted
        DeliveryResult result = dispatchService.dispatch(notification);

        // Then: Delivery succeeds and notification is marked DELIVERED
        assertThat(result.isSuccessful()).isTrue();
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.DELIVERED);
        verify(notificationRepository).save(notification);
    }

    @Test
    void testFailedWebhookWithRetry() {
        // Given: A notification that will fail on first attempt
        Notification notification = createPendingNotification();
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
            .thenThrow(new RestClientException("Connection refused"));

        // When: Dispatch is attempted
        DeliveryResult result = dispatchService.dispatch(notification);

        // Then: Delivery fails but retry is scheduled
        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.shouldRetry()).isTrue();
        assertThat(notification.getRetryCount()).isEqualTo(1);
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
    }

    @Test
    void testExponentialBackoff_1s_2s_4s() {
        // Given: A notification with retry history
        Notification notification = createNotificationWithRetries(0);

        // When/Then: First retry delay is 1 second
        long delay1 = dispatchService.calculateRetryDelay(notification);
        assertThat(delay1).isEqualTo(1000L);

        // When/Then: Second retry delay is 2 seconds
        notification.setRetryCount(1);
        long delay2 = dispatchService.calculateRetryDelay(notification);
        assertThat(delay2).isEqualTo(2000L);

        // When/Then: Third retry delay is 4 seconds
        notification.setRetryCount(2);
        long delay3 = dispatchService.calculateRetryDelay(notification);
        assertThat(delay3).isEqualTo(4000L);
    }

    @Test
    void testMaxRetriesExceeded() {
        // Given: A notification that has exhausted retries (3 attempts)
        Notification notification = createNotificationWithRetries(3);
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
            .thenThrow(new RestClientException("Still failing"));

        // When: Dispatch is attempted
        DeliveryResult result = dispatchService.dispatch(notification);

        // Then: Notification is marked FAILED, no more retries
        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.shouldRetry()).isFalse();
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
    }

    @Test
    void testCircuitBreakerOpens() {
        // Given: Multiple consecutive failures (circuit breaker threshold)
        for (int i = 0; i < 5; i++) {
            when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenThrow(new RestClientException("Service unavailable"));
            dispatchService.dispatch(createPendingNotification());
        }

        // When: Circuit breaker is queried
        boolean isOpen = dispatchService.isCircuitBreakerOpen();

        // Then: Circuit breaker should be open
        assertThat(isOpen).isTrue();
    }

    @Test
    void testWebhook4xxNotRetried() {
        // Given: A webhook that returns 400 Bad Request
        Notification notification = createPendingNotification();
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
            .thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST));

        // When: Dispatch is attempted
        DeliveryResult result = dispatchService.dispatch(notification);

        // Then: Marked as failed, no retry (client error)
        assertThat(result.shouldRetry()).isFalse();
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
    }

    @Test
    void testWebhook5xxIsRetried() {
        // Given: A webhook that returns 500 Internal Server Error
        Notification notification = createPendingNotification();
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
            .thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

        // When: Dispatch is attempted
        DeliveryResult result = dispatchService.dispatch(notification);

        // Then: Should retry (server error)
        assertThat(result.shouldRetry()).isTrue();
    }

    // Helper methods
    private Notification createPendingNotification() {
        return Notification.builder()
            .id(UUID.randomUUID())
            .webhookUrl("https://example.com/webhook")
            .payload(Map.of("event", "incident.created"))
            .status(NotificationStatus.PENDING)
            .retryCount(0)
            .createdAt(Instant.now())
            .build();
    }

    private Notification createNotificationWithRetries(int retryCount) {
        Notification notification = createPendingNotification();
        notification.setRetryCount(retryCount);
        return notification;
    }
}
```

### NotificationControllerTest

Tests for REST API endpoints.

```java
package com.ird.notification.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(NotificationController.class)
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NotificationService notificationService;

    @Test
    void testCreateNotification_201() throws Exception {
        // Given
        NotificationResponse response = new NotificationResponse(
            UUID.randomUUID(), NotificationStatus.PENDING);
        when(notificationService.create(any())).thenReturn(response);

        // When/Then
        mockMvc.perform(post("/api/v1/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "webhookUrl": "https://example.com/webhook",
                        "payload": {"event": "incident.created", "incidentId": "123"}
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void testCreateNotification_400_InvalidUrl() throws Exception {
        mockMvc.perform(post("/api/v1/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "webhookUrl": "not-a-valid-url",
                        "payload": {}
                    }
                    """))
            .andExpect(status().isBadRequest());
    }

    @Test
    void testGetNotificationStatus_200() throws Exception {
        // Given
        UUID id = UUID.randomUUID();
        Notification notification = createNotification(id, NotificationStatus.DELIVERED);
        when(notificationService.findById(id)).thenReturn(Optional.of(notification));

        // When/Then
        mockMvc.perform(get("/api/v1/notifications/{id}", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(id.toString()))
            .andExpect(jsonPath("$.status").value("DELIVERED"));
    }

    @Test
    void testGetNotificationStatus_404() throws Exception {
        // Given
        UUID id = UUID.randomUUID();
        when(notificationService.findById(id)).thenReturn(Optional.empty());

        // When/Then
        mockMvc.perform(get("/api/v1/notifications/{id}", id))
            .andExpect(status().isNotFound());
    }

    @Test
    void testListNotifications_Paginated() throws Exception {
        // Given
        Page<Notification> page = new PageImpl<>(List.of(
            createNotification(UUID.randomUUID(), NotificationStatus.PENDING),
            createNotification(UUID.randomUUID(), NotificationStatus.DELIVERED)
        ), PageRequest.of(0, 10), 2);
        when(notificationService.findAll(any(Pageable.class))).thenReturn(page);

        // When/Then
        mockMvc.perform(get("/api/v1/notifications")
                .param("page", "0")
                .param("size", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.content.length()").value(2))
            .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void testListNotificationsByStatus() throws Exception {
        // Given
        List<Notification> pending = List.of(
            createNotification(UUID.randomUUID(), NotificationStatus.PENDING));
        when(notificationService.findByStatus(NotificationStatus.PENDING))
            .thenReturn(pending);

        // When/Then
        mockMvc.perform(get("/api/v1/notifications")
                .param("status", "PENDING"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].status").value("PENDING"));
    }
}
```

---

## Portal-BFF Service Tests

### Location
`microservices/portal-bff/src/test/java/com/ird/portal/`

### ClaimsAggregationServiceTest

Tests for the claims data aggregation logic.

```java
package com.ird.portal.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClaimsAggregationServiceTest {

    @Mock
    private IncidentServiceClient incidentClient;

    @Mock
    private DirectoryServiceClient directoryClient;

    @InjectMocks
    private ClaimsAggregationService aggregationService;

    @Test
    void testAggregateIncidentWithActorNames() {
        // Given: An incident with actor UUIDs
        UUID policyholderId = UUID.randomUUID();
        UUID insurerId = UUID.randomUUID();
        UUID expertId = UUID.randomUUID();

        IncidentDto incident = IncidentDto.builder()
            .id(UUID.randomUUID())
            .policyholderId(policyholderId)
            .insurerId(insurerId)
            .assignedExpertId(expertId)
            .status(IncidentStatus.IN_PROGRESS)
            .build();

        // Mock directory lookups
        when(directoryClient.getPolicyholder(policyholderId))
            .thenReturn(new DirectoryEntry(policyholderId, "John Doe"));
        when(directoryClient.getInsurer(insurerId))
            .thenReturn(new DirectoryEntry(insurerId, "Acme Insurance"));
        when(directoryClient.getExpert(expertId))
            .thenReturn(new DirectoryEntry(expertId, "Jane Expert"));

        // When: Aggregating the incident
        AggregatedClaim claim = aggregationService.aggregate(incident);

        // Then: Names are resolved
        assertThat(claim.getPolicyholderName()).isEqualTo("John Doe");
        assertThat(claim.getInsurerName()).isEqualTo("Acme Insurance");
        assertThat(claim.getExpertName()).isEqualTo("Jane Expert");
    }

    @Test
    void testFallbackToUnknown_WhenDirectoryUnavailable() {
        // Given: Directory service is down
        UUID policyholderId = UUID.randomUUID();
        IncidentDto incident = IncidentDto.builder()
            .id(UUID.randomUUID())
            .policyholderId(policyholderId)
            .status(IncidentStatus.DECLARED)
            .build();

        when(directoryClient.getPolicyholder(policyholderId))
            .thenThrow(new ServiceUnavailableException("Directory service down"));

        // When: Aggregating the incident
        AggregatedClaim claim = aggregationService.aggregate(incident);

        // Then: Fallback to "Unknown" is used
        assertThat(claim.getPolicyholderName()).isEqualTo("Unknown");
    }

    @Test
    void testCircuitBreakerState() {
        // Given: Multiple failures trigger circuit breaker
        when(directoryClient.getPolicyholder(any()))
            .thenThrow(new ServiceUnavailableException("Service down"));

        // Trigger 5 failures
        for (int i = 0; i < 5; i++) {
            aggregationService.aggregate(createIncidentWithPolicyholder());
        }

        // When: Checking circuit breaker state
        CircuitBreakerState state = aggregationService.getCircuitBreakerState();

        // Then: Circuit breaker should be open
        assertThat(state).isEqualTo(CircuitBreakerState.OPEN);
    }

    @Test
    void testAggregateMultipleIncidents() {
        // Given: Multiple incidents
        List<IncidentDto> incidents = List.of(
            createIncidentWithPolicyholder(),
            createIncidentWithPolicyholder()
        );

        when(directoryClient.getPolicyholder(any()))
            .thenReturn(new DirectoryEntry(UUID.randomUUID(), "Test User"));

        // When: Aggregating all
        List<AggregatedClaim> claims = aggregationService.aggregateAll(incidents);

        // Then: All are processed
        assertThat(claims).hasSize(2);
    }

    @Test
    void testNullActorHandling() {
        // Given: Incident with no assigned expert (null)
        IncidentDto incident = IncidentDto.builder()
            .id(UUID.randomUUID())
            .policyholderId(UUID.randomUUID())
            .insurerId(UUID.randomUUID())
            .assignedExpertId(null)  // No expert assigned yet
            .status(IncidentStatus.DECLARED)
            .build();

        when(directoryClient.getPolicyholder(any()))
            .thenReturn(new DirectoryEntry(UUID.randomUUID(), "John Doe"));
        when(directoryClient.getInsurer(any()))
            .thenReturn(new DirectoryEntry(UUID.randomUUID(), "Acme Insurance"));

        // When: Aggregating
        AggregatedClaim claim = aggregationService.aggregate(incident);

        // Then: Expert name is null/empty, not "Unknown"
        assertThat(claim.getExpertName()).isNull();
        verify(directoryClient, never()).getExpert(any());
    }
}
```

### DashboardServiceTest

Tests for dashboard metrics and KPIs.

```java
package com.ird.portal.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private IncidentServiceClient incidentClient;

    @InjectMocks
    private DashboardService dashboardService;

    @Test
    void testKPICalculation() {
        // Given: A set of incidents with various statuses
        List<IncidentDto> incidents = List.of(
            createIncident(IncidentStatus.DECLARED),
            createIncident(IncidentStatus.DECLARED),
            createIncident(IncidentStatus.QUALIFIED),
            createIncident(IncidentStatus.IN_PROGRESS),
            createIncident(IncidentStatus.RESOLVED)
        );
        when(incidentClient.getAllIncidents()).thenReturn(incidents);

        // When: Calculating KPIs
        DashboardKPIs kpis = dashboardService.calculateKPIs();

        // Then: KPIs are correctly calculated
        assertThat(kpis.getTotalIncidents()).isEqualTo(5);
        assertThat(kpis.getOpenIncidents()).isEqualTo(4);  // All except RESOLVED
        assertThat(kpis.getResolvedIncidents()).isEqualTo(1);
        assertThat(kpis.getResolutionRate()).isEqualTo(20.0);  // 1/5 * 100
    }

    @Test
    void testClaimsByStatus() {
        // Given: Incidents with different statuses
        List<IncidentDto> incidents = List.of(
            createIncident(IncidentStatus.DECLARED),
            createIncident(IncidentStatus.DECLARED),
            createIncident(IncidentStatus.QUALIFIED),
            createIncident(IncidentStatus.IN_PROGRESS),
            createIncident(IncidentStatus.RESOLVED)
        );
        when(incidentClient.getAllIncidents()).thenReturn(incidents);

        // When: Getting status breakdown
        Map<IncidentStatus, Long> breakdown = dashboardService.getClaimsByStatus();

        // Then: Counts are correct
        assertThat(breakdown.get(IncidentStatus.DECLARED)).isEqualTo(2);
        assertThat(breakdown.get(IncidentStatus.QUALIFIED)).isEqualTo(1);
        assertThat(breakdown.get(IncidentStatus.IN_PROGRESS)).isEqualTo(1);
        assertThat(breakdown.get(IncidentStatus.RESOLVED)).isEqualTo(1);
    }

    @Test
    void testEmptyIncidentsList() {
        // Given: No incidents
        when(incidentClient.getAllIncidents()).thenReturn(List.of());

        // When: Calculating KPIs
        DashboardKPIs kpis = dashboardService.calculateKPIs();

        // Then: All zero, no division by zero
        assertThat(kpis.getTotalIncidents()).isEqualTo(0);
        assertThat(kpis.getResolutionRate()).isEqualTo(0.0);
    }

    @Test
    void testAverageResolutionTime() {
        // Given: Resolved incidents with resolution times
        List<IncidentDto> resolved = List.of(
            createResolvedIncident(Duration.ofDays(2)),
            createResolvedIncident(Duration.ofDays(4)),
            createResolvedIncident(Duration.ofDays(6))
        );
        when(incidentClient.getResolvedIncidents()).thenReturn(resolved);

        // When: Calculating average resolution time
        Duration avgTime = dashboardService.getAverageResolutionTime();

        // Then: Average is 4 days
        assertThat(avgTime).isEqualTo(Duration.ofDays(4));
    }
}
```

---

## SFTP-Server Service Tests

### Location
`microservices/sftp-server/src/test/java/com/ird/sftp/`

### SftpAuthenticationTest

Tests for SSH key authentication.

```java
package com.ird.sftp.security;

import org.apache.sshd.server.auth.pubkey.PublickeyAuthenticator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;

import static org.assertj.core.api.Assertions.*;

class SftpAuthenticationTest {

    @TempDir
    Path tempDir;

    private PublickeyAuthenticator authenticator;
    private KeyPair validKeyPair;
    private KeyPair invalidKeyPair;

    @BeforeEach
    void setUp() throws Exception {
        // Generate test key pairs
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        validKeyPair = keyGen.generateKeyPair();
        invalidKeyPair = keyGen.generateKeyPair();

        // Create authorized_keys file with valid key
        Path authorizedKeys = tempDir.resolve("authorized_keys");
        String publicKeyEntry = encodePublicKey(validKeyPair.getPublic());
        Files.writeString(authorizedKeys, publicKeyEntry);

        // Initialize authenticator
        authenticator = new AuthorizedKeysAuthenticator(authorizedKeys);
    }

    @Test
    void testValidPublicKey_Authenticated() {
        // Given: A valid public key that is in authorized_keys
        PublicKey validKey = validKeyPair.getPublic();

        // When: Authentication is attempted
        boolean result = authenticator.authenticate(
            "sftp-user", validKey, null);

        // Then: Authentication succeeds
        assertThat(result).isTrue();
    }

    @Test
    void testInvalidKey_Rejected() {
        // Given: A public key that is NOT in authorized_keys
        PublicKey invalidKey = invalidKeyPair.getPublic();

        // When: Authentication is attempted
        boolean result = authenticator.authenticate(
            "sftp-user", invalidKey, null);

        // Then: Authentication fails
        assertThat(result).isFalse();
    }

    @Test
    void testWrongUsername_Rejected() {
        // Given: Valid key but wrong username
        PublicKey validKey = validKeyPair.getPublic();

        // When: Authentication with wrong user
        boolean result = authenticator.authenticate(
            "wrong-user", validKey, null);

        // Then: Authentication fails
        assertThat(result).isFalse();
    }

    @Test
    void testMissingAuthorizedKeysFile() {
        // Given: No authorized_keys file exists
        Path missingFile = tempDir.resolve("nonexistent");
        authenticator = new AuthorizedKeysAuthenticator(missingFile);

        // When: Authentication is attempted
        boolean result = authenticator.authenticate(
            "sftp-user", validKeyPair.getPublic(), null);

        // Then: Authentication fails gracefully
        assertThat(result).isFalse();
    }

    @Test
    void testEmptyAuthorizedKeysFile() throws Exception {
        // Given: Empty authorized_keys file
        Path emptyFile = tempDir.resolve("empty_authorized_keys");
        Files.writeString(emptyFile, "");
        authenticator = new AuthorizedKeysAuthenticator(emptyFile);

        // When: Authentication is attempted
        boolean result = authenticator.authenticate(
            "sftp-user", validKeyPair.getPublic(), null);

        // Then: Authentication fails
        assertThat(result).isFalse();
    }

    private String encodePublicKey(PublicKey key) {
        // Simplified - actual implementation uses proper SSH encoding
        return "ssh-rsa " + Base64.getEncoder().encodeToString(key.getEncoded()) + " test@example.com";
    }
}
```

### SftpFileSystemTest

Tests for read-only file system operations.

```java
package com.ird.sftp.filesystem;

import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

class SftpFileSystemTest {

    @TempDir
    Path dataDir;

    private ReadOnlySftpFileSystem fileSystem;

    @BeforeEach
    void setUp() throws Exception {
        // Create test directory structure
        Files.createDirectories(dataDir.resolve("exports"));
        Files.writeString(dataDir.resolve("exports/policyholders.csv"), "id,name\n1,John");
        Files.writeString(dataDir.resolve("exports/claims.csv"), "id,status\n1,OPEN");

        fileSystem = new ReadOnlySftpFileSystem(dataDir);
    }

    @Test
    void testReadOnlyEnforced() {
        // Given: A path in the SFTP root
        Path testFile = dataDir.resolve("test.txt");

        // When: Attempting to create a file
        // Then: Operation should be rejected
        assertThatThrownBy(() -> fileSystem.createFile(testFile))
            .isInstanceOf(SftpReadOnlyException.class)
            .hasMessageContaining("Read-only");
    }

    @Test
    void testDeleteRejected() {
        // Given: An existing file
        Path existingFile = dataDir.resolve("exports/policyholders.csv");

        // When: Attempting to delete
        // Then: Operation should be rejected
        assertThatThrownBy(() -> fileSystem.delete(existingFile))
            .isInstanceOf(SftpReadOnlyException.class);
    }

    @Test
    void testDirectoryListing() throws Exception {
        // Given: The exports directory
        Path exportsDir = dataDir.resolve("exports");

        // When: Listing directory
        List<String> files = fileSystem.listDirectory(exportsDir);

        // Then: Files are listed correctly
        assertThat(files).containsExactlyInAnyOrder("policyholders.csv", "claims.csv");
    }

    @Test
    void testFileRead() throws Exception {
        // Given: An existing CSV file
        Path csvFile = dataDir.resolve("exports/policyholders.csv");

        // When: Reading the file
        byte[] content = fileSystem.readFile(csvFile);

        // Then: Content is correct
        assertThat(new String(content)).contains("id,name");
    }

    @Test
    void testPathTraversalPrevented() {
        // Given: An attempt to access parent directory
        Path maliciousPath = dataDir.resolve("../etc/passwd");

        // When: Attempting to read
        // Then: Access should be denied
        assertThatThrownBy(() -> fileSystem.readFile(maliciousPath))
            .isInstanceOf(SecurityException.class)
            .hasMessageContaining("outside root");
    }

    @Test
    void testStatFile() throws Exception {
        // Given: An existing file
        Path file = dataDir.resolve("exports/policyholders.csv");

        // When: Getting file stats
        FileAttributes attrs = fileSystem.stat(file);

        // Then: Attributes are returned
        assertThat(attrs.isRegularFile()).isTrue();
        assertThat(attrs.getSize()).isGreaterThan(0);
    }

    @Test
    void testWriteRejected() {
        // Given: An existing file
        Path file = dataDir.resolve("exports/policyholders.csv");

        // When: Attempting to write
        // Then: Operation should be rejected
        assertThatThrownBy(() -> fileSystem.writeFile(file, "new content".getBytes()))
            .isInstanceOf(SftpReadOnlyException.class);
    }

    @Test
    void testRenameRejected() {
        // Given: An existing file
        Path source = dataDir.resolve("exports/policyholders.csv");
        Path target = dataDir.resolve("exports/renamed.csv");

        // When: Attempting to rename
        // Then: Operation should be rejected
        assertThatThrownBy(() -> fileSystem.rename(source, target))
            .isInstanceOf(SftpReadOnlyException.class);
    }
}
```

### SftpServerIntegrationTest

Integration test using actual SFTP connections.

```java
package com.ird.sftp;

import com.jcraft.jsch.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.io.ByteArrayOutputStream;
import java.util.Vector;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SftpServerIntegrationTest {

    @LocalServerPort
    private int sftpPort;

    private Session session;
    private ChannelSftp sftpChannel;

    @BeforeEach
    void setUp() throws Exception {
        JSch jsch = new JSch();

        // Add the test private key
        jsch.addIdentity("test-key", getTestPrivateKey(), null, null);

        session = jsch.getSession("sftp-user", "localhost", sftpPort);
        session.setConfig("StrictHostKeyChecking", "no");
        session.connect(5000);

        Channel channel = session.openChannel("sftp");
        channel.connect(5000);
        sftpChannel = (ChannelSftp) channel;
    }

    @AfterEach
    void tearDown() {
        if (sftpChannel != null) sftpChannel.disconnect();
        if (session != null) session.disconnect();
    }

    @Test
    void testListDirectory() throws Exception {
        // When: Listing root directory
        Vector<ChannelSftp.LsEntry> entries = sftpChannel.ls("/");

        // Then: Export files are visible
        assertThat(entries).isNotEmpty();
    }

    @Test
    void testDownloadFile() throws Exception {
        // Given: A known file path
        String remotePath = "/exports/policyholders.csv";

        // When: Downloading the file
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        sftpChannel.get(remotePath, out);

        // Then: Content is retrieved
        assertThat(out.toString()).contains("id");
    }

    @Test
    void testUploadRejected() {
        // When: Attempting to upload a file
        // Then: Operation should fail
        assertThatThrownBy(() ->
            sftpChannel.put(new ByteArrayInputStream("test".getBytes()), "/test.txt"))
            .isInstanceOf(SftpException.class);
    }

    @Test
    void testDeleteRejected() {
        // When: Attempting to delete a file
        // Then: Operation should fail
        assertThatThrownBy(() ->
            sftpChannel.rm("/exports/policyholders.csv"))
            .isInstanceOf(SftpException.class);
    }

    private byte[] getTestPrivateKey() {
        // Return test private key bytes
        // In real tests, load from test resources
        return TestKeys.getPrivateKey();
    }
}
```

---

## Incident Service Tests

### Location
`microservices/incident/src/test/java/com/ird/incident/`

### IncidentStateMachineTest

Tests for state machine transitions.

```java
package com.ird.incident.statemachine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.*;

class IncidentStateMachineTest {

    private final IncidentStateMachine stateMachine = new IncidentStateMachine();

    @ParameterizedTest
    @CsvSource({
        "DECLARED, QUALIFIED, true",
        "QUALIFIED, IN_PROGRESS, true",
        "IN_PROGRESS, RESOLVED, true",
        "DECLARED, IN_PROGRESS, false",
        "DECLARED, RESOLVED, false",
        "QUALIFIED, DECLARED, false",
        "RESOLVED, DECLARED, false",
        "RESOLVED, IN_PROGRESS, false"
    })
    void testStateTransitions(IncidentStatus from, IncidentStatus to, boolean allowed) {
        // Given: Current state
        Incident incident = Incident.builder().status(from).build();

        // When/Then: Transition validity
        if (allowed) {
            assertThat(stateMachine.canTransition(incident, to)).isTrue();
        } else {
            assertThat(stateMachine.canTransition(incident, to)).isFalse();
        }
    }

    @Test
    void testTransitionCreatesEvent() {
        // Given: An incident in DECLARED state
        Incident incident = createIncident(IncidentStatus.DECLARED);

        // When: Transitioning to QUALIFIED
        IncidentEvent event = stateMachine.transition(incident, IncidentStatus.QUALIFIED);

        // Then: Event is created with correct details
        assertThat(event.getFromStatus()).isEqualTo(IncidentStatus.DECLARED);
        assertThat(event.getToStatus()).isEqualTo(IncidentStatus.QUALIFIED);
        assertThat(event.getTimestamp()).isNotNull();
    }

    @Test
    void testInvalidTransitionThrows() {
        // Given: An incident in DECLARED state
        Incident incident = createIncident(IncidentStatus.DECLARED);

        // When/Then: Direct transition to RESOLVED should fail
        assertThatThrownBy(() -> stateMachine.transition(incident, IncidentStatus.RESOLVED))
            .isInstanceOf(InvalidStateTransitionException.class)
            .hasMessageContaining("Cannot transition from DECLARED to RESOLVED");
    }

    @Test
    void testGetValidNextStates() {
        // Given: Various states
        assertThat(stateMachine.getValidNextStates(IncidentStatus.DECLARED))
            .containsExactly(IncidentStatus.QUALIFIED);

        assertThat(stateMachine.getValidNextStates(IncidentStatus.QUALIFIED))
            .containsExactly(IncidentStatus.IN_PROGRESS);

        assertThat(stateMachine.getValidNextStates(IncidentStatus.IN_PROGRESS))
            .containsExactly(IncidentStatus.RESOLVED);

        assertThat(stateMachine.getValidNextStates(IncidentStatus.RESOLVED))
            .isEmpty();
    }
}
```

---

## Test Execution Checklist

Before marking tests complete:

- [ ] All tests compile without errors
- [ ] All tests pass locally
- [ ] Tests are added to CI pipeline
- [ ] Coverage thresholds are met
- [ ] No flaky tests (run 3x to verify)
- [ ] Test data is properly isolated (no shared state)
- [ ] Mocks are properly verified
- [ ] Edge cases are covered

## Next Steps

1. Implement these test classes in respective modules
2. Run `./mvnw test` to verify all pass
3. Run `./mvnw jacoco:report` to check coverage
4. See [integration-tests.md](integration-tests.md) for E2E test scenarios

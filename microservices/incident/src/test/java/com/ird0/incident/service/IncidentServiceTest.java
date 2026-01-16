package com.ird0.incident.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ird0.incident.dto.CreateIncidentRequest;
import com.ird0.incident.dto.LocationDTO;
import com.ird0.incident.dto.StatusUpdateRequest;
import com.ird0.incident.exception.IncidentNotFoundException;
import com.ird0.incident.model.Incident;
import com.ird0.incident.model.IncidentStatus;
import com.ird0.incident.repository.IncidentRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IncidentServiceTest {

  @Mock private IncidentRepository incidentRepository;

  @Mock private ReferenceNumberGenerator referenceNumberGenerator;

  @Mock private DirectoryValidationService directoryValidationService;

  private IncidentService incidentService;

  private UUID testIncidentId;
  private UUID testPolicyholderId;
  private UUID testInsurerId;
  private Incident testIncident;

  @BeforeEach
  void setUp() {
    incidentService =
        new IncidentService(
            incidentRepository,
            referenceNumberGenerator,
            directoryValidationService,
            Optional.empty());

    testIncidentId = UUID.randomUUID();
    testPolicyholderId = UUID.randomUUID();
    testInsurerId = UUID.randomUUID();

    testIncident = new Incident();
    testIncident.setId(testIncidentId);
    testIncident.setReferenceNumber("INC-2026-000001");
    testIncident.setPolicyholderId(testPolicyholderId);
    testIncident.setInsurerId(testInsurerId);
    testIncident.setType("VEHICLE_ACCIDENT");
    testIncident.setDescription("Car accident on highway");
    testIncident.setIncidentDate(Instant.now());
    testIncident.setEstimatedDamage(new BigDecimal("5000.00"));
    testIncident.setCurrency("EUR");
    testIncident.setStatus(IncidentStatus.DECLARED);
  }

  @Test
  void getById_ExistingIncident_ReturnsIncident() {
    when(incidentRepository.findById(testIncidentId)).thenReturn(Optional.of(testIncident));

    Incident result = incidentService.getById(testIncidentId);

    assertNotNull(result);
    assertEquals(testIncidentId, result.getId());
    assertEquals("INC-2026-000001", result.getReferenceNumber());
  }

  @Test
  void getById_NonExistingIncident_ThrowsException() {
    UUID unknownId = UUID.randomUUID();
    when(incidentRepository.findById(unknownId)).thenReturn(Optional.empty());

    assertThrows(IncidentNotFoundException.class, () -> incidentService.getById(unknownId));
  }

  @Test
  void getByReferenceNumber_ExistingIncident_ReturnsIncident() {
    when(incidentRepository.findByReferenceNumber("INC-2026-000001"))
        .thenReturn(Optional.of(testIncident));

    Incident result = incidentService.getByReferenceNumber("INC-2026-000001");

    assertNotNull(result);
    assertEquals("INC-2026-000001", result.getReferenceNumber());
  }

  @Test
  void getByReferenceNumber_NonExistingIncident_ThrowsException() {
    when(incidentRepository.findByReferenceNumber("UNKNOWN")).thenReturn(Optional.empty());

    assertThrows(
        IncidentNotFoundException.class, () -> incidentService.getByReferenceNumber("UNKNOWN"));
  }

  @Test
  void createIncident_ValidRequest_CreatesIncident() {
    CreateIncidentRequest request = new CreateIncidentRequest();
    request.setPolicyholderId(testPolicyholderId);
    request.setInsurerId(testInsurerId);
    request.setType("VEHICLE_ACCIDENT");
    request.setDescription("Car accident");
    request.setIncidentDate(Instant.now());
    request.setEstimatedDamage(new BigDecimal("5000.00"));

    LocationDTO location = new LocationDTO();
    location.setAddress("123 Main St");
    location.setLatitude(48.8566);
    location.setLongitude(2.3522);
    request.setLocation(location);

    when(referenceNumberGenerator.generate()).thenReturn("INC-2026-000001");
    doNothing().when(directoryValidationService).validatePolicyholder(any());
    doNothing().when(directoryValidationService).validateInsurer(any());
    when(incidentRepository.save(any(Incident.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    Incident result = incidentService.createIncident(request, testPolicyholderId);

    assertNotNull(result);
    assertEquals("INC-2026-000001", result.getReferenceNumber());
    assertEquals(testPolicyholderId, result.getPolicyholderId());
    assertEquals(testInsurerId, result.getInsurerId());
    assertEquals(IncidentStatus.DECLARED, result.getStatus());
    assertNotNull(result.getLocation());
    assertEquals("123 Main St", result.getLocation().getAddress());

    verify(directoryValidationService, times(1)).validatePolicyholder(testPolicyholderId);
    verify(directoryValidationService, times(1)).validateInsurer(testInsurerId);
    verify(incidentRepository, times(1)).save(any(Incident.class));
  }

  @Test
  void updateStatus_ValidTransition_UpdatesStatus() {
    testIncident.setStatus(IncidentStatus.DECLARED);
    when(incidentRepository.findById(testIncidentId)).thenReturn(Optional.of(testIncident));
    when(incidentRepository.save(any(Incident.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    StatusUpdateRequest request = new StatusUpdateRequest();
    request.setStatus(IncidentStatus.UNDER_REVIEW);
    request.setReason("Starting review process");

    Incident result = incidentService.updateStatus(testIncidentId, request, UUID.randomUUID());

    assertNotNull(result);
    assertEquals(IncidentStatus.UNDER_REVIEW, result.getStatus());
  }

  @Test
  void deleteIncident_ExistingIncident_DeletesSuccessfully() {
    when(incidentRepository.existsById(testIncidentId)).thenReturn(true);

    incidentService.deleteIncident(testIncidentId);

    verify(incidentRepository, times(1)).deleteById(testIncidentId);
  }

  @Test
  void deleteIncident_NonExistingIncident_ThrowsException() {
    UUID unknownId = UUID.randomUUID();
    when(incidentRepository.existsById(unknownId)).thenReturn(false);

    assertThrows(IncidentNotFoundException.class, () -> incidentService.deleteIncident(unknownId));
  }
}

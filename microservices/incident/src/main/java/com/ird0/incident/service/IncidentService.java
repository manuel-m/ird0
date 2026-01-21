package com.ird0.incident.service;

import com.ird0.incident.dto.CreateIncidentRequest;
import com.ird0.incident.dto.ExpertAssignmentRequest;
import com.ird0.incident.dto.StatusUpdateRequest;
import com.ird0.incident.exception.IncidentNotFoundException;
import com.ird0.incident.model.Comment;
import com.ird0.incident.model.ExpertAssignment;
import com.ird0.incident.model.Incident;
import com.ird0.incident.model.IncidentEvent;
import com.ird0.incident.model.IncidentStatus;
import com.ird0.incident.model.Location;
import com.ird0.incident.repository.IncidentRepository;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class IncidentService {

  private final IncidentRepository incidentRepository;
  private final ReferenceNumberGenerator referenceNumberGenerator;
  private final DirectoryValidationService directoryValidationService;
  private final Optional<NotificationClient> notificationClient;

  @Transactional(readOnly = true)
  public Incident getById(UUID id) {
    return incidentRepository.findById(id).orElseThrow(() -> new IncidentNotFoundException(id));
  }

  @Transactional(readOnly = true)
  public Incident getByReferenceNumber(String referenceNumber) {
    return incidentRepository
        .findByReferenceNumber(referenceNumber)
        .orElseThrow(() -> new IncidentNotFoundException(referenceNumber));
  }

  @Transactional(readOnly = true)
  public Page<Incident> findAll(Pageable pageable) {
    return incidentRepository.findAll(pageable);
  }

  @Transactional(readOnly = true)
  public Page<Incident> findWithFilters(
      UUID policyholderId,
      UUID insurerId,
      IncidentStatus status,
      String type,
      Instant fromDate,
      Instant toDate,
      Pageable pageable) {
    Specification<Incident> spec = Specification.where(null);

    if (policyholderId != null) {
      spec = spec.and((root, query, cb) -> cb.equal(root.get("policyholderId"), policyholderId));
    }
    if (insurerId != null) {
      spec = spec.and((root, query, cb) -> cb.equal(root.get("insurerId"), insurerId));
    }
    if (status != null) {
      spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
    }
    if (type != null) {
      spec = spec.and((root, query, cb) -> cb.equal(root.get("type"), type));
    }
    if (fromDate != null) {
      spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), fromDate));
    }
    if (toDate != null) {
      spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), toDate));
    }

    return incidentRepository.findAll(spec, pageable);
  }

  @Transactional
  public Incident createIncident(CreateIncidentRequest request, UUID createdBy) {
    log.info(
        "Creating incident for policyholder {} with insurer {}",
        request.getPolicyholderId(),
        request.getInsurerId());

    // Validate references exist in directory services
    directoryValidationService.validatePolicyholder(request.getPolicyholderId());
    directoryValidationService.validateInsurer(request.getInsurerId());

    Incident incident = new Incident();
    incident.setReferenceNumber(referenceNumberGenerator.generate());
    incident.setPolicyholderId(request.getPolicyholderId());
    incident.setInsurerId(request.getInsurerId());
    incident.setType(request.getType());
    incident.setDescription(request.getDescription());
    incident.setIncidentDate(request.getIncidentDate());
    incident.setEstimatedDamage(request.getEstimatedDamage());
    incident.setCurrency(request.getCurrency() != null ? request.getCurrency() : "EUR");
    incident.setCreatedBy(createdBy);

    if (request.getLocation() != null) {
      Location location =
          new Location(
              request.getLocation().getAddress(),
              request.getLocation().getLatitude(),
              request.getLocation().getLongitude());
      incident.setLocation(location);
    }

    // Create initial event
    IncidentEvent event =
        IncidentEvent.createStatusChangeEvent(
            incident,
            null,
            IncidentStatus.DECLARED,
            createdBy,
            Map.of("action", "INCIDENT_CREATED"));
    incident.addEvent(event);

    Incident saved = incidentRepository.save(incident);
    log.info("Created incident with reference number: {}", saved.getReferenceNumber());

    // Send notification to insurer
    notificationClient.ifPresent(client -> client.sendIncidentDeclaredNotification(saved));

    return saved;
  }

  @Transactional
  public Incident updateStatus(UUID incidentId, StatusUpdateRequest request, UUID updatedBy) {
    Incident incident = getById(incidentId);
    IncidentStatus previousStatus = incident.getStatus();
    IncidentStatus newStatus = request.getStatus();

    log.info(
        "Updating incident {} status from {} to {}",
        incident.getReferenceNumber(),
        previousStatus,
        newStatus);

    incident.transitionTo(newStatus);

    // Create status change event
    Map<String, Object> payload =
        Map.of(
            "reason",
            request.getReason() != null ? request.getReason() : "",
            "qualificationDetails",
            request.getQualificationDetails() != null ? request.getQualificationDetails() : "");

    IncidentEvent event =
        IncidentEvent.createStatusChangeEvent(
            incident, previousStatus, newStatus, updatedBy, payload);
    incident.addEvent(event);

    Incident saved = incidentRepository.save(incident);

    // Send notifications based on status change
    if (newStatus == IncidentStatus.QUALIFIED) {
      notificationClient.ifPresent(client -> client.sendIncidentQualifiedNotification(saved));
    } else if (newStatus == IncidentStatus.ABANDONED) {
      notificationClient.ifPresent(client -> client.sendIncidentAbandonedNotification(saved));
    }

    return saved;
  }

  @Transactional
  public Incident assignExpert(UUID incidentId, ExpertAssignmentRequest request, UUID assignedBy) {
    Incident incident = getById(incidentId);

    // Validate expert exists
    directoryValidationService.validateExpert(request.getExpertId());

    log.info(
        "Assigning expert {} to incident {}", request.getExpertId(), incident.getReferenceNumber());

    ExpertAssignment assignment = new ExpertAssignment();
    assignment.setExpertId(request.getExpertId());
    assignment.setAssignedBy(assignedBy);
    assignment.setScheduledDate(request.getScheduledDate());
    assignment.setNotes(request.getNotes());

    incident.addExpertAssignment(assignment);

    // Create event
    IncidentEvent event =
        IncidentEvent.createExpertAssignedEvent(incident, request.getExpertId(), assignedBy);
    incident.addEvent(event);

    // If incident is QUALIFIED, transition to IN_PROGRESS
    if (incident.getStatus() == IncidentStatus.QUALIFIED) {
      IncidentStatus previousStatus = incident.getStatus();
      incident.transitionTo(IncidentStatus.IN_PROGRESS);

      IncidentEvent statusEvent =
          IncidentEvent.createStatusChangeEvent(
              incident,
              previousStatus,
              IncidentStatus.IN_PROGRESS,
              assignedBy,
              Map.of("reason", "Expert assigned, processing started"));
      incident.addEvent(statusEvent);
    }

    Incident saved = incidentRepository.save(incident);

    // Send notification about expert assignment
    notificationClient.ifPresent(
        client -> client.sendExpertAssignedNotification(saved, request.getExpertId()));

    return saved;
  }

  @Transactional
  public Incident addComment(
      UUID incidentId, String content, UUID authorId, Comment.AuthorType authorType) {
    Incident incident = getById(incidentId);

    log.info("Adding comment to incident {}", incident.getReferenceNumber());

    Comment comment = new Comment();
    comment.setAuthorId(authorId);
    comment.setAuthorType(authorType);
    comment.setContent(content);

    incident.addComment(comment);

    // Create event
    IncidentEvent event =
        IncidentEvent.createCommentAddedEvent(incident, authorId, authorType.name());
    incident.addEvent(event);

    return incidentRepository.save(incident);
  }

  @Transactional
  public void deleteIncident(UUID id) {
    if (!incidentRepository.existsById(id)) {
      throw new IncidentNotFoundException(id);
    }
    incidentRepository.deleteById(id);
    log.info("Deleted incident with id: {}", id);
  }
}

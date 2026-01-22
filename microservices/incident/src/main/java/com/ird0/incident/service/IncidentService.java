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
      spec =
          spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), fromDate));
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
        request.policyholderId(),
        request.insurerId());

    // Validate references exist in directory services
    directoryValidationService.validatePolicyholder(request.policyholderId());
    directoryValidationService.validateInsurer(request.insurerId());

    Incident incident = new Incident();
    incident.setReferenceNumber(referenceNumberGenerator.generate());
    incident.setPolicyholderId(request.policyholderId());
    incident.setInsurerId(request.insurerId());
    incident.setType(request.type());
    incident.setDescription(request.description());
    incident.setIncidentDate(request.incidentDate());
    incident.setEstimatedDamage(request.estimatedDamage());
    incident.setCurrency(request.currency() != null ? request.currency() : "EUR");
    incident.setCreatedBy(createdBy);

    if (request.location() != null) {
      Location location =
          new Location(
              request.location().address(),
              request.location().latitude(),
              request.location().longitude());
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
    IncidentStatus newStatus = request.status();

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
            request.reason() != null ? request.reason() : "",
            "qualificationDetails",
            request.qualificationDetails() != null ? request.qualificationDetails() : "");

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
  public Incident updateInsurer(UUID incidentId, UUID insurerId, String reason, UUID updatedBy) {
    Incident incident = getById(incidentId);
    UUID previousInsurerId = incident.getInsurerId();

    log.info(
        "Updating incident {} insurer from {} to {}",
        incident.getReferenceNumber(),
        previousInsurerId,
        insurerId);

    // Validate insurer exists
    directoryValidationService.validateInsurer(insurerId);

    incident.setInsurerId(insurerId);

    // Create insurer change event
    IncidentEvent event =
        IncidentEvent.createInsurerUpdatedEvent(
            incident, previousInsurerId, insurerId, updatedBy, reason);
    incident.addEvent(event);

    Incident saved = incidentRepository.save(incident);
    log.info("Updated insurer for incident {}", saved.getReferenceNumber());

    return saved;
  }

  @Transactional
  public Incident assignExpert(UUID incidentId, ExpertAssignmentRequest request, UUID assignedBy) {
    Incident incident = getById(incidentId);

    // Validate expert exists
    directoryValidationService.validateExpert(request.expertId());

    log.info(
        "Assigning expert {} to incident {}", request.expertId(), incident.getReferenceNumber());

    ExpertAssignment assignment = new ExpertAssignment();
    assignment.setExpertId(request.expertId());
    assignment.setAssignedBy(assignedBy);
    assignment.setScheduledDate(request.scheduledDate());
    assignment.setNotes(request.notes());

    incident.addExpertAssignment(assignment);

    // Create event
    IncidentEvent event =
        IncidentEvent.createExpertAssignedEvent(incident, request.expertId(), assignedBy);
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
        client -> client.sendExpertAssignedNotification(saved, request.expertId()));

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

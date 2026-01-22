package com.ird0.portal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.ird0.portal.client.DirectoryClient;
import com.ird0.portal.client.IncidentClient;
import com.ird0.portal.dto.request.CommentRequestDTO;
import com.ird0.portal.dto.request.CreateClaimRequestDTO;
import com.ird0.portal.dto.request.ExpertAssignmentRequestDTO;
import com.ird0.portal.dto.request.StatusUpdateRequestDTO;
import com.ird0.portal.dto.response.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClaimsAggregationService {

  private final IncidentClient incidentClient;
  private final DirectoryClient directoryClient;

  private static final Map<String, List<String>> STATUS_TRANSITIONS =
      Map.of(
          "DECLARED", List.of("UNDER_REVIEW"),
          "UNDER_REVIEW", List.of("QUALIFIED", "ABANDONED"),
          "QUALIFIED", List.of("IN_PROGRESS", "ABANDONED"),
          "IN_PROGRESS", List.of("CLOSED"),
          "CLOSED", List.of(),
          "ABANDONED", List.of());

  public Page<ClaimSummaryDTO> getClaims(
      UUID policyholderId,
      UUID insurerId,
      String status,
      String type,
      Instant fromDate,
      Instant toDate,
      int page,
      int size,
      String sort) {

    JsonNode response =
        incidentClient.getIncidents(
            policyholderId, insurerId, status, type, fromDate, toDate, page, size, sort);

    if (response == null) {
      return Page.empty();
    }

    List<ClaimSummaryDTO> claims = new ArrayList<>();
    JsonNode content = response.get("content");
    if (content != null && content.isArray()) {
      for (JsonNode incident : content) {
        claims.add(mapToClaimSummary(incident));
      }
    }

    long totalElements = response.has("totalElements") ? response.get("totalElements").asLong() : 0;
    return new PageImpl<>(claims, PageRequest.of(page, size), totalElements);
  }

  public ClaimDetailDTO getClaimById(UUID id) {
    JsonNode incident = incidentClient.getIncidentById(id);
    if (incident == null) {
      return null;
    }
    return mapToClaimDetail(incident);
  }

  public ClaimDetailDTO createClaim(CreateClaimRequestDTO request) {
    // Validate policyholder and insurer exist
    ActorDTO policyholder = directoryClient.getPolicyholder(request.policyholderId());
    ActorDTO insurer = directoryClient.getInsurer(request.insurerId());

    if (policyholder == null || insurer == null) {
      throw new IllegalArgumentException("Invalid policyholder or insurer ID");
    }

    Map<String, Object> incidentRequest = new HashMap<>();
    incidentRequest.put("policyholderId", request.policyholderId().toString());
    incidentRequest.put("insurerId", request.insurerId().toString());
    incidentRequest.put("type", request.type());
    incidentRequest.put("description", request.description());
    incidentRequest.put("incidentDate", request.incidentDate().toString());
    incidentRequest.put("estimatedDamage", request.estimatedDamage());
    incidentRequest.put("currency", request.currency());

    if (request.location() != null) {
      Map<String, Object> location = new HashMap<>();
      location.put("address", request.location().address());
      location.put("latitude", request.location().latitude());
      location.put("longitude", request.location().longitude());
      incidentRequest.put("location", location);
    }

    JsonNode response = incidentClient.createIncident(incidentRequest);
    return mapToClaimDetail(response);
  }

  public ClaimDetailDTO updateStatus(UUID id, StatusUpdateRequestDTO request) {
    Map<String, Object> statusRequest = new HashMap<>();
    statusRequest.put("status", request.status());
    if (request.reason() != null) {
      statusRequest.put("reason", request.reason());
    }

    JsonNode response = incidentClient.updateStatus(id, statusRequest);
    return mapToClaimDetail(response);
  }

  public ClaimDetailDTO assignExpert(UUID id, ExpertAssignmentRequestDTO request) {
    // Validate expert exists
    ActorDTO expert = directoryClient.getExpert(request.expertId());
    if (expert == null) {
      throw new IllegalArgumentException("Invalid expert ID");
    }

    Map<String, Object> assignmentRequest = new HashMap<>();
    assignmentRequest.put("expertId", request.expertId().toString());
    if (request.scheduledDate() != null) {
      assignmentRequest.put("scheduledDate", request.scheduledDate().toString());
    }
    if (request.notes() != null) {
      assignmentRequest.put("notes", request.notes());
    }

    JsonNode response = incidentClient.assignExpert(id, assignmentRequest);
    return mapToClaimDetail(response);
  }

  public ClaimDetailDTO addComment(UUID id, CommentRequestDTO request) {
    Map<String, Object> commentRequest = new HashMap<>();
    commentRequest.put("content", request.content());
    commentRequest.put("authorId", request.authorId().toString());
    commentRequest.put("authorType", request.authorType());

    JsonNode response = incidentClient.addComment(id, commentRequest);
    return mapToClaimDetail(response);
  }

  public List<CommentDTO> getComments(UUID id) {
    JsonNode comments = incidentClient.getComments(id);
    if (comments == null || !comments.isArray()) {
      return List.of();
    }

    List<CommentDTO> result = new ArrayList<>();
    for (JsonNode comment : comments) {
      result.add(mapToCommentDTO(comment));
    }
    return result;
  }

  public List<EventDTO> getHistory(UUID id) {
    JsonNode events = incidentClient.getHistory(id);
    if (events == null || !events.isArray()) {
      return List.of();
    }

    List<EventDTO> result = new ArrayList<>();
    for (JsonNode event : events) {
      result.add(mapToEventDTO(event));
    }
    return result;
  }

  public List<ActorDTO> getExperts() {
    return directoryClient.getAllExperts();
  }

  public List<ActorDTO> getPolicyholders() {
    return directoryClient.getAllPolicyholders();
  }

  public List<ActorDTO> getInsurers() {
    return directoryClient.getAllInsurers();
  }

  private ClaimSummaryDTO mapToClaimSummary(JsonNode incident) {
    UUID policyholderId =
        incident.has("policyholderId")
            ? UUID.fromString(incident.get("policyholderId").asText())
            : null;
    UUID insurerId =
        incident.has("insurerId") ? UUID.fromString(incident.get("insurerId").asText()) : null;

    String policyholderName = "Unknown";
    String insurerName = "Unknown";

    if (policyholderId != null) {
      ActorDTO policyholder = directoryClient.getPolicyholder(policyholderId);
      if (policyholder != null) {
        policyholderName = policyholder.name();
      }
    }

    if (insurerId != null) {
      ActorDTO insurer = directoryClient.getInsurer(insurerId);
      if (insurer != null) {
        insurerName = insurer.name();
      }
    }

    return new ClaimSummaryDTO(
        incident.has("id") ? UUID.fromString(incident.get("id").asText()) : null,
        incident.has("referenceNumber") ? incident.get("referenceNumber").asText() : null,
        incident.has("status") ? incident.get("status").asText() : null,
        incident.has("type") ? incident.get("type").asText() : null,
        policyholderName,
        insurerName,
        incident.has("estimatedDamage")
            ? new BigDecimal(incident.get("estimatedDamage").asText())
            : null,
        incident.has("currency") ? incident.get("currency").asText() : null,
        incident.has("incidentDate") ? Instant.parse(incident.get("incidentDate").asText()) : null,
        incident.has("createdAt") ? Instant.parse(incident.get("createdAt").asText()) : null);
  }

  private ClaimDetailDTO mapToClaimDetail(JsonNode incident) {
    UUID policyholderId =
        incident.has("policyholderId")
            ? UUID.fromString(incident.get("policyholderId").asText())
            : null;
    UUID insurerId =
        incident.has("insurerId") ? UUID.fromString(incident.get("insurerId").asText()) : null;

    ActorDTO policyholder =
        policyholderId != null ? directoryClient.getPolicyholder(policyholderId) : null;
    ActorDTO insurer = insurerId != null ? directoryClient.getInsurer(insurerId) : null;

    String status = incident.has("status") ? incident.get("status").asText() : "DECLARED";
    List<String> availableTransitions = STATUS_TRANSITIONS.getOrDefault(status, List.of());

    // Map expert assignments
    List<ExpertAssignmentDTO> expertAssignments = new ArrayList<>();
    if (incident.has("expertAssignments") && incident.get("expertAssignments").isArray()) {
      for (JsonNode assignment : incident.get("expertAssignments")) {
        expertAssignments.add(mapToExpertAssignment(assignment));
      }
    }

    // Map comments
    List<CommentDTO> comments = new ArrayList<>();
    if (incident.has("comments") && incident.get("comments").isArray()) {
      for (JsonNode comment : incident.get("comments")) {
        comments.add(mapToCommentDTO(comment));
      }
    }

    // Map location
    LocationDTO location = null;
    if (incident.has("location") && !incident.get("location").isNull()) {
      JsonNode locNode = incident.get("location");
      location =
          new LocationDTO(
              locNode.has("address") ? locNode.get("address").asText() : null,
              locNode.has("latitude") ? locNode.get("latitude").asDouble() : null,
              locNode.has("longitude") ? locNode.get("longitude").asDouble() : null);
    }

    return new ClaimDetailDTO(
        incident.has("id") ? UUID.fromString(incident.get("id").asText()) : null,
        incident.has("referenceNumber") ? incident.get("referenceNumber").asText() : null,
        status,
        availableTransitions,
        incident.has("type") ? incident.get("type").asText() : null,
        incident.has("description") ? incident.get("description").asText() : null,
        incident.has("incidentDate") ? Instant.parse(incident.get("incidentDate").asText()) : null,
        incident.has("estimatedDamage") && !incident.get("estimatedDamage").isNull()
            ? new BigDecimal(incident.get("estimatedDamage").asText())
            : null,
        incident.has("currency") ? incident.get("currency").asText() : null,
        location,
        policyholder,
        insurer,
        expertAssignments,
        comments,
        List.of(), // History is fetched separately
        incident.has("createdAt") ? Instant.parse(incident.get("createdAt").asText()) : null,
        incident.has("updatedAt") ? Instant.parse(incident.get("updatedAt").asText()) : null);
  }

  private ExpertAssignmentDTO mapToExpertAssignment(JsonNode assignment) {
    UUID expertId =
        assignment.has("expertId") ? UUID.fromString(assignment.get("expertId").asText()) : null;
    ActorDTO expert = expertId != null ? directoryClient.getExpert(expertId) : null;

    return new ExpertAssignmentDTO(
        assignment.has("id") ? UUID.fromString(assignment.get("id").asText()) : null,
        expert,
        assignment.has("scheduledDate")
            ? Instant.parse(assignment.get("scheduledDate").asText())
            : null,
        assignment.has("notes") ? assignment.get("notes").asText() : null,
        assignment.has("assignedAt") ? Instant.parse(assignment.get("assignedAt").asText()) : null);
  }

  private CommentDTO mapToCommentDTO(JsonNode comment) {
    return new CommentDTO(
        comment.has("id") ? UUID.fromString(comment.get("id").asText()) : null,
        comment.has("content") ? comment.get("content").asText() : null,
        comment.has("authorId") ? UUID.fromString(comment.get("authorId").asText()) : null,
        comment.has("authorType") ? comment.get("authorType").asText() : null,
        comment.has("authorName") ? comment.get("authorName").asText() : null,
        comment.has("createdAt") ? Instant.parse(comment.get("createdAt").asText()) : null);
  }

  private EventDTO mapToEventDTO(JsonNode event) {
    return new EventDTO(
        event.has("id") ? UUID.fromString(event.get("id").asText()) : null,
        event.has("eventType") ? event.get("eventType").asText() : null,
        event.has("description") ? event.get("description").asText() : null,
        event.has("oldValue") ? event.get("oldValue").asText() : null,
        event.has("newValue") ? event.get("newValue").asText() : null,
        event.has("actorId") ? UUID.fromString(event.get("actorId").asText()) : null,
        event.has("actorName") ? event.get("actorName").asText() : null,
        event.has("occurredAt") ? Instant.parse(event.get("occurredAt").asText()) : null);
  }
}

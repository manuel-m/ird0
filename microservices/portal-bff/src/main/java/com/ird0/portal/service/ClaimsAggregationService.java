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
    ActorDTO policyholder = directoryClient.getPolicyholder(request.getPolicyholderId());
    ActorDTO insurer = directoryClient.getInsurer(request.getInsurerId());

    if (policyholder == null || insurer == null) {
      throw new IllegalArgumentException("Invalid policyholder or insurer ID");
    }

    Map<String, Object> incidentRequest = new HashMap<>();
    incidentRequest.put("policyholderId", request.getPolicyholderId().toString());
    incidentRequest.put("insurerId", request.getInsurerId().toString());
    incidentRequest.put("type", request.getType());
    incidentRequest.put("description", request.getDescription());
    incidentRequest.put("incidentDate", request.getIncidentDate().toString());
    incidentRequest.put("estimatedDamage", request.getEstimatedDamage());
    incidentRequest.put("currency", request.getCurrency());

    if (request.getLocation() != null) {
      Map<String, Object> location = new HashMap<>();
      location.put("address", request.getLocation().getAddress());
      location.put("latitude", request.getLocation().getLatitude());
      location.put("longitude", request.getLocation().getLongitude());
      incidentRequest.put("location", location);
    }

    JsonNode response = incidentClient.createIncident(incidentRequest);
    return mapToClaimDetail(response);
  }

  public ClaimDetailDTO updateStatus(UUID id, StatusUpdateRequestDTO request) {
    Map<String, Object> statusRequest = new HashMap<>();
    statusRequest.put("status", request.getStatus());
    if (request.getReason() != null) {
      statusRequest.put("reason", request.getReason());
    }

    JsonNode response = incidentClient.updateStatus(id, statusRequest);
    return mapToClaimDetail(response);
  }

  public ClaimDetailDTO assignExpert(UUID id, ExpertAssignmentRequestDTO request) {
    // Validate expert exists
    ActorDTO expert = directoryClient.getExpert(request.getExpertId());
    if (expert == null) {
      throw new IllegalArgumentException("Invalid expert ID");
    }

    Map<String, Object> assignmentRequest = new HashMap<>();
    assignmentRequest.put("expertId", request.getExpertId().toString());
    if (request.getScheduledDate() != null) {
      assignmentRequest.put("scheduledDate", request.getScheduledDate().toString());
    }
    if (request.getNotes() != null) {
      assignmentRequest.put("notes", request.getNotes());
    }

    JsonNode response = incidentClient.assignExpert(id, assignmentRequest);
    return mapToClaimDetail(response);
  }

  public ClaimDetailDTO addComment(UUID id, CommentRequestDTO request) {
    Map<String, Object> commentRequest = new HashMap<>();
    commentRequest.put("content", request.getContent());
    commentRequest.put("authorId", request.getAuthorId().toString());
    commentRequest.put("authorType", request.getAuthorType());

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
        policyholderName = policyholder.getName();
      }
    }

    if (insurerId != null) {
      ActorDTO insurer = directoryClient.getInsurer(insurerId);
      if (insurer != null) {
        insurerName = insurer.getName();
      }
    }

    return ClaimSummaryDTO.builder()
        .id(incident.has("id") ? UUID.fromString(incident.get("id").asText()) : null)
        .referenceNumber(
            incident.has("referenceNumber") ? incident.get("referenceNumber").asText() : null)
        .status(incident.has("status") ? incident.get("status").asText() : null)
        .type(incident.has("type") ? incident.get("type").asText() : null)
        .policyholderName(policyholderName)
        .insurerName(insurerName)
        .estimatedDamage(
            incident.has("estimatedDamage")
                ? new BigDecimal(incident.get("estimatedDamage").asText())
                : null)
        .currency(incident.has("currency") ? incident.get("currency").asText() : null)
        .incidentDate(
            incident.has("incidentDate")
                ? Instant.parse(incident.get("incidentDate").asText())
                : null)
        .createdAt(
            incident.has("createdAt") ? Instant.parse(incident.get("createdAt").asText()) : null)
        .build();
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
          LocationDTO.builder()
              .address(locNode.has("address") ? locNode.get("address").asText() : null)
              .latitude(locNode.has("latitude") ? locNode.get("latitude").asDouble() : null)
              .longitude(locNode.has("longitude") ? locNode.get("longitude").asDouble() : null)
              .build();
    }

    return ClaimDetailDTO.builder()
        .id(incident.has("id") ? UUID.fromString(incident.get("id").asText()) : null)
        .referenceNumber(
            incident.has("referenceNumber") ? incident.get("referenceNumber").asText() : null)
        .status(status)
        .availableTransitions(availableTransitions)
        .type(incident.has("type") ? incident.get("type").asText() : null)
        .description(incident.has("description") ? incident.get("description").asText() : null)
        .incidentDate(
            incident.has("incidentDate")
                ? Instant.parse(incident.get("incidentDate").asText())
                : null)
        .estimatedDamage(
            incident.has("estimatedDamage") && !incident.get("estimatedDamage").isNull()
                ? new BigDecimal(incident.get("estimatedDamage").asText())
                : null)
        .currency(incident.has("currency") ? incident.get("currency").asText() : null)
        .location(location)
        .policyholder(policyholder)
        .insurer(insurer)
        .expertAssignments(expertAssignments)
        .comments(comments)
        .history(List.of()) // History is fetched separately
        .createdAt(
            incident.has("createdAt") ? Instant.parse(incident.get("createdAt").asText()) : null)
        .updatedAt(
            incident.has("updatedAt") ? Instant.parse(incident.get("updatedAt").asText()) : null)
        .build();
  }

  private ExpertAssignmentDTO mapToExpertAssignment(JsonNode assignment) {
    UUID expertId =
        assignment.has("expertId") ? UUID.fromString(assignment.get("expertId").asText()) : null;
    ActorDTO expert = expertId != null ? directoryClient.getExpert(expertId) : null;

    return ExpertAssignmentDTO.builder()
        .id(assignment.has("id") ? UUID.fromString(assignment.get("id").asText()) : null)
        .expert(expert)
        .scheduledDate(
            assignment.has("scheduledDate")
                ? Instant.parse(assignment.get("scheduledDate").asText())
                : null)
        .notes(assignment.has("notes") ? assignment.get("notes").asText() : null)
        .assignedAt(
            assignment.has("assignedAt")
                ? Instant.parse(assignment.get("assignedAt").asText())
                : null)
        .build();
  }

  private CommentDTO mapToCommentDTO(JsonNode comment) {
    return CommentDTO.builder()
        .id(comment.has("id") ? UUID.fromString(comment.get("id").asText()) : null)
        .content(comment.has("content") ? comment.get("content").asText() : null)
        .authorId(
            comment.has("authorId") ? UUID.fromString(comment.get("authorId").asText()) : null)
        .authorType(comment.has("authorType") ? comment.get("authorType").asText() : null)
        .authorName(comment.has("authorName") ? comment.get("authorName").asText() : null)
        .createdAt(
            comment.has("createdAt") ? Instant.parse(comment.get("createdAt").asText()) : null)
        .build();
  }

  private EventDTO mapToEventDTO(JsonNode event) {
    return EventDTO.builder()
        .id(event.has("id") ? UUID.fromString(event.get("id").asText()) : null)
        .eventType(event.has("eventType") ? event.get("eventType").asText() : null)
        .description(event.has("description") ? event.get("description").asText() : null)
        .oldValue(event.has("oldValue") ? event.get("oldValue").asText() : null)
        .newValue(event.has("newValue") ? event.get("newValue").asText() : null)
        .actorId(event.has("actorId") ? UUID.fromString(event.get("actorId").asText()) : null)
        .actorName(event.has("actorName") ? event.get("actorName").asText() : null)
        .occurredAt(
            event.has("occurredAt") ? Instant.parse(event.get("occurredAt").asText()) : null)
        .build();
  }
}

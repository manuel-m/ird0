package com.ird0.incident.controller;

import com.ird0.incident.dto.CommentRequest;
import com.ird0.incident.dto.CommentResponse;
import com.ird0.incident.dto.CreateIncidentRequest;
import com.ird0.incident.dto.ExpertAssignmentRequest;
import com.ird0.incident.dto.IncidentEventResponse;
import com.ird0.incident.dto.IncidentResponse;
import com.ird0.incident.dto.IncidentSummaryResponse;
import com.ird0.incident.dto.StatusUpdateRequest;
import com.ird0.incident.mapper.IncidentMapper;
import com.ird0.incident.model.Incident;
import com.ird0.incident.model.IncidentStatus;
import com.ird0.incident.repository.IncidentEventRepository;
import com.ird0.incident.service.IncidentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("${incident.api.base-path:/api/v1/incidents}")
@RequiredArgsConstructor
@Tag(name = "Incidents", description = "Incident lifecycle management")
public class IncidentController {

  private final IncidentService incidentService;
  private final IncidentEventRepository eventRepository;
  private final IncidentMapper incidentMapper;

  @Operation(summary = "Create new incident", operationId = "createIncident")
  @ApiResponse(responseCode = "201", description = "Incident created")
  @PostMapping
  public ResponseEntity<IncidentResponse> createIncident(
      @Valid @RequestBody CreateIncidentRequest request,
      @RequestParam(required = false) UUID createdBy) {
    // In a real app, createdBy would come from authentication context
    UUID userId = createdBy != null ? createdBy : request.getPolicyholderId();
    Incident incident = incidentService.createIncident(request, userId);
    return ResponseEntity.status(HttpStatus.CREATED).body(incidentMapper.toResponse(incident));
  }

  @Operation(summary = "Get incident by ID", operationId = "getIncidentById")
  @ApiResponse(responseCode = "200", description = "Incident found")
  @ApiResponse(responseCode = "404", description = "Incident not found")
  @GetMapping("/{id}")
  public ResponseEntity<IncidentResponse> getIncident(@PathVariable UUID id) {
    Incident incident = incidentService.getById(id);
    return ResponseEntity.ok(incidentMapper.toResponse(incident));
  }

  @Operation(summary = "Get incident by reference number", operationId = "getIncidentByReference")
  @ApiResponse(responseCode = "200", description = "Incident found")
  @ApiResponse(responseCode = "404", description = "Incident not found")
  @GetMapping("/reference/{referenceNumber}")
  public ResponseEntity<IncidentResponse> getIncidentByReference(
      @PathVariable String referenceNumber) {
    Incident incident = incidentService.getByReferenceNumber(referenceNumber);
    return ResponseEntity.ok(incidentMapper.toResponse(incident));
  }

  @Operation(summary = "List incidents with filters", operationId = "listIncidents")
  @ApiResponse(responseCode = "200", description = "Paginated list of incidents")
  @GetMapping
  public ResponseEntity<Page<IncidentSummaryResponse>> listIncidents(
      @RequestParam(required = false) UUID policyholderId,
      @RequestParam(required = false) UUID insurerId,
      @RequestParam(required = false) IncidentStatus status,
      @RequestParam(required = false) String type,
      @RequestParam(required = false) Instant fromDate,
      @RequestParam(required = false) Instant toDate,
      @ParameterObject @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
    Page<Incident> incidents =
        incidentService.findWithFilters(
            policyholderId, insurerId, status, type, fromDate, toDate, pageable);
    Page<IncidentSummaryResponse> response = incidents.map(incidentMapper::toSummaryResponse);
    return ResponseEntity.ok(response);
  }

  @Operation(summary = "Update incident status", operationId = "updateIncidentStatus")
  @ApiResponse(responseCode = "200", description = "Status updated")
  @PutMapping("/{id}/status")
  public ResponseEntity<IncidentResponse> updateStatus(
      @PathVariable UUID id,
      @Valid @RequestBody StatusUpdateRequest request,
      @RequestParam(required = false) UUID updatedBy) {
    // In a real app, updatedBy would come from authentication context
    UUID userId = updatedBy != null ? updatedBy : UUID.randomUUID();
    Incident incident = incidentService.updateStatus(id, request, userId);
    return ResponseEntity.ok(incidentMapper.toResponse(incident));
  }

  @Operation(summary = "Assign expert to incident", operationId = "assignIncidentExpert")
  @ApiResponse(responseCode = "200", description = "Expert assigned")
  @PostMapping("/{id}/expert")
  public ResponseEntity<IncidentResponse> assignExpert(
      @PathVariable UUID id,
      @Valid @RequestBody ExpertAssignmentRequest request,
      @RequestParam(required = false) UUID assignedBy) {
    // In a real app, assignedBy would come from authentication context
    UUID userId = assignedBy != null ? assignedBy : UUID.randomUUID();
    Incident incident = incidentService.assignExpert(id, request, userId);
    return ResponseEntity.ok(incidentMapper.toResponse(incident));
  }

  @Operation(summary = "Add comment to incident", operationId = "addIncidentComment")
  @ApiResponse(responseCode = "200", description = "Comment added")
  @PostMapping("/{id}/comments")
  public ResponseEntity<IncidentResponse> addComment(
      @PathVariable UUID id, @Valid @RequestBody CommentRequest request) {
    Incident incident =
        incidentService.addComment(
            id, request.getContent(), request.getAuthorId(), request.getAuthorType());
    return ResponseEntity.ok(incidentMapper.toResponse(incident));
  }

  @Operation(summary = "Get incident comments", operationId = "getIncidentComments")
  @ApiResponse(responseCode = "200", description = "List of comments")
  @GetMapping("/{id}/comments")
  public ResponseEntity<List<CommentResponse>> getComments(@PathVariable UUID id) {
    Incident incident = incidentService.getById(id);
    List<CommentResponse> comments = incidentMapper.toCommentResponseList(incident.getComments());
    return ResponseEntity.ok(comments);
  }

  @Operation(summary = "Get incident event history", operationId = "getIncidentHistory")
  @ApiResponse(responseCode = "200", description = "Event history")
  @GetMapping("/{id}/history")
  public ResponseEntity<List<IncidentEventResponse>> getHistory(@PathVariable UUID id) {
    List<IncidentEventResponse> events =
        incidentMapper.toEventResponseList(
            eventRepository.findByIncidentIdOrderByOccurredAtDesc(id));
    return ResponseEntity.ok(events);
  }

  @Operation(summary = "Delete incident", operationId = "deleteIncident")
  @ApiResponse(responseCode = "204", description = "Incident deleted")
  @ApiResponse(responseCode = "404", description = "Incident not found")
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deleteIncident(@PathVariable UUID id) {
    incidentService.deleteIncident(id);
    return ResponseEntity.noContent().build();
  }
}

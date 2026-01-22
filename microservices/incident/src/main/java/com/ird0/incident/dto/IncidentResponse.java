package com.ird0.incident.dto;

import com.ird0.incident.model.IncidentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record IncidentResponse(
    UUID id,
    String referenceNumber,
    UUID policyholderId,
    UUID insurerId,
    IncidentStatus status,
    String type,
    String description,
    Instant incidentDate,
    LocationDTO location,
    BigDecimal estimatedDamage,
    String currency,
    Instant createdAt,
    Instant updatedAt,
    List<ExpertAssignmentResponse> expertAssignments,
    List<CommentResponse> comments) {}

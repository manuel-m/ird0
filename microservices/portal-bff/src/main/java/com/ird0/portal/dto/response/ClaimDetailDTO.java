package com.ird0.portal.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ClaimDetailDTO(
    UUID id,
    String referenceNumber,
    String status,
    List<String> availableTransitions,
    String type,
    String description,
    Instant incidentDate,
    BigDecimal estimatedDamage,
    String currency,
    LocationDTO location,
    ActorDTO policyholder,
    ActorDTO insurer,
    List<ExpertAssignmentDTO> expertAssignments,
    List<CommentDTO> comments,
    List<EventDTO> history,
    Instant createdAt,
    Instant updatedAt) {}

package com.ird0.incident.dto;

import com.ird0.incident.model.ExpertAssignment.AssignmentStatus;
import java.time.Instant;
import java.util.UUID;

public record ExpertAssignmentResponse(
    UUID id,
    UUID expertId,
    Instant assignedAt,
    Instant scheduledDate,
    AssignmentStatus status,
    String notes) {}

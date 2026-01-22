package com.ird0.incident.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record ExpertAssignmentRequest(
    @NotNull(message = "Expert ID is required") UUID expertId,
    String assignmentReason,
    Instant scheduledDate,
    String notes) {}

package com.ird0.portal.dto.request;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record ExpertAssignmentRequestDTO(
    @NotNull(message = "Expert ID is required") UUID expertId,
    Instant scheduledDate,
    String notes) {}

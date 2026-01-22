package com.ird0.portal.dto.response;

import java.time.Instant;
import java.util.UUID;

public record ExpertAssignmentDTO(
    UUID id, ActorDTO expert, Instant scheduledDate, String notes, Instant assignedAt) {}

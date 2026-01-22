package com.ird0.portal.dto.response;

import java.time.Instant;
import java.util.UUID;

public record EventDTO(
    UUID id,
    String eventType,
    String description,
    String oldValue,
    String newValue,
    UUID actorId,
    String actorName,
    Instant occurredAt) {}

package com.ird0.incident.dto;

import com.ird0.incident.model.IncidentStatus;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record IncidentEventResponse(
    UUID id,
    String eventType,
    IncidentStatus previousStatus,
    IncidentStatus newStatus,
    Map<String, Object> payload,
    UUID triggeredBy,
    Instant occurredAt) {}

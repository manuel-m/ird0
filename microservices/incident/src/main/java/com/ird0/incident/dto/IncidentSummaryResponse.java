package com.ird0.incident.dto;

import com.ird0.incident.model.IncidentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record IncidentSummaryResponse(
    UUID id,
    String referenceNumber,
    UUID policyholderId,
    UUID insurerId,
    IncidentStatus status,
    String type,
    Instant incidentDate,
    BigDecimal estimatedDamage,
    String currency,
    Instant createdAt) {}

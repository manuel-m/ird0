package com.ird0.portal.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ClaimSummaryDTO(
    UUID id,
    String referenceNumber,
    String status,
    String type,
    String policyholderName,
    String insurerName,
    BigDecimal estimatedDamage,
    String currency,
    Instant incidentDate,
    Instant createdAt) {}

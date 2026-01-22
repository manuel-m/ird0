package com.ird0.incident.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CreateIncidentRequest(
    @NotNull(message = "Policyholder ID is required") UUID policyholderId,
    @NotNull(message = "Insurer ID is required") UUID insurerId,
    @NotBlank(message = "Incident type is required") String type,
    String description,
    @NotNull(message = "Incident date is required") Instant incidentDate,
    LocationDTO location,
    BigDecimal estimatedDamage,
    String currency) {}

package com.ird0.incident.dto;

import com.ird0.incident.model.IncidentStatus;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record StatusUpdateRequest(
    @NotNull(message = "Status is required") IncidentStatus status,
    String reason,
    Map<String, Object> qualificationDetails) {}

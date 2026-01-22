package com.ird0.incident.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record UpdateInsurerRequest(
    @NotNull(message = "Insurer ID is required") UUID insurerId, String reason) {}

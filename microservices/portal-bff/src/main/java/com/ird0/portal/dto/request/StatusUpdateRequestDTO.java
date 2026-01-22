package com.ird0.portal.dto.request;

import jakarta.validation.constraints.NotBlank;

public record StatusUpdateRequestDTO(
    @NotBlank(message = "Status is required") String status, String reason) {}

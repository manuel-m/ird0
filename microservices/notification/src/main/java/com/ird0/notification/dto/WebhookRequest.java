package com.ird0.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record WebhookRequest(
    @NotBlank(message = "Webhook URL is required") String webhookUrl,
    @NotNull(message = "Payload is required") Map<String, Object> payload) {}

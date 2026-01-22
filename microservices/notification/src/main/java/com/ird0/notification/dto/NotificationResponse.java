package com.ird0.notification.dto;

import com.ird0.notification.model.NotificationStatus;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record NotificationResponse(
    UUID id,
    UUID eventId,
    String eventType,
    UUID incidentId,
    String webhookUrl,
    NotificationStatus status,
    Map<String, Object> payload,
    Instant sentAt,
    Integer responseCode,
    Integer retryCount,
    Instant nextRetryAt,
    String failureReason,
    Instant createdAt) {}

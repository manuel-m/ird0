package com.ird0.notification.dto;

import com.ird0.notification.model.NotificationStatus;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {

  private UUID id;
  private UUID eventId;
  private String eventType;
  private UUID incidentId;
  private String webhookUrl;
  private NotificationStatus status;
  private Map<String, Object> payload;
  private Instant sentAt;
  private Integer responseCode;
  private Integer retryCount;
  private Instant nextRetryAt;
  private String failureReason;
  private Instant createdAt;
}

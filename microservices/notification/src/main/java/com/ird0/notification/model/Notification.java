package com.ird0.notification.model;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.Data;
import org.hibernate.annotations.Type;

@Data
@Entity
@Table(name = "notification")
public class Notification {

  @Id
  @Column(columnDefinition = "uuid", updatable = false, nullable = false)
  private UUID id;

  @Column(name = "event_id", nullable = false)
  private UUID eventId;

  @Column(name = "event_type", nullable = false)
  private String eventType;

  @Column(name = "incident_id")
  private UUID incidentId;

  @Column(name = "recipient_id")
  private UUID recipientId;

  @Column(name = "webhook_url", nullable = false, length = 500)
  private String webhookUrl;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private NotificationStatus status = NotificationStatus.PENDING;

  @Type(JsonType.class)
  @Column(columnDefinition = "jsonb", nullable = false)
  private Map<String, Object> payload;

  @Column(name = "sent_at")
  private Instant sentAt;

  @Column(name = "response_code")
  private Integer responseCode;

  @Column(name = "response_body", columnDefinition = "TEXT")
  private String responseBody;

  @Column(name = "retry_count")
  private Integer retryCount = 0;

  @Column(name = "next_retry_at")
  private Instant nextRetryAt;

  @Column(name = "failure_reason", columnDefinition = "TEXT")
  private String failureReason;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @PrePersist
  public void prePersist() {
    if (this.id == null) {
      this.id = UUID.randomUUID();
    }
    if (this.eventId == null) {
      this.eventId = UUID.randomUUID();
    }
    if (this.createdAt == null) {
      this.createdAt = Instant.now();
    }
  }

  public void markAsSent(int responseCode, String responseBody) {
    this.status = NotificationStatus.SENT;
    this.sentAt = Instant.now();
    this.responseCode = responseCode;
    this.responseBody = truncate(responseBody, 2000);
  }

  public void markAsDelivered() {
    this.status = NotificationStatus.DELIVERED;
    this.sentAt = Instant.now();
  }

  public void markAsFailed(String reason) {
    this.status = NotificationStatus.FAILED;
    this.failureReason = truncate(reason, 500);
  }

  public void incrementRetry(Instant nextRetry) {
    this.retryCount = (this.retryCount == null ? 0 : this.retryCount) + 1;
    this.nextRetryAt = nextRetry;
  }

  private String truncate(String value, int maxLength) {
    if (value == null) return null;
    return value.length() > maxLength ? value.substring(0, maxLength) : value;
  }
}

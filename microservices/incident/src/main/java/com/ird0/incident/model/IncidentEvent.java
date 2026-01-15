package com.ird0.incident.model;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.Data;
import org.hibernate.annotations.Type;

@Data
@Entity
@Table(name = "incident_event")
public class IncidentEvent {

  @Id
  @Column(columnDefinition = "uuid", updatable = false, nullable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "incident_id", nullable = false)
  private Incident incident;

  @Column(name = "event_type", nullable = false)
  private String eventType;

  @Enumerated(EnumType.STRING)
  @Column(name = "previous_status")
  private IncidentStatus previousStatus;

  @Enumerated(EnumType.STRING)
  @Column(name = "new_status")
  private IncidentStatus newStatus;

  @Type(JsonType.class)
  @Column(columnDefinition = "jsonb")
  private Map<String, Object> payload;

  @Column(name = "triggered_by", nullable = false)
  private UUID triggeredBy;

  @Column(name = "occurred_at", nullable = false, updatable = false)
  private Instant occurredAt;

  @PrePersist
  public void prePersist() {
    if (this.id == null) {
      this.id = UUID.randomUUID();
    }
    if (this.occurredAt == null) {
      this.occurredAt = Instant.now();
    }
  }

  public static IncidentEvent createStatusChangeEvent(
      Incident incident,
      IncidentStatus previousStatus,
      IncidentStatus newStatus,
      UUID triggeredBy,
      Map<String, Object> payload) {
    IncidentEvent event = new IncidentEvent();
    event.setIncident(incident);
    event.setEventType("STATUS_CHANGE");
    event.setPreviousStatus(previousStatus);
    event.setNewStatus(newStatus);
    event.setTriggeredBy(triggeredBy);
    event.setPayload(payload);
    return event;
  }

  public static IncidentEvent createExpertAssignedEvent(
      Incident incident, UUID expertId, UUID triggeredBy) {
    IncidentEvent event = new IncidentEvent();
    event.setIncident(incident);
    event.setEventType("EXPERT_ASSIGNED");
    event.setTriggeredBy(triggeredBy);
    event.setPayload(Map.of("expertId", expertId.toString()));
    return event;
  }

  public static IncidentEvent createCommentAddedEvent(
      Incident incident, UUID authorId, String authorType) {
    IncidentEvent event = new IncidentEvent();
    event.setIncident(incident);
    event.setEventType("COMMENT_ADDED");
    event.setTriggeredBy(authorId);
    event.setPayload(Map.of("authorType", authorType));
    return event;
  }
}

package com.ird0.incident.model;

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
import java.util.UUID;
import lombok.Data;

@Data
@Entity
@Table(name = "expert_assignment")
public class ExpertAssignment {

  @Id
  @Column(columnDefinition = "uuid", updatable = false, nullable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "incident_id", nullable = false)
  private Incident incident;

  @Column(name = "expert_id", nullable = false)
  private UUID expertId;

  @Column(name = "assigned_at", nullable = false)
  private Instant assignedAt;

  @Column(name = "assigned_by", nullable = false)
  private UUID assignedBy;

  @Column(name = "scheduled_date")
  private Instant scheduledDate;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private AssignmentStatus status = AssignmentStatus.PENDING;

  @Column(columnDefinition = "TEXT")
  private String notes;

  @PrePersist
  public void prePersist() {
    if (this.id == null) {
      this.id = UUID.randomUUID();
    }
    if (this.assignedAt == null) {
      this.assignedAt = Instant.now();
    }
  }

  public enum AssignmentStatus {
    PENDING,
    ACCEPTED,
    COMPLETED,
    CANCELLED
  }
}

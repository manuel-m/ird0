package com.ird0.incident.model;

import com.ird0.incident.exception.InvalidStateTransitionException;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Data;
import org.hibernate.annotations.Type;

@Data
@Entity
@Table(name = "incident")
public class Incident {

  @Id
  @Column(columnDefinition = "uuid", updatable = false, nullable = false)
  private UUID id;

  @Column(name = "reference_number", unique = true, nullable = false)
  private String referenceNumber;

  @Column(name = "policyholder_id", nullable = false)
  private UUID policyholderId;

  @Column(name = "insurer_id", nullable = false)
  private UUID insurerId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private IncidentStatus status = IncidentStatus.DECLARED;

  @Column(nullable = false)
  private String type;

  @Column(columnDefinition = "TEXT")
  private String description;

  @Column(name = "incident_date", nullable = false)
  private Instant incidentDate;

  @Type(JsonType.class)
  @Column(columnDefinition = "jsonb")
  private Location location;

  @Column(name = "estimated_damage", precision = 12, scale = 2)
  private BigDecimal estimatedDamage;

  @Column(length = 3)
  private String currency = "EUR";

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "created_by", nullable = false, updatable = false)
  private UUID createdBy;

  @OneToMany(mappedBy = "incident", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<ExpertAssignment> expertAssignments = new ArrayList<>();

  @OneToMany(mappedBy = "incident", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("createdAt DESC")
  private List<Comment> comments = new ArrayList<>();

  @OneToMany(mappedBy = "incident", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("occurredAt DESC")
  private List<IncidentEvent> events = new ArrayList<>();

  @PrePersist
  public void prePersist() {
    if (this.id == null) {
      this.id = UUID.randomUUID();
    }
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  @PreUpdate
  public void preUpdate() {
    this.updatedAt = Instant.now();
  }

  public void transitionTo(IncidentStatus newStatus) {
    if (!this.status.canTransitionTo(newStatus)) {
      throw new InvalidStateTransitionException(this.status, newStatus);
    }
    this.status = newStatus;
  }

  public void addComment(Comment comment) {
    comments.add(comment);
    comment.setIncident(this);
  }

  public void addEvent(IncidentEvent event) {
    events.add(event);
    event.setIncident(this);
  }

  public void addExpertAssignment(ExpertAssignment assignment) {
    expertAssignments.add(assignment);
    assignment.setIncident(this);
  }
}

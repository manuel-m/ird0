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
@Table(name = "comment")
public class Comment {

  @Id
  @Column(columnDefinition = "uuid", updatable = false, nullable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "incident_id", nullable = false)
  private Incident incident;

  @Column(name = "author_id", nullable = false)
  private UUID authorId;

  @Enumerated(EnumType.STRING)
  @Column(name = "author_type", nullable = false)
  private AuthorType authorType;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String content;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @PrePersist
  public void prePersist() {
    if (this.id == null) {
      this.id = UUID.randomUUID();
    }
    if (this.createdAt == null) {
      this.createdAt = Instant.now();
    }
  }

  public enum AuthorType {
    POLICYHOLDER,
    INSURER,
    EXPERT,
    SYSTEM
  }
}

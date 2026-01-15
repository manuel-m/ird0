package com.ird0.directory.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.Data;

@Data
@Entity
@Table(
    uniqueConstraints = {
      @UniqueConstraint(name = "uk_directory_entry_email", columnNames = "email")
    })
public class DirectoryEntry {

  @Id
  @Column(columnDefinition = "uuid", updatable = false, nullable = false)
  private UUID id;

  private String name;
  private String type;

  @Column(unique = true, nullable = false)
  private String email;

  private String phone;
  private String address;

  private String additionalInfo;

  private String webhookUrl;

  @PrePersist
  public void generateId() {
    if (this.id == null) {
      this.id = UUID.randomUUID();
    }
  }
}

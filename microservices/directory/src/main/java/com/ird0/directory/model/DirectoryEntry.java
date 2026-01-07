package com.ird0.directory.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;

@Data
@Entity
@Table(
    uniqueConstraints = {
      @UniqueConstraint(name = "uk_directory_entry_email", columnNames = "email")
    })
public class DirectoryEntry {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String name;
  private String type;

  @Column(unique = true, nullable = false)
  private String email;

  private String phone;
  private String address;

  private String additionalInfo;
}

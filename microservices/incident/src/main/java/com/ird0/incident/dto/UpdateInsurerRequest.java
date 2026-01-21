package com.ird0.incident.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateInsurerRequest {

  @NotNull(message = "Insurer ID is required")
  private UUID insurerId;

  private String reason; // Optional reason for the update
}

package com.ird0.incident.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateIncidentRequest {

  @NotNull(message = "Policyholder ID is required")
  private UUID policyholderId;

  @NotNull(message = "Insurer ID is required")
  private UUID insurerId;

  @NotBlank(message = "Incident type is required")
  private String type;

  private String description;

  @NotNull(message = "Incident date is required")
  private Instant incidentDate;

  private LocationDTO location;

  private BigDecimal estimatedDamage;

  private String currency;
}

package com.ird0.portal.dto.request;

import com.ird0.portal.dto.response.LocationDTO;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateClaimRequestDTO {

  @NotNull(message = "Policyholder ID is required")
  private UUID policyholderId;

  @NotNull(message = "Insurer ID is required")
  private UUID insurerId;

  @NotBlank(message = "Claim type is required")
  private String type;

  private String description;

  @NotNull(message = "Incident date is required")
  private Instant incidentDate;

  private LocationDTO location;

  private BigDecimal estimatedDamage;

  @Builder.Default private String currency = "EUR";
}

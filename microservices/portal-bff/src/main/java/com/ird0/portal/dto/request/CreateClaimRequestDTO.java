package com.ird0.portal.dto.request;

import com.ird0.portal.dto.response.LocationDTO;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CreateClaimRequestDTO(
    @NotNull(message = "Policyholder ID is required") UUID policyholderId,
    @NotNull(message = "Insurer ID is required") UUID insurerId,
    @NotBlank(message = "Claim type is required") String type,
    String description,
    @NotNull(message = "Incident date is required") Instant incidentDate,
    LocationDTO location,
    BigDecimal estimatedDamage,
    String currency) {

  public CreateClaimRequestDTO {
    if (currency == null) {
      currency = "EUR";
    }
  }
}

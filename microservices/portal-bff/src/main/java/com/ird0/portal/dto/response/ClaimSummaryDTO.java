package com.ird0.portal.dto.response;

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
public class ClaimSummaryDTO {

  private UUID id;
  private String referenceNumber;
  private String status;
  private String type;
  private String policyholderName;
  private String insurerName;
  private BigDecimal estimatedDamage;
  private String currency;
  private Instant incidentDate;
  private Instant createdAt;
}

package com.ird0.portal.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClaimDetailDTO {

  private UUID id;
  private String referenceNumber;
  private String status;
  private List<String> availableTransitions;
  private String type;
  private String description;
  private Instant incidentDate;
  private BigDecimal estimatedDamage;
  private String currency;
  private LocationDTO location;

  private ActorDTO policyholder;
  private ActorDTO insurer;
  private List<ExpertAssignmentDTO> expertAssignments;

  private List<CommentDTO> comments;
  private List<EventDTO> history;

  private Instant createdAt;
  private Instant updatedAt;
}

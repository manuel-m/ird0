package com.ird0.portal.dto.response;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActorDTO {

  private UUID id;
  private String name;
  private String type;
  private String email;
  private String phone;
  private String address;
}

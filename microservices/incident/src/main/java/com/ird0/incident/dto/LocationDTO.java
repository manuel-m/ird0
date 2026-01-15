package com.ird0.incident.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LocationDTO {

  private String address;
  private Double latitude;
  private Double longitude;
}

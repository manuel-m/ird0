package com.ird0.incident.model;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Location implements Serializable {

  private String address;
  private Double latitude;
  private Double longitude;
}

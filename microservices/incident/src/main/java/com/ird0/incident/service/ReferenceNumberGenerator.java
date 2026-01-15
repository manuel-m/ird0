package com.ird0.incident.service;

import com.ird0.incident.repository.IncidentRepository;
import java.time.Year;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReferenceNumberGenerator {

  private final IncidentRepository incidentRepository;

  @Transactional
  public String generate() {
    int year = Year.now().getValue();
    String prefix = "INC-" + year + "-%";

    Integer maxNumber = incidentRepository.findMaxReferenceNumberForPrefix(prefix);
    int nextNumber = (maxNumber != null ? maxNumber : 0) + 1;

    return String.format("INC-%d-%05d", year, nextNumber);
  }
}

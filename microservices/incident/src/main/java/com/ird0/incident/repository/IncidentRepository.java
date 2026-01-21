package com.ird0.incident.repository;

import com.ird0.incident.model.Incident;
import com.ird0.incident.model.IncidentStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface IncidentRepository
    extends JpaRepository<Incident, UUID>, JpaSpecificationExecutor<Incident> {

  Optional<Incident> findByReferenceNumber(String referenceNumber);

  List<Incident> findByPolicyholderId(UUID policyholderId);

  List<Incident> findByInsurerId(UUID insurerId);

  Page<Incident> findByPolicyholderId(UUID policyholderId, Pageable pageable);

  Page<Incident> findByInsurerId(UUID insurerId, Pageable pageable);

  Page<Incident> findByStatus(IncidentStatus status, Pageable pageable);

  @Query(
      value =
          "SELECT COALESCE(MAX(CAST(SUBSTRING(reference_number FROM 10) AS INTEGER)), 0) "
              + "FROM incident WHERE reference_number LIKE :prefix",
      nativeQuery = true)
  Integer findMaxReferenceNumberForPrefix(@Param("prefix") String prefix);
}

package com.ird0.incident.repository;

import com.ird0.incident.model.Incident;
import com.ird0.incident.model.IncidentStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface IncidentRepository extends JpaRepository<Incident, UUID> {

  Optional<Incident> findByReferenceNumber(String referenceNumber);

  List<Incident> findByPolicyholderId(UUID policyholderId);

  List<Incident> findByInsurerId(UUID insurerId);

  Page<Incident> findByPolicyholderId(UUID policyholderId, Pageable pageable);

  Page<Incident> findByInsurerId(UUID insurerId, Pageable pageable);

  Page<Incident> findByStatus(IncidentStatus status, Pageable pageable);

  @Query(
      "SELECT i FROM Incident i WHERE "
          + "(:policyholderId IS NULL OR i.policyholderId = :policyholderId) AND "
          + "(:insurerId IS NULL OR i.insurerId = :insurerId) AND "
          + "(:status IS NULL OR i.status = :status) AND "
          + "(:type IS NULL OR i.type = :type) AND "
          + "(:fromDate IS NULL OR i.createdAt >= :fromDate) AND "
          + "(:toDate IS NULL OR i.createdAt <= :toDate)")
  Page<Incident> findWithFilters(
      @Param("policyholderId") UUID policyholderId,
      @Param("insurerId") UUID insurerId,
      @Param("status") IncidentStatus status,
      @Param("type") String type,
      @Param("fromDate") Instant fromDate,
      @Param("toDate") Instant toDate,
      Pageable pageable);

  @Query(
      value =
          "SELECT COALESCE(MAX(CAST(SUBSTRING(reference_number FROM 10) AS INTEGER)), 0) "
              + "FROM incident WHERE reference_number LIKE :prefix",
      nativeQuery = true)
  Integer findMaxReferenceNumberForPrefix(@Param("prefix") String prefix);
}

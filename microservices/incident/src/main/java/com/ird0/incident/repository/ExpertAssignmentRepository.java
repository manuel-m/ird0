package com.ird0.incident.repository;

import com.ird0.incident.model.ExpertAssignment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ExpertAssignmentRepository extends JpaRepository<ExpertAssignment, UUID> {

  List<ExpertAssignment> findByIncidentId(UUID incidentId);

  List<ExpertAssignment> findByExpertId(UUID expertId);
}

package com.ird0.incident.repository;

import com.ird0.incident.model.IncidentEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IncidentEventRepository extends JpaRepository<IncidentEvent, UUID> {

  List<IncidentEvent> findByIncidentIdOrderByOccurredAtDesc(UUID incidentId);

  Page<IncidentEvent> findByIncidentId(UUID incidentId, Pageable pageable);

  List<IncidentEvent> findByEventType(String eventType);
}

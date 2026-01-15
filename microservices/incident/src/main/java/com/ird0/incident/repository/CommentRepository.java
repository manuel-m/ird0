package com.ird0.incident.repository;

import com.ird0.incident.model.Comment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CommentRepository extends JpaRepository<Comment, UUID> {

  List<Comment> findByIncidentIdOrderByCreatedAtDesc(UUID incidentId);
}

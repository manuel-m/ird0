package com.ird0.notification.repository;

import com.ird0.notification.model.Notification;
import com.ird0.notification.model.NotificationStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

  List<Notification> findByStatus(NotificationStatus status);

  List<Notification> findByIncidentId(UUID incidentId);

  @Query(
      "SELECT n FROM Notification n WHERE n.status = :status "
          + "AND (n.nextRetryAt IS NULL OR n.nextRetryAt <= :now) "
          + "ORDER BY n.createdAt ASC")
  List<Notification> findPendingNotifications(
      @Param("status") NotificationStatus status, @Param("now") Instant now);

  @Query(
      "SELECT n FROM Notification n WHERE n.status = 'PENDING' "
          + "AND (n.nextRetryAt IS NULL OR n.nextRetryAt <= :now) "
          + "ORDER BY n.createdAt ASC")
  List<Notification> findReadyToSend(@Param("now") Instant now);
}

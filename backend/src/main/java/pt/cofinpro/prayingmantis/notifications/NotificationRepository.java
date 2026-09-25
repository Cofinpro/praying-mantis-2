package pt.cofinpro.prayingmantis.notifications;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * One page of a user's notifications, newest first, with an id below {@code beforeId} (the cursor, T-4.1).
     * Uses idx_notifications_user_id.
     */
    @Query("""
            select n from Notification n
            where n.user.id = :userId and n.id < :beforeId and (:unreadOnly = false or n.readAt is null)
            order by n.id desc""")
    List<Notification> findPage(
            @Param("userId") Long userId,
            @Param("beforeId") long beforeId,
            @Param("unreadOnly") boolean unreadOnly,
            Limit limit);

    /** The badge: counts through the partial index idx_notifications_unread. */
    long countByUserIdAndReadAtIsNull(Long userId);

    Optional<Notification> findByIdAndUserId(Long id, Long userId);

    /** Marks every unread notification of the user in one statement. Returns how many changed. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Notification n set n.readAt = :now where n.user.id = :userId and n.readAt is null")
    int markAllRead(@Param("userId") Long userId, @Param("now") Instant now);
}

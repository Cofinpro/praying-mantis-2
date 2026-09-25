package pt.cofinpro.prayingmantis.notifications;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pt.cofinpro.prayingmantis.common.NotFoundException;
import pt.cofinpro.prayingmantis.users.User;

/**
 * Creates notifications for the other features (BE-4.1), and lets users read and dismiss their own (BE-4.2).
 *
 * <p>{@code notify} is {@code MANDATORY}: it must run inside the caller's transaction, so a notification is
 * stored if and only if the business change it's about is (plan.md BE-4.1). Calling it without a transaction
 * is a bug, and Spring says so with an IllegalTransactionStateException.
 */
@Service
public class NotificationService {

    public record Page(List<Notification> items, boolean hasMore) {
    }

    static final int DEFAULT_LIMIT = 20;

    private final NotificationRepository notifications;
    private final Clock clock;

    public NotificationService(NotificationRepository notifications, Clock clock) {
        this.notifications = notifications;
        this.clock = clock;
    }

    /**
     * {@code message} is a ready-to-show English sentence, at most 500 characters. {@code link} is an in-app
     * path starting with a single "/" (T-4.1, and a DB check).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Notification notify(User recipient, NotificationType type, String message, String link) {
        return notifications.save(new Notification(recipient, type, message, link));
    }

    /**
     * One page of the user's own notifications, newest first, before the {@code before} id if given (T-4.1).
     * Asks for one more than the page size: if it comes back, there's a next page.
     */
    @Transactional(readOnly = true)
    public Page page(Long userId, boolean unreadOnly, Integer limit, Long before) {
        int size = limit != null ? limit : DEFAULT_LIMIT;
        List<Notification> found = notifications.findPage(
                userId, before != null ? before : Long.MAX_VALUE, unreadOnly, Limit.of(size + 1));
        boolean hasMore = found.size() > size;
        return new Page(hasMore ? found.subList(0, size) : found, hasMore);
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return notifications.countByUserIdAndReadAtIsNull(userId);
    }

    /** Idempotent: an already read notification keeps its first readAt. Someone else's is a 404 (T-4.1). */
    @Transactional
    public void markRead(Long userId, Long notificationId) {
        Notification notification = notifications.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new NotFoundException("Notification not found"));
        if (notification.getReadAt() == null) {
            notification.setReadAt(Instant.now(clock));
        }
    }

    @Transactional
    public void markAllRead(Long userId) {
        notifications.markAllRead(userId, Instant.now(clock));
    }
}

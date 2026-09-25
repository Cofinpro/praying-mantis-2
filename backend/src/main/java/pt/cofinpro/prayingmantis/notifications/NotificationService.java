package pt.cofinpro.prayingmantis.notifications;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pt.cofinpro.prayingmantis.users.User;

/**
 * Creates notifications for the other features (BE-4.1). Reading and marking them read is BE-4.2.
 *
 * <p>{@code notify} is {@code MANDATORY}: it must run inside the caller's transaction, so a notification is
 * stored if and only if the business change it's about is (plan.md BE-4.1). Calling it without a transaction
 * is a bug, and Spring says so with an IllegalTransactionStateException.
 */
@Service
public class NotificationService {

    private final NotificationRepository notifications;

    public NotificationService(NotificationRepository notifications) {
        this.notifications = notifications;
    }

    /**
     * {@code message} is a ready-to-show English sentence, at most 500 characters. {@code link} is an in-app
     * path starting with a single "/" (T-4.1, and a DB check).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Notification notify(User recipient, NotificationType type, String message, String link) {
        return notifications.save(new Notification(recipient, type, message, link));
    }
}

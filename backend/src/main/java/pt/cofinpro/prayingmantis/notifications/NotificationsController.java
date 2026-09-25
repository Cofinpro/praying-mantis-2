package pt.cofinpro.prayingmantis.notifications;

import org.springframework.web.bind.annotation.RestController;
import pt.cofinpro.prayingmantis.api.NotificationsApi;
import pt.cofinpro.prayingmantis.api.model.NotificationPage;
import pt.cofinpro.prayingmantis.api.model.UnreadCount;
import pt.cofinpro.prayingmantis.auth.AuthenticatedUsers;

/** Only ever the caller's own notifications (plan.md BE-4.2, decision #11): the user comes from the session. */
@RestController
public class NotificationsController implements NotificationsApi {

    private final NotificationService notificationService;

    public NotificationsController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public NotificationPage getMyNotifications(Boolean unread, Integer limit, Long before) {
        NotificationService.Page page = notificationService.page(
                AuthenticatedUsers.current().getId(), Boolean.TRUE.equals(unread), limit, before);
        return new NotificationPage(page.items().stream().map(NotificationsController::toApi).toList(), page.hasMore());
    }

    @Override
    public UnreadCount getMyUnreadNotificationCount() {
        return new UnreadCount(Math.toIntExact(notificationService.unreadCount(AuthenticatedUsers.current().getId())));
    }

    @Override
    public void markMyNotificationRead(Long id) {
        notificationService.markRead(AuthenticatedUsers.current().getId(), id);
    }

    @Override
    public void markAllMyNotificationsRead() {
        notificationService.markAllRead(AuthenticatedUsers.current().getId());
    }

    private static pt.cofinpro.prayingmantis.api.model.Notification toApi(Notification n) {
        return new pt.cofinpro.prayingmantis.api.model.Notification(
                        n.getId(),
                        pt.cofinpro.prayingmantis.api.model.NotificationType.valueOf(n.getType().name()),
                        n.getMessage(),
                        n.getLink(),
                        n.getCreatedAt())
                .readAt(n.getReadAt());
    }
}

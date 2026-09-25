package pt.cofinpro.prayingmantis.absences;

import static pt.cofinpro.prayingmantis.notifications.NotificationTexts.days;
import static pt.cofinpro.prayingmantis.notifications.NotificationTexts.range;

import org.springframework.stereotype.Component;
import pt.cofinpro.prayingmantis.notifications.NotificationService;
import pt.cofinpro.prayingmantis.notifications.NotificationType;

/** The absence notifications as rows (BE-4.1), with the recipients, texts and links of the T-4.1 table. */
@Component
class StoredAbsenceNotifications implements AbsenceNotifications {

    private final NotificationService notifications;

    StoredAbsenceNotifications(NotificationService notifications) {
        this.notifications = notifications;
    }

    /** To the approver: "Carla Mendes requested 5 days of vacation (2–6 Nov)". */
    @Override
    public void requested(AbsenceRequest request) {
        notifications.notify(request.getApprover(), NotificationType.ABSENCE_REQUESTED,
                "%s requested %s of %s (%s)".formatted(
                        request.getUser().getName(), days(request.getWorkingDays()), typeName(request),
                        range(request.getStartDate(), request.getEndDate())),
                "/approvals");
    }

    /** To the approver, when an approved request is cancelled: "Carla Mendes cancelled their vacation (2–6 Nov)". */
    @Override
    public void cancelled(AbsenceRequest request) {
        notifications.notify(request.getApprover(), NotificationType.ABSENCE_CANCELLED,
                "%s cancelled their %s (%s)".formatted(
                        request.getUser().getName(), typeName(request), range(request.getStartDate(), request.getEndDate())),
                "/approvals");
    }

    private static String typeName(AbsenceRequest request) {
        return request.getType().getName().toLowerCase();
    }
}

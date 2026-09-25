package pt.cofinpro.prayingmantis.absences;

import static pt.cofinpro.prayingmantis.notifications.NotificationTexts.days;
import static pt.cofinpro.prayingmantis.notifications.NotificationTexts.range;

import org.springframework.stereotype.Component;
import pt.cofinpro.prayingmantis.notifications.NotificationService;
import pt.cofinpro.prayingmantis.notifications.NotificationType;

/** The absence notifications as rows (BE-4.1), with the recipients, texts and links of the T-4.1 table. */
@Component
class StoredAbsenceNotifications implements AbsenceNotifications {

    private static final int MAX_MESSAGE = 500;

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

    /** To the requester: "Your vacation (2–6 Nov) was approved". Not who decided: an admin may have (decision #31). */
    @Override
    public void approved(AbsenceRequest request) {
        notifications.notify(request.getUser(), NotificationType.ABSENCE_APPROVED,
                "Your %s (%s) was approved".formatted(typeName(request), range(request.getStartDate(), request.getEndDate())),
                linkTo(request));
    }

    /** To the requester, with the reason: "Your vacation (2–6 Nov) was rejected: Release week". */
    @Override
    public void rejected(AbsenceRequest request) {
        String message = "Your %s (%s) was rejected: %s".formatted(
                typeName(request), range(request.getStartDate(), request.getEndDate()), request.getDecisionComment());
        notifications.notify(request.getUser(), NotificationType.ABSENCE_REJECTED, fit(message), linkTo(request));
    }

    /** The calendar, opened on the request (T-4.1). */
    private static String linkTo(AbsenceRequest request) {
        return "/absences?request=" + request.getId();
    }

    /** The comment alone can be 500 characters; the message column holds 500. */
    private static String fit(String message) {
        return message.length() <= MAX_MESSAGE ? message : message.substring(0, MAX_MESSAGE - 1) + "…";
    }

    private static String typeName(AbsenceRequest request) {
        return request.getType().getName().toLowerCase();
    }
}

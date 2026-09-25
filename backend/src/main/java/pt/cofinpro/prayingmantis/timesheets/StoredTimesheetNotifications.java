package pt.cofinpro.prayingmantis.timesheets;

import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.stereotype.Component;
import pt.cofinpro.prayingmantis.notifications.NotificationService;
import pt.cofinpro.prayingmantis.notifications.NotificationType;

/** The timesheet notifications as rows (BE-4.1), with the recipients, texts and links of the T-4.1 table. */
@Component
class StoredTimesheetNotifications implements TimesheetNotifications {

    private static final int MAX_MESSAGE = 500;
    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);

    private final NotificationService notifications;

    StoredTimesheetNotifications(NotificationService notifications) {
        this.notifications = notifications;
    }

    /** To the approver: "Eva Santos submitted the week of 12 Oct". */
    @Override
    public void submitted(Timesheet timesheet) {
        notifications.notify(timesheet.getApprover(), NotificationType.TIMESHEET_SUBMITTED,
                "%s submitted the week of %s".formatted(timesheet.getUser().getName(), week(timesheet)),
                "/approvals?tab=timesheets");
    }

    /** To the owner: "Your week of 12 Oct was approved". Not who decided: an admin may have (decision #31). */
    @Override
    public void approved(Timesheet timesheet) {
        notifications.notify(timesheet.getUser(), NotificationType.TIMESHEET_APPROVED,
                "Your week of %s was approved".formatted(week(timesheet)), linkTo(timesheet));
    }

    /** To the owner, with what to fix: "Your week of 12 Oct was rejected: Thursday is missing". */
    @Override
    public void rejected(Timesheet timesheet) {
        String message = "Your week of %s was rejected: %s".formatted(week(timesheet), timesheet.getDecisionComment());
        notifications.notify(timesheet.getUser(), NotificationType.TIMESHEET_REJECTED, fit(message), linkTo(timesheet));
    }

    /** The week grid, opened on that week (T-4.1). */
    private static String linkTo(Timesheet timesheet) {
        return "/timesheets?week=" + timesheet.getWeekStart();
    }

    /** The comment alone can be 500 characters; the message column holds 500. */
    private static String fit(String message) {
        return message.length() <= MAX_MESSAGE ? message : message.substring(0, MAX_MESSAGE - 1) + "…";
    }

    static String week(Timesheet timesheet) {
        return timesheet.getWeekStart().format(DAY_MONTH);
    }
}

package pt.cofinpro.prayingmantis.timesheets;

import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.stereotype.Component;
import pt.cofinpro.prayingmantis.notifications.NotificationService;
import pt.cofinpro.prayingmantis.notifications.NotificationType;

/** The timesheet notifications as rows (BE-4.1), with the recipients, texts and links of the T-4.1 table. */
@Component
class StoredTimesheetNotifications implements TimesheetNotifications {

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

    static String week(Timesheet timesheet) {
        return timesheet.getWeekStart().format(DAY_MONTH);
    }
}

package pt.cofinpro.prayingmantis.notifications;

/** What happened (T-4.1). Stored by name (decision #8); the DB check lists the same values. */
public enum NotificationType {
    ABSENCE_REQUESTED,
    ABSENCE_APPROVED,
    ABSENCE_REJECTED,
    ABSENCE_CANCELLED,
    TIMESHEET_SUBMITTED,
    TIMESHEET_APPROVED,
    TIMESHEET_REJECTED
}

package pt.cofinpro.prayingmantis.timesheets;

/**
 * What the timesheet services tell other people about (epics 6 and 7), as rows in the notifications table:
 * {@link StoredTimesheetNotifications} (BE-4.1). Every call runs inside the business change's transaction.
 */
public interface TimesheetNotifications {

    /** A week is waiting for its approver: TIMESHEET_SUBMITTED. */
    void submitted(Timesheet timesheet);

    /** The user's week was approved (BE-7.2): TIMESHEET_APPROVED. */
    void approved(Timesheet timesheet);

    /** The user's week was rejected, with the approver's comment (BE-7.2): TIMESHEET_REJECTED. */
    void rejected(Timesheet timesheet);
}

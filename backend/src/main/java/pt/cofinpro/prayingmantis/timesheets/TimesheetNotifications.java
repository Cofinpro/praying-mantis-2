package pt.cofinpro.prayingmantis.timesheets;

/**
 * What the timesheet services tell other people about (epics 6 and 7), as rows in the notifications table:
 * {@link StoredTimesheetNotifications} (BE-4.1). Every call runs inside the business change's transaction.
 */
public interface TimesheetNotifications {

    /** A week is waiting for its approver: TIMESHEET_SUBMITTED. */
    void submitted(Timesheet timesheet);
}

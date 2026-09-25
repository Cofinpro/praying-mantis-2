package pt.cofinpro.prayingmantis.timesheets;

/**
 * What the timesheet services tell other people about (epics 6 and 7). Epic 4 implements it with rows in the
 * notifications table, with the types of the T-4.1 contract; until then {@link LoggingTimesheetNotifications}
 * only logs.
 */
public interface TimesheetNotifications {

    /** A week is waiting for its approver: TIMESHEET_SUBMITTED. */
    void submitted(Timesheet timesheet);
}

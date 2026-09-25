package pt.cofinpro.prayingmantis.timesheets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Stub until epic 4 stores notifications. Logs ids only. */
@Component
class LoggingTimesheetNotifications implements TimesheetNotifications {

    private static final Logger log = LoggerFactory.getLogger(LoggingTimesheetNotifications.class);

    @Override
    public void submitted(Timesheet timesheet) {
        log.info("Timesheet {} (week of {}) waits for approver {}", timesheet.getId(), timesheet.getWeekStart(),
                timesheet.getApprover().getId());
    }
}

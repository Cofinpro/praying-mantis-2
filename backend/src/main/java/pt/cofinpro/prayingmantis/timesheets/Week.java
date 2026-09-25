package pt.cofinpro.prayingmantis.timesheets;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import pt.cofinpro.prayingmantis.absences.AbsenceRequest;
import pt.cofinpro.prayingmantis.holidays.PublicHoliday;

/**
 * What the week grid shows (T-6.1). {@code timesheet} is null for a week that has never been saved, which the
 * API shows as an unsaved DRAFT (decision #32). The absences are approved ones, with type and approver loaded.
 */
public record Week(
        LocalDate weekStart,
        Timesheet timesheet,
        List<TimeEntry> entries,
        List<AbsenceRequest> absences,
        List<PublicHoliday> holidays) {

    public TimesheetStatus status() {
        return timesheet == null ? TimesheetStatus.DRAFT : timesheet.getStatus();
    }

    public BigDecimal totalHours() {
        return entries.stream().map(TimeEntry::getHours).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}

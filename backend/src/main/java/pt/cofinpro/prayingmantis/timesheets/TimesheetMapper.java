package pt.cofinpro.prayingmantis.timesheets;

import pt.cofinpro.prayingmantis.absences.AbsenceMapper;
import pt.cofinpro.prayingmantis.api.model.ProjectRef;
import pt.cofinpro.prayingmantis.api.model.PublicHoliday;
import pt.cofinpro.prayingmantis.api.model.UserRef;

/** Domain to contract types. Everything it touches must be loaded: the service fetches it all up front. */
final class TimesheetMapper {

    private TimesheetMapper() {
    }

    static pt.cofinpro.prayingmantis.api.model.Timesheet toApi(Week week) {
        Timesheet t = week.timesheet();
        var api = new pt.cofinpro.prayingmantis.api.model.Timesheet(
                week.weekStart(),
                pt.cofinpro.prayingmantis.api.model.TimesheetStatus.valueOf(week.status().name()),
                week.entries().stream().map(TimesheetMapper::toApi).toList(),
                week.totalHours(),
                week.absences().stream().map(AbsenceMapper::toApi).toList(),
                week.holidays().stream().map(h -> new PublicHoliday(h.getDate(), h.getName())).toList());
        if (t != null) {
            api.id(t.getId())
                    .approver(t.getApprover() == null ? null : new UserRef(t.getApprover().getId(), t.getApprover().getName()))
                    .submittedAt(t.getSubmittedAt())
                    .decidedAt(t.getDecidedAt())
                    .decisionComment(t.getDecisionComment());
        }
        return api;
    }

    private static pt.cofinpro.prayingmantis.api.model.TimeEntry toApi(TimeEntry entry) {
        return new pt.cofinpro.prayingmantis.api.model.TimeEntry(
                        entry.getId(),
                        new ProjectRef(entry.getProject().getId(), entry.getProject().getCode(), entry.getProject().getName()),
                        entry.getWorkDate(),
                        entry.getHours())
                .description(entry.getDescription());
    }
}

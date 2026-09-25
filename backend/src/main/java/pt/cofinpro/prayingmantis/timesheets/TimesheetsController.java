package pt.cofinpro.prayingmantis.timesheets;

import java.time.LocalDate;
import java.util.List;
import org.springframework.web.bind.annotation.RestController;
import pt.cofinpro.prayingmantis.api.TimesheetsApi;
import pt.cofinpro.prayingmantis.api.model.Timesheet;
import pt.cofinpro.prayingmantis.api.model.TimesheetEntries;
import pt.cofinpro.prayingmantis.auth.AuthenticatedUsers;

/** Only the caller's own weeks (decision #11): the user comes from the session, never from the path. */
@RestController
public class TimesheetsController implements TimesheetsApi {

    private final TimesheetService timesheetService;

    public TimesheetsController(TimesheetService timesheetService) {
        this.timesheetService = timesheetService;
    }

    @Override
    public Timesheet getMyTimesheet(LocalDate weekStart) {
        return TimesheetMapper.toApi(timesheetService.week(AuthenticatedUsers.current().getId(), weekStart));
    }

    @Override
    public Timesheet saveMyTimesheetEntries(LocalDate weekStart, TimesheetEntries body) {
        List<EntryInput> input = body.getEntries().stream()
                .map(e -> new EntryInput(e.getProjectId(), e.getWorkDate(), e.getHours(), e.getDescription()))
                .toList();
        return TimesheetMapper.toApi(timesheetService.saveEntries(AuthenticatedUsers.current().getId(), weekStart, input));
    }

    @Override
    public Timesheet submitMyTimesheet(LocalDate weekStart) {
        return TimesheetMapper.toApi(timesheetService.submit(AuthenticatedUsers.current().getId(), weekStart));
    }
}

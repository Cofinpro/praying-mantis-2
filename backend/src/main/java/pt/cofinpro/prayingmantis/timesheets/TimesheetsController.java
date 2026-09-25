package pt.cofinpro.prayingmantis.timesheets;

import java.time.LocalDate;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
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

    /** BE-6.3 (SCRUM-58). The generated interface has no default methods, so it needs a body until then. */
    @Override
    public Timesheet saveMyTimesheetEntries(LocalDate weekStart, TimesheetEntries timesheetEntries) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Coming in BE-6.3");
    }

    /** BE-6.4 (SCRUM-59). */
    @Override
    public Timesheet submitMyTimesheet(LocalDate weekStart) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Coming in BE-6.4");
    }
}

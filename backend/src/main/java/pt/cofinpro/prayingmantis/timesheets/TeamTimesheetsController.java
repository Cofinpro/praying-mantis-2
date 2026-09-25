package pt.cofinpro.prayingmantis.timesheets;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import pt.cofinpro.prayingmantis.api.TeamTimesheetsApi;
import pt.cofinpro.prayingmantis.api.model.TeamTimesheet;
import pt.cofinpro.prayingmantis.api.model.Timesheet;
import pt.cofinpro.prayingmantis.api.model.TimesheetDecision;
import pt.cofinpro.prayingmantis.auth.AuthenticatedUsers;

/** The approver's side (T-7.1). The list filters by the caller; deciding checks Permissions in the service. */
@RestController
public class TeamTimesheetsController implements TeamTimesheetsApi {

    private final TeamTimesheetService teamService;

    public TeamTimesheetsController(TeamTimesheetService teamService) {
        this.teamService = teamService;
    }

    @Override
    public List<TeamTimesheet> getTeamTimesheets(pt.cofinpro.prayingmantis.api.model.TimesheetStatus status) {
        TimesheetStatus wanted = status == null ? null : TimesheetStatus.valueOf(status.name());
        return teamService.forApprover(AuthenticatedUsers.current().getId(), wanted).stream()
                .map(TimesheetMapper::toApi)
                .toList();
    }

    /** BE-7.2 (SCRUM-64). The generated interface has no default methods, so it needs a body until then. */
    @Override
    public Timesheet approveTimesheet(Long id, TimesheetDecision decision) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Coming in BE-7.2");
    }

    /** BE-7.2 (SCRUM-64). */
    @Override
    public Timesheet rejectTimesheet(Long id, TimesheetDecision decision) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Coming in BE-7.2");
    }
}

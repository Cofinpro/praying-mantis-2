package pt.cofinpro.prayingmantis.absences;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import pt.cofinpro.prayingmantis.api.TeamAbsencesApi;
import pt.cofinpro.prayingmantis.api.model.AbsenceDecision;
import pt.cofinpro.prayingmantis.api.model.AbsenceRequest;
import pt.cofinpro.prayingmantis.api.model.TeamAbsenceRequest;
import pt.cofinpro.prayingmantis.auth.AuthenticatedUsers;

/** The approver's side (T-5.1). The list filters by the caller; deciding checks Permissions in the service. */
@RestController
public class TeamAbsencesController implements TeamAbsencesApi {

    private final TeamAbsenceService teamService;

    public TeamAbsencesController(TeamAbsenceService teamService) {
        this.teamService = teamService;
    }

    @Override
    public List<TeamAbsenceRequest> getTeamAbsenceRequests(pt.cofinpro.prayingmantis.api.model.AbsenceStatus status) {
        AbsenceStatus wanted = status == null ? null : AbsenceStatus.valueOf(status.name());
        return teamService.forApprover(AuthenticatedUsers.current().getId(), wanted).stream()
                .map(AbsenceMapper::toApi)
                .toList();
    }

    /** BE-5.2 (SCRUM-50). The generated interface has no default methods, so it needs a body until then. */
    @Override
    public AbsenceRequest approveAbsenceRequest(Long id, AbsenceDecision absenceDecision) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Coming in BE-5.2");
    }

    /** BE-5.2 (SCRUM-50). */
    @Override
    public AbsenceRequest rejectAbsenceRequest(Long id, AbsenceDecision absenceDecision) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Coming in BE-5.2");
    }
}

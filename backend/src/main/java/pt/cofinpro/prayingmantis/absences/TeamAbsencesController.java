package pt.cofinpro.prayingmantis.absences;

import java.util.List;
import org.springframework.web.bind.annotation.RestController;
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

    /** The body is optional here: approving needs no comment. */
    @Override
    public AbsenceRequest approveAbsenceRequest(Long id, AbsenceDecision decision) {
        return AbsenceMapper.toApi(teamService.approve(id, decision == null ? null : decision.getComment()));
    }

    @Override
    public AbsenceRequest rejectAbsenceRequest(Long id, AbsenceDecision decision) {
        return AbsenceMapper.toApi(teamService.reject(id, decision.getComment()));
    }
}

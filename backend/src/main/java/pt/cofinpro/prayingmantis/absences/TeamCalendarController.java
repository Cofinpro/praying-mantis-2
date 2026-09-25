package pt.cofinpro.prayingmantis.absences;

import java.time.LocalDate;
import java.util.List;
import org.springframework.web.bind.annotation.RestController;
import pt.cofinpro.prayingmantis.api.TeamCalendarApi;
import pt.cofinpro.prayingmantis.api.model.TeamAbsence;
import pt.cofinpro.prayingmantis.api.model.TeamMemberAbsences;
import pt.cofinpro.prayingmantis.api.model.UserRef;
import pt.cofinpro.prayingmantis.auth.AuthenticatedUsers;

/** The team calendar (T-5.3). The team is always the caller's own, from the session (decision #36). */
@RestController
public class TeamCalendarController implements TeamCalendarApi {

    private final TeamCalendarService calendarService;

    public TeamCalendarController(TeamCalendarService calendarService) {
        this.calendarService = calendarService;
    }

    @Override
    public List<TeamMemberAbsences> getTeamAbsences(LocalDate from, LocalDate to) {
        return calendarService.calendar(AuthenticatedUsers.current().getId(), from, to).stream()
                .map(row -> new TeamMemberAbsences(
                        new UserRef(row.user().getId(), row.user().getName()),
                        row.absences().stream().map(TeamCalendarController::toApi).toList()))
                .toList();
    }

    /** No reason and no decision comment: those stay between requester and approver (decision #36). */
    private static TeamAbsence toApi(AbsenceRequest r) {
        return new TeamAbsence(
                r.getId(),
                AbsenceMapper.toApi(r.getType().getCode()),
                r.getStartDate(),
                r.getEndDate(),
                pt.cofinpro.prayingmantis.api.model.DayPart.valueOf(r.getStartPart().name()),
                pt.cofinpro.prayingmantis.api.model.DayPart.valueOf(r.getEndPart().name()),
                pt.cofinpro.prayingmantis.api.model.AbsenceStatus.valueOf(r.getStatus().name()));
    }
}

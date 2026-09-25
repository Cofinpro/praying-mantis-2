package pt.cofinpro.prayingmantis.absences;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.cofinpro.prayingmantis.users.User;
import pt.cofinpro.prayingmantis.users.UserRepository;

/** Who on my team is away when (BE-5.3, decision #36). */
@Service
public class TeamCalendarService {

    /** One calendar row: a person and their pending and approved absences in the range, by start date. */
    public record Row(User user, List<AbsenceRequest> absences) {
    }

    private static final List<AbsenceStatus> SHOWN = List.of(AbsenceStatus.PENDING, AbsenceStatus.APPROVED);

    private final UserRepository users;
    private final AbsenceRequestRepository requests;

    public TeamCalendarService(UserRepository users, AbsenceRequestRepository requests) {
        this.users = users;
        this.requests = requests;
    }

    /**
     * The caller's row first, then one per person whose team lead is the caller, by name. Someone who leads
     * nobody gets only their own row. All absences come in one query.
     */
    @Transactional(readOnly = true)
    public List<Row> calendar(Long callerId, LocalDate from, LocalDate to) {
        AbsenceRequestService.requireRange(from, to);
        List<User> team = new ArrayList<>();
        team.add(users.findById(callerId).orElseThrow());
        team.addAll(users.findByTeamLeadIdOrderByName(callerId));

        Map<Long, List<AbsenceRequest>> byUser = requests
                .findForUsersOverlapping(team.stream().map(User::getId).toList(), from, to, SHOWN).stream()
                .collect(Collectors.groupingBy(r -> r.getUser().getId()));
        return team.stream().map(u -> new Row(u, byUser.getOrDefault(u.getId(), List.of()))).toList();
    }
}

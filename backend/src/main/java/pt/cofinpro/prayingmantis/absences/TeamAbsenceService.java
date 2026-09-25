package pt.cofinpro.prayingmantis.absences;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The approver's side of absence requests (epic 5): listing them (BE-5.1). */
@Service
public class TeamAbsenceService {

    private final AbsenceRequestRepository requests;
    private final AbsenceBalanceService balances;

    public TeamAbsenceService(AbsenceRequestRepository requests, AbsenceBalanceService balances) {
        this.requests = requests;
        this.balances = balances;
    }

    /**
     * The requests whose stored approver is {@code approverId}, in one status (default PENDING). Pending ones
     * soonest start first, since those need a decision first; decided ones newest first (T-5.1, decision #31).
     * Someone who approves nobody gets an empty list.
     */
    @Transactional(readOnly = true)
    public List<TeamRequest> forApprover(Long approverId, AbsenceStatus status) {
        AbsenceStatus wanted = status != null ? status : AbsenceStatus.PENDING;
        Sort byStart = wanted == AbsenceStatus.PENDING
                ? Sort.by("startDate", "id")
                : Sort.by(Sort.Direction.DESC, "startDate", "id");

        // One balance lookup per requester, type and year, however many requests share them
        Map<String, BigDecimal> left = new HashMap<>();
        return requests.findForApprover(approverId, wanted, byStart).stream()
                .map(r -> new TeamRequest(r, r.getType().isDeductsFromBalance() ? left.computeIfAbsent(
                        r.getUser().getId() + "/" + r.getType().getCode() + "/" + r.getStartDate().getYear(),
                        key -> balances.daysLeft(r.getUser().getId(), r.getType().getCode(), r.getStartDate().getYear()))
                        : null))
                .toList();
    }
}

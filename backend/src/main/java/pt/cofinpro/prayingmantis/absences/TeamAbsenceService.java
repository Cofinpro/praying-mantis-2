package pt.cofinpro.prayingmantis.absences;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.cofinpro.prayingmantis.auth.Permissions;
import pt.cofinpro.prayingmantis.common.ConflictException;
import pt.cofinpro.prayingmantis.common.InvalidFieldException;
import pt.cofinpro.prayingmantis.common.NotFoundException;

/** The approver's side of absence requests (epic 5): listing them (BE-5.1), approving and rejecting them (BE-5.2). */
@Service
public class TeamAbsenceService {

    static final String NOT_PENDING = "absence-not-pending";

    private final AbsenceRequestRepository requests;
    private final AbsenceBalanceService balances;
    private final Permissions permissions;
    private final AbsenceNotifications notifications;
    private final Clock clock;

    public TeamAbsenceService(
            AbsenceRequestRepository requests,
            AbsenceBalanceService balances,
            Permissions permissions,
            AbsenceNotifications notifications,
            Clock clock) {
        this.requests = requests;
        this.balances = balances;
        this.permissions = permissions;
        this.notifications = notifications;
        this.clock = clock;
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

    /**
     * Approves a pending request, if the caller may decide it (the approver or an admin, never the requester,
     * decision #16). A VACATION request must still fit in the balance: another request may have been approved
     * since this one was created (decision #29).
     */
    @Transactional
    public AbsenceRequest approve(Long requestId, String comment) {
        AbsenceRequest request = pendingForDecision(requestId);
        if (request.getType().isDeductsFromBalance()) {
            balances.requireDaysLeft(request.getUser().getId(), request.getType(), new AbsencePeriod(
                    request.getStartDate(), request.getStartPart(), request.getEndDate(), request.getEndPart()));
        }
        decide(request, AbsenceStatus.APPROVED, blankToNull(comment));
        notifications.approved(request);
        return request;
    }

    /** Rejects a pending request. The comment is required, so the requester learns why (decision #31). */
    @Transactional
    public AbsenceRequest reject(Long requestId, String comment) {
        String reason = blankToNull(comment);
        if (reason == null) {
            throw new InvalidFieldException("comment", "must not be blank");
        }
        AbsenceRequest request = pendingForDecision(requestId);
        decide(request, AbsenceStatus.REJECTED, reason);
        notifications.rejected(request);
        return request;
    }

    /** 404, then 403 (Permissions), then 409: in that order, so nobody learns a status they may not decide on. */
    private AbsenceRequest pendingForDecision(Long requestId) {
        AbsenceRequest request = requests.findWithPeople(requestId)
                .orElseThrow(() -> new NotFoundException("Absence request not found"));
        Long approverId = request.getApprover() == null ? null : request.getApprover().getId();
        permissions.requireApproverOrAdmin(approverId, request.getUser().getId());
        if (request.getStatus() != AbsenceStatus.PENDING) {
            throw new ConflictException(NOT_PENDING,
                    "Only pending requests can be decided; this one is " + request.getStatus().name().toLowerCase());
        }
        return request;
    }

    private void decide(AbsenceRequest request, AbsenceStatus status, String comment) {
        request.setStatus(status);
        request.setDecidedAt(Instant.now(clock));
        request.setDecisionComment(comment);
        requests.flush();
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }
}

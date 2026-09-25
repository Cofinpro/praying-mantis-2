package pt.cofinpro.prayingmantis.timesheets;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.cofinpro.prayingmantis.auth.Permissions;
import pt.cofinpro.prayingmantis.common.ConflictException;
import pt.cofinpro.prayingmantis.common.InvalidFieldException;
import pt.cofinpro.prayingmantis.common.NotFoundException;

/** The approver's side of timesheets (epic 7): listing them (BE-7.1), approving and rejecting them (BE-7.2). */
@Service
public class TeamTimesheetService {

    static final String NOT_SUBMITTED = "timesheet-not-submitted";

    private final TimesheetRepository timesheets;
    private final TimesheetService timesheetService;
    private final Permissions permissions;
    private final TimesheetNotifications notifications;
    private final Clock clock;

    public TeamTimesheetService(
            TimesheetRepository timesheets,
            TimesheetService timesheetService,
            Permissions permissions,
            TimesheetNotifications notifications,
            Clock clock) {
        this.timesheets = timesheets;
        this.timesheetService = timesheetService;
        this.permissions = permissions;
        this.notifications = notifications;
        this.clock = clock;
    }

    /**
     * The weeks whose stored approver is {@code approverId}, in one status (default SUBMITTED). Submitted ones
     * oldest week first, since they've waited longest; others newest first (T-7.1). Each comes as the whole
     * week, so the FE can show the grid read-only. Someone who approves nobody gets an empty list.
     */
    @Transactional(readOnly = true)
    public List<TeamWeek> forApprover(Long approverId, TimesheetStatus status) {
        TimesheetStatus wanted = status != null ? status : TimesheetStatus.SUBMITTED;
        Sort byWeek = wanted == TimesheetStatus.SUBMITTED
                ? Sort.by("weekStart", "id")
                : Sort.by(Sort.Direction.DESC, "weekStart", "id");
        // A few queries per week (entries, absences, holidays): fine for a team, revisit if lists get long
        return timesheets.findForApprover(approverId, wanted, byWeek).stream()
                .map(t -> new TeamWeek(timesheetService.view(t.getUser().getId(), t.getWeekStart(), t)))
                .toList();
    }

    /** Approves a submitted week, with the same rules as an absence (decision #31). The comment is optional. */
    @Transactional
    public Week approve(Long timesheetId, String comment) {
        Timesheet timesheet = submittedForDecision(timesheetId);
        decide(timesheet, TimesheetStatus.APPROVED, blankToNull(comment));
        notifications.approved(timesheet);
        return timesheetService.view(timesheet.getUser().getId(), timesheet.getWeekStart(), timesheet);
    }

    /** Rejects a submitted week; the comment is required, so the user knows what to fix (decision #31). */
    @Transactional
    public Week reject(Long timesheetId, String comment) {
        String reason = blankToNull(comment);
        if (reason == null) {
            throw new InvalidFieldException("comment", "must not be blank");
        }
        Timesheet timesheet = submittedForDecision(timesheetId);
        decide(timesheet, TimesheetStatus.REJECTED, reason);
        notifications.rejected(timesheet);
        return timesheetService.view(timesheet.getUser().getId(), timesheet.getWeekStart(), timesheet);
    }

    /** 404, then 403 (Permissions), then 409, as for absences (BE-5.2). */
    private Timesheet submittedForDecision(Long timesheetId) {
        Timesheet timesheet = timesheets.findWithPeople(timesheetId)
                .orElseThrow(() -> new NotFoundException("Timesheet not found"));
        Long approverId = timesheet.getApprover() == null ? null : timesheet.getApprover().getId();
        permissions.requireApproverOrAdmin(approverId, timesheet.getUser().getId());
        if (timesheet.getStatus() != TimesheetStatus.SUBMITTED) {
            throw new ConflictException(NOT_SUBMITTED,
                    "Only submitted timesheets can be decided; this one is " + timesheet.getStatus().name().toLowerCase());
        }
        return timesheet;
    }

    private void decide(Timesheet timesheet, TimesheetStatus status, String comment) {
        timesheet.setStatus(status);
        timesheet.setDecidedAt(Instant.now(clock));
        timesheet.setDecisionComment(comment);
        timesheets.flush();
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }
}

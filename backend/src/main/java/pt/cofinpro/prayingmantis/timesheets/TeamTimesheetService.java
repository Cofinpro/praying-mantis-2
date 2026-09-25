package pt.cofinpro.prayingmantis.timesheets;

import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The approver's side of timesheets (epic 7): listing them (BE-7.1). */
@Service
public class TeamTimesheetService {

    private final TimesheetRepository timesheets;
    private final TimesheetService timesheetService;

    public TeamTimesheetService(TimesheetRepository timesheets, TimesheetService timesheetService) {
        this.timesheets = timesheets;
        this.timesheetService = timesheetService;
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
}

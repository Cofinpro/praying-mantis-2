package pt.cofinpro.prayingmantis.timesheets;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.cofinpro.prayingmantis.absences.AbsenceRequestService;
import pt.cofinpro.prayingmantis.absences.AbsenceStatus;
import pt.cofinpro.prayingmantis.common.ConflictException;
import pt.cofinpro.prayingmantis.common.InvalidFieldException;
import pt.cofinpro.prayingmantis.holidays.PublicHolidayRepository;
import pt.cofinpro.prayingmantis.projects.Project;
import pt.cofinpro.prayingmantis.projects.ProjectRepository;
import pt.cofinpro.prayingmantis.users.UserRepository;

/** A user's own weekly timesheets (epic 6): opening a week (BE-6.2) and saving its entries (BE-6.3). */
@Service
public class TimesheetService {

    static final String NOT_EDITABLE = "timesheet-not-editable";

    private static final BigDecimal MAX_DAY = new BigDecimal("24");
    private static final BigDecimal QUARTERS = new BigDecimal("4");

    private final TimesheetRepository timesheets;
    private final TimeEntryRepository entries;
    private final ProjectRepository projects;
    private final UserRepository users;
    private final AbsenceRequestService absences;
    private final PublicHolidayRepository holidays;

    public TimesheetService(
            TimesheetRepository timesheets,
            TimeEntryRepository entries,
            ProjectRepository projects,
            UserRepository users,
            AbsenceRequestService absences,
            PublicHolidayRepository holidays) {
        this.timesheets = timesheets;
        this.entries = entries;
        this.projects = projects;
        this.users = users;
        this.absences = absences;
        this.holidays = holidays;
    }

    /**
     * The user's week, with its approved absences and holidays. Stores nothing: a week that has never been
     * saved comes back without a timesheet (decision #32).
     */
    @Transactional(readOnly = true)
    public Week week(Long userId, LocalDate weekStart) {
        requireMonday(weekStart);
        Timesheet timesheet = timesheets.findWeek(userId, weekStart).orElse(null);
        return view(userId, weekStart, timesheet);
    }

    /**
     * Replaces all entries of the user's week with {@code input}, creating the timesheet on the first save
     * (BE-6.3, decision #32). Every check runs before anything changes; the first failure is the 400.
     */
    @Transactional
    public Week saveEntries(Long userId, LocalDate weekStart, List<EntryInput> input) {
        requireMonday(weekStart);
        Timesheet timesheet = timesheets.findWeek(userId, weekStart).orElse(null);
        if (timesheet != null) {
            requireEditable(timesheet);
        }
        List<TimeEntry> before = timesheet == null ? List.of() : entries.findForTimesheet(timesheet.getId());
        Set<Long> alreadyOnWeek = before.stream().map(e -> e.getProject().getId()).collect(Collectors.toSet());
        Map<Long, Project> projectsById = projects.findAllById(input.stream().map(EntryInput::projectId).toList()).stream()
                .collect(Collectors.toMap(Project::getId, Function.identity()));

        validate(weekStart, input, projectsById, alreadyOnWeek);

        if (timesheet == null) {
            timesheet = timesheets.save(new Timesheet(users.getReferenceById(userId), weekStart));
        } else {
            // A bulk delete that runs now: re-inserting a cell before the old row is gone would hit uq_time_entries_cell
            entries.deleteForTimesheet(timesheet.getId());
        }
        for (EntryInput e : input) {
            entries.save(new TimeEntry(timesheet, projectsById.get(e.projectId()), e.workDate(), e.hours(), blankToNull(e.description())));
        }
        entries.flush();
        return view(userId, weekStart, timesheet);
    }

    /** The grid rules (decision #32) that Bean Validation can't check on one entry alone. */
    private static void validate(LocalDate weekStart, List<EntryInput> input, Map<Long, Project> projectsById, Set<Long> alreadyOnWeek) {
        LocalDate sunday = weekStart.plusDays(6);
        Set<String> cells = new HashSet<>();
        Map<LocalDate, BigDecimal> perDay = new TreeMap<>();
        for (int i = 0; i < input.size(); i++) {
            EntryInput e = input.get(i);
            String at = "entries[" + i + "].";
            if (e.workDate().isBefore(weekStart) || e.workDate().isAfter(sunday)) {
                throw new InvalidFieldException(at + "workDate", "must be inside the week of " + weekStart);
            }
            Project project = projectsById.get(e.projectId());
            if (project == null) {
                throw new InvalidFieldException(at + "projectId", "no such project");
            }
            if (!project.isActive() && !alreadyOnWeek.contains(project.getId())) {
                throw new InvalidFieldException(at + "projectId", "project " + project.getCode() + " is inactive");
            }
            BigDecimal quarters = e.hours().multiply(QUARTERS);
            if (quarters.stripTrailingZeros().scale() > 0) {
                throw new InvalidFieldException(at + "hours", "must be in quarter hours");
            }
            if (!cells.add(e.projectId() + "/" + e.workDate())) {
                throw new InvalidFieldException(at + "workDate", "a second entry for " + project.getCode() + " on " + e.workDate());
            }
            perDay.merge(e.workDate(), e.hours(), BigDecimal::add);
        }
        perDay.forEach((day, total) -> {
            if (total.compareTo(MAX_DAY) > 0) {
                throw new InvalidFieldException("entries", "more than 24 hours on " + day);
            }
        });
    }

    Week view(Long userId, LocalDate weekStart, Timesheet timesheet) {
        LocalDate sunday = weekStart.plusDays(6);
        return new Week(
                weekStart,
                timesheet,
                timesheet == null || timesheet.getId() == null ? List.of() : entries.findForTimesheet(timesheet.getId()),
                absences.overlapping(userId, weekStart, sunday).stream()
                        .filter(r -> r.getStatus() == AbsenceStatus.APPROVED)
                        .toList(),
                holidays.findByDateBetweenOrderByDate(weekStart, sunday));
    }

    static void requireMonday(LocalDate weekStart) {
        if (weekStart.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new InvalidFieldException("weekStart", "must be a Monday");
        }
    }

    static void requireEditable(Timesheet timesheet) {
        if (!timesheet.getStatus().isEditable()) {
            throw new ConflictException(NOT_EDITABLE,
                    "This week is " + timesheet.getStatus().name().toLowerCase() + " and can't be changed");
        }
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }
}

package pt.cofinpro.prayingmantis.timesheets;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.cofinpro.prayingmantis.absences.AbsenceRequestService;
import pt.cofinpro.prayingmantis.absences.AbsenceStatus;
import pt.cofinpro.prayingmantis.common.InvalidFieldException;
import pt.cofinpro.prayingmantis.holidays.PublicHolidayRepository;

/** A user's own weekly timesheets (epic 6): opening a week (BE-6.2). */
@Service
public class TimesheetService {

    private final TimesheetRepository timesheets;
    private final TimeEntryRepository entries;
    private final AbsenceRequestService absences;
    private final PublicHolidayRepository holidays;

    public TimesheetService(
            TimesheetRepository timesheets,
            TimeEntryRepository entries,
            AbsenceRequestService absences,
            PublicHolidayRepository holidays) {
        this.timesheets = timesheets;
        this.entries = entries;
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
}

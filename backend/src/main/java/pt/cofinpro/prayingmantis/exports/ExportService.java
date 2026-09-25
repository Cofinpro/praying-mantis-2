package pt.cofinpro.prayingmantis.exports;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.cofinpro.prayingmantis.absences.AbsenceRequestService;
import pt.cofinpro.prayingmantis.absences.AbsenceStatus;
import pt.cofinpro.prayingmantis.holidays.PublicHolidayRepository;
import pt.cofinpro.prayingmantis.timesheets.TimeEntry;
import pt.cofinpro.prayingmantis.timesheets.TimeEntryRepository;
import pt.cofinpro.prayingmantis.timesheets.Timesheet;
import pt.cofinpro.prayingmantis.timesheets.TimesheetRepository;
import pt.cofinpro.prayingmantis.timesheets.TimesheetStatus;
import pt.cofinpro.prayingmantis.users.User;
import pt.cofinpro.prayingmantis.users.UserRepository;

/** The monthly export (BE-8.2): what it would contain, and the file itself. Only ever the caller's own month. */
@Service
public class ExportService {

    public record WeekSummary(LocalDate weekStart, TimesheetStatus status, BigDecimal hoursInMonth) {
    }

    public record MonthSummary(YearMonth month, BigDecimal totalHours, List<WeekSummary> weeks) {
    }

    public record ExportFile(String filename, byte[] content) {
    }

    private final TimeEntryRepository entries;
    private final TimesheetRepository timesheets;
    private final UserRepository users;
    private final AbsenceRequestService absences;
    private final ExportTemplateRegistry templates;
    private final PublicHolidayRepository holidays;

    public ExportService(
            TimeEntryRepository entries,
            TimesheetRepository timesheets,
            UserRepository users,
            AbsenceRequestService absences,
            ExportTemplateRegistry templates,
            PublicHolidayRepository holidays) {
        this.entries = entries;
        this.timesheets = timesheets;
        this.users = users;
        this.absences = absences;
        this.templates = templates;
        this.holidays = holidays;
    }

    /**
     * Every week that touches the month, with its status and its hours inside the month (T-8.1). A week with no
     * timesheet is DRAFT with 0 hours. The FE warns when any week isn't APPROVED (decision #18).
     */
    @Transactional(readOnly = true)
    public MonthSummary summary(Long userId, YearMonth month) {
        LocalDate first = month.atDay(1);
        LocalDate last = month.atEndOfMonth();
        List<TimeEntry> monthEntries = entries.findForUserBetween(userId, first, last);
        LocalDate firstMonday = first.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate lastMonday = last.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Map<LocalDate, Timesheet> byWeek = timesheets.findByUserIdAndWeekStartBetweenOrderByWeekStart(userId, firstMonday, lastMonday)
                .stream().collect(Collectors.toMap(Timesheet::getWeekStart, Function.identity()));

        List<WeekSummary> weeks = new ArrayList<>();
        for (LocalDate monday = firstMonday; !monday.isAfter(lastMonday); monday = monday.plusWeeks(1)) {
            LocalDate weekStart = monday;
            Timesheet timesheet = byWeek.get(weekStart);
            BigDecimal hours = monthEntries.stream()
                    .filter(e -> e.getTimesheet().getWeekStart().equals(weekStart))
                    .map(TimeEntry::getHours)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            weeks.add(new WeekSummary(weekStart, timesheet == null ? TimesheetStatus.DRAFT : timesheet.getStatus(), hours));
        }
        BigDecimal total = monthEntries.stream().map(TimeEntry::getHours).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new MonthSummary(month, total, weeks);
    }

    /** The month as an .xlsx in the given template (400 on {@code template} if there's no such template). */
    @Transactional(readOnly = true)
    public ExportFile export(Long userId, YearMonth month, String templateCode) {
        ExportTemplate template = templates.get(templateCode);
        User user = users.findById(userId).orElseThrow();
        LocalDate first = month.atDay(1);
        LocalDate last = month.atEndOfMonth();
        MonthExport data = new MonthExport(
                user,
                month,
                entries.findForUserBetween(userId, first, last),
                absences.overlapping(userId, first, last).stream().filter(r -> r.getStatus() == AbsenceStatus.APPROVED).toList(),
                holidays.findByDateBetweenOrderByDate(first, last));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            template.write(data, out);
        } catch (IOException e) {
            throw new UncheckedIOException("Writing the " + template.code() + " export failed", e);
        }
        String who = user.getEmail().substring(0, user.getEmail().indexOf('@'));
        return new ExportFile("timesheet-%s-%s-%s.xlsx".formatted(month, who, template.code()), out.toByteArray());
    }
}

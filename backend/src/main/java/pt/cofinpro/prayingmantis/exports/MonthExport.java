package pt.cofinpro.prayingmantis.exports;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import pt.cofinpro.prayingmantis.absences.AbsenceRequest;
import pt.cofinpro.prayingmantis.holidays.PublicHoliday;
import pt.cofinpro.prayingmantis.timesheets.TimeEntry;
import pt.cofinpro.prayingmantis.users.Client;
import pt.cofinpro.prayingmantis.users.User;

/**
 * Everything a template needs to write one user's month (BE-8.2): the entries with a work date in the month,
 * by day and project code, whatever their week's status (decision #18), the month's approved absences and its
 * public holidays. Everything is loaded, so a template can't hit a lazy load.
 */
public record MonthExport(
        User user, YearMonth month, List<TimeEntry> entries, List<AbsenceRequest> absences, List<PublicHoliday> holidays) {

    public BigDecimal totalHours() {
        return entries.stream().map(TimeEntry::getHours).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Project code → hours, by project code. */
    public Map<String, BigDecimal> hoursByProjectCode() {
        Map<String, BigDecimal> byCode = new LinkedHashMap<>();
        entries.stream()
                .sorted((a, b) -> a.getProject().getCode().compareTo(b.getProject().getCode()))
                .forEach(e -> byCode.merge(e.getProject().getCode(), e.getHours(), BigDecimal::add));
        return byCode;
    }

    /** Only the entries on this client's projects: a client's sheet shows the hours it's billed for (BE-8.3). */
    public List<TimeEntry> entriesFor(Client client) {
        return entries.stream().filter(e -> e.getProject().getClient() == client).toList();
    }

    /** The approved absence covering this day, if any. */
    public Optional<AbsenceRequest> absenceOn(LocalDate day) {
        return absences.stream().filter(a -> !day.isBefore(a.getStartDate()) && !day.isAfter(a.getEndDate())).findFirst();
    }

    public Optional<PublicHoliday> holidayOn(LocalDate day) {
        return holidays.stream().filter(h -> h.getDate().equals(day)).findFirst();
    }
}

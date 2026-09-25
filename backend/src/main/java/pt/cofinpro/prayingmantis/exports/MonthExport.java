package pt.cofinpro.prayingmantis.exports;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import pt.cofinpro.prayingmantis.absences.AbsenceRequest;
import pt.cofinpro.prayingmantis.timesheets.TimeEntry;
import pt.cofinpro.prayingmantis.users.User;

/**
 * Everything a template needs to write one user's month (BE-8.2): the entries with a work date in the month,
 * by day and project code, whatever their week's status (decision #18), and the month's approved absences.
 * Everything is loaded, so a template can't hit a lazy load.
 */
public record MonthExport(User user, YearMonth month, List<TimeEntry> entries, List<AbsenceRequest> absences) {

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
}

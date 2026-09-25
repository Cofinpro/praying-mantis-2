package pt.cofinpro.prayingmantis.absences;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

/**
 * Counts working days (decision #15): weekends and public holidays don't count, and a first or last day
 * that's only a morning or an afternoon counts 0.5. Pure logic, no Spring, so BE-3.2 can compute a new
 * request's days with it too.
 */
public final class WorkingDays {

    private static final BigDecimal HALF = new BigDecimal("0.5");

    private WorkingDays() {
    }

    /** All working days of the request. */
    public static BigDecimal count(LocalDate start, DayPart startPart, LocalDate end, DayPart endPart, Set<LocalDate> holidays) {
        return countWithin(start, startPart, end, endPart, start, end, holidays);
    }

    /**
     * Only the working days of the request that fall inside {@code from..to} (inclusive), e.g. one year of
     * a request across New Year. {@code holidays} must cover that window.
     */
    public static BigDecimal countWithin(
            LocalDate start, DayPart startPart, LocalDate end, DayPart endPart,
            LocalDate from, LocalDate to, Set<LocalDate> holidays) {
        LocalDate first = start.isAfter(from) ? start : from;
        LocalDate last = end.isBefore(to) ? end : to;
        BigDecimal total = BigDecimal.ZERO;
        for (LocalDate day = first; !day.isAfter(last); day = day.plusDays(1)) {
            if (isWeekend(day) || holidays.contains(day)) {
                continue;
            }
            boolean halfDay = (day.equals(start) && startPart != DayPart.FULL) || (day.equals(end) && endPart != DayPart.FULL);
            total = total.add(halfDay ? HALF : BigDecimal.ONE);
        }
        return total;
    }

    private static boolean isWeekend(LocalDate day) {
        DayOfWeek dayOfWeek = day.getDayOfWeek();
        return dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;
    }
}

package pt.cofinpro.prayingmantis.absences;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Set;
import pt.cofinpro.prayingmantis.common.InvalidFieldException;

/**
 * The days an absence covers, checked when it's built: the end isn't before the start, the day parts fit
 * (the DayPart rule from T-2.1, also a DB check), and it's at most a year long. Errors name the contract's
 * fields, so they become the 400 the FE shows next to the right input (T-3.1).
 */
public record AbsencePeriod(LocalDate start, DayPart startPart, LocalDate end, DayPart endPart) {

    /** Same limit as a calendar query (T-2.1); it also keeps working_days inside numeric(4,1). */
    static final long MAX_DAYS = 366;

    public AbsencePeriod {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(startPart, "startPart");
        Objects.requireNonNull(end, "end");
        Objects.requireNonNull(endPart, "endPart");
        if (end.isBefore(start)) {
            throw new InvalidFieldException("endDate", "must not be before startDate");
        }
        if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_DAYS) {
            throw new InvalidFieldException("endDate", "an absence can be at most " + MAX_DAYS + " days long");
        }
        if (start.equals(end)) {
            if (startPart != endPart) {
                throw new InvalidFieldException("endPart", "must be the same as startPart for a single day");
            }
        } else {
            if (startPart == DayPart.MORNING) {
                throw new InvalidFieldException("startPart", "must be FULL or AFTERNOON when the absence lasts several days");
            }
            if (endPart == DayPart.AFTERNOON) {
                throw new InvalidFieldException("endPart", "must be FULL or MORNING when the absence lasts several days");
            }
        }
    }

    public boolean isSingleDay() {
        return start.equals(end);
    }

    /** All its working days (decision #15); {@code holidays} must cover start..end. */
    public BigDecimal workingDays(Set<LocalDate> holidays) {
        return WorkingDays.count(start, startPart, end, endPart, holidays);
    }

    /** Only its working days inside {@code from..to}, e.g. one year; {@code holidays} must cover that part. */
    public BigDecimal workingDaysWithin(LocalDate from, LocalDate to, Set<LocalDate> holidays) {
        return WorkingDays.countWithin(start, startPart, end, endPart, from, to, holidays);
    }
}

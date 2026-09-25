package pt.cofinpro.prayingmantis.absences;

import static org.assertj.core.api.Assertions.assertThat;
import static pt.cofinpro.prayingmantis.absences.DayPart.AFTERNOON;
import static pt.cofinpro.prayingmantis.absences.DayPart.FULL;
import static pt.cofinpro.prayingmantis.absences.DayPart.MORNING;

import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Plain unit tests: WorkingDays has no Spring and no DB. Dates are in 2026/2027; 5 Oct 2026 is a Monday holiday. */
class WorkingDaysTest {

    private static final Set<LocalDate> NO_HOLIDAYS = Set.of();
    private static final LocalDate MON = LocalDate.of(2026, 9, 28);

    @Test
    void aFullWeekIsFiveDays() {
        assertThat(WorkingDays.count(MON, FULL, MON.plusDays(4), FULL, NO_HOLIDAYS)).isEqualByComparingTo("5");
    }

    @Test
    void weekendsDontCount() {
        // Friday to Monday
        assertThat(WorkingDays.count(MON.plusDays(4), FULL, MON.plusDays(7), FULL, NO_HOLIDAYS)).isEqualByComparingTo("2");
    }

    @Test
    void holidaysDontCount() {
        LocalDate republicDay = LocalDate.of(2026, 10, 5);

        assertThat(WorkingDays.count(republicDay, FULL, republicDay.plusDays(4), FULL, Set.of(republicDay)))
                .isEqualByComparingTo("4");
    }

    @Test
    void anAfternoonStartAndAMorningEndAreHalfDays() {
        assertThat(WorkingDays.count(MON, AFTERNOON, MON.plusDays(2), MORNING, NO_HOLIDAYS)).isEqualByComparingTo("2");
    }

    @Test
    void aSingleMorningIsHalfADay() {
        assertThat(WorkingDays.count(MON, MORNING, MON, MORNING, NO_HOLIDAYS)).isEqualByComparingTo("0.5");
    }

    @Test
    void aHalfDayOnAHolidayIsNothing() {
        LocalDate republicDay = LocalDate.of(2026, 10, 5);

        assertThat(WorkingDays.count(republicDay, AFTERNOON, republicDay, AFTERNOON, Set.of(republicDay)))
                .isEqualByComparingTo("0");
    }

    @Test
    void aWindowOnlyCountsTheDaysInsideIt() {
        // Mon 28 Dec 2026 to Fri 8 Jan 2027; 1 Jan 2027 is a holiday
        LocalDate start = LocalDate.of(2026, 12, 28);
        LocalDate end = LocalDate.of(2027, 1, 8);
        Set<LocalDate> holidays = Set.of(LocalDate.of(2027, 1, 1));

        assertThat(WorkingDays.countWithin(start, FULL, end, FULL, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), holidays))
                .isEqualByComparingTo("4");
        assertThat(WorkingDays.countWithin(start, FULL, end, FULL, LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31), holidays))
                .isEqualByComparingTo("5");
        assertThat(WorkingDays.count(start, FULL, end, FULL, holidays)).isEqualByComparingTo("9");
    }

    @Test
    void aHalfDayOutsideTheWindowDoesntCount() {
        LocalDate start = LocalDate.of(2026, 12, 30);
        LocalDate end = LocalDate.of(2027, 1, 4);

        // 2026: the afternoon of Wed 30 Dec and Thu 31 Dec. 2027: Fri 1 Jan (no holidays given here) and
        // the morning of Mon 4 Jan. The half days only count in the year they fall in.
        assertThat(WorkingDays.countWithin(start, AFTERNOON, end, MORNING, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), NO_HOLIDAYS))
                .isEqualByComparingTo("1.5");
        assertThat(WorkingDays.countWithin(start, AFTERNOON, end, MORNING, LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31), NO_HOLIDAYS))
                .isEqualByComparingTo("1.5");
    }
}

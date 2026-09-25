package pt.cofinpro.prayingmantis.absences;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pt.cofinpro.prayingmantis.absences.DayPart.AFTERNOON;
import static pt.cofinpro.prayingmantis.absences.DayPart.FULL;
import static pt.cofinpro.prayingmantis.absences.DayPart.MORNING;

import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pt.cofinpro.prayingmantis.common.InvalidFieldException;

/** Plain unit tests (BE-3.1): which periods are valid, and which field an invalid one is reported on. */
class AbsencePeriodTest {

    private static final LocalDate MON = LocalDate.of(2026, 9, 28);

    @ParameterizedTest(name = "{0}..{1} days after, {2} to {3}")
    @CsvSource({
            // single day: the same part at both ends
            "0, 0, FULL, FULL",
            "0, 0, MORNING, MORNING",
            "0, 0, AFTERNOON, AFTERNOON",
            // several days: may start in the afternoon and end in the morning
            "0, 1, FULL, FULL",
            "0, 1, AFTERNOON, MORNING",
            "0, 4, AFTERNOON, FULL",
            "0, 4, FULL, MORNING",
    })
    void validPeriods(int startOffset, int endOffset, DayPart startPart, DayPart endPart) {
        AbsencePeriod period = new AbsencePeriod(MON.plusDays(startOffset), startPart, MON.plusDays(endOffset), endPart);

        assertThat(period.isSingleDay()).isEqualTo(startOffset == endOffset);
    }

    @Test
    void anEndBeforeTheStartIsReportedOnEndDate() {
        assertInvalid(MON, FULL, MON.minusDays(1), FULL, "endDate", "must not be before startDate");
    }

    @Test
    void aSingleDayWithTwoDifferentPartsIsReportedOnEndPart() {
        assertInvalid(MON, MORNING, MON, AFTERNOON, "endPart", "must be the same as startPart for a single day");
        assertInvalid(MON, FULL, MON, MORNING, "endPart", "must be the same as startPart for a single day");
    }

    @Test
    void severalDaysStartingWithAMorningLeaveAGap() {
        assertInvalid(MON, MORNING, MON.plusDays(1), FULL, "startPart", "must be FULL or AFTERNOON when the absence lasts several days");
    }

    @Test
    void severalDaysEndingWithAnAfternoonLeaveAGap() {
        assertInvalid(MON, FULL, MON.plusDays(1), AFTERNOON, "endPart", "must be FULL or MORNING when the absence lasts several days");
    }

    @Test
    void atMostAYear() {
        LocalDate jan1 = LocalDate.of(2028, 1, 1);

        // 2028 is a leap year: all of it is 366 days
        new AbsencePeriod(jan1, FULL, LocalDate.of(2028, 12, 31), FULL);
        assertInvalid(jan1, FULL, LocalDate.of(2029, 1, 1), FULL, "endDate", "an absence can be at most 366 days long");
    }

    @Test
    void workingDaysUseTheCalculation() {
        LocalDate goodFriday = LocalDate.of(2027, 3, 26);
        // Thu 25 Mar afternoon to Tue 30 Mar 2027 morning, around Good Friday
        AbsencePeriod easter = new AbsencePeriod(goodFriday.minusDays(1), AFTERNOON, goodFriday.plusDays(4), MORNING);

        // 0.5 (Thu) + 0 (Good Friday) + 0 (weekend) + 1 (Mon) + 0.5 (Tue)
        assertThat(easter.workingDays(Set.of(goodFriday))).isEqualByComparingTo("2");
        assertThat(easter.workingDaysWithin(goodFriday, goodFriday.plusDays(3), Set.of(goodFriday))).isEqualByComparingTo("1");
    }

    @Test
    void aWeekendOnlyPeriodHasNoWorkingDays() {
        LocalDate saturday = MON.minusDays(2);

        assertThat(new AbsencePeriod(saturday, FULL, saturday.plusDays(1), FULL).workingDays(Set.of())).isEqualByComparingTo("0");
    }

    private static void assertInvalid(LocalDate start, DayPart startPart, LocalDate end, DayPart endPart, String field, String message) {
        assertThatThrownBy(() -> new AbsencePeriod(start, startPart, end, endPart))
                .isInstanceOfSatisfying(InvalidFieldException.class, e -> {
                    assertThat(e.getField()).isEqualTo(field);
                    assertThat(e.getMessage()).isEqualTo(message);
                });
    }
}

package pt.cofinpro.prayingmantis.absences;

import java.math.BigDecimal;

/** One balance card: a user's days of one absence type in one year. {@code remainingDays} is null unless the type deducts. */
public record TypeBalance(
        AbsenceTypeCode type,
        int year,
        BigDecimal entitledDays,
        BigDecimal carriedOverDays,
        BigDecimal usedDays,
        BigDecimal pendingDays,
        BigDecimal remainingDays) {
}

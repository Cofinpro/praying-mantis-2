package pt.cofinpro.prayingmantis.absences;

import java.math.BigDecimal;

/**
 * A request as its approver sees it (T-5.1). {@code remainingDays} is the requester's days left in the start
 * year, only for types that deduct from the balance; null otherwise.
 */
public record TeamRequest(AbsenceRequest request, BigDecimal remainingDays) {
}

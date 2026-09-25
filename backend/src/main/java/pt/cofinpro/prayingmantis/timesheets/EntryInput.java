package pt.cofinpro.prayingmantis.timesheets;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One grid cell as the FE sends it (T-6.1, TimeEntryInput). Bean Validation has already checked the basics. */
public record EntryInput(Long projectId, LocalDate workDate, BigDecimal hours, String description) {
}

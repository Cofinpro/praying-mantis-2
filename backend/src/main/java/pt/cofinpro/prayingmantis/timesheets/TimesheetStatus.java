package pt.cofinpro.prayingmantis.timesheets;

/** DRAFT → SUBMITTED → APPROVED or REJECTED; REJECTED → SUBMITTED again after edits (plan.md §4). */
public enum TimesheetStatus {
    DRAFT,
    SUBMITTED,
    APPROVED,
    REJECTED;

    /** Only these can take new entries or be submitted (decision #32). */
    public boolean isEditable() {
        return this == DRAFT || this == REJECTED;
    }
}

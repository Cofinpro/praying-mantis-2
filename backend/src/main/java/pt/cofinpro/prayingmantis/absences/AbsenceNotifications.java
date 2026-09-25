package pt.cofinpro.prayingmantis.absences;

/**
 * What the absence services tell other people about (plan.md epic 3), as rows in the notifications table:
 * {@link StoredAbsenceNotifications} (BE-4.1). Every call runs inside the business change's transaction.
 */
public interface AbsenceNotifications {

    /** A new request is waiting for its approver: ABSENCE_REQUESTED. */
    void requested(AbsenceRequest request);

    /** An approved request was cancelled, so its approver should know (BE-3.3). */
    void cancelled(AbsenceRequest request);
}

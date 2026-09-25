package pt.cofinpro.prayingmantis.absences;

/**
 * What the absence services tell other people about (plan.md epic 3). Epic 4 implements it with rows in
 * the notifications table; until then {@link LoggingAbsenceNotifications} only logs.
 */
public interface AbsenceNotifications {

    /** A new request is waiting for its approver: ABSENCE_REQUESTED. */
    void requested(AbsenceRequest request);

    /** An approved request was cancelled, so its approver should know (BE-3.3). */
    void cancelled(AbsenceRequest request);
}

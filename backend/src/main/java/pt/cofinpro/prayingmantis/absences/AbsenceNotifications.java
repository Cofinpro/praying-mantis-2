package pt.cofinpro.prayingmantis.absences;

/**
 * What the absence services tell other people about (plan.md epics 3 and 5), as rows in the notifications
 * table: {@link StoredAbsenceNotifications} (BE-4.1). Every call runs inside the business change's transaction.
 */
public interface AbsenceNotifications {

    /** A new request is waiting for its approver: ABSENCE_REQUESTED. */
    void requested(AbsenceRequest request);

    /** An approved request was cancelled, so its approver should know (BE-3.3): ABSENCE_CANCELLED. */
    void cancelled(AbsenceRequest request);

    /** The requester's request was approved (BE-5.2): ABSENCE_APPROVED. */
    void approved(AbsenceRequest request);

    /** The requester's request was rejected, with the approver's comment (BE-5.2): ABSENCE_REJECTED. */
    void rejected(AbsenceRequest request);
}

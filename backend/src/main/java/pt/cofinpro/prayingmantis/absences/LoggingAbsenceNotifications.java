package pt.cofinpro.prayingmantis.absences;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Stub until epic 4 stores notifications. Logs ids only, not names or reasons. */
@Component
class LoggingAbsenceNotifications implements AbsenceNotifications {

    private static final Logger log = LoggerFactory.getLogger(LoggingAbsenceNotifications.class);

    @Override
    public void requested(AbsenceRequest request) {
        log.info("Absence request {} waits for approver {}", request.getId(), request.getApprover().getId());
    }

    @Override
    public void cancelled(AbsenceRequest request) {
        log.info("Approved absence request {} was cancelled; approver {} to be told", request.getId(),
                request.getApprover() == null ? null : request.getApprover().getId());
    }
}

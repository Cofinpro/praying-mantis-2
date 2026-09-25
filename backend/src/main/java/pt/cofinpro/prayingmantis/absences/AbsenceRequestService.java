package pt.cofinpro.prayingmantis.absences;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.cofinpro.prayingmantis.common.InvalidFieldException;

/** Reading a user's absence requests (BE-2.3). Creating and cancelling them is epic 3. */
@Service
public class AbsenceRequestService {

    /** At most a year per call (T-2.1), so a mistaken query can't fetch everything. */
    static final long MAX_RANGE_DAYS = 366;

    private final AbsenceRequestRepository requests;

    public AbsenceRequestService(AbsenceRequestRepository requests) {
        this.requests = requests;
    }

    /**
     * Every request of the user that overlaps {@code from..to} (both inclusive), in any status, ordered by
     * start date. A request that only partly overlaps comes back whole.
     */
    @Transactional(readOnly = true)
    public List<AbsenceRequest> overlapping(Long userId, LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new InvalidFieldException("to", "must not be before from");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_RANGE_DAYS) {
            throw new InvalidFieldException("to", "the range must be at most " + MAX_RANGE_DAYS + " days");
        }
        return requests.findForCalendar(userId, from, to);
    }
}

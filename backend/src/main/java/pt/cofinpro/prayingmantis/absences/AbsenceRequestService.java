package pt.cofinpro.prayingmantis.absences;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.cofinpro.prayingmantis.auth.Permissions;
import pt.cofinpro.prayingmantis.common.ConflictException;
import pt.cofinpro.prayingmantis.common.InvalidFieldException;
import pt.cofinpro.prayingmantis.holidays.PublicHoliday;
import pt.cofinpro.prayingmantis.holidays.PublicHolidayRepository;
import pt.cofinpro.prayingmantis.users.User;
import pt.cofinpro.prayingmantis.users.UserRepository;

/** A user's own absence requests: reading them (BE-2.3) and creating them (BE-3.2). */
@Service
public class AbsenceRequestService {

    /** At most a year per call (T-2.1), so a mistaken query can't fetch everything. */
    static final long MAX_RANGE_DAYS = 366;

    /** The 409 reasons of T-3.1, as the Problem's type. */
    static final String OVERLAP = "absence-overlap";
    static final String INSUFFICIENT_BALANCE = "insufficient-balance";
    static final String NO_APPROVER = "no-approver";

    private static final List<AbsenceStatus> BLOCKING = List.of(AbsenceStatus.PENDING, AbsenceStatus.APPROVED);
    private static final String NO_OVERLAP_CONSTRAINT = "ex_absence_requests_no_overlap";

    private final AbsenceRequestRepository requests;
    private final AbsenceTypeRepository types;
    private final PublicHolidayRepository holidays;
    private final UserRepository users;
    private final AbsenceBalanceService balances;
    private final Permissions permissions;
    private final AbsenceNotifications notifications;
    private final Clock clock;

    public AbsenceRequestService(
            AbsenceRequestRepository requests,
            AbsenceTypeRepository types,
            PublicHolidayRepository holidays,
            UserRepository users,
            AbsenceBalanceService balances,
            Permissions permissions,
            AbsenceNotifications notifications,
            Clock clock) {
        this.requests = requests;
        this.types = types;
        this.holidays = holidays;
        this.users = users;
        this.balances = balances;
        this.permissions = permissions;
        this.notifications = notifications;
        this.clock = clock;
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

    /**
     * Creates a request for the user (BE-3.2). The checks run in the order the FE can best explain them:
     * the period itself (400), then overlap, balance and approver (409). A type that needs no approval
     * (SICK) is approved straight away. Any type may start in the past (decision #27).
     */
    @Transactional
    public AbsenceRequest create(
            Long userId, AbsenceTypeCode code, LocalDate start, DayPart startPart, LocalDate end, DayPart endPart,
            String reason) {
        AbsencePeriod period = new AbsencePeriod(start, startPart, end, endPart);
        AbsenceType type = types.findByCode(code).orElseThrow();
        Set<LocalDate> periodHolidays = holidays.findByDateBetweenOrderByDate(start, end).stream()
                .map(PublicHoliday::getDate)
                .collect(Collectors.toSet());
        BigDecimal workingDays = period.workingDays(periodHolidays);
        if (workingDays.signum() == 0) {
            throw new InvalidFieldException("endDate", "the absence has no working days");
        }

        if (requests.existsOverlapping(userId, start, end, BLOCKING)) {
            throw overlap();
        }
        if (type.isDeductsFromBalance()) {
            requireBalance(userId, type, period, periodHolidays);
        }

        User approver = null;
        AbsenceStatus status = AbsenceStatus.APPROVED;
        if (type.isRequiresApproval()) {
            approver = permissions.approverFor(userId).orElseThrow(() -> new ConflictException(NO_APPROVER,
                    "Nobody can approve this request: you have no team lead and there is no other admin"));
            status = AbsenceStatus.PENDING;
        }

        AbsenceRequest request = new AbsenceRequest(users.getReferenceById(userId), type, start, startPart, end, endPart,
                workingDays, status, approver);
        request.setReason(reason);
        if (status == AbsenceStatus.APPROVED) {
            request.setDecidedAt(Instant.now(clock));
        }
        try {
            requests.saveAndFlush(request);
        } catch (DataIntegrityViolationException e) {
            // Another request for the same days got in between the check and the insert (decision #14)
            if (String.valueOf(e.getMessage()).contains(NO_OVERLAP_CONSTRAINT)) {
                throw overlap();
            }
            throw e;
        }

        if (status == AbsenceStatus.PENDING) {
            notifications.requested(request);
        }
        return request;
    }

    /** Each year the period touches needs enough days left for its part of the period (decision #27). */
    private void requireBalance(Long userId, AbsenceType type, AbsencePeriod period, Set<LocalDate> periodHolidays) {
        for (int year = period.start().getYear(); year <= period.end().getYear(); year++) {
            BigDecimal needed = period.workingDaysWithin(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31), periodHolidays);
            BigDecimal left = balances.daysLeft(userId, type.getCode(), year);
            if (needed.compareTo(left) > 0) {
                throw new ConflictException(INSUFFICIENT_BALANCE, "Only %s %s days left in %d, but the request needs %s"
                        .formatted(plain(left), type.getName().toLowerCase(), year, plain(needed)));
            }
        }
    }

    private static ConflictException overlap() {
        return new ConflictException(OVERLAP, "These days overlap another pending or approved request of yours");
    }

    private static String plain(BigDecimal days) {
        return days.stripTrailingZeros().toPlainString();
    }
}

package pt.cofinpro.prayingmantis.absences;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.cofinpro.prayingmantis.holidays.PublicHoliday;
import pt.cofinpro.prayingmantis.holidays.PublicHolidayRepository;

/**
 * A user's balance per absence type (BE-2.2). Nothing here is stored: remaining = entitled + carried over
 * − used, computed on every call (decision #9).
 */
@Service
public class AbsenceBalanceService {

    private static final List<AbsenceStatus> COUNTED = List.of(AbsenceStatus.APPROVED, AbsenceStatus.PENDING);

    private final AbsenceEntitlementRepository entitlements;
    private final AbsenceRequestRepository requests;
    private final PublicHolidayRepository holidays;
    private final Clock clock;

    public AbsenceBalanceService(
            AbsenceEntitlementRepository entitlements,
            AbsenceRequestRepository requests,
            PublicHolidayRepository holidays,
            Clock clock) {
        this.entitlements = entitlements;
        this.requests = requests;
        this.holidays = holidays;
        this.clock = clock;
    }

    /**
     * One entry per type the user has an entitlement for in {@code year}, in type name order. Without a
     * year, the current one in Lisbon (T-2.1).
     */
    @Transactional(readOnly = true)
    public List<TypeBalance> balance(Long userId, Integer year) {
        int y = year != null ? year : LocalDate.now(clock).getYear();
        LocalDate jan1 = LocalDate.of(y, 1, 1);
        LocalDate dec31 = LocalDate.of(y, 12, 31);

        List<AbsenceEntitlement> yearEntitlements = entitlements.findWithTypeByUserIdAndYear(userId, y);
        if (yearEntitlements.isEmpty()) {
            return List.of();
        }
        List<AbsenceRequest> counted = requests.findWithTypeOverlapping(userId, jan1, dec31, COUNTED);
        Set<LocalDate> yearHolidays = holidays.findByDateBetweenOrderByDate(jan1, dec31).stream()
                .map(PublicHoliday::getDate)
                .collect(Collectors.toSet());

        return yearEntitlements.stream()
                .map(entitlement -> {
                    AbsenceType type = entitlement.getType();
                    BigDecimal used = sum(counted, type, AbsenceStatus.APPROVED, jan1, dec31, yearHolidays);
                    BigDecimal pending = sum(counted, type, AbsenceStatus.PENDING, jan1, dec31, yearHolidays);
                    BigDecimal remaining = type.isDeductsFromBalance()
                            ? entitlement.getEntitledDays().add(entitlement.getCarriedOverDays()).subtract(used)
                            : null;
                    return new TypeBalance(type.getCode(), y, entitlement.getEntitledDays(),
                            entitlement.getCarriedOverDays(), used, pending, remaining);
                })
                .toList();
    }

    private static BigDecimal sum(
            List<AbsenceRequest> requests, AbsenceType type, AbsenceStatus status,
            LocalDate jan1, LocalDate dec31, Set<LocalDate> holidays) {
        return requests.stream()
                .filter(r -> r.getStatus() == status && r.getType().getId().equals(type.getId()))
                .map(r -> daysIn(r, jan1, dec31, holidays))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * A request inside the year counts its stored working days, which don't change if holidays are edited
     * later (decision #15). One across New Year only counts its days in this year, computed with this
     * year's holidays.
     */
    private static BigDecimal daysIn(AbsenceRequest r, LocalDate jan1, LocalDate dec31, Set<LocalDate> holidays) {
        boolean insideYear = !r.getStartDate().isBefore(jan1) && !r.getEndDate().isAfter(dec31);
        if (insideYear) {
            return r.getWorkingDays();
        }
        return WorkingDays.countWithin(
                r.getStartDate(), r.getStartPart(), r.getEndDate(), r.getEndPart(), jan1, dec31, holidays);
    }
}

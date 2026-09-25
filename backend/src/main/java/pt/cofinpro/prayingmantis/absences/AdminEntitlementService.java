package pt.cofinpro.prayingmantis.absences;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.cofinpro.prayingmantis.auth.Permissions;
import pt.cofinpro.prayingmantis.common.InvalidFieldException;
import pt.cofinpro.prayingmantis.common.NotFoundException;
import pt.cofinpro.prayingmantis.users.User;
import pt.cofinpro.prayingmantis.users.UserRepository;

/** Managing yearly entitlements (BE-9.2, decision #35). Every method checks that the caller is an admin. */
@Service
public class AdminEntitlementService {

    private static final BigDecimal TWO = new BigDecimal("2");

    private final AbsenceEntitlementRepository entitlements;
    private final AbsenceTypeRepository types;
    private final UserRepository users;
    private final Permissions permissions;

    public AdminEntitlementService(
            AbsenceEntitlementRepository entitlements, AbsenceTypeRepository types, UserRepository users, Permissions permissions) {
        this.entitlements = entitlements;
        this.types = types;
        this.users = users;
        this.permissions = permissions;
    }

    @Transactional(readOnly = true)
    public List<AbsenceEntitlement> forYear(int year) {
        permissions.requireAdmin();
        return entitlements.findAllForYear(year);
    }

    /**
     * Creates the entitlement for (user, type, year) or replaces its days: an upsert on the unique key, so the
     * admin grid can save a cell without knowing whether it exists (decision #35).
     */
    @Transactional
    public AbsenceEntitlement save(Long userId, AbsenceTypeCode code, int year, BigDecimal entitled, BigDecimal carriedOver) {
        permissions.requireAdmin();
        requireHalfDays("entitledDays", entitled);
        requireHalfDays("carriedOverDays", carriedOver);
        User user = users.findById(userId).orElseThrow(() -> new InvalidFieldException("userId", "no such user"));
        AbsenceType type = types.findByCode(code).orElseThrow();

        AbsenceEntitlement entitlement = entitlements.findByUserIdAndTypeIdAndYear(userId, type.getId(), year)
                .orElseGet(() -> new AbsenceEntitlement(user, type, year, entitled, carriedOver));
        entitlement.setEntitledDays(entitled);
        entitlement.setCarriedOverDays(carriedOver);
        return entitlements.saveAndFlush(entitlement);
    }

    /** The user then has no days of that type left in that year (decision #29). */
    @Transactional
    public void delete(Long entitlementId) {
        permissions.requireAdmin();
        AbsenceEntitlement entitlement = entitlements.findById(entitlementId)
                .orElseThrow(() -> new NotFoundException("Entitlement not found"));
        entitlements.delete(entitlement);
    }

    /** The contract says steps of 0.5, which the generated validation doesn't check; the DB would, as a 500. */
    private static void requireHalfDays(String field, BigDecimal days) {
        if (days.multiply(TWO).stripTrailingZeros().scale() > 0) {
            throw new InvalidFieldException(field, "must be in steps of 0.5");
        }
    }
}

package pt.cofinpro.prayingmantis.auth;

import java.util.Objects;
import java.util.Optional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import pt.cofinpro.prayingmantis.users.User;
import pt.cofinpro.prayingmantis.users.UserRepository;

/**
 * The permission checks services use (decision #11: the backend enforces all of them). Questions about
 * the caller read the DB, not the session, so a changed admin flag or team lead applies straight away.
 * The {@code require...} methods throw {@link AccessDeniedException}, which becomes a 403 Problem Details.
 *
 * <p>Deliberately not {@code @Transactional}: every check is a single query. If it joined the caller's
 * transaction, an AccessDeniedException leaving this proxy would mark the caller's whole transaction
 * rollback-only, even if the caller caught it.
 */
@Component
public class Permissions {

    private final UserRepository users;

    public Permissions(UserRepository users) {
        this.users = users;
    }

    /** Is the logged-in user an admin (the "privileged account")? */
    public boolean isAdmin() {
        return users.existsByIdAndAdminTrue(callerId());
    }

    /** Is the logged-in user the direct team lead of {@code userId}? A lead's lead doesn't count. */
    public boolean isTeamLeadOf(Long userId) {
        return userId != null && users.existsByIdAndTeamLeadId(userId, callerId());
    }

    public void requireAdmin() {
        if (!isAdmin()) {
            throw new AccessDeniedException("Admins only");
        }
    }

    /**
     * For deciding on an absence request or timesheet (BE-5.2, BE-7.2): the approver stored on it, or any
     * admin, but never someone deciding on their own request (decision #16). Checks the stored approver,
     * not the current team lead, which may have changed since.
     */
    public void requireApproverOrAdmin(Long approverId, Long requesterId) {
        Long callerId = callerId();
        if (Objects.equals(callerId, requesterId)) {
            throw new AccessDeniedException("Nobody decides on their own request");
        }
        if (!Objects.equals(callerId, approverId) && !isAdmin()) {
            throw new AccessDeniedException("Only the approver or an admin");
        }
    }

    /**
     * Who approves this user's absences and timesheets (decision #16): their team lead, which for a team
     * lead is their own lead. Without one, the first admin by id who isn't the user. Empty only for the
     * sole admin without a team lead; callers reject that request (409). The returned user is fully loaded.
     */
    public Optional<User> approverFor(Long userId) {
        return users.findTeamLeadOf(userId)
                .or(() -> users.findFirstByAdminTrueAndIdNotOrderByIdAsc(userId));
    }

    private static Long callerId() {
        return AuthenticatedUsers.current().getId();
    }
}

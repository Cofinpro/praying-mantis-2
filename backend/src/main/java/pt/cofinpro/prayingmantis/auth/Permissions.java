package pt.cofinpro.prayingmantis.auth;

import java.util.Objects;
import java.util.Optional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pt.cofinpro.prayingmantis.users.User;
import pt.cofinpro.prayingmantis.users.UserRepository;

/**
 * The permission checks services use (decision #11: the backend enforces all of them). Questions about
 * the caller read the DB, not the session, so a changed admin flag or team lead applies straight away.
 * The {@code require...} methods throw {@link AccessDeniedException}, which becomes a 403 Problem Details.
 */
@Component
@Transactional(readOnly = true)
public class Permissions {

    private final UserRepository users;

    public Permissions(UserRepository users) {
        this.users = users;
    }

    /** Is the logged-in user an admin (the "privileged account")? */
    public boolean isAdmin() {
        return caller().isAdmin();
    }

    /** Is the logged-in user the direct team lead of {@code userId}? A lead's lead doesn't count. */
    public boolean isTeamLeadOf(Long userId) {
        Long callerId = AuthenticatedUsers.current().getId();
        return users.findById(userId)
                .map(User::getTeamLead)
                .map(lead -> Objects.equals(lead.getId(), callerId))
                .orElse(false);
    }

    public void requireAdmin() {
        if (!isAdmin()) {
            throw new AccessDeniedException("Admins only");
        }
    }

    public void requireTeamLeadOf(Long userId) {
        if (!isTeamLeadOf(userId)) {
            throw new AccessDeniedException("Only the team lead of user " + userId);
        }
    }

    /**
     * Who approves this user's absences and timesheets (decision #16): their team lead, which for a team
     * lead is their own lead. Without one, an admin: the first by id who isn't the user, so nobody approves
     * their own requests (any admin can still decide, BE-5.2). Empty only for the sole admin without a lead.
     */
    public Optional<User> approverFor(User user) {
        if (user.getTeamLead() != null) {
            return Optional.of(user.getTeamLead());
        }
        return users.findFirstByAdminTrueAndIdNotOrderByIdAsc(user.getId());
    }

    private User caller() {
        Long callerId = AuthenticatedUsers.current().getId();
        return users.findById(callerId)
                .orElseThrow(() -> new AccessDeniedException("Logged-in user " + callerId + " no longer exists"));
    }
}

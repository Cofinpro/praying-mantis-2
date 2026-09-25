package pt.cofinpro.prayingmantis.users;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.cofinpro.prayingmantis.auth.Permissions;
import pt.cofinpro.prayingmantis.common.ConflictException;
import pt.cofinpro.prayingmantis.common.InvalidFieldException;
import pt.cofinpro.prayingmantis.common.NotFoundException;

/** Managing users (BE-9.1, decision #35). Every method checks that the caller is an admin, in the DB. */
@Service
public class AdminUserService {

    static final String EMAIL_TAKEN = "email-taken";
    static final String TEAM_LEAD_CYCLE = "team-lead-cycle";
    static final String LAST_ADMIN = "last-admin";

    /** What the admin can set on a user; the password only on create. */
    public record UserInput(String name, String email, Client client, Level level, boolean admin, Long teamLeadId) {
    }

    /** A user plus the derived "is team lead" (decision #9). */
    public record Listed(User user, boolean teamLead) {
    }

    private final UserRepository users;
    private final Permissions permissions;
    private final PasswordEncoder passwordEncoder;

    public AdminUserService(UserRepository users, Permissions permissions, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.permissions = permissions;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<Listed> all() {
        permissions.requireAdmin();
        Set<Long> leads = users.findTeamLeadIds();
        return users.findAllWithTeamLead().stream().map(u -> new Listed(u, leads.contains(u.getId()))).toList();
    }

    @Transactional
    public Listed create(UserInput input, String password) {
        permissions.requireAdmin();
        String email = User.normalizeEmail(input.email());
        if (users.existsByEmail(email)) {
            throw emailTaken();
        }
        User user = new User(input.name().strip(), email, passwordEncoder.encode(password), input.client(), input.level());
        user.setAdmin(input.admin());
        user.setTeamLead(teamLead(input.teamLeadId()));
        return new Listed(save(user), false);
    }

    /**
     * Replaces the user's fields. A new team lead only gets requests and timesheets submitted from now on;
     * the waiting ones keep their stored approver (decisions #31, #35).
     */
    @Transactional
    public Listed update(Long userId, UserInput input) {
        permissions.requireAdmin();
        User user = users.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
        String email = User.normalizeEmail(input.email());
        if (users.existsByEmailAndIdNot(email, userId)) {
            throw emailTaken();
        }
        if (Objects.equals(input.teamLeadId(), userId)) {
            throw new InvalidFieldException("teamLeadId", "a user can't be their own team lead");
        }
        User lead = teamLead(input.teamLeadId());
        requireNoCycle(user, lead);
        if (user.isAdmin() && !input.admin() && users.countByAdminTrue() == 1) {
            throw new ConflictException(LAST_ADMIN, "This is the only admin; make someone else an admin first");
        }

        user.setName(input.name().strip());
        user.setEmail(email);
        user.setClient(input.client());
        user.setLevel(input.level());
        user.setAdmin(input.admin());
        user.setTeamLead(lead);
        return new Listed(save(user), users.existsByTeamLeadId(userId));
    }

    @Transactional
    public void setPassword(Long userId, String password) {
        permissions.requireAdmin();
        User user = users.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
        user.setPasswordHash(passwordEncoder.encode(password));
    }

    /** Walks up from the new lead: reaching {@code user} means it already leads the new lead, directly or not. */
    private static void requireNoCycle(User user, User newLead) {
        Set<Long> seen = new HashSet<>();
        for (User up = newLead; up != null && seen.add(up.getId()); up = up.getTeamLead()) {
            if (up.getId().equals(user.getId())) {
                throw new ConflictException(TEAM_LEAD_CYCLE,
                        "%s already leads %s, directly or through others".formatted(user.getName(), newLead.getName()));
            }
        }
    }

    private User teamLead(Long teamLeadId) {
        if (teamLeadId == null) {
            return null;
        }
        return users.findById(teamLeadId).orElseThrow(() -> new InvalidFieldException("teamLeadId", "no such user"));
    }

    /** A concurrent create with the same email gets past the check; the unique constraint catches it. */
    private User save(User user) {
        try {
            return users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            if (String.valueOf(e.getMessage()).contains("uq_users_email")) {
                throw emailTaken();
            }
            throw e;
        }
    }

    private static ConflictException emailTaken() {
        return new ConflictException(EMAIL_TAKEN, "Another user already has this email");
    }
}

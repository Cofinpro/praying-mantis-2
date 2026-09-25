package pt.cofinpro.prayingmantis.users;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository users;

    public UserService(UserRepository users) {
        this.users = users;
    }

    /** Read fresh on every call, so role changes apply without logging in again. */
    @Transactional(readOnly = true)
    public UserProfile profile(Long userId) {
        User user = users.findById(userId)
                .orElseThrow(() -> new IllegalStateException("Logged-in user " + userId + " no longer exists"));
        return new UserProfile(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getClient(),
                user.getLevel(),
                user.isAdmin(),
                users.existsByTeamLeadId(user.getId()));
    }
}

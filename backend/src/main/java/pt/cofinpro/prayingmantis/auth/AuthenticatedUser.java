package pt.cofinpro.prayingmantis.auth;

import java.util.Collection;
import java.util.List;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import pt.cofinpro.prayingmantis.users.User;

/**
 * The principal Spring Security keeps in the session after login. It holds only what's needed to
 * find the user again: the id, plus the admin role. Everything else is read fresh from the DB, so a
 * change (e.g. a new team lead) shows up without logging in again.
 */
public final class AuthenticatedUser implements UserDetails, CredentialsContainer {

    public static final String ROLE_ADMIN = "ROLE_ADMIN";

    private final Long id;
    private final String email;
    private final boolean admin;
    // Only needed while the password is checked; erased afterwards so it isn't kept in the session
    private String passwordHash;

    AuthenticatedUser(User user) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.admin = user.isAdmin();
        this.passwordHash = user.getPasswordHash();
    }

    public Long getId() {
        return id;
    }

    public boolean isAdmin() {
        return admin;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return admin ? List.of(new SimpleGrantedAuthority(ROLE_ADMIN)) : List.of();
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public void eraseCredentials() {
        passwordHash = null;
    }
}

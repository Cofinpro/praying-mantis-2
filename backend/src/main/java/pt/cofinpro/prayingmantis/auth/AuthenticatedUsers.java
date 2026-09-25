package pt.cofinpro.prayingmantis.auth;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Who is calling. The generated API interfaces have no principal parameter, so controllers ask here.
 * Only reachable behind {@code authenticated()} routes, so a missing user is a 401, not a bug.
 */
public final class AuthenticatedUsers {

    private AuthenticatedUsers() {
    }

    public static AuthenticatedUser current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user;
        }
        throw new AuthenticationCredentialsNotFoundException("Not logged in");
    }
}

package pt.cofinpro.prayingmantis.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfLogoutHandler;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.stereotype.Component;
import pt.cofinpro.prayingmantis.config.SecurityConfig;

/**
 * Starts and ends the server session (decision #12). Spring's formLogin would do this for us, but our
 * login is a JSON endpoint from the contract, so the steps are explicit here:
 * authenticate, change the session id (session fixation), rotate the CSRF token, save the context.
 */
@Component
public class SessionLogin {

    private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository contextRepository;
    private final SessionAuthenticationStrategy sessionStrategy;
    private final CookieClearingLogoutHandler cookieClearing = new CookieClearingLogoutHandler(SecurityConfig.SESSION_COOKIE);
    private final SecurityContextLogoutHandler contextLogout = new SecurityContextLogoutHandler();
    private final CsrfLogoutHandler csrfLogout;

    public SessionLogin(
            AuthenticationManager authenticationManager,
            SecurityContextRepository contextRepository,
            CsrfTokenRepository csrfTokenRepository) {
        this.authenticationManager = authenticationManager;
        this.contextRepository = contextRepository;
        this.sessionStrategy = new CompositeSessionAuthenticationStrategy(List.of(
                new ChangeSessionIdAuthenticationStrategy(),
                new CsrfAuthenticationStrategy(csrfTokenRepository)));
        this.csrfLogout = new CsrfLogoutHandler(csrfTokenRepository);
    }

    /** Throws {@code BadCredentialsException} for a wrong email or password. */
    public AuthenticatedUser login(String email, String password, HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(email, password));

        sessionStrategy.onAuthentication(authentication, request, response);
        SecurityContext context = contextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        contextHolder.setContext(context);
        // Since Spring Security 6 nothing saves the context for us: without this the next request is anonymous
        contextRepository.saveContext(context, request, response);

        return (AuthenticatedUser) authentication.getPrincipal();
    }

    /** Invalidates the session, clears the session cookie and the CSRF token. Safe without a session. */
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = contextHolder.getContext().getAuthentication();
        csrfLogout.logout(request, response, authentication);
        cookieClearing.logout(request, response, authentication);
        contextLogout.logout(request, response, authentication);
    }
}

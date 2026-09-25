package pt.cofinpro.prayingmantis.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import pt.cofinpro.prayingmantis.users.User;
import pt.cofinpro.prayingmantis.users.UserRepository;

/** How a login is checked: email lookup plus BCrypt (decision #12). Kept apart from the filter chain. */
@Configuration
public class AuthConfig {

    /** Plain BCrypt: the stored hashes are raw {@code $2a$...} values without a {@code {bcrypt}} prefix. */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(UserRepository users) {
        return email -> users.findByEmail(User.normalizeEmail(email))
                .map(AuthenticatedUser::new)
                .orElseThrow(() -> new UsernameNotFoundException("No user with that email"));
    }

    /**
     * DaoAuthenticationProvider reports an unknown email as BadCredentialsException too, and still runs
     * a BCrypt check, so neither the answer nor the response time reveals whether an account exists.
     */
    @Bean
    AuthenticationManager authenticationManager(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        var provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }
}

package pt.cofinpro.prayingmantis.config;

import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Session login with CSRF protection (decision #12). Health, the sample endpoint, login and logout
 * are public; everything else needs a logged-in user. The login itself is in {@code auth.SessionLogin}.
 */
@Configuration
public class SecurityConfig {

    /** Tomcat's default session cookie, as named in the contract's sessionCookie scheme. */
    public static final String SESSION_COOKIE = "JSESSIONID";

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            CsrfTokenRepository csrfTokenRepository,
            SecurityContextRepository securityContextRepository,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        // Security also runs on the internal forward to /error; without this a 404/500 becomes 401
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        // Sample endpoint (BE-0.2), public so the FE can call it before login exists (M0)
                        .requestMatchers(HttpMethod.GET, "/api/hello").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/logout").permitAll()
                        .anyRequest().authenticated())
                // SPA setup: a readable XSRF-TOKEN cookie, sent back by the FE as the X-XSRF-TOKEN header on
                // unsafe requests. Our own repository instance, so SessionLogin can rotate the token on login.
                .csrf(csrf -> csrf.spa().csrfTokenRepository(csrfTokenRepository))
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                // No formLogin/httpBasic: the only way in is POST /api/auth/login
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                // Don't create a session just to remember the URL an anonymous caller asked for
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                // 401 and 403 from the filter chain never reach @RestControllerAdvice on their own. Hand them to
                // the MVC exception resolvers, so ApiExceptionHandler writes their Problem Details (decision #21).
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) ->
                                exceptionResolver.resolveException(request, response, null, authException))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                exceptionResolver.resolveException(request, response, null, accessDeniedException)));
        return http.build();
    }

    @Bean
    CsrfTokenRepository csrfTokenRepository() {
        // Readable by JavaScript on purpose: the FE copies it into the header (decision #12)
        return CookieCsrfTokenRepository.withHttpOnlyFalse();
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }
}

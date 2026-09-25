package pt.cofinpro.prayingmantis.common;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns every error into RFC 9457 Problem Details (decision #21). The base class already does this
 * for Spring MVC's own exceptions (404, 405, unreadable JSON, ...). Feature stories add handlers for
 * their domain exceptions here, explicitly: the catch-all below would otherwise win over
 * {@code @ResponseStatus} on an exception class.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** Invalid request body: 400 with the failing fields in an {@code errors} extension (contract: Problem). */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var result = ex.getBindingResult();
        // Class-level (cross-field) constraints have no field; they are reported under the object name
        List<Map<String, String>> errors = Stream.concat(
                        result.getFieldErrors().stream()
                                .map(error -> fieldError(error.getField(), error.getDefaultMessage())),
                        result.getGlobalErrors().stream()
                                .map(error -> fieldError(error.getObjectName(), error.getDefaultMessage())))
                .toList();
        return handleExceptionInternal(ex, withErrors(ex.getBody(), errors), headers, status, request);
    }

    /** Invalid query or path parameter: same 400 shape, keyed by parameter name. */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errors = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> fieldError(
                                result.getMethodParameter().getParameterName(), error.getDefaultMessage())))
                .toList();
        return handleExceptionInternal(ex, withErrors(ex.getBody(), errors), headers, status, request);
    }

    /** Wrong email or password. One message for both, so it doesn't reveal whether an account exists. */
    @ExceptionHandler(BadCredentialsException.class)
    ProblemDetail handleBadCredentials(BadCredentialsException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Invalid email or password");
    }

    /** The user lookup itself failed (e.g. the DB is down), wrapped by DaoAuthenticationProvider: a real 500. */
    @ExceptionHandler(AuthenticationServiceException.class)
    ProblemDetail handleAuthenticationBackendFailure(AuthenticationServiceException ex) {
        log.error("Authentication backend failed", ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");
    }

    /** Not logged in, or the session expired; the filter chain's entry point sends this here. */
    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail handleUnauthenticated(AuthenticationException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Not logged in");
    }

    /** Missing or wrong X-XSRF-TOKEN header on an unsafe request (decision #12). */
    @ExceptionHandler(CsrfException.class)
    ProblemDetail handleCsrf(CsrfException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Missing or invalid CSRF token");
    }

    /** Thrown by method security inside a controller or service; without this the catch-all makes it a 500. */
    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        return ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
    }

    /** Anything unexpected: 500 without internals in the body; the stack trace goes to the log. */
    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");
    }

    private static ProblemDetail withErrors(ProblemDetail problem, List<Map<String, String>> errors) {
        problem.setDetail("Request has invalid fields");
        problem.setProperty("errors", errors);
        return problem;
    }

    private static Map<String, String> fieldError(String field, String message) {
        return Map.of("field", String.valueOf(field), "message", String.valueOf(message));
    }
}

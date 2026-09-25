package pt.cofinpro.prayingmantis.common;

import java.net.URI;

/**
 * A valid request that breaks a business rule: 409 (T-3.1). {@code problemType} names the rule, and the
 * Problem's {@code type} becomes {@code /problems/<problemType>} so the FE can pick its message without
 * parsing {@code detail}. The message becomes {@code detail}, so keep it free of internals.
 */
public class ConflictException extends RuntimeException {

    private final String problemType;

    public ConflictException(String problemType, String detail) {
        super(detail);
        this.problemType = problemType;
    }

    public URI getType() {
        return URI.create("/problems/" + problemType);
    }
}

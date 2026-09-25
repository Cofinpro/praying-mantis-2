package pt.cofinpro.prayingmantis.common;

/**
 * A request that Bean Validation can't reject on its own, e.g. a range whose end is before its start.
 * ApiExceptionHandler answers 400 in the same shape as a validation error, with this field in {@code errors}.
 */
public class InvalidFieldException extends RuntimeException {

    private final String field;

    public InvalidFieldException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}

package pt.cofinpro.prayingmantis.common;

/**
 * 404. Also for something that exists but isn't the caller's, so ids of other people's data aren't
 * confirmed (T-3.1). The message becomes {@code detail}.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String detail) {
        super(detail);
    }
}

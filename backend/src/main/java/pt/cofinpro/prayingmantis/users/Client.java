package pt.cofinpro.prayingmantis.users;

/**
 * The client a user works for. Stored by name (decision #8); the DB check lists the same values.
 * Kept separate from the generated {@code api.model.Client}, so the domain doesn't depend on the API layer.
 */
public enum Client {
    DKB,
    DEKA,
    VV,
    DBIS,
    UNION
}

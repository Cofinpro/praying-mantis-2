package pt.cofinpro.prayingmantis.users;

/** A user as the app shell sees them, including the derived team-lead flag (decision #9). */
public record UserProfile(
        Long id, String name, String email, Client client, Level level, boolean admin, boolean teamLead) {
}

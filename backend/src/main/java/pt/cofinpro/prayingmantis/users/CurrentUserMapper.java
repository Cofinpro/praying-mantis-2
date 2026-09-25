package pt.cofinpro.prayingmantis.users;

import pt.cofinpro.prayingmantis.api.model.CurrentUser;

/** Domain profile to the contract's CurrentUser. The enums share their names with the API enums. */
public final class CurrentUserMapper {

    private CurrentUserMapper() {
    }

    public static CurrentUser toApi(UserProfile profile) {
        return new CurrentUser(
                profile.id(),
                profile.name(),
                profile.email(),
                pt.cofinpro.prayingmantis.api.model.Client.valueOf(profile.client().name()),
                pt.cofinpro.prayingmantis.api.model.Level.valueOf(profile.level().name()),
                profile.admin(),
                profile.teamLead());
    }
}

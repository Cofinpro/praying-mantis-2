package pt.cofinpro.prayingmantis.users;

import org.springframework.web.bind.annotation.RestController;
import pt.cofinpro.prayingmantis.api.MeApi;
import pt.cofinpro.prayingmantis.api.model.CurrentUser;
import pt.cofinpro.prayingmantis.auth.AuthenticatedUsers;

@RestController
public class MeController implements MeApi {

    private final UserService userService;

    public MeController(UserService userService) {
        this.userService = userService;
    }

    @Override
    public CurrentUser getMe() {
        return CurrentUserMapper.toApi(userService.profile(AuthenticatedUsers.current().getId()));
    }
}

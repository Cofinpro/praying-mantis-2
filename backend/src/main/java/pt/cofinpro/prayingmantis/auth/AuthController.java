package pt.cofinpro.prayingmantis.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.RestController;
import pt.cofinpro.prayingmantis.api.AuthApi;
import pt.cofinpro.prayingmantis.api.model.CurrentUser;
import pt.cofinpro.prayingmantis.api.model.LoginRequest;
import pt.cofinpro.prayingmantis.users.CurrentUserMapper;
import pt.cofinpro.prayingmantis.users.UserService;

@RestController
public class AuthController implements AuthApi {

    private final SessionLogin sessionLogin;
    private final UserService userService;
    // Request-scoped proxies: the generated interface methods can't take them as parameters
    private final HttpServletRequest request;
    private final HttpServletResponse response;

    public AuthController(
            SessionLogin sessionLogin, UserService userService, HttpServletRequest request, HttpServletResponse response) {
        this.sessionLogin = sessionLogin;
        this.userService = userService;
        this.request = request;
        this.response = response;
    }

    @Override
    public CurrentUser login(LoginRequest loginRequest) {
        AuthenticatedUser user = sessionLogin.login(loginRequest.getEmail(), loginRequest.getPassword(), request, response);
        return CurrentUserMapper.toApi(userService.profile(user.getId()));
    }

    @Override
    public void logout() {
        sessionLogin.logout(request, response);
    }
}

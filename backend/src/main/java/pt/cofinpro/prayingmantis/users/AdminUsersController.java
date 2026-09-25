package pt.cofinpro.prayingmantis.users;

import java.util.List;
import org.springframework.web.bind.annotation.RestController;
import pt.cofinpro.prayingmantis.api.AdminUsersApi;
import pt.cofinpro.prayingmantis.api.model.AdminUser;
import pt.cofinpro.prayingmantis.api.model.AdminUserUpdate;
import pt.cofinpro.prayingmantis.api.model.NewAdminUser;
import pt.cofinpro.prayingmantis.api.model.PasswordReset;
import pt.cofinpro.prayingmantis.api.model.UserRef;

/** Admin, users (T-9.1). The admin check is in AdminUserService, against the DB (decision #35). */
@RestController
public class AdminUsersController implements AdminUsersApi {

    private final AdminUserService adminUsers;

    public AdminUsersController(AdminUserService adminUsers) {
        this.adminUsers = adminUsers;
    }

    @Override
    public List<AdminUser> getAdminUsers() {
        return adminUsers.all().stream().map(AdminUsersController::toApi).toList();
    }

    @Override
    public AdminUser createAdminUser(NewAdminUser body) {
        return toApi(adminUsers.create(new AdminUserService.UserInput(
                body.getName(), body.getEmail(), Client.valueOf(body.getClient().name()), Level.valueOf(body.getLevel().name()),
                body.getIsAdmin(), body.getTeamLeadId()), body.getPassword()));
    }

    @Override
    public AdminUser updateAdminUser(Long id, AdminUserUpdate body) {
        return toApi(adminUsers.update(id, new AdminUserService.UserInput(
                body.getName(), body.getEmail(), Client.valueOf(body.getClient().name()), Level.valueOf(body.getLevel().name()),
                body.getIsAdmin(), body.getTeamLeadId())));
    }

    @Override
    public void setAdminUserPassword(Long id, PasswordReset body) {
        adminUsers.setPassword(id, body.getPassword());
    }

    private static AdminUser toApi(AdminUserService.Listed listed) {
        User u = listed.user();
        return new AdminUser(u.getId(), u.getName(), u.getEmail(),
                        pt.cofinpro.prayingmantis.api.model.Client.valueOf(u.getClient().name()),
                        pt.cofinpro.prayingmantis.api.model.Level.valueOf(u.getLevel().name()),
                        u.isAdmin(), listed.teamLead())
                .teamLead(u.getTeamLead() == null ? null : new UserRef(u.getTeamLead().getId(), u.getTeamLead().getName()));
    }
}

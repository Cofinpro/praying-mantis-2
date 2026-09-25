package pt.cofinpro.prayingmantis.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import pt.cofinpro.prayingmantis.TestcontainersConfiguration;
import pt.cofinpro.prayingmantis.users.User;
import pt.cofinpro.prayingmantis.users.UserRepository;

/**
 * Against the dev seed: Alex is the only admin; Ana leads Bruno, Carla and Diogo; Bruno leads Eva,
 * Filipe and Hugo; Gabriela and Ana have no team lead.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class PermissionsTest {

    @Autowired
    Permissions permissions;

    @Autowired
    UserRepository users;

    @AfterEach
    void logOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void isAdminOnlyForAdmins() {
        loginAs("alex.admin@cofinpro.pt");
        assertThat(permissions.isAdmin()).isTrue();

        loginAs("ana.silva@cofinpro.pt");
        assertThat(permissions.isAdmin()).isFalse();
    }

    @Test
    void isAdminReadsTheCurrentFlagNotTheOneFromLogin() {
        loginAs("carla.mendes@cofinpro.pt");
        user("carla.mendes@cofinpro.pt").setAdmin(true);

        assertThat(permissions.isAdmin()).isTrue();
    }

    @Test
    void isTeamLeadOfOnlyTheDirectReports() {
        loginAs("ana.silva@cofinpro.pt");

        assertThat(permissions.isTeamLeadOf(id("carla.mendes@cofinpro.pt"))).isTrue();
        assertThat(permissions.isTeamLeadOf(id("bruno.costa@cofinpro.pt"))).isTrue();
        // Eva reports to Bruno, who reports to Ana: the lead's lead is not the team lead
        assertThat(permissions.isTeamLeadOf(id("eva.santos@cofinpro.pt"))).isFalse();
        assertThat(permissions.isTeamLeadOf(id("gabriela.lopes@cofinpro.pt"))).isFalse();
        assertThat(permissions.isTeamLeadOf(id("ana.silva@cofinpro.pt"))).isFalse();
    }

    @Test
    void isTeamLeadOfAnUnknownUserIsFalse() {
        loginAs("ana.silva@cofinpro.pt");

        assertThat(permissions.isTeamLeadOf(999_999L)).isFalse();
    }

    @Test
    void requireAdminThrowsAccessDeniedForOthers() {
        loginAs("ana.silva@cofinpro.pt");
        assertThatThrownBy(() -> permissions.requireAdmin()).isInstanceOf(AccessDeniedException.class);

        loginAs("alex.admin@cofinpro.pt");
        assertThatCode(() -> permissions.requireAdmin()).doesNotThrowAnyException();
    }

    @Test
    void requireTeamLeadOfThrowsAccessDeniedForOthers() {
        loginAs("bruno.costa@cofinpro.pt");
        assertThatCode(() -> permissions.requireTeamLeadOf(id("eva.santos@cofinpro.pt"))).doesNotThrowAnyException();
        assertThatThrownBy(() -> permissions.requireTeamLeadOf(id("carla.mendes@cofinpro.pt")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void checksWithoutALoggedInUserAreUnauthenticated() {
        assertThatThrownBy(() -> permissions.isAdmin())
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    void approverIsTheTeamLead() {
        assertThat(approverOf("carla.mendes@cofinpro.pt")).isEqualTo("ana.silva@cofinpro.pt");
        assertThat(approverOf("eva.santos@cofinpro.pt")).isEqualTo("bruno.costa@cofinpro.pt");
    }

    @Test
    void aTeamLeadsOwnApproverIsTheirTeamLead() {
        assertThat(approverOf("bruno.costa@cofinpro.pt")).isEqualTo("ana.silva@cofinpro.pt");
    }

    @Test
    void withoutATeamLeadTheApproverIsAnAdmin() {
        assertThat(approverOf("gabriela.lopes@cofinpro.pt")).isEqualTo("alex.admin@cofinpro.pt");
        assertThat(approverOf("ana.silva@cofinpro.pt")).isEqualTo("alex.admin@cofinpro.pt");
    }

    @Test
    void anAdminNeverApprovesTheirOwnRequests() {
        // Alex is the only admin and has no team lead
        assertThat(permissions.approverFor(user("alex.admin@cofinpro.pt"))).isEmpty();

        User second = user("diogo.pereira@cofinpro.pt");
        second.setAdmin(true);
        second.setTeamLead(null);
        users.flush();
        assertThat(approverOf("alex.admin@cofinpro.pt")).isEqualTo("diogo.pereira@cofinpro.pt");
        assertThat(approverOf("diogo.pereira@cofinpro.pt")).isEqualTo("alex.admin@cofinpro.pt");
    }

    private String approverOf(String email) {
        return permissions.approverFor(user(email)).orElseThrow().getEmail();
    }

    private void loginAs(String email) {
        var principal = new AuthenticatedUser(user(email));
        principal.eraseCredentials();
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }

    private User user(String email) {
        return users.findByEmail(email).orElseThrow();
    }

    private Long id(String email) {
        return user(email).getId();
    }
}

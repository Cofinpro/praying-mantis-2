package pt.cofinpro.prayingmantis.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import pt.cofinpro.prayingmantis.TestcontainersConfiguration;
import pt.cofinpro.prayingmantis.users.User;
import pt.cofinpro.prayingmantis.users.UserRepository;

/**
 * No test transaction here, unlike PermissionsTest: with open-in-view off, a caller outside any
 * transaction must still get an approver whose fields can be read (no LazyInitializationException).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PermissionsOutsideTransactionTest {

    @Autowired
    Permissions permissions;

    @Autowired
    UserRepository users;

    @Test
    void theApproverIsFullyLoaded() {
        Long carla = users.findByEmail("carla.mendes@cofinpro.pt").orElseThrow().getId();

        User approver = permissions.approverFor(carla).orElseThrow();

        assertThat(approver.getName()).isEqualTo("Ana Silva");
        assertThat(approver.getEmail()).isEqualTo("ana.silva@cofinpro.pt");
    }
}

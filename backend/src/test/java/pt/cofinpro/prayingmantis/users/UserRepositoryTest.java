package pt.cofinpro.prayingmantis.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import pt.cofinpro.prayingmantis.TestcontainersConfiguration;

/** Against real Postgres with the Liquibase schema and the dev seed (decision #10). Each test rolls back. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class UserRepositoryTest {

    @Autowired
    UserRepository users;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void devSeedHasAnAdminTwoTeamLeadsAndEmployees() {
        assertThat(users.count()).isEqualTo(9);
        assertThat(users.findByEmail("alex.admin@cofinpro.pt")).get().extracting(User::isAdmin).isEqualTo(true);

        User ana = users.findByEmail("ana.silva@cofinpro.pt").orElseThrow();
        User bruno = users.findByEmail("bruno.costa@cofinpro.pt").orElseThrow();
        User carla = users.findByEmail("carla.mendes@cofinpro.pt").orElseThrow();
        assertThat(users.existsByTeamLeadId(ana.getId())).isTrue();
        assertThat(users.existsByTeamLeadId(bruno.getId())).isTrue();
        assertThat(users.existsByTeamLeadId(carla.getId())).isFalse();
        assertThat(bruno.getTeamLead().getId()).isEqualTo(ana.getId());
    }

    @Test
    void savesAUserWithEnumsAsNamesAndANormalizedEmail() {
        User saved = users.saveAndFlush(
                new User("Test Person", "  Test.Person@Cofinpro.PT ", "hash", Client.VV, Level.SENIOR_ARCHITECT));

        assertThat(saved.getEmail()).isEqualTo("test.person@cofinpro.pt");
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(jdbc.queryForObject("select client || '/' || level from users where id = ?", String.class, saved.getId()))
                .isEqualTo("VV/SENIOR_ARCHITECT");
        assertThat(users.findByEmail(User.normalizeEmail("TEST.PERSON@cofinpro.pt"))).isPresent();
    }

    @Test
    void rejectsADuplicateEmail() {
        assertThatThrownBy(() -> users.saveAndFlush(
                        new User("Copy", "ana.silva@cofinpro.pt", "hash", Client.DKB, Level.JUNIOR)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsAnEmailThatIsNotLowerCase() {
        assertThatThrownBy(() -> jdbc.update(
                        "insert into users (name, email, password_hash, client, level) values ('X', 'Upper@cofinpro.pt', 'h', 'DKB', 'JUNIOR')"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_users_email_lowercase");
    }

    @Test
    void rejectsAnUnknownClient() {
        assertThatThrownBy(() -> jdbc.update(
                        "insert into users (name, email, password_hash, client, level) values ('X', 'x@cofinpro.pt', 'h', 'Deka', 'JUNIOR')"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_users_client");
    }

    @Test
    void rejectsBeingYourOwnTeamLead() {
        User carla = users.findByEmail("carla.mendes@cofinpro.pt").orElseThrow();
        carla.setTeamLead(carla);

        assertThatThrownBy(() -> users.flush())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_users_not_own_team_lead");
    }
}

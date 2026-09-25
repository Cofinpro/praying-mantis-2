package pt.cofinpro.prayingmantis.projects;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pt.cofinpro.prayingmantis.TestcontainersConfiguration;
import pt.cofinpro.prayingmantis.users.Client;

/** GET /api/projects and the projects table (BE-6.1), against the dev seed: 8 active projects, 1 inactive. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class ProjectsIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ProjectRepository projects;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    @WithUserDetails("eva.santos@cofinpro.pt")
    void activeProjectsByCodeByDefault() throws Exception {
        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(8)))
                .andExpect(jsonPath("$[*].code", contains("DBIS-PORTAL", "DEKA-RISK", "DKB-APP", "DKB-CORE",
                        "INTERNAL", "TRAINING", "UNION-FUNDS", "VV-MIGRATION")))
                .andExpect(jsonPath("$[3].name").value("DKB core banking"))
                .andExpect(jsonPath("$[3].client").value("DKB"))
                .andExpect(jsonPath("$[3].isBillable").value(true))
                .andExpect(jsonPath("$[3].isActive").value(true));
    }

    @Test
    @WithUserDetails("eva.santos@cofinpro.pt")
    void internalProjectsHaveNoClient() throws Exception {
        mockMvc.perform(get("/api/projects"))
                .andExpect(jsonPath("$[4].code").value("INTERNAL"))
                .andExpect(jsonPath("$[4].client").doesNotExist())
                .andExpect(jsonPath("$[4].isBillable").value(false));
    }

    @Test
    @WithUserDetails("eva.santos@cofinpro.pt")
    void inactiveProjectsOnlyWhenAskedFor() throws Exception {
        mockMvc.perform(get("/api/projects").param("active", "false"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].code").value("DKB-LEGACY"))
                .andExpect(jsonPath("$[0].isActive").value(false));
    }

    @Test
    @WithUserDetails("eva.santos@cofinpro.pt")
    void aNonBooleanActiveIsABadRequest() throws Exception {
        mockMvc.perform(get("/api/projects").param("active", "maybe")).andExpect(status().isBadRequest());
    }

    @Test
    void projectsNeedALogin() throws Exception {
        mockMvc.perform(get("/api/projects")).andExpect(status().isUnauthorized());
    }

    @Test
    void codesAreUnique() {
        assertThatThrownBy(() -> projects.saveAndFlush(new Project("DKB-CORE", "Copy", Client.DKB, true, true)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_projects_code");
    }

    @Test
    void theClientMustBeKnown() {
        assertThatThrownBy(() -> jdbc.update(
                        "insert into projects (code, name, client, is_billable) values ('X', 'X', 'Dkb', true)"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_projects_client");
    }
}

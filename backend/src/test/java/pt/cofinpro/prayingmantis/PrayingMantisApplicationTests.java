package pt.cofinpro.prayingmantis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PrayingMantisApplicationTests {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void liquibaseRunsTheBaselineChangesetOnStart() {
        Integer applied = jdbc.queryForObject(
                "select count(*) from databasechangelog where id = '0001-baseline'", Integer.class);

        assertThat(applied).isEqualTo(1);
    }

    @Test
    void healthIsPublicAndUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void errorsOnPublicRoutesKeepTheirStatus() throws Exception {
        mockMvc.perform(get("/actuator/health/unknown"))
                .andExpect(status().isNotFound());
    }

    @Test
    void otherRoutesRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/anything"))
                .andExpect(status().isUnauthorized());
    }
}

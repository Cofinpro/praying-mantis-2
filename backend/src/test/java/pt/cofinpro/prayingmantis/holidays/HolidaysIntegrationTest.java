package pt.cofinpro.prayingmantis.holidays;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;
import pt.cofinpro.prayingmantis.TestcontainersConfiguration;
import pt.cofinpro.prayingmantis.config.TimeConfig;

/** GET /api/public-holidays end to end (BE-2.3), against the seeded 2026 and 2027 holidays. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class HolidaysIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    @WithUserDetails("eva.santos@cofinpro.pt")
    void aYearsHolidaysInDateOrder() throws Exception {
        mockMvc.perform(get("/api/public-holidays").param("year", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(13)))
                .andExpect(jsonPath("$[0].date").value("2026-01-01"))
                .andExpect(jsonPath("$[0].name").value("New Year's Day"))
                .andExpect(jsonPath("$[8].date").value("2026-10-05"))
                .andExpect(jsonPath("$[8].name").value("Republic Day"))
                .andExpect(jsonPath("$[12].date").value("2026-12-25"));
    }

    @Test
    @WithUserDetails("eva.santos@cofinpro.pt")
    void withoutAYearItsTheCurrentYearInLisbon() throws Exception {
        String year = String.valueOf(LocalDate.now(TimeConfig.ZONE).getYear());

        mockMvc.perform(get("/api/public-holidays"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].date", everyItem(startsWith(year))));
    }

    @Test
    @WithUserDetails("eva.santos@cofinpro.pt")
    void aYearWithoutHolidaysIsAnEmptyList() throws Exception {
        mockMvc.perform(get("/api/public-holidays").param("year", "2030"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @WithUserDetails("eva.santos@cofinpro.pt")
    void aYearOutOfRangeIsAValidationError() throws Exception {
        mockMvc.perform(get("/api/public-holidays").param("year", "2101"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void holidaysNeedALogin() throws Exception {
        mockMvc.perform(get("/api/public-holidays")).andExpect(status().isUnauthorized());
    }
}

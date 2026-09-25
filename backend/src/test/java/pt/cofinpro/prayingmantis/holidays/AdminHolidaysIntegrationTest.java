package pt.cofinpro.prayingmantis.holidays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import pt.cofinpro.prayingmantis.TestcontainersConfiguration;
import pt.cofinpro.prayingmantis.XsrfToken;

/** /api/admin/public-holidays end to end (BE-9.4), against the seeded 2026 and 2027 holidays. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AdminHolidaysIntegrationTest {

    private static final String ALEX = "alex.admin@cofinpro.pt";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    PublicHolidayRepository holidays;

    @Test
    @WithUserDetails(ALEX)
    void aYearsHolidaysWithTheirIds() throws Exception {
        mockMvc.perform(get("/api/admin/public-holidays").param("year", "2027"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(13)))
                .andExpect(jsonPath("$[0].id").isNumber())
                .andExpect(jsonPath("$[0].date").value("2027-01-01"));
    }

    @Test
    @WithUserDetails(ALEX)
    void addingNextYearsHolidays() throws Exception {
        send(post("/api/admin/public-holidays"), holiday("2028-04-25", " Freedom Day "))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Freedom Day"));

        // Everyone sees it in the calendar
        mockMvc.perform(get("/api/public-holidays").param("year", "2028")).andExpect(jsonPath("$[0].date").value("2028-04-25"));
    }

    @Test
    @WithUserDetails(ALEX)
    void aDateThatIsAlreadyAHolidayIsAConflict() throws Exception {
        send(post("/api/admin/public-holidays"), holiday("2027-12-25", "Christmas again"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("/problems/holiday-date-taken"))
                .andExpect(jsonPath("$.detail").value("2027-12-25 is already a public holiday"));
    }

    @Test
    @WithUserDetails(ALEX)
    void editingAHoliday() throws Exception {
        Long id = id(LocalDate.of(2027, 6, 10));

        send(put("/api/admin/public-holidays/{id}", id), holiday("2027-06-10", "Dia de Portugal"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Dia de Portugal"));
        send(put("/api/admin/public-holidays/{id}", id), holiday("2027-12-25", "Clash"))
                .andExpect(status().isConflict());
    }

    @Test
    @WithUserDetails(ALEX)
    void deletingAHoliday() throws Exception {
        Long id = id(LocalDate.of(2027, 6, 10));

        mockMvc.perform(delete("/api/admin/public-holidays/{id}", id).with(XsrfToken.from(mockMvc))).andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/admin/public-holidays/{id}", id).with(XsrfToken.from(mockMvc))).andExpect(status().isNotFound());
        assertThat(holidays.findById(id)).isEmpty();
    }

    @Test
    @WithUserDetails(ALEX)
    void aMissingNameIsAFieldError() throws Exception {
        send(post("/api/admin/public-holidays"), "{\"date\": \"2028-01-01\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    @Test
    @WithUserDetails("bruno.costa@cofinpro.pt")
    void onlyAdminsGetIn() throws Exception {
        mockMvc.perform(get("/api/admin/public-holidays").param("year", "2027")).andExpect(status().isForbidden());
        send(post("/api/admin/public-holidays"), holiday("2028-01-01", "New Year's Day")).andExpect(status().isForbidden());
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String json) throws Exception {
        return mockMvc.perform(request.with(XsrfToken.from(mockMvc)).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String holiday(String date, String name) {
        return "{\"date\": \"%s\", \"name\": \"%s\"}".formatted(date, name);
    }

    private Long id(LocalDate date) {
        return holidays.findByDateBetweenOrderByDate(date, date).get(0).getId();
    }
}

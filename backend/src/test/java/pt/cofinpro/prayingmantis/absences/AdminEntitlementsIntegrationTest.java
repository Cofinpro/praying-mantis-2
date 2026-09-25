package pt.cofinpro.prayingmantis.absences;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.transaction.annotation.Transactional;
import pt.cofinpro.prayingmantis.TestcontainersConfiguration;
import pt.cofinpro.prayingmantis.XsrfToken;
import pt.cofinpro.prayingmantis.users.UserRepository;

/** /api/admin/entitlements end to end (BE-9.2). The dev seed gives everyone 22 vacation days this year. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AdminEntitlementsIntegrationTest {

    private static final String ALEX = "alex.admin@cofinpro.pt";
    private static final int YEAR = LocalDate.now().getYear();

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository users;

    @Autowired
    AbsenceEntitlementRepository entitlements;

    @Test
    @WithUserDetails(ALEX)
    void theYearsEntitlementsByUserName() throws Exception {
        mockMvc.perform(get("/api/admin/entitlements").param("year", String.valueOf(YEAR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(9)))
                .andExpect(jsonPath("$[0].user.name").value("Alex Admin"))
                .andExpect(jsonPath("$[0].type").value("VACATION"))
                .andExpect(jsonPath("$[0].entitledDays").value(22.0))
                .andExpect(jsonPath("$[?(@.user.name == 'Carla Mendes')].carriedOverDays").value(2.5));
    }

    @Test
    @WithUserDetails(ALEX)
    void saveCreatesAnEntitlementForANewYear() throws Exception {
        save(diogo(), "VACATION", YEAR + 1, "23", "1.5")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.user.name").value("Diogo Pereira"))
                .andExpect(jsonPath("$.year").value(YEAR + 1))
                .andExpect(jsonPath("$.entitledDays").value(23))
                .andExpect(jsonPath("$.carriedOverDays").value(1.5));
    }

    @Test
    @WithUserDetails(ALEX)
    void saveReplacesTheDaysOfAnExistingOne() throws Exception {
        long before = entitlements.count();

        save(diogo(), "VACATION", YEAR, "25", "0").andExpect(status().isOk()).andExpect(jsonPath("$.entitledDays").value(25));

        assertThat(entitlements.count()).isEqualTo(before);
    }

    @Test
    @WithUserDetails(ALEX)
    void daysComeInHalfDays() throws Exception {
        save(diogo(), "VACATION", YEAR + 1, "22.3", "0")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("entitledDays"));
        save(diogo(), "VACATION", YEAR + 1, "-1", "0").andExpect(status().isBadRequest());
    }

    @Test
    @WithUserDetails(ALEX)
    void anUnknownUserIsAFieldError() throws Exception {
        save(999_999L, "VACATION", YEAR + 1, "22", "0")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("userId"));
    }

    @Test
    @WithUserDetails(ALEX)
    void deletingAnEntitlement() throws Exception {
        Long id = entitlements.findWithTypeByUserIdAndYear(diogo(), YEAR).get(0).getId();

        mockMvc.perform(delete("/api/admin/entitlements/{id}", id).with(XsrfToken.from(mockMvc))).andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/admin/entitlements/{id}", id).with(XsrfToken.from(mockMvc))).andExpect(status().isNotFound());
        assertThat(entitlements.findById(id)).isEmpty();
    }

    @Test
    @WithUserDetails("ana.silva@cofinpro.pt")
    void onlyAdminsGetIn() throws Exception {
        mockMvc.perform(get("/api/admin/entitlements").param("year", String.valueOf(YEAR))).andExpect(status().isForbidden());
        save(diogo(), "VACATION", YEAR + 1, "22", "0").andExpect(status().isForbidden());
    }

    @Test
    void entitlementsNeedALogin() throws Exception {
        mockMvc.perform(get("/api/admin/entitlements").param("year", String.valueOf(YEAR))).andExpect(status().isUnauthorized());
    }

    private ResultActions save(Long userId, String type, int year, String entitled, String carried) throws Exception {
        return mockMvc.perform(put("/api/admin/entitlements").with(XsrfToken.from(mockMvc)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\": %d, \"type\": \"%s\", \"year\": %d, \"entitledDays\": %s, \"carriedOverDays\": %s}"
                        .formatted(userId, type, year, entitled, carried)));
    }

    private Long diogo() {
        return users.findByEmail("diogo.pereira@cofinpro.pt").orElseThrow().getId();
    }
}

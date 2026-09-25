package pt.cofinpro.prayingmantis.projects;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

/** /api/admin/projects end to end (BE-9.3), against the dev seed's 9 projects. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AdminProjectsIntegrationTest {

    private static final String ALEX = "alex.admin@cofinpro.pt";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ProjectRepository projects;

    @Test
    @WithUserDetails(ALEX)
    void theListHasActiveAndInactiveProjects() throws Exception {
        mockMvc.perform(get("/api/admin/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(9)))
                .andExpect(jsonPath("$[?(@.code == 'DKB-LEGACY')].isActive").value(false));
    }

    @Test
    @WithUserDetails(ALEX)
    void creatingAProjectStoresTheCodeInUpperCase() throws Exception {
        send(post("/api/admin/projects"), project("deka-funds", "Deka funds", "\"DEKA\"", true, true))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.code").value("DEKA-FUNDS"))
                .andExpect(jsonPath("$.client").value("DEKA"));

        // It's bookable straight away
        mockMvc.perform(get("/api/projects")).andExpect(jsonPath("$[?(@.code == 'DEKA-FUNDS')]").isNotEmpty());
    }

    @Test
    @WithUserDetails(ALEX)
    void anInternalProjectHasNoClient() throws Exception {
        send(post("/api/admin/projects"), project("PRESALES", "Presales", null, false, true))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.client").doesNotExist())
                .andExpect(jsonPath("$.isBillable").value(false));
    }

    @Test
    @WithUserDetails(ALEX)
    void aCodeInUseIsAConflictWhateverTheCase() throws Exception {
        send(post("/api/admin/projects"), project("dkb-core", "Copy", "\"DKB\"", true, true))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("/problems/project-code-taken"))
                .andExpect(jsonPath("$.detail").value("Another project already has the code DKB-CORE"));
    }

    @Test
    @WithUserDetails(ALEX)
    void aBadCodeIsAFieldError() throws Exception {
        send(post("/api/admin/projects"), project("DKB CORE!", "Bad", null, true, true))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("code"));
    }

    @Test
    @WithUserDetails(ALEX)
    void deactivatingAProjectHidesItFromBooking() throws Exception {
        Long id = id("TRAINING");

        send(put("/api/admin/projects/{id}", id), project("TRAINING", "Training", null, false, false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(false));
        mockMvc.perform(get("/api/projects")).andExpect(jsonPath("$[?(@.code == 'TRAINING')]").isEmpty());
    }

    @Test
    @WithUserDetails(ALEX)
    void renamingToAnotherProjectsCodeIsAConflict() throws Exception {
        send(put("/api/admin/projects/{id}", id("TRAINING")), project("INTERNAL", "Training", null, false, true))
                .andExpect(status().isConflict());
        // Keeping its own code is fine
        send(put("/api/admin/projects/{id}", id("TRAINING")), project("TRAINING", "Trainings", null, false, true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Trainings"));
    }

    @Test
    @WithUserDetails(ALEX)
    void editingAnUnknownProjectIsNotFound() throws Exception {
        send(put("/api/admin/projects/{id}", 999_999L), project("X1", "X", null, true, true)).andExpect(status().isNotFound());
    }

    @Test
    @WithUserDetails("eva.santos@cofinpro.pt")
    void onlyAdminsGetIn() throws Exception {
        mockMvc.perform(get("/api/admin/projects")).andExpect(status().isForbidden());
        send(post("/api/admin/projects"), project("X1", "X", null, true, true)).andExpect(status().isForbidden());
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String json) throws Exception {
        return mockMvc.perform(request.with(XsrfToken.from(mockMvc)).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String project(String code, String name, String clientJson, boolean billable, boolean active) {
        return "{\"code\": \"%s\", \"name\": \"%s\"%s, \"isBillable\": %s, \"isActive\": %s}"
                .formatted(code, name, clientJson == null ? "" : ", \"client\": " + clientJson, billable, active);
    }

    private Long id(String code) {
        return projects.findAllByOrderByCode().stream().filter(p -> p.getCode().equals(code)).findFirst().orElseThrow().getId();
    }
}

package pt.cofinpro.prayingmantis.users;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.transaction.annotation.Transactional;
import pt.cofinpro.prayingmantis.TestcontainersConfiguration;
import pt.cofinpro.prayingmantis.XsrfToken;

/** /api/admin/users end to end (BE-9.1), as Alex, the only admin in the seed. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AdminUsersIntegrationTest {

    private static final String ALEX = "alex.admin@cofinpro.pt";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository users;

    @Test
    @WithUserDetails(ALEX)
    void theListHasEveryoneByNameWithTeamLeads() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(9)))
                .andExpect(jsonPath("$[0].name").value("Alex Admin"))
                .andExpect(jsonPath("$[0].isAdmin").value(true))
                .andExpect(jsonPath("$[0].teamLead").doesNotExist())
                .andExpect(jsonPath("$[?(@.name == 'Bruno Costa')].teamLead.name").value("Ana Silva"))
                .andExpect(jsonPath("$[?(@.name == 'Bruno Costa')].isTeamLead").value(true))
                .andExpect(jsonPath("$[?(@.name == 'Eva Santos')].isTeamLead").value(false));
    }

    @Test
    @WithUserDetails("ana.silva@cofinpro.pt")
    void onlyAdminsGetIn() throws Exception {
        mockMvc.perform(get("/api/admin/users")).andExpect(status().isForbidden());
        send(post("/api/admin/users"), newUser("x@cofinpro.pt", null)).andExpect(status().isForbidden());
    }

    @Test
    @WithUserDetails(ALEX)
    void aNewUserCanLogInWithTheInitialPassword() throws Exception {
        Long ana = id("ana.silva@cofinpro.pt");

        send(post("/api/admin/users"), newUser("Nuno.Faria@Cofinpro.PT", ana))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("nuno.faria@cofinpro.pt"))
                .andExpect(jsonPath("$.teamLead.name").value("Ana Silva"))
                .andExpect(jsonPath("$.isTeamLead").value(false));

        login("nuno.faria@cofinpro.pt", "welcome-2026").andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Nuno Faria"));
    }

    @Test
    @WithUserDetails(ALEX)
    void anEmailInUseIsAConflict() throws Exception {
        send(post("/api/admin/users"), newUser("Eva.Santos@cofinpro.pt", null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("/problems/email-taken"));
    }

    @Test
    @WithUserDetails(ALEX)
    void anUnknownTeamLeadIsAFieldError() throws Exception {
        send(post("/api/admin/users"), newUser("x@cofinpro.pt", 999_999L))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("teamLeadId"));
    }

    @Test
    @WithUserDetails(ALEX)
    void aShortPasswordIsAFieldError() throws Exception {
        send(post("/api/admin/users"), newUser("x@cofinpro.pt", null).replace("welcome-2026", "short"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("password"));
    }

    @Test
    @WithUserDetails(ALEX)
    void editingAUser() throws Exception {
        Long eva = id("eva.santos@cofinpro.pt");
        Long ana = id("ana.silva@cofinpro.pt");

        send(put("/api/admin/users/{id}", eva), update("Eva Santos-Lima", "eva.lima@cofinpro.pt", "DKB", "SENIOR", false, ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Eva Santos-Lima"))
                .andExpect(jsonPath("$.email").value("eva.lima@cofinpro.pt"))
                .andExpect(jsonPath("$.client").value("DKB"))
                .andExpect(jsonPath("$.level").value("SENIOR"))
                .andExpect(jsonPath("$.teamLead.name").value("Ana Silva"));
    }

    @Test
    @WithUserDetails(ALEX)
    void beingYourOwnTeamLeadIsAFieldError() throws Exception {
        Long eva = id("eva.santos@cofinpro.pt");

        send(put("/api/admin/users/{id}", eva), update("Eva Santos", "eva.santos@cofinpro.pt", "VV", "EXPERT", false, eva))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("teamLeadId"));
    }

    @Test
    @WithUserDetails(ALEX)
    void aTeamLeadCycleIsAConflict() throws Exception {
        // Ana leads Bruno, Bruno leads Eva: Ana can't get Eva as her team lead
        Long ana = id("ana.silva@cofinpro.pt");
        Long eva = id("eva.santos@cofinpro.pt");

        send(put("/api/admin/users/{id}", ana), update("Ana Silva", "ana.silva@cofinpro.pt", "DKB", "SENIOR_ARCHITECT", false, eva))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("/problems/team-lead-cycle"))
                .andExpect(jsonPath("$.detail").value("Ana Silva already leads Eva Santos, directly or through others"));
    }

    @Test
    @WithUserDetails(ALEX)
    void theOnlyAdminKeepsTheFlag() throws Exception {
        Long alex = id(ALEX);

        send(put("/api/admin/users/{id}", alex), update("Alex Admin", ALEX, "DBIS", "ARCHITECT", false, null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("/problems/last-admin"));

        // With a second admin, it's fine
        users.findByEmail("ana.silva@cofinpro.pt").orElseThrow().setAdmin(true);
        users.flush();
        send(put("/api/admin/users/{id}", alex), update("Alex Admin", ALEX, "DBIS", "ARCHITECT", false, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isAdmin").value(false));
    }

    @Test
    @WithUserDetails(ALEX)
    void editingAnUnknownUserIsNotFound() throws Exception {
        send(put("/api/admin/users/{id}", 999_999L), update("X", "x@cofinpro.pt", "DKB", "JUNIOR", false, null))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithUserDetails(ALEX)
    void settingAPasswordReplacesTheOldOne() throws Exception {
        Long diogo = id("diogo.pereira@cofinpro.pt");

        send(post("/api/admin/users/{id}/password", diogo), "{\"password\": \"new-secret-1\"}").andExpect(status().isNoContent());

        login("diogo.pereira@cofinpro.pt", "password").andExpect(status().isUnauthorized());
        login("diogo.pereira@cofinpro.pt", "new-secret-1").andExpect(status().isOk());
    }

    @Test
    @WithUserDetails(ALEX)
    void writesNeedTheCsrfToken() throws Exception {
        mockMvc.perform(post("/api/admin/users").contentType(MediaType.APPLICATION_JSON).content(newUser("x@cofinpro.pt", null)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminNeedsALogin() throws Exception {
        mockMvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
    }

    private ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, String json)
            throws Exception {
        return mockMvc.perform(request.with(XsrfToken.from(mockMvc)).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions login(String email, String password) throws Exception {
        return send(post("/api/auth/login"), "{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, password));
    }

    private static String newUser(String email, Long teamLeadId) {
        return """
                {"name": "Nuno Faria", "email": "%s", "client": "DEKA", "level": "JUNIOR", "isAdmin": false,
                 "password": "welcome-2026"%s}""".formatted(email, teamLeadId == null ? "" : ", \"teamLeadId\": " + teamLeadId);
    }

    private static String update(String name, String email, String client, String level, boolean admin, Long teamLeadId) {
        return """
                {"name": "%s", "email": "%s", "client": "%s", "level": "%s", "isAdmin": %s%s}"""
                .formatted(name, email, client, level, admin, teamLeadId == null ? "" : ", \"teamLeadId\": " + teamLeadId);
    }

    private Long id(String email) {
        return users.findByEmail(email).orElseThrow().getId();
    }
}

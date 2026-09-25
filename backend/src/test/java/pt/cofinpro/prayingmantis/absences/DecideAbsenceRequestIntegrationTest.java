package pt.cofinpro.prayingmantis.absences;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import pt.cofinpro.prayingmantis.TestcontainersConfiguration;
import pt.cofinpro.prayingmantis.XsrfToken;
import pt.cofinpro.prayingmantis.users.User;
import pt.cofinpro.prayingmantis.users.UserRepository;

/**
 * POST /api/team/absence-requests/{id}/approve and /reject end to end (BE-5.2). Carla's seeded pending
 * vacation (3 days, approver Ana) is the request most tests decide on.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class DecideAbsenceRequestIntegrationTest {

    private static final String ANA = "ana.silva@cofinpro.pt";
    private static final int YEAR = LocalDate.now().getYear();

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AbsenceTypeRepository types;

    @Autowired
    AbsenceEntitlementRepository entitlements;

    @Autowired
    AbsenceRequestRepository requests;

    @Autowired
    UserRepository users;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    @WithUserDetails(ANA)
    void theApproverApprovesAndTheDaysCountAsUsed() throws Exception {
        Long id = carlasPending();

        decide(id, "approve", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.decidedAt").isString())
                .andExpect(jsonPath("$.decisionComment").doesNotExist());

        // 22 + 2.5 − (5.5 + 3): Carla's balance counts it now
        assertThat(jdbc.queryForObject("""
                select sum(working_days) from absence_requests r join users u on u.id = r.user_id
                join absence_types t on t.id = r.absence_type_id
                where u.email = 'carla.mendes@cofinpro.pt' and t.code = 'VACATION' and r.status = 'APPROVED'""",
                BigDecimal.class)).isEqualByComparingTo("8.5");

        // Carla is told, with a link to the request in her calendar (T-4.1)
        assertThat(carlasNotifications()).singleElement().satisfies(n -> {
            assertThat(n).containsEntry("type", "ABSENCE_APPROVED").containsEntry("link", "/absences?request=" + id);
            assertThat((String) n.get("message")).startsWith("Your vacation (").endsWith(") was approved");
        });
    }

    @Test
    @WithUserDetails(ANA)
    void anApprovalCanHaveAComment() throws Exception {
        decide(carlasPending(), "approve", "{\"comment\": \"  Enjoy!  \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decisionComment").value("Enjoy!"));
    }

    @Test
    @WithUserDetails(ANA)
    void theApproverRejectsWithAComment() throws Exception {
        Long id = carlasPending();

        decide(id, "reject", "{\"comment\": \"Release week\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.decisionComment").value("Release week"))
                .andExpect(jsonPath("$.decidedAt").isString());
        assertThat(carlasNotifications()).singleElement().satisfies(n -> {
            assertThat(n).containsEntry("type", "ABSENCE_REJECTED").containsEntry("link", "/absences?request=" + id);
            assertThat((String) n.get("message")).startsWith("Your vacation (").endsWith(") was rejected: Release week");
        });

        // A decided request leaves the pending list
        mockMvc.perform(get("/api/team/absence-requests")).andExpect(jsonPath("$[?(@.request.id == " + id + ")]").isEmpty());
    }

    @Test
    @WithUserDetails(ANA)
    void rejectingNeedsAComment() throws Exception {
        Long id = carlasPending();

        decide(id, "reject", "{\"comment\": \"   \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("comment"));
        decide(id, "reject", "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("comment"));
        // No body at all: the body is required for reject
        decide(id, "reject", "")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    @WithUserDetails(ANA)
    void aCommentOver500CharactersIsAFieldError() throws Exception {
        decide(carlasPending(), "reject", "{\"comment\": \"" + "x".repeat(501) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("comment"));
    }

    @Test
    @WithUserDetails("alex.admin@cofinpro.pt")
    void anAdminMayDecideARequestTheyArentTheApproverOf() throws Exception {
        decide(carlasPending(), "approve", null).andExpect(status().isOk());
    }

    @Test
    @WithUserDetails("bruno.costa@cofinpro.pt")
    void anotherTeamLeadMayNotDecide() throws Exception {
        Long id = carlasPending();

        decide(id, "approve", null)
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
        assertThat(requests.findById(id)).get().extracting(AbsenceRequest::getStatus).isEqualTo(AbsenceStatus.PENDING);
    }

    @Test
    @WithUserDetails("carla.mendes@cofinpro.pt")
    void nobodyApprovesTheirOwnRequest() throws Exception {
        decide(carlasPending(), "approve", null).andExpect(status().isForbidden());
    }

    @Test
    @WithUserDetails("alex.admin@cofinpro.pt")
    void notEvenAnAdminApprovesTheirOwnRequest() throws Exception {
        User alex = users.findByEmail("alex.admin@cofinpro.pt").orElseThrow();
        User ana = users.findByEmail(ANA).orElseThrow();
        LocalDate day = LocalDate.of(YEAR + 1, 2, 1).with(TemporalAdjusters.firstInMonth(DayOfWeek.MONDAY));
        Long id = requests.saveAndFlush(new AbsenceRequest(alex, types.findByCode(AbsenceTypeCode.TRAINING).orElseThrow(),
                day, DayPart.FULL, day, DayPart.FULL, BigDecimal.ONE, AbsenceStatus.PENDING, ana)).getId();

        decide(id, "approve", null).andExpect(status().isForbidden());
    }

    @Test
    @WithUserDetails(ANA)
    void onlyPendingRequestsCanBeDecided() throws Exception {
        Long rejected = jdbc.queryForObject("""
                select r.id from absence_requests r join users u on u.id = r.user_id
                where u.email = 'carla.mendes@cofinpro.pt' and r.status = 'REJECTED'""", Long.class);

        decide(rejected, "approve", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("/problems/absence-not-pending"))
                .andExpect(jsonPath("$.detail").value("Only pending requests can be decided; this one is rejected"));
        decide(rejected, "reject", "{\"comment\": \"again\"}").andExpect(status().isConflict());
    }

    @Test
    @WithUserDetails(ANA)
    void approvingChecksTheBalanceAgain() throws Exception {
        User carla = users.findByEmail("carla.mendes@cofinpro.pt").orElseThrow();
        AbsenceType vacation = types.findByCode(AbsenceTypeCode.VACATION).orElseThrow();
        entitlements.saveAndFlush(new AbsenceEntitlement(carla, vacation, YEAR + 1, new BigDecimal("2"), BigDecimal.ZERO));
        // Mon–Wed of the first week of February next year: 3 days, only 2 left
        LocalDate monday = LocalDate.of(YEAR + 1, 2, 1).with(TemporalAdjusters.firstInMonth(DayOfWeek.MONDAY));
        Long id = requests.saveAndFlush(new AbsenceRequest(carla, vacation, monday, DayPart.FULL, monday.plusDays(2),
                DayPart.FULL, new BigDecimal("3"), AbsenceStatus.PENDING, carla.getTeamLead())).getId();

        decide(id, "approve", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("/problems/insufficient-balance"))
                .andExpect(jsonPath("$.detail").value("Only 2 vacation days left in " + (YEAR + 1) + ", but the request needs 3"));
        // Rejecting it is still fine
        decide(id, "reject", "{\"comment\": \"Not enough days left\"}").andExpect(status().isOk());
    }

    @Test
    @WithUserDetails(ANA)
    void anUnknownRequestIsNotFound() throws Exception {
        decide(Long.MAX_VALUE, "approve", null).andExpect(status().isNotFound());
    }

    @Test
    @WithUserDetails(ANA)
    void withoutTheCsrfTokenItsForbidden() throws Exception {
        mockMvc.perform(post("/api/team/absence-requests/{id}/approve", carlasPending()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Missing or invalid CSRF token"));
    }

    @Test
    void decidingNeedsALogin() throws Exception {
        decide(1L, "approve", null).andExpect(status().isUnauthorized());
    }

    private ResultActions decide(Long id, String action, String body) throws Exception {
        var request = post("/api/team/absence-requests/{id}/" + action, id).with(XsrfToken.from(mockMvc));
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request);
    }

    private java.util.List<java.util.Map<String, Object>> carlasNotifications() {
        return jdbc.queryForList("""
                select n.type, n.message, n.link from notifications n join users u on u.id = n.user_id
                where u.email = 'carla.mendes@cofinpro.pt' order by n.id""");
    }

    private Long carlasPending() {
        return jdbc.queryForObject("""
                select r.id from absence_requests r join users u on u.id = r.user_id
                where u.email = 'carla.mendes@cofinpro.pt' and r.status = 'PENDING'""", Long.class);
    }
}

package pt.cofinpro.prayingmantis.timesheets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import pt.cofinpro.prayingmantis.users.User;
import pt.cofinpro.prayingmantis.users.UserRepository;

/** POST /api/team/timesheets/{id}/approve and /reject end to end (BE-7.2). Eva's week goes to Bruno. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class DecideTimesheetIntegrationTest {

    private static final String BRUNO = "bruno.costa@cofinpro.pt";
    private static final LocalDate MON = LocalDate.of(2026, 10, 12);

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TimesheetRepository timesheets;

    @Autowired
    UserRepository users;

    @Autowired
    org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Test
    @WithUserDetails(BRUNO)
    void theApproverApproves() throws Exception {
        Long id = submitted("eva.santos@cofinpro.pt");

        decide(id, "approve", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.decidedAt").isString())
                .andExpect(jsonPath("$.decisionComment").doesNotExist());
        assertThat(evasNotifications()).singleElement().satisfies(n -> assertThat(n)
                .containsEntry("type", "TIMESHEET_APPROVED")
                .containsEntry("message", "Your week of 12 Oct was approved")
                .containsEntry("link", "/timesheets?week=2026-10-12"));
    }

    @Test
    @WithUserDetails(BRUNO)
    void theApproverRejectsWithACommentAndTheUserCanFixIt() throws Exception {
        Long id = submitted("eva.santos@cofinpro.pt");

        decide(id, "reject", "{\"comment\": \" Thursday is missing \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.decisionComment").value("Thursday is missing"));
        assertThat(timesheets.findById(id)).get().extracting(Timesheet::getStatus).isEqualTo(TimesheetStatus.REJECTED);
        assertThat(evasNotifications()).singleElement().satisfies(n -> assertThat(n)
                .containsEntry("type", "TIMESHEET_REJECTED")
                .containsEntry("message", "Your week of 12 Oct was rejected: Thursday is missing"));
    }

    @Test
    @WithUserDetails(BRUNO)
    void rejectingNeedsAComment() throws Exception {
        Long id = submitted("eva.santos@cofinpro.pt");

        decide(id, "reject", "{\"comment\": \"\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("comment"));
        decide(id, "reject", "{}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("comment"));
        decide(id, "reject", "").andExpect(status().isBadRequest());
    }

    @Test
    @WithUserDetails("alex.admin@cofinpro.pt")
    void anAdminMayDecideAWeekTheyArentTheApproverOf() throws Exception {
        decide(submitted("eva.santos@cofinpro.pt"), "approve", null).andExpect(status().isOk());
    }

    @Test
    @WithUserDetails("ana.silva@cofinpro.pt")
    void anotherTeamLeadMayNotDecide() throws Exception {
        decide(submitted("eva.santos@cofinpro.pt"), "approve", null).andExpect(status().isForbidden());
    }

    @Test
    @WithUserDetails("eva.santos@cofinpro.pt")
    void nobodyApprovesTheirOwnWeek() throws Exception {
        decide(submitted("eva.santos@cofinpro.pt"), "approve", null).andExpect(status().isForbidden());
    }

    @Test
    @WithUserDetails(BRUNO)
    void onlySubmittedWeeksCanBeDecided() throws Exception {
        Long id = submitted("eva.santos@cofinpro.pt");
        decide(id, "approve", null).andExpect(status().isOk());

        decide(id, "reject", "{\"comment\": \"too late\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("/problems/timesheet-not-submitted"))
                .andExpect(jsonPath("$.detail").value("Only submitted timesheets can be decided; this one is approved"));
    }

    @Test
    @WithUserDetails(BRUNO)
    void anUnknownWeekIsNotFound() throws Exception {
        decide(Long.MAX_VALUE, "approve", null).andExpect(status().isNotFound()).andExpect(jsonPath("$.detail").value("Timesheet not found"));
    }

    @Test
    @WithUserDetails(BRUNO)
    void withoutTheCsrfTokenItsForbidden() throws Exception {
        mockMvc.perform(post("/api/team/timesheets/{id}/approve", submitted("eva.santos@cofinpro.pt")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Missing or invalid CSRF token"));
    }

    @Test
    void decidingNeedsALogin() throws Exception {
        decide(1L, "approve", null).andExpect(status().isUnauthorized());
    }

    private ResultActions decide(Long id, String action, String body) throws Exception {
        var request = post("/api/team/timesheets/{id}/" + action, id).with(XsrfToken.from(mockMvc));
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request);
    }

    private java.util.List<java.util.Map<String, Object>> evasNotifications() {
        return jdbc.queryForList("""
                select n.type, n.message, n.link from notifications n join users u on u.id = n.user_id
                where u.email = 'eva.santos@cofinpro.pt' order by n.id""");
    }

    /** A week submitted to the user's team lead, stored directly: submitting itself is tested in BE-6.4. */
    private Long submitted(String email) {
        User user = users.findByEmail(email).orElseThrow();
        Timesheet week = new Timesheet(user, MON);
        week.setStatus(TimesheetStatus.SUBMITTED);
        week.setApprover(user.getTeamLead());
        return timesheets.saveAndFlush(week).getId();
    }
}

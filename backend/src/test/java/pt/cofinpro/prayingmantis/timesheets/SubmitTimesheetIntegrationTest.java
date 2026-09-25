package pt.cofinpro.prayingmantis.timesheets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
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
import pt.cofinpro.prayingmantis.projects.ProjectRepository;
import pt.cofinpro.prayingmantis.users.User;
import pt.cofinpro.prayingmantis.users.UserRepository;

/** POST /api/me/timesheets/{weekStart}/submit end to end (BE-6.4). Eva's team lead is Bruno. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class SubmitTimesheetIntegrationTest {

    private static final String EVA = "eva.santos@cofinpro.pt";
    private static final LocalDate MON = LocalDate.of(2026, 10, 12);

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TimesheetRepository timesheets;

    @Autowired
    UserRepository users;

    @Autowired
    ProjectRepository projects;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    @WithUserDetails(EVA)
    void submittingASavedDraftSetsTheApprover() throws Exception {
        mockMvc.perform(put("/api/me/timesheets/{week}/entries", MON).with(XsrfToken.from(mockMvc))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entries\": [{\"projectId\": %d, \"workDate\": \"2026-10-12\", \"hours\": 8}]}".formatted(internalId())))
                .andExpect(status().isOk());

        submit(MON)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.approver.name").value("Bruno Costa"))
                .andExpect(jsonPath("$.submittedAt").isString())
                .andExpect(jsonPath("$.totalHours").value(8.0));

        // Bruno is told (T-4.1)
        assertThat(jdbc.queryForList("""
                select n.type, n.message, n.link from notifications n join users u on u.id = n.user_id
                where u.email = 'bruno.costa@cofinpro.pt'"""))
                .singleElement()
                .satisfies(n -> assertThat(n)
                        .containsEntry("type", "TIMESHEET_SUBMITTED")
                        .containsEntry("message", "Eva Santos submitted the week of 12 Oct")
                        .containsEntry("link", "/approvals?tab=timesheets"));
    }

    @Test
    @WithUserDetails(EVA)
    void aNeverSavedWeekWithoutHoursCanBeSubmitted() throws Exception {
        // E.g. a week spent on vacation (decision #32)
        submit(MON)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.entries").isEmpty());
    }

    @Test
    @WithUserDetails(EVA)
    void aSubmittedWeekIsLockedForEditsAndResubmits() throws Exception {
        submit(MON).andExpect(status().isOk());

        submit(MON)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("/problems/timesheet-not-editable"));
        mockMvc.perform(put("/api/me/timesheets/{week}/entries", MON).with(XsrfToken.from(mockMvc))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"entries\": []}"))
                .andExpect(status().isConflict());
    }

    @Test
    @WithUserDetails(EVA)
    void anApprovedWeekCantBeSubmittedAgain() throws Exception {
        Timesheet week = timesheets.saveAndFlush(new Timesheet(eva(), MON));
        week.setStatus(TimesheetStatus.APPROVED);
        timesheets.flush();

        submit(MON).andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value("This week is approved and can't be changed"));
    }

    @Test
    @WithUserDetails(EVA)
    void resubmittingARejectedWeekClearsTheOldDecision() throws Exception {
        Timesheet week = timesheets.saveAndFlush(new Timesheet(eva(), MON));
        week.setStatus(TimesheetStatus.REJECTED);
        week.setDecidedAt(Instant.parse("2026-10-19T09:00:00Z"));
        week.setDecisionComment("Thursday is missing");
        timesheets.flush();

        submit(MON)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.decidedAt").doesNotExist())
                .andExpect(jsonPath("$.decisionComment").doesNotExist());
    }

    @Test
    @WithUserDetails("bruno.costa@cofinpro.pt")
    void aTeamLeadsWeekGoesToTheirOwnLead() throws Exception {
        submit(MON).andExpect(jsonPath("$.approver.name").value("Ana Silva"));
    }

    @Test
    @WithUserDetails("gabriela.lopes@cofinpro.pt")
    void withoutATeamLeadItGoesToAnAdmin() throws Exception {
        submit(MON).andExpect(jsonPath("$.approver.name").value("Alex Admin"));
    }

    @Test
    @WithUserDetails("alex.admin@cofinpro.pt")
    void theSoleAdminWithoutATeamLeadHasNoApproverAndNothingIsStored() throws Exception {
        submit(MON)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("/problems/no-approver"));

        User alex = users.findByEmail("alex.admin@cofinpro.pt").orElseThrow();
        assertThat(timesheets.findWeek(alex.getId(), MON)).isEmpty();
    }

    @Test
    @WithUserDetails(EVA)
    void aWeekMustStartOnMonday() throws Exception {
        submit(MON.plusDays(2)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("weekStart"));
    }

    @Test
    @WithUserDetails(EVA)
    void withoutTheCsrfTokenItsForbidden() throws Exception {
        mockMvc.perform(post("/api/me/timesheets/{week}/submit", MON)).andExpect(status().isForbidden());
    }

    @Test
    void submittingNeedsALogin() throws Exception {
        submit(MON).andExpect(status().isUnauthorized());
    }

    private ResultActions submit(LocalDate week) throws Exception {
        return mockMvc.perform(post("/api/me/timesheets/{week}/submit", week).with(XsrfToken.from(mockMvc)));
    }

    private User eva() {
        return users.findByEmail(EVA).orElseThrow();
    }

    private long internalId() {
        return projects.findByActiveOrderByCode(true).stream().filter(p -> p.getCode().equals("INTERNAL")).findFirst().orElseThrow().getId();
    }
}

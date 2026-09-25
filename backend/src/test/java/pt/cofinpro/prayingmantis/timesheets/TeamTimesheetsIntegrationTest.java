package pt.cofinpro.prayingmantis.timesheets;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pt.cofinpro.prayingmantis.TestcontainersConfiguration;
import pt.cofinpro.prayingmantis.projects.Project;
import pt.cofinpro.prayingmantis.projects.ProjectRepository;
import pt.cofinpro.prayingmantis.users.User;
import pt.cofinpro.prayingmantis.users.UserRepository;

/** GET /api/team/timesheets end to end (BE-7.1). Bruno leads Eva, Filipe and Hugo. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class TeamTimesheetsIntegrationTest {

    private static final String BRUNO = "bruno.costa@cofinpro.pt";
    private static final LocalDate MON = LocalDate.of(2026, 10, 12);

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TimesheetRepository timesheets;

    @Autowired
    TimeEntryRepository entries;

    @Autowired
    ProjectRepository projects;

    @Autowired
    UserRepository users;

    @Test
    @WithUserDetails(BRUNO)
    void aTeamLeadSeesSubmittedWeeksWithHoursPerProject() throws Exception {
        Timesheet week = submitted("eva.santos@cofinpro.pt", MON);
        entry(week, "INTERNAL", 0, "2");
        entry(week, "DKB-CORE", 0, "6");
        entry(week, "DKB-CORE", 1, "8");

        mockMvc.perform(get("/api/team/timesheets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].user.name").value("Eva Santos"))
                .andExpect(jsonPath("$[0].timesheet.status").value("SUBMITTED"))
                .andExpect(jsonPath("$[0].timesheet.weekStart").value("2026-10-12"))
                .andExpect(jsonPath("$[0].timesheet.totalHours").value(16.0))
                .andExpect(jsonPath("$[0].timesheet.entries", hasSize(3)))
                .andExpect(jsonPath("$[0].timesheet.approver.name").value("Bruno Costa"))
                .andExpect(jsonPath("$[0].projectHours[*].project.code", contains("DKB-CORE", "INTERNAL")))
                .andExpect(jsonPath("$[0].projectHours[0].hours").value(14.0))
                .andExpect(jsonPath("$[0].projectHours[1].hours").value(2.0));
    }

    @Test
    @WithUserDetails(BRUNO)
    void submittedWeeksComeOldestFirst() throws Exception {
        submitted("eva.santos@cofinpro.pt", MON);
        submitted("filipe.rocha@cofinpro.pt", MON.minusWeeks(1));
        submitted("hugo.marques@cofinpro.pt", MON.plusWeeks(1));

        mockMvc.perform(get("/api/team/timesheets"))
                .andExpect(jsonPath("$[*].timesheet.weekStart", contains("2026-10-05", "2026-10-12", "2026-10-19")));
    }

    @Test
    @WithUserDetails(BRUNO)
    void decidedWeeksComeNewestFirstAndOnlyInTheirStatus() throws Exception {
        submitted("eva.santos@cofinpro.pt", MON);
        submitted("eva.santos@cofinpro.pt", MON.minusWeeks(2)).setStatus(TimesheetStatus.APPROVED);
        submitted("eva.santos@cofinpro.pt", MON.minusWeeks(1)).setStatus(TimesheetStatus.APPROVED);
        timesheets.flush();

        mockMvc.perform(get("/api/team/timesheets").param("status", "APPROVED"))
                .andExpect(jsonPath("$[*].timesheet.weekStart", contains("2026-10-05", "2026-09-28")));
    }

    @Test
    @WithUserDetails("ana.silva@cofinpro.pt")
    void aLeadDoesntSeeTheWeeksOfAnotherLeadsTeam() throws Exception {
        submitted("eva.santos@cofinpro.pt", MON);

        mockMvc.perform(get("/api/team/timesheets")).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @WithUserDetails("eva.santos@cofinpro.pt")
    void someoneWhoApprovesNobodyGetsAnEmptyList() throws Exception {
        mockMvc.perform(get("/api/team/timesheets")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @WithUserDetails(BRUNO)
    void anUnknownStatusIsABadRequest() throws Exception {
        mockMvc.perform(get("/api/team/timesheets").param("status", "PENDING")).andExpect(status().isBadRequest());
    }

    @Test
    void theListNeedsALogin() throws Exception {
        mockMvc.perform(get("/api/team/timesheets")).andExpect(status().isUnauthorized());
    }

    /** A week submitted to the user's team lead, stored directly: submitting itself is tested in BE-6.4. */
    private Timesheet submitted(String email, LocalDate weekStart) {
        User user = users.findByEmail(email).orElseThrow();
        Timesheet week = new Timesheet(user, weekStart);
        week.setStatus(TimesheetStatus.SUBMITTED);
        week.setApprover(user.getTeamLead());
        return timesheets.saveAndFlush(week);
    }

    private void entry(Timesheet week, String projectCode, int day, String hours) {
        Project project = projects.findByActiveOrderByCode(true).stream()
                .filter(p -> p.getCode().equals(projectCode)).findFirst().orElseThrow();
        entries.saveAndFlush(new TimeEntry(week, project, week.getWeekStart().plusDays(day), new BigDecimal(hours), null));
    }
}

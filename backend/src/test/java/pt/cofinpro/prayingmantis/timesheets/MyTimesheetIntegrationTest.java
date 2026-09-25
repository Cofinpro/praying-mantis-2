package pt.cofinpro.prayingmantis.timesheets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pt.cofinpro.prayingmantis.TestcontainersConfiguration;
import pt.cofinpro.prayingmantis.projects.ProjectRepository;
import pt.cofinpro.prayingmantis.users.User;
import pt.cofinpro.prayingmantis.users.UserRepository;

/** GET /api/me/timesheets/{weekStart} end to end (BE-6.2), and the timesheet tables' constraints. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class MyTimesheetIntegrationTest {

    private static final String EVA = "eva.santos@cofinpro.pt";
    private static final LocalDate MON = LocalDate.of(2026, 10, 5);

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
    @WithUserDetails(EVA)
    void aNewWeekIsAnUnsavedEmptyDraft() throws Exception {
        mockMvc.perform(get("/api/me/timesheets/{week}", MON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.weekStart").value("2026-10-05"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.entries", hasSize(0)))
                .andExpect(jsonPath("$.totalHours").value(0))
                .andExpect(jsonPath("$.approver").doesNotExist());

        // Opening a week stores nothing (decision #32)
        assertThat(timesheets.count()).isZero();
    }

    @Test
    @WithUserDetails(EVA)
    void theWeeksHolidaysComeAlong() throws Exception {
        // Mon 5 Oct 2026 is Republic Day
        mockMvc.perform(get("/api/me/timesheets/{week}", MON))
                .andExpect(jsonPath("$.holidays", hasSize(1)))
                .andExpect(jsonPath("$.holidays[0].date").value("2026-10-05"))
                .andExpect(jsonPath("$.holidays[0].name").value("Republic Day"));
    }

    @Test
    @WithUserDetails(EVA)
    void aSavedWeekHasItsEntriesByProjectCodeThenDay() throws Exception {
        Timesheet week = timesheets.saveAndFlush(new Timesheet(eva(), MON));
        entry(week, "INTERNAL", MON.plusDays(1), "2");
        entry(week, "DKB-CORE", MON.plusDays(2), "8");
        entry(week, "DKB-CORE", MON.plusDays(1), "6", "Sprint planning");

        mockMvc.perform(get("/api/me/timesheets/{week}", MON))
                .andExpect(jsonPath("$.id").value(week.getId()))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.totalHours").value(16.0))
                .andExpect(jsonPath("$.entries[*].project.code", contains("DKB-CORE", "DKB-CORE", "INTERNAL")))
                .andExpect(jsonPath("$.entries[*].workDate", contains("2026-10-06", "2026-10-07", "2026-10-06")))
                .andExpect(jsonPath("$.entries[0].hours").value(6.0))
                .andExpect(jsonPath("$.entries[0].description").value("Sprint planning"))
                .andExpect(jsonPath("$.entries[0].project.name").value("DKB core banking"))
                .andExpect(jsonPath("$.entries[0].id").isNumber());
    }

    @Test
    @WithUserDetails("carla.mendes@cofinpro.pt")
    void onlyApprovedAbsencesOfTheWeekAreShown() throws Exception {
        int year = LocalDate.now().getYear();
        // Carla's seeded week: approved vacation Mon–Fri of the week of 15 Feb; the rejected one in July isn't shown
        LocalDate feb = LocalDate.of(year, 2, 15).with(DayOfWeek.MONDAY);
        LocalDate jul = LocalDate.of(year, 7, 16).with(DayOfWeek.MONDAY);

        mockMvc.perform(get("/api/me/timesheets/{week}", feb))
                .andExpect(jsonPath("$.absences", hasSize(1)))
                .andExpect(jsonPath("$.absences[0].status").value("APPROVED"))
                .andExpect(jsonPath("$.absences[0].workingDays").value(5.0))
                .andExpect(jsonPath("$.absences[0].approver.name").value("Ana Silva"));
        mockMvc.perform(get("/api/me/timesheets/{week}", jul)).andExpect(jsonPath("$.absences", hasSize(0)));
    }

    @Test
    @WithUserDetails("filipe.rocha@cofinpro.pt")
    void someoneElsesWeekIsntSeen() throws Exception {
        Timesheet evas = timesheets.saveAndFlush(new Timesheet(eva(), MON));
        entry(evas, "INTERNAL", MON.plusDays(1), "8");

        mockMvc.perform(get("/api/me/timesheets/{week}", MON))
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.entries", hasSize(0)));
    }

    @Test
    @WithUserDetails(EVA)
    void aWeekMustStartOnMonday() throws Exception {
        mockMvc.perform(get("/api/me/timesheets/{week}", MON.plusDays(1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("weekStart"))
                .andExpect(jsonPath("$.errors[0].message").value("must be a Monday"));
    }

    @Test
    @WithUserDetails(EVA)
    void aDateThatIsntIsoIsABadRequest() throws Exception {
        mockMvc.perform(get("/api/me/timesheets/{week}", "05-10-2026")).andExpect(status().isBadRequest());
    }

    @Test
    void timesheetsNeedALogin() throws Exception {
        mockMvc.perform(get("/api/me/timesheets/{week}", MON)).andExpect(status().isUnauthorized());
    }

    @Test
    void oneTimesheetPerUserAndWeek() {
        timesheets.saveAndFlush(new Timesheet(eva(), MON));

        assertThatThrownBy(() -> timesheets.saveAndFlush(new Timesheet(eva(), MON)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_timesheets_user_week");
    }

    @Test
    void theDbRejectsAWeekThatDoesntStartOnMonday() {
        assertThatThrownBy(() -> timesheets.saveAndFlush(new Timesheet(eva(), MON.plusDays(2))))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_timesheets_week_start_monday");
    }

    @Test
    void oneEntryPerCell() {
        Timesheet week = timesheets.saveAndFlush(new Timesheet(eva(), MON));
        entry(week, "INTERNAL", MON.plusDays(1), "2");

        assertThatThrownBy(() -> entry(week, "INTERNAL", MON.plusDays(1), "3"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_time_entries_cell");
    }

    @Test
    void hoursComeInQuarters() {
        Timesheet week = timesheets.saveAndFlush(new Timesheet(eva(), MON));

        assertThatThrownBy(() -> entry(week, "INTERNAL", MON.plusDays(1), "1.1"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_time_entries_hours");
    }

    private User eva() {
        return users.findByEmail(EVA).orElseThrow();
    }

    private void entry(Timesheet week, String projectCode, LocalDate day, String hours) {
        entry(week, projectCode, day, hours, null);
    }

    private void entry(Timesheet week, String projectCode, LocalDate day, String hours, String description) {
        var project = projects.findByActiveOrderByCode(true).stream().filter(p -> p.getCode().equals(projectCode)).findFirst().orElseThrow();
        entries.saveAndFlush(new TimeEntry(week, project, day, new BigDecimal(hours), description));
    }
}

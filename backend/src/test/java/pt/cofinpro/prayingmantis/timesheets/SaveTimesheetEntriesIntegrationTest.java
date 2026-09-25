package pt.cofinpro.prayingmantis.timesheets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
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
import pt.cofinpro.prayingmantis.projects.Project;
import pt.cofinpro.prayingmantis.projects.ProjectRepository;
import pt.cofinpro.prayingmantis.users.UserRepository;

/** PUT /api/me/timesheets/{weekStart}/entries end to end (BE-6.3). The week is Mon 12 Oct 2026, without holidays. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class SaveTimesheetEntriesIntegrationTest {

    private static final String EVA = "eva.santos@cofinpro.pt";
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
    @WithUserDetails(EVA)
    void theFirstSaveCreatesTheTimesheet() throws Exception {
        save(entry("DKB-CORE", 0, "8", "Sprint planning"), entry("INTERNAL", 0, "0.5", null), entry("DKB-CORE", 1, "7.75", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.totalHours").value(16.25))
                .andExpect(jsonPath("$.entries[*].project.code", contains("DKB-CORE", "DKB-CORE", "INTERNAL")))
                .andExpect(jsonPath("$.entries[0].description").value("Sprint planning"));

        assertThat(timesheets.findWeek(users.findByEmail(EVA).orElseThrow().getId(), MON)).isPresent();
    }

    @Test
    @WithUserDetails(EVA)
    void savingReplacesTheWholeWeek() throws Exception {
        save(entry("DKB-CORE", 0, "8", null), entry("INTERNAL", 1, "2", null)).andExpect(status().isOk());

        // The same cell again with other hours, and INTERNAL left out: it's deleted
        save(entry("DKB-CORE", 0, "6", "changed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries", hasSize(1)))
                .andExpect(jsonPath("$.entries[0].hours").value(6.0))
                .andExpect(jsonPath("$.totalHours").value(6.0));
        assertThat(entries.count()).isEqualTo(1);
    }

    @Test
    @WithUserDetails(EVA)
    void anEmptyListClearsTheWeek() throws Exception {
        save(entry("DKB-CORE", 0, "8", null)).andExpect(status().isOk());

        save().andExpect(status().isOk()).andExpect(jsonPath("$.entries", hasSize(0))).andExpect(jsonPath("$.id").isNumber());
    }

    @Test
    @WithUserDetails(EVA)
    void aDayOutsideTheWeekIsAFieldError() throws Exception {
        save(entry("DKB-CORE", 0, "8", null), entry("DKB-CORE", 7, "8", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("entries[1].workDate"))
                .andExpect(jsonPath("$.errors[0].message").value("must be inside the week of 2026-10-12"));
    }

    @Test
    @WithUserDetails(EVA)
    void anUnknownProjectIsAFieldError() throws Exception {
        saveBody("{\"entries\": [{\"projectId\": 999999, \"workDate\": \"2026-10-12\", \"hours\": 8}]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("entries[0].projectId"))
                .andExpect(jsonPath("$.errors[0].message").value("no such project"));
    }

    @Test
    @WithUserDetails(EVA)
    void newHoursNeedAnActiveProject() throws Exception {
        save(entry("DKB-LEGACY", 0, "8", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value("project DKB-LEGACY is inactive"));
    }

    @Test
    @WithUserDetails(EVA)
    void anInactiveProjectAlreadyOnTheWeekMayStay() throws Exception {
        Project legacy = project("DKB-LEGACY");
        legacy.setActive(true);
        save(entry("DKB-LEGACY", 0, "8", null)).andExpect(status().isOk());
        legacy.setActive(false);
        projects.flush();

        save(entry("DKB-LEGACY", 0, "4", null), entry("DKB-LEGACY", 1, "4", null)).andExpect(status().isOk());
    }

    @Test
    @WithUserDetails(EVA)
    void hoursComeInQuarters() throws Exception {
        save(entry("DKB-CORE", 0, "7.3", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("entries[0].hours"))
                .andExpect(jsonPath("$.errors[0].message").value("must be in quarter hours"));
    }

    @Test
    @WithUserDetails(EVA)
    void hoursMustBeMoreThanZeroAndAtMost24() throws Exception {
        save(entry("DKB-CORE", 0, "0", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("entries[0].hours"));
        save(entry("DKB-CORE", 0, "24.25", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("entries[0].hours"));
    }

    @Test
    @WithUserDetails(EVA)
    void oneEntryPerProjectAndDay() throws Exception {
        save(entry("DKB-CORE", 0, "4", null), entry("DKB-CORE", 0, "4", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("entries[1].workDate"));
    }

    @Test
    @WithUserDetails(EVA)
    void atMost24HoursADay() throws Exception {
        save(entry("DKB-CORE", 0, "20", null), entry("INTERNAL", 0, "4.25", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("entries"))
                .andExpect(jsonPath("$.errors[0].message").value("more than 24 hours on 2026-10-12"));
    }

    @Test
    @WithUserDetails(EVA)
    void aDescriptionOver500CharactersIsAFieldError() throws Exception {
        save(entry("DKB-CORE", 0, "8", "x".repeat(501)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("entries[0].description"));
    }

    @Test
    @WithUserDetails(EVA)
    void aSubmittedOrApprovedWeekCantBeChanged() throws Exception {
        save(entry("DKB-CORE", 0, "8", null)).andExpect(status().isOk());
        Timesheet week = timesheets.findWeek(users.findByEmail(EVA).orElseThrow().getId(), MON).orElseThrow();
        for (TimesheetStatus locked : List.of(TimesheetStatus.SUBMITTED, TimesheetStatus.APPROVED)) {
            week.setStatus(locked);
            timesheets.flush();

            save(entry("DKB-CORE", 0, "1", null))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.type").value("/problems/timesheet-not-editable"))
                    .andExpect(jsonPath("$.detail").value("This week is " + locked.name().toLowerCase() + " and can't be changed"));
        }
    }

    @Test
    @WithUserDetails(EVA)
    void aRejectedWeekCanBeEditedAgain() throws Exception {
        save(entry("DKB-CORE", 0, "8", null)).andExpect(status().isOk());
        Timesheet week = timesheets.findWeek(users.findByEmail(EVA).orElseThrow().getId(), MON).orElseThrow();
        week.setStatus(TimesheetStatus.REJECTED);
        timesheets.flush();

        save(entry("DKB-CORE", 0, "7", null)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    @WithUserDetails(EVA)
    void aWeekMustStartOnMonday() throws Exception {
        mockMvc.perform(put("/api/me/timesheets/{week}/entries", MON.plusDays(1)).with(XsrfToken.from(mockMvc))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"entries\": []}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("weekStart"));
    }

    @Test
    @WithUserDetails(EVA)
    void withoutTheCsrfTokenItsForbidden() throws Exception {
        mockMvc.perform(put("/api/me/timesheets/{week}/entries", MON)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"entries\": []}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void savingNeedsALogin() throws Exception {
        save().andExpect(status().isUnauthorized());
    }

    private ResultActions save(String... entryJson) throws Exception {
        return saveBody("{\"entries\": [" + String.join(", ", entryJson) + "]}");
    }

    private ResultActions saveBody(String body) throws Exception {
        return mockMvc.perform(put("/api/me/timesheets/{week}/entries", MON).with(XsrfToken.from(mockMvc))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String entry(String projectCode, int day, String hours, String description) {
        String text = description == null ? "" : ", \"description\": \"" + description + "\"";
        return "{\"projectId\": %d, \"workDate\": \"%s\", \"hours\": %s%s}".formatted(project(projectCode).getId(), MON.plusDays(day), hours, text);
    }

    private Project project(String code) {
        return projects.findAll().stream().collect(Collectors.toMap(Project::getCode, p -> p)).get(code);
    }
}

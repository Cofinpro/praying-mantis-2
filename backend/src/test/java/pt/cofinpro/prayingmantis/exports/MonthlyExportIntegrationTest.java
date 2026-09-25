package pt.cofinpro.prayingmantis.exports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pt.cofinpro.prayingmantis.TestcontainersConfiguration;
import pt.cofinpro.prayingmantis.absences.AbsenceRequest;
import pt.cofinpro.prayingmantis.absences.AbsenceRequestRepository;
import pt.cofinpro.prayingmantis.absences.AbsenceStatus;
import pt.cofinpro.prayingmantis.absences.AbsenceTypeCode;
import pt.cofinpro.prayingmantis.absences.AbsenceTypeRepository;
import pt.cofinpro.prayingmantis.absences.DayPart;
import pt.cofinpro.prayingmantis.projects.Project;
import pt.cofinpro.prayingmantis.projects.ProjectRepository;
import pt.cofinpro.prayingmantis.timesheets.TimeEntry;
import pt.cofinpro.prayingmantis.timesheets.TimeEntryRepository;
import pt.cofinpro.prayingmantis.timesheets.Timesheet;
import pt.cofinpro.prayingmantis.timesheets.TimesheetRepository;
import pt.cofinpro.prayingmantis.timesheets.TimesheetStatus;
import pt.cofinpro.prayingmantis.users.User;
import pt.cofinpro.prayingmantis.users.UserRepository;

/**
 * GET /api/me/timesheet-months/{month} and /api/me/timesheet-exports end to end (BE-8.2). Eva's October 2026:
 * the week of 28 Sep is approved and crosses into October; the week of 5 Oct is only a draft.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class MonthlyExportIntegrationTest {

    private static final String EVA = "eva.santos@cofinpro.pt";
    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

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

    @Autowired
    AbsenceRequestRepository absences;

    @Autowired
    AbsenceTypeRepository types;

    @BeforeEach
    void evasOctober() {
        User eva = users.findByEmail(EVA).orElseThrow();
        Timesheet sep28 = week(eva, LocalDate.of(2026, 9, 28), TimesheetStatus.APPROVED);
        entry(sep28, "DKB-CORE", LocalDate.of(2026, 9, 30), "8", null);      // September: not in the export
        entry(sep28, "DKB-CORE", LocalDate.of(2026, 10, 1), "7.5", "Release");
        entry(sep28, "INTERNAL", LocalDate.of(2026, 10, 2), "2", null);
        Timesheet oct5 = week(eva, LocalDate.of(2026, 10, 5), TimesheetStatus.DRAFT);
        entry(oct5, "DKB-CORE", LocalDate.of(2026, 10, 6), "8", null);
        // An approved vacation day on Fri 9 Oct, and a pending one that isn't listed
        vacation(eva, LocalDate.of(2026, 10, 9), AbsenceStatus.APPROVED);
        vacation(eva, LocalDate.of(2026, 10, 16), AbsenceStatus.PENDING);
    }

    @Test
    @WithUserDetails(EVA)
    void theSummaryHasEveryWeekOfTheMonthWithItsStatus() throws Exception {
        mockMvc.perform(get("/api/me/timesheet-months/{month}", "2026-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value("2026-10"))
                .andExpect(jsonPath("$.totalHours").value(17.5))
                // 28 Sep to 26 Oct: five weeks touch October
                .andExpect(jsonPath("$.weeks", hasSize(5)))
                .andExpect(jsonPath("$.weeks[*].weekStart", contains("2026-09-28", "2026-10-05", "2026-10-12", "2026-10-19", "2026-10-26")))
                .andExpect(jsonPath("$.weeks[*].status", contains("APPROVED", "DRAFT", "DRAFT", "DRAFT", "DRAFT")))
                // Only the October days of the week of 28 Sep
                .andExpect(jsonPath("$.weeks[0].hoursInMonth").value(9.5))
                .andExpect(jsonPath("$.weeks[1].hoursInMonth").value(8.0))
                .andExpect(jsonPath("$.weeks[2].hoursInMonth").value(0));
    }

    @Test
    @WithUserDetails(EVA)
    void theExportIsAnXlsxAttachmentWithTheMonthsEntries() throws Exception {
        byte[] file = mockMvc.perform(get("/api/me/timesheet-exports").param("month", "2026-10").param("template", "GENERIC"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", XLSX))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"timesheet-2026-10-eva.santos-GENERIC.xlsx\""))
                .andReturn().getResponse().getContentAsByteArray();

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file))) {
            Sheet sheet = workbook.getSheet(GenericExportTemplate.SHEET);
            DataFormatter text = new DataFormatter();
            assertThat(text.formatCellValue(sheet.getRow(0).getCell(0))).isEqualTo("Timesheet October 2026");
            assertThat(text.formatCellValue(sheet.getRow(1).getCell(1))).isEqualTo("Eva Santos");
            assertThat(text.formatCellValue(sheet.getRow(3).getCell(1))).isEqualTo("VV");

            // All October entries, whatever the week's status, by day; the 30 Sep one isn't there
            int first = GenericExportTemplate.ENTRIES_HEADER_ROW + 1;
            assertThat(row(sheet, first, text)).containsExactly("2026-10-01", "Thu", "DKB-CORE", "DKB core banking", "7.50", "Release");
            assertThat(row(sheet, first + 1, text)).startsWith("2026-10-02", "Fri", "INTERNAL");
            assertThat(row(sheet, first + 2, text)).startsWith("2026-10-06", "Tue", "DKB-CORE");
            assertThat(sheet.getRow(first + 3)).isNull();

            // Hours per project, then the total
            assertThat(row(sheet, first + 4, text)).containsExactly("Project", "Hours");
            assertThat(row(sheet, first + 5, text)).containsExactly("DKB-CORE", "15.50");
            assertThat(row(sheet, first + 6, text)).containsExactly("INTERNAL", "2.00");
            assertThat(row(sheet, first + 7, text)).containsExactly("Total", "17.50");
            assertThat(sheet.getRow(first + 7).getCell(1).getNumericCellValue()).isEqualTo(17.5);

            // Only the approved absence
            assertThat(row(sheet, first + 9, text)).containsExactly("Absence", "From", "To", "Working days");
            assertThat(row(sheet, first + 10, text)).containsExactly("Vacation", "2026-10-09", "2026-10-09", "1.00");
            assertThat(sheet.getRow(first + 11)).isNull();
        }
    }

    @Test
    @WithUserDetails("filipe.rocha@cofinpro.pt")
    void aMonthWithoutEntriesIsAnEmptyExport() throws Exception {
        mockMvc.perform(get("/api/me/timesheet-months/{month}", "2026-10"))
                .andExpect(jsonPath("$.totalHours").value(0))
                .andExpect(jsonPath("$.weeks[*].status", contains("DRAFT", "DRAFT", "DRAFT", "DRAFT", "DRAFT")));
        mockMvc.perform(get("/api/me/timesheet-exports").param("month", "2026-10").param("template", "GENERIC"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"timesheet-2026-10-filipe.rocha-GENERIC.xlsx\""));
    }

    @Test
    @WithUserDetails(EVA)
    void aMonthThatIsntYyyyMmIsAFieldError() throws Exception {
        mockMvc.perform(get("/api/me/timesheet-months/{month}", "2026-13")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/me/timesheet-exports").param("month", "10-2026").param("template", "GENERIC"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("month"));
    }

    @Test
    @WithUserDetails(EVA)
    void anUnknownTemplateIsAFieldError() throws Exception {
        mockMvc.perform(get("/api/me/timesheet-exports").param("month", "2026-10").param("template", "DKB"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("template"));
    }

    @Test
    void exportsNeedALogin() throws Exception {
        mockMvc.perform(get("/api/me/timesheet-exports").param("month", "2026-10").param("template", "GENERIC"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/me/timesheet-months/{month}", "2026-10")).andExpect(status().isUnauthorized());
    }

    private static java.util.List<String> row(Sheet sheet, int index, DataFormatter text) {
        Row row = sheet.getRow(index);
        java.util.List<String> cells = new java.util.ArrayList<>();
        for (int c = 0; c < row.getLastCellNum(); c++) {
            cells.add(text.formatCellValue(row.getCell(c)));
        }
        return cells;
    }

    private Timesheet week(User user, LocalDate monday, TimesheetStatus status) {
        Timesheet week = new Timesheet(user, monday);
        week.setStatus(status);
        return timesheets.saveAndFlush(week);
    }

    private void entry(Timesheet week, String code, LocalDate day, String hours, String description) {
        Project project = projects.findAll().stream().filter(p -> p.getCode().equals(code)).findFirst().orElseThrow();
        entries.saveAndFlush(new TimeEntry(week, project, day, new BigDecimal(hours), description));
    }

    private void vacation(User user, LocalDate day, AbsenceStatus status) {
        absences.saveAndFlush(new AbsenceRequest(user, types.findByCode(AbsenceTypeCode.VACATION).orElseThrow(),
                day, DayPart.FULL, day, DayPart.FULL, BigDecimal.ONE, status, user.getTeamLead()));
    }
}

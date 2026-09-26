package pt.cofinpro.prayingmantis.exports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Nested;
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
import pt.cofinpro.prayingmantis.timesheets.TimeEntry;
import pt.cofinpro.prayingmantis.timesheets.TimeEntryRepository;
import pt.cofinpro.prayingmantis.timesheets.Timesheet;
import pt.cofinpro.prayingmantis.timesheets.TimesheetRepository;
import pt.cofinpro.prayingmantis.users.Client;
import pt.cofinpro.prayingmantis.users.Level;
import pt.cofinpro.prayingmantis.users.User;
import pt.cofinpro.prayingmantis.users.UserRepository;

/** The mock client templates (BE-8.3): the files match their layouts, and exports fill the right cells. */
class ClientExportTemplatesTest {

    private static final DataFormatter TEXT = new DataFormatter();

    /** Plain unit tests: the committed .xlsx files and the layouts in ClientExportTemplates must agree. */
    @Nested
    class TheFilesMatchTheirLayouts {

        @Test
        void dailyLists() {
            for (var entry : Map.of("dkb", ClientExportTemplates.DKB, "vv", ClientExportTemplates.VV,
                    "union", ClientExportTemplates.UNION).entrySet()) {
                DailyListLayout l = entry.getValue();
                try (Workbook wb = Sheets.open("/export-templates/" + entry.getKey() + ".xlsx")) {
                    Sheet sheet = wb.getSheet(l.sheet());
                    assertThat(sheet).as(entry.getKey()).isNotNull();
                    assertThat(text(sheet, "A1")).isEqualTo(l.title());
                    assertThat(text(sheet, "A2")).startsWith("MUSTER");
                    assertThat(Sheets.cell(sheet, l.totalCell()).getCellFormula())
                            .isEqualTo("SUM(%s%d:%s%d)".formatted(l.hoursColumn(), l.firstDayRow(), l.hoursColumn(), l.lastDayRow()));
                } catch (Exception e) {
                    throw new AssertionError(entry.getKey(), e);
                }
            }
        }

        @Test
        void projectGrids() {
            for (var entry : Map.of("deka", ClientExportTemplates.DEKA, "dbis", ClientExportTemplates.DBIS).entrySet()) {
                ProjectGridLayout l = entry.getValue();
                try (Workbook wb = Sheets.open("/export-templates/" + entry.getKey() + ".xlsx")) {
                    Sheet sheet = wb.getSheet(l.sheet());
                    assertThat(text(sheet, "A1")).isEqualTo(l.title());
                    String totalCol = CellReference.convertNumToColString(l.dayTotalColumnIndex());
                    assertThat(Sheets.cell(sheet, l.totalRow() - 1, l.dayTotalColumnIndex()).getCellFormula())
                            .isEqualTo("SUM(%s%d:%s%d)".formatted(totalCol, l.firstDayRow(), totalCol, l.lastDayRow()));
                } catch (Exception e) {
                    throw new AssertionError(entry.getKey(), e);
                }
            }
        }
    }

    /** Unit test of the grid's overflow rule, without Spring: DBIS has 4 project columns. */
    @Nested
    class TheGrid {

        @Test
        void moreProjectsThanColumnsGoIntoWeitere() throws Exception {
            User user = new User("Test Person", "t@cofinpro.pt", "x", Client.DBIS, Level.JUNIOR);
            LocalDate day = LocalDate.of(2026, 10, 13);
            List<TimeEntry> entries = List.of("DBIS-A", "DBIS-B", "DBIS-C", "DBIS-D", "DBIS-E").stream()
                    .map(code -> new TimeEntry(null, new Project(code, code, Client.DBIS, true, true), day, BigDecimal.ONE, null))
                    .toList();
            var template = new ProjectGridExportTemplate("DBIS", "DBIS", Client.DBIS, "/export-templates/dbis.xlsx", ClientExportTemplates.DBIS);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            template.write(new MonthExport(user, YearMonth.of(2026, 10), entries, List.of(), List.of()), out);

            try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
                Sheet sheet = wb.getSheet(ClientExportTemplates.DBIS.sheet());
                assertThat(text(sheet, "B6")).isEqualTo("DBIS-A");
                assertThat(text(sheet, "D6")).isEqualTo("DBIS-C");
                assertThat(text(sheet, "E6")).isEqualTo(ProjectGridExportTemplate.MORE);
                // Day 13 is row 7 + 12 = 19; D and E together in the last column
                assertThat(Sheets.cell(sheet, "E19").getNumericCellValue()).isEqualTo(2.0);
                assertThat(cached(sheet, "F19")).isEqualTo(5.0);
            }
        }
    }

    /** Real exports through the API, against the dev seed: Carla works for DKB, Filipe for Deka. */
    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    @Import(TestcontainersConfiguration.class)
    @Transactional
    class Exports {

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
        @WithUserDetails("carla.mendes@cofinpro.pt")
        void dkbListsTheDaysWithOnlyDkbHours() throws Exception {
            Timesheet week = week("carla.mendes@cofinpro.pt", LocalDate.of(2026, 10, 12));
            entry(week, "DKB-CORE", "2026-10-13", "8", "Release");
            entry(week, "DKB-APP", "2026-10-13", "2", null);
            entry(week, "INTERNAL", "2026-10-14", "4", null); // not DKB's: not on DKB's sheet

            try (Workbook wb = export("DKB")) {
                DailyListLayout l = ClientExportTemplates.DKB;
                Sheet sheet = wb.getSheet(l.sheet());
                assertThat(text(sheet, l.nameCell())).isEqualTo("Carla Mendes");
                assertThat(text(sheet, l.monthCell())).isEqualTo("Oktober 2026");
                // Day d is on row firstDayRow + d - 1
                assertThat(text(sheet, "A20")).isEqualTo("13.10.2026");
                assertThat(text(sheet, "B20")).isEqualTo("Di.");
                assertThat(text(sheet, "C20")).isEqualTo("DKB-APP; DKB-CORE: Release");
                assertThat(Sheets.cell(sheet, "D20").getNumericCellValue()).isEqualTo(10.0);
                assertThat(text(sheet, "D21")).isEmpty();
                assertThat(text(sheet, "E10")).isEqualTo("Wochenende");               // Sat 3 Oct
                assertThat(text(sheet, "E12")).isEqualTo("Feiertag: Republic Day");   // Mon 5 Oct
                assertThat(cached(sheet, l.totalCell())).isEqualTo(10.0);
            }
        }

        @Test
        @WithUserDetails("filipe.rocha@cofinpro.pt")
        void dekaPutsEachProjectInItsOwnColumn() throws Exception {
            Timesheet week = week("filipe.rocha@cofinpro.pt", LocalDate.of(2026, 10, 12));
            entry(week, "DEKA-RISK", "2026-10-13", "6", null);
            entry(week, "DEKA-RISK", "2026-10-15", "7.5", null);

            try (Workbook wb = export("DEKA")) {
                ProjectGridLayout l = ClientExportTemplates.DEKA;
                Sheet sheet = wb.getSheet(l.sheet());
                assertThat(text(sheet, "B7")).isEqualTo("DEKA-RISK");
                assertThat(text(sheet, "C7")).isEmpty();
                assertThat(Sheets.cell(sheet, "B20").getNumericCellValue()).isEqualTo(6.0);  // 13 Oct
                assertThat(cached(sheet, "H20")).isEqualTo(6.0);                        // day total
                assertThat(cached(sheet, "H" + l.totalRow())).isEqualTo(13.5);           // month total
            }
        }

        @Test
        @WithUserDetails("filipe.rocha@cofinpro.pt")
        void everyClientTemplateCanBeDownloaded() throws Exception {
            for (String code : List.of("DKB", "DEKA", "VV", "DBIS", "UNION")) {
                mockMvc.perform(get("/api/me/timesheet-exports").param("month", "2026-10").param("template", code))
                        .andExpect(status().isOk());
            }
        }

        private Workbook export(String template) throws Exception {
            byte[] file = mockMvc.perform(get("/api/me/timesheet-exports").param("month", "2026-10").param("template", template))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsByteArray();
            return new XSSFWorkbook(new ByteArrayInputStream(file));
        }

        private Timesheet week(String email, LocalDate monday) {
            return timesheets.saveAndFlush(new Timesheet(users.findByEmail(email).orElseThrow(), monday));
        }

        private void entry(Timesheet week, String code, String day, String hours, String description) {
            Project project = projects.findAll().stream().filter(p -> p.getCode().equals(code)).findFirst().orElseThrow();
            entries.saveAndFlush(new TimeEntry(week, project, LocalDate.parse(day), new BigDecimal(hours), description));
        }
    }

    private static String text(Sheet sheet, String reference) {
        return TEXT.formatCellValue(Sheets.cell(sheet, reference));
    }

    /**
     * The total as the file stores it. Viewers that don't recalculate (Excel's Protected View for downloads,
     * previews, Google Sheets imports) show this cached value, so the export has to compute it.
     */
    private static double cached(Sheet sheet, String reference) {
        Cell cell = Sheets.cell(sheet, reference);
        assertThat(cell.getCellType()).isEqualTo(CellType.FORMULA);
        return cell.getNumericCellValue();
    }
}

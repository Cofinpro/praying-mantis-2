package pt.cofinpro.prayingmantis.exports;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import pt.cofinpro.prayingmantis.timesheets.TimeEntry;
import pt.cofinpro.prayingmantis.users.Client;

/**
 * A client sheet with one row per day (decision #19): the template .xlsx is opened from the classpath and
 * only its cells are filled in, so the client's layout, styles and formulas stay as they are. Shows only the
 * hours on this client's projects. Not a component: {@link ClientExportTemplates} creates one per client.
 */
class DailyListExportTemplate implements ExportTemplate {

    private final String code;
    private final String name;
    private final Client client;
    private final String resource;
    private final DailyListLayout layout;

    DailyListExportTemplate(String code, String name, Client client, String resource, DailyListLayout layout) {
        this.code = code;
        this.name = name;
        this.client = client;
        this.resource = resource;
        this.layout = layout;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Client client() {
        return client;
    }

    @Override
    public void write(MonthExport month, OutputStream out) throws IOException {
        Map<LocalDate, List<TimeEntry>> byDay = month.entriesFor(client).stream()
                .collect(Collectors.groupingBy(TimeEntry::getWorkDate, TreeMap::new, Collectors.toList()));
        try (Workbook workbook = Sheets.open(resource)) {
            Sheet sheet = workbook.getSheet(layout.sheet());
            Sheets.cell(sheet, layout.nameCell()).setCellValue(month.user().getName());
            Sheets.cell(sheet, layout.monthCell()).setCellValue(month.month().format(Sheets.MONTH));

            for (int d = 1; d <= month.month().lengthOfMonth(); d++) {
                LocalDate day = month.month().atDay(d);
                int row = layout.firstDayRow() - 1 + (d - 1);
                Sheets.cell(sheet, row, Sheets.column(layout.dateColumn())).setCellValue(day);
                Sheets.cell(sheet, row, Sheets.column(layout.weekdayColumn())).setCellValue(Sheets.weekday(day));
                List<TimeEntry> entries = byDay.getOrDefault(day, List.of());
                if (entries.isEmpty()) {
                    Sheets.cell(sheet, row, Sheets.column(layout.remarkColumn())).setCellValue(Sheets.remark(month, day));
                    continue;
                }
                BigDecimal hours = entries.stream().map(TimeEntry::getHours).reduce(BigDecimal.ZERO, BigDecimal::add);
                Sheets.cell(sheet, row, Sheets.column(layout.hoursColumn())).setCellValue(hours.doubleValue());
                Sheets.cell(sheet, row, Sheets.column(layout.descriptionColumn())).setCellValue(describe(entries));
            }
            // The template's SUM formulas are computed by Excel on opening, with the new values
            workbook.setForceFormulaRecalculation(true);
            workbook.write(out);
        }
    }

    /** "DKB-CORE: Sprint planning; DKB-APP" – the project codes, with the descriptions where there are any. */
    private static String describe(List<TimeEntry> entries) {
        return entries.stream()
                .map(e -> e.getDescription() == null ? e.getProject().getCode() : e.getProject().getCode() + ": " + e.getDescription())
                .collect(Collectors.joining("; "));
    }
}

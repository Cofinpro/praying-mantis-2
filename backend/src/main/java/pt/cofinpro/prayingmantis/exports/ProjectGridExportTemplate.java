package pt.cofinpro.prayingmantis.exports;

import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDate;
import java.util.List;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import pt.cofinpro.prayingmantis.timesheets.TimeEntry;
import pt.cofinpro.prayingmantis.users.Client;

/**
 * A client sheet with days down and the client's projects across (decision #19). Like
 * {@link DailyListExportTemplate} it fills a template .xlsx and keeps its formulas. Projects go into the
 * columns by code; if there are more than the sheet has, the last column holds the rest, headed "Weitere".
 */
class ProjectGridExportTemplate implements ExportTemplate {

    static final String MORE = "Weitere";

    private final String code;
    private final String name;
    private final Client client;
    private final String resource;
    private final ProjectGridLayout layout;

    ProjectGridExportTemplate(String code, String name, Client client, String resource, ProjectGridLayout layout) {
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
        List<TimeEntry> entries = month.entriesFor(client);
        List<String> codes = entries.stream().map(e -> e.getProject().getCode()).distinct().sorted().toList();
        int columns = layout.projectColumns();
        boolean overflow = codes.size() > columns;

        try (Workbook workbook = Sheets.open(resource)) {
            Sheet sheet = workbook.getSheet(layout.sheet());
            Sheets.cell(sheet, layout.nameCell()).setCellValue(month.user().getName());
            Sheets.cell(sheet, layout.monthCell()).setCellValue(month.month().format(Sheets.MONTH));
            int header = layout.headerRow() - 1;
            for (int i = 0; i < Math.min(codes.size(), columns); i++) {
                String title = overflow && i == columns - 1 ? MORE : codes.get(i);
                Sheets.cell(sheet, header, layout.projectColumnIndex(i)).setCellValue(title);
            }

            for (int d = 1; d <= month.month().lengthOfMonth(); d++) {
                LocalDate day = month.month().atDay(d);
                int row = layout.firstDayRow() - 1 + (d - 1);
                Sheets.cell(sheet, row, Sheets.column(layout.dateColumn())).setCellValue(day);
                boolean worked = false;
                for (TimeEntry e : entries) {
                    if (!e.getWorkDate().equals(day)) {
                        continue;
                    }
                    int i = Math.min(codes.indexOf(e.getProject().getCode()), columns - 1);
                    var cell = Sheets.cell(sheet, row, layout.projectColumnIndex(i));
                    double before = cell.getCellType() == CellType.NUMERIC ? cell.getNumericCellValue() : 0;
                    cell.setCellValue(before + e.getHours().doubleValue());
                    worked = true;
                }
                if (!worked) {
                    Sheets.cell(sheet, row, Sheets.column(layout.remarkColumn())).setCellValue(Sheets.remark(month, day));
                }
            }
            Sheets.computeFormulas(workbook);
            workbook.write(out);
        }
    }
}

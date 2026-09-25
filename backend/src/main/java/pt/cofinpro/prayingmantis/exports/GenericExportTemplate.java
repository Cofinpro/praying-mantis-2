package pt.cofinpro.prayingmantis.exports;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Map;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import pt.cofinpro.prayingmantis.absences.AbsenceRequest;
import pt.cofinpro.prayingmantis.timesheets.TimeEntry;
import pt.cofinpro.prayingmantis.users.Client;

/**
 * The template for every client until their own sheet exists (decision #33). One sheet, built from scratch
 * with Apache POI: a header, one row per entry, the hours per project with the total, and the approved
 * absences. The layout is ours, so its cell positions are constants the tests can check.
 */
@Component
class GenericExportTemplate implements ExportTemplate {

    static final String CODE = "GENERIC";
    static final String SHEET = "Timesheet";
    /** Row (0-based) of the entries' column headers; the entries start on the next row. */
    static final int ENTRIES_HEADER_ROW = 5;

    private static final DateTimeFormatter MONTH_TITLE = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String name() {
        return "Generic monthly timesheet";
    }

    @Override
    public Client client() {
        return null;
    }

    @Override
    public void write(MonthExport month, OutputStream out) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Styles styles = new Styles(workbook);
            Sheet sheet = workbook.createSheet(SHEET);

            text(sheet, 0, 0, "Timesheet " + month.month().format(MONTH_TITLE), styles.title);
            label(sheet, 1, "Name", month.user().getName(), styles);
            label(sheet, 2, "Email", month.user().getEmail(), styles);
            label(sheet, 3, "Client", month.user().getClient().name(), styles);

            int r = ENTRIES_HEADER_ROW;
            header(sheet, r++, styles, "Date", "Day", "Project", "Project name", "Hours", "Description");
            for (TimeEntry e : month.entries()) {
                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(e.getWorkDate());
                row.getCell(0).setCellStyle(styles.date);
                row.createCell(1).setCellValue(e.getWorkDate().getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH));
                row.createCell(2).setCellValue(e.getProject().getCode());
                row.createCell(3).setCellValue(e.getProject().getName());
                hours(row, 4, e.getHours(), styles.hours);
                if (e.getDescription() != null) {
                    row.createCell(5).setCellValue(e.getDescription());
                }
            }

            r++;
            header(sheet, r++, styles, "Project", "Hours");
            for (Map.Entry<String, BigDecimal> p : month.hoursByProjectCode().entrySet()) {
                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(p.getKey());
                hours(row, 1, p.getValue(), styles.hours);
            }
            Row total = sheet.createRow(r++);
            total.createCell(0).setCellValue("Total");
            total.getCell(0).setCellStyle(styles.bold);
            hours(total, 1, month.totalHours(), styles.boldHours);

            r++;
            header(sheet, r++, styles, "Absence", "From", "To", "Working days");
            for (AbsenceRequest a : month.absences()) {
                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(a.getType().getName());
                row.createCell(1).setCellValue(a.getStartDate());
                row.getCell(1).setCellStyle(styles.date);
                row.createCell(2).setCellValue(a.getEndDate());
                row.getCell(2).setCellStyle(styles.date);
                hours(row, 3, a.getWorkingDays(), styles.hours);
            }

            // Fixed widths, in 1/256 of a character. autoSizeColumn would measure text with AWT fonts, which a
            // slim JRE container may not have.
            int[] widths = {12, 6, 16, 32, 8, 40};
            for (int c = 0; c < widths.length; c++) {
                sheet.setColumnWidth(c, widths[c] * 256);
            }
            workbook.write(out);
        }
    }

    private static void label(Sheet sheet, int rowIndex, String label, String value, Styles styles) {
        Row row = sheet.createRow(rowIndex);
        row.createCell(0).setCellValue(label);
        row.getCell(0).setCellStyle(styles.bold);
        row.createCell(1).setCellValue(value);
    }

    private static void header(Sheet sheet, int rowIndex, Styles styles, String... titles) {
        Row row = sheet.createRow(rowIndex);
        for (int c = 0; c < titles.length; c++) {
            row.createCell(c).setCellValue(titles[c]);
            row.getCell(c).setCellStyle(styles.bold);
        }
    }

    private static void text(Sheet sheet, int rowIndex, int column, String value, CellStyle style) {
        Row row = sheet.getRow(rowIndex) != null ? sheet.getRow(rowIndex) : sheet.createRow(rowIndex);
        row.createCell(column).setCellValue(value);
        row.getCell(column).setCellStyle(style);
    }

    /** Numbers, not text, so the client's own formulas can sum them. */
    private static void hours(Row row, int column, BigDecimal value, CellStyle style) {
        row.createCell(column).setCellValue(value.doubleValue());
        row.getCell(column).setCellStyle(style);
    }

    /** Cell styles belong to the workbook: create each once, not per cell (Excel has a limit on styles). */
    private static final class Styles {
        final CellStyle title;
        final CellStyle bold;
        final CellStyle date;
        final CellStyle hours;
        final CellStyle boldHours;

        Styles(Workbook workbook) {
            CreationHelper helper = workbook.getCreationHelper();
            Font boldFont = workbook.createFont();
            boldFont.setBold(true);
            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);

            title = workbook.createCellStyle();
            title.setFont(titleFont);
            bold = workbook.createCellStyle();
            bold.setFont(boldFont);
            date = workbook.createCellStyle();
            date.setDataFormat(helper.createDataFormat().getFormat("yyyy-mm-dd"));
            hours = workbook.createCellStyle();
            hours.setDataFormat(helper.createDataFormat().getFormat("0.00"));
            boldHours = workbook.createCellStyle();
            boldHours.cloneStyleFrom(hours);
            boldHours.setFont(boldFont);
        }
    }
}

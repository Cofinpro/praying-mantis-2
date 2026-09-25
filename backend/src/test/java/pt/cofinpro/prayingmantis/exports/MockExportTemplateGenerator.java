package pt.cofinpro.prayingmantis.exports;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Builds the MOCK client templates in src/main/resources/export-templates/ from the layouts in
 * {@link ClientExportTemplates} (BE-8.3). They stand in for the clients' real sheets until those arrive.
 * Not a test: run it after changing a layout, from backend/, with the test classpath, e.g. from the IDE:
 * {@code MockExportTemplateGenerator src/main/resources/export-templates}. ClientExportTemplatesTest fails
 * when the committed files and the layouts drift apart.
 */
public final class MockExportTemplateGenerator {

    private MockExportTemplateGenerator() {
    }

    public static void main(String[] args) throws IOException {
        Path dir = Path.of(args.length > 0 ? args[0] : "src/main/resources/export-templates");
        Files.createDirectories(dir);
        daily(dir.resolve("dkb.xlsx"), ClientExportTemplates.DKB, "DKB");
        grid(dir.resolve("deka.xlsx"), ClientExportTemplates.DEKA, "Deka");
        daily(dir.resolve("vv.xlsx"), ClientExportTemplates.VV, "VV");
        grid(dir.resolve("dbis.xlsx"), ClientExportTemplates.DBIS, "DBIS");
        daily(dir.resolve("union.xlsx"), ClientExportTemplates.UNION, "Union Investment");
        System.out.println("Wrote the mock export templates to " + dir.toAbsolutePath());
    }

    static void daily(Path file, DailyListLayout l, String client) throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            Styles s = new Styles(wb);
            Sheet sheet = wb.createSheet(l.sheet());
            head(sheet, s, l.title(), client, l.nameCell(), l.monthCell());

            int header = l.firstDayRow() - 2;
            text(sheet, header, l.dateColumn(), "Datum", s.header);
            text(sheet, header, l.weekdayColumn(), "Tag", s.header);
            text(sheet, header, l.descriptionColumn(), "Tätigkeit", s.header);
            text(sheet, header, l.hoursColumn(), "Stunden", s.header);
            text(sheet, header, l.remarkColumn(), "Bemerkung", s.header);
            for (int r = l.firstDayRow() - 1; r <= l.lastDayRow() - 1; r++) {
                Sheets.cell(sheet, r, Sheets.column(l.dateColumn())).setCellStyle(s.date);
                Sheets.cell(sheet, r, Sheets.column(l.weekdayColumn())).setCellStyle(s.border);
                Sheets.cell(sheet, r, Sheets.column(l.descriptionColumn())).setCellStyle(s.border);
                Sheets.cell(sheet, r, Sheets.column(l.hoursColumn())).setCellStyle(s.hours);
                Sheets.cell(sheet, r, Sheets.column(l.remarkColumn())).setCellStyle(s.border);
            }
            CellReference total = new CellReference(l.totalCell());
            Sheets.cell(sheet, total.getRow(), total.getCol() - 1).setCellValue("Summe");
            Sheets.cell(sheet, total.getRow(), total.getCol() - 1).setCellStyle(s.header);
            var sum = Sheets.cell(sheet, l.totalCell());
            sum.setCellFormula("SUM(%s%d:%s%d)".formatted(l.hoursColumn(), l.firstDayRow(), l.hoursColumn(), l.lastDayRow()));
            sum.setCellStyle(s.totalHours);
            signatures(sheet, total.getRow() + 3);

            widths(sheet, l.dateColumn(), 12, l.weekdayColumn(), 6, l.descriptionColumn(), 48, l.hoursColumn(), 10, l.remarkColumn(), 28);
            write(wb, file);
        }
    }

    static void grid(Path file, ProjectGridLayout l, String client) throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            Styles s = new Styles(wb);
            Sheet sheet = wb.createSheet(l.sheet());
            head(sheet, s, l.title(), client, l.nameCell(), l.monthCell());

            int header = l.headerRow() - 1;
            text(sheet, header, l.dateColumn(), "Datum", s.header);
            for (int i = 0; i < l.projectColumns(); i++) {
                Sheets.cell(sheet, header, l.projectColumnIndex(i)).setCellStyle(s.header);
            }
            Sheets.cell(sheet, header, l.dayTotalColumnIndex()).setCellValue("Summe");
            Sheets.cell(sheet, header, l.dayTotalColumnIndex()).setCellStyle(s.header);
            text(sheet, header, l.remarkColumn(), "Bemerkung", s.header);

            String first = l.firstProjectColumn();
            String last = CellReference.convertNumToColString(l.projectColumnIndex(l.projectColumns() - 1));
            for (int row = l.firstDayRow(); row <= l.lastDayRow(); row++) {
                int r = row - 1;
                Sheets.cell(sheet, r, Sheets.column(l.dateColumn())).setCellStyle(s.date);
                for (int i = 0; i < l.projectColumns(); i++) {
                    Sheets.cell(sheet, r, l.projectColumnIndex(i)).setCellStyle(s.hours);
                }
                var dayTotal = Sheets.cell(sheet, r, l.dayTotalColumnIndex());
                dayTotal.setCellFormula("SUM(%s%d:%s%d)".formatted(first, row, last, row));
                dayTotal.setCellStyle(s.totalHours);
                Sheets.cell(sheet, r, Sheets.column(l.remarkColumn())).setCellStyle(s.border);
            }
            int totalRow = l.totalRow() - 1;
            text(sheet, totalRow, l.dateColumn(), "Summe", s.header);
            for (int c = l.projectColumnIndex(0); c <= l.dayTotalColumnIndex(); c++) {
                String col = CellReference.convertNumToColString(c);
                var sum = Sheets.cell(sheet, totalRow, c);
                sum.setCellFormula("SUM(%s%d:%s%d)".formatted(col, l.firstDayRow(), col, l.lastDayRow()));
                sum.setCellStyle(s.totalHours);
            }
            signatures(sheet, totalRow + 3);

            sheet.setColumnWidth(Sheets.column(l.dateColumn()), 12 * 256);
            for (int c = l.projectColumnIndex(0); c <= l.dayTotalColumnIndex(); c++) {
                sheet.setColumnWidth(c, 14 * 256);
            }
            sheet.setColumnWidth(Sheets.column(l.remarkColumn()), 28 * 256);
            write(wb, file);
        }
    }

    /** Title, the mock warning, and the labels left of the name and month cells. */
    private static void head(Sheet sheet, Styles s, String title, String client, String nameCell, String monthCell) {
        Sheets.cell(sheet, "A1").setCellValue(title);
        Sheets.cell(sheet, "A1").setCellStyle(s.title);
        Sheets.cell(sheet, "A2").setCellValue(
                "MUSTER: Platzhalter, bis die echte Vorlage von " + client + " vorliegt. Nicht beim Kunden einreichen.");
        Sheets.cell(sheet, "A2").setCellStyle(s.warning);
        label(sheet, s, nameCell, "Mitarbeiter:");
        label(sheet, s, monthCell, "Monat:");
    }

    private static void label(Sheet sheet, Styles s, String valueCell, String label) {
        CellReference ref = new CellReference(valueCell);
        Sheets.cell(sheet, ref.getRow(), ref.getCol() - 1).setCellValue(label);
        Sheets.cell(sheet, ref.getRow(), ref.getCol() - 1).setCellStyle(s.header);
    }

    private static void signatures(Sheet sheet, int row) {
        Sheets.cell(sheet, row, 0).setCellValue("Unterschrift Mitarbeiter: ____________________");
        Sheets.cell(sheet, row + 2, 0).setCellValue("Unterschrift Kunde: ____________________");
    }

    private static void text(Sheet sheet, int row, String column, String value, CellStyle style) {
        Sheets.cell(sheet, row, Sheets.column(column)).setCellValue(value);
        Sheets.cell(sheet, row, Sheets.column(column)).setCellStyle(style);
    }

    private static void widths(Sheet sheet, Object... columnAndWidth) {
        for (int i = 0; i < columnAndWidth.length; i += 2) {
            sheet.setColumnWidth(Sheets.column((String) columnAndWidth[i]), (Integer) columnAndWidth[i + 1] * 256);
        }
    }

    private static void write(Workbook wb, Path file) throws IOException {
        try (OutputStream out = Files.newOutputStream(file)) {
            wb.write(out);
        }
    }

    private static final class Styles {
        final CellStyle title;
        final CellStyle warning;
        final CellStyle header;
        final CellStyle border;
        final CellStyle date;
        final CellStyle hours;
        final CellStyle totalHours;

        Styles(Workbook wb) {
            Font bold = wb.createFont();
            bold.setBold(true);
            Font big = wb.createFont();
            big.setBold(true);
            big.setFontHeightInPoints((short) 14);
            Font red = wb.createFont();
            red.setColor(IndexedColors.RED.getIndex());
            red.setItalic(true);

            title = wb.createCellStyle();
            title.setFont(big);
            warning = wb.createCellStyle();
            warning.setFont(red);
            header = wb.createCellStyle();
            header.setFont(bold);
            header.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            borders(header);
            border = wb.createCellStyle();
            borders(border);
            date = wb.createCellStyle();
            date.cloneStyleFrom(border);
            date.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat("dd.mm.yyyy"));
            hours = wb.createCellStyle();
            hours.cloneStyleFrom(border);
            hours.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat("0.00"));
            totalHours = wb.createCellStyle();
            totalHours.cloneStyleFrom(hours);
            totalHours.setFont(bold);
        }

        private static void borders(CellStyle style) {
            style.setBorderTop(BorderStyle.THIN);
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
        }
    }
}

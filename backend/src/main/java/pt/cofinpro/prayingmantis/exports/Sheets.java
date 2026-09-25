package pt.cofinpro.prayingmantis.exports;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import pt.cofinpro.prayingmantis.absences.AbsenceRequest;
import pt.cofinpro.prayingmantis.absences.DayPart;

/** Small POI helpers the file-based client templates share (BE-8.3). */
final class Sheets {

    /** The mock client sheets are German, like the clients. */
    static final Locale GERMAN = Locale.GERMAN;
    static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", GERMAN);

    private Sheets() {
    }

    /** Opens a template from the classpath, e.g. /export-templates/dkb.xlsx. The caller closes it. */
    static Workbook open(String resource) {
        try (InputStream in = Sheets.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Export template " + resource + " is missing from the classpath");
            }
            return new XSSFWorkbook(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Can't read export template " + resource, e);
        }
    }

    /** The cell at an Excel reference like "C3", created if the template left it empty. */
    static Cell cell(Sheet sheet, String reference) {
        CellReference ref = new CellReference(reference);
        return cell(sheet, ref.getRow(), ref.getCol());
    }

    /** The cell at a 0-based row and column, created if needed. Keeps the template's style when there is one. */
    static Cell cell(Sheet sheet, int row, int column) {
        Row r = sheet.getRow(row) != null ? sheet.getRow(row) : sheet.createRow(row);
        return r.getCell(column) != null ? r.getCell(column) : r.createCell(column);
    }

    static int column(String letters) {
        return CellReference.convertColStringToIndex(letters);
    }

    static String weekday(LocalDate day) {
        return day.getDayOfWeek().getDisplayName(TextStyle.SHORT, GERMAN);
    }

    /** What the remark column says for a day without hours: weekend, holiday or absence. */
    static String remark(MonthExport month, LocalDate day) {
        return month.absenceOn(day).map(Sheets::absenceLabel)
                .or(() -> month.holidayOn(day).map(h -> "Feiertag: " + h.getName()))
                .orElse(day.getDayOfWeek().getValue() >= 6 ? "Wochenende" : "");
    }

    private static String absenceLabel(AbsenceRequest a) {
        String half = a.getStartDate().equals(a.getEndDate()) && a.getStartPart() != DayPart.FULL
                ? " (halber Tag)" : "";
        return switch (a.getType().getCode()) {
            case VACATION -> "Urlaub";
            case SICK -> "Krank";
            case TRAINING -> "Schulung";
            case PARENTAL -> "Elternzeit";
            case UNPAID -> "Unbezahlter Urlaub";
        } + half;
    }
}

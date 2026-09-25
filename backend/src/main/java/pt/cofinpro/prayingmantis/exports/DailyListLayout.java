package pt.cofinpro.prayingmantis.exports;

/**
 * Where a "one row per day" client sheet keeps things (BE-8.3). Cells are Excel references ("C3"), columns are
 * letters and rows are Excel row numbers (1-based), so they read like the sheet itself. The mock generator
 * builds the .xlsx from the same layout, and a test checks that file and layout still agree.
 *
 * @param firstDayRow the row of day 1; the sheet has 31 day rows, and the total's SUM formula covers them
 */
public record DailyListLayout(
        String sheet,
        String title,
        String nameCell,
        String monthCell,
        int firstDayRow,
        String dateColumn,
        String weekdayColumn,
        String descriptionColumn,
        String hoursColumn,
        String remarkColumn,
        String totalCell) {

    public int lastDayRow() {
        return firstDayRow + 30;
    }
}

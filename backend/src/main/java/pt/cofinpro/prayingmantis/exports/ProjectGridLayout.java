package pt.cofinpro.prayingmantis.exports;

import org.apache.poi.ss.util.CellReference;

/**
 * Where a "days × projects" client sheet keeps things (BE-8.3): a header row of project codes, one row per day,
 * a total per day and a total row, both SUM formulas in the file. Rows are Excel row numbers (1-based) and
 * columns letters, as in {@link DailyListLayout}.
 *
 * @param projectColumns how many project columns the sheet has; the last one sums every further project
 */
public record ProjectGridLayout(
        String sheet,
        String title,
        String nameCell,
        String monthCell,
        int headerRow,
        String dateColumn,
        String firstProjectColumn,
        int projectColumns,
        String remarkColumn) {

    public int firstDayRow() {
        return headerRow + 1;
    }

    public int lastDayRow() {
        return firstDayRow() + 30;
    }

    public int totalRow() {
        return lastDayRow() + 1;
    }

    /** 0-based index of project column {@code i} (0-based). */
    public int projectColumnIndex(int i) {
        return CellReference.convertColStringToIndex(firstProjectColumn) + i;
    }

    /** The per-day total, right of the project columns. */
    public int dayTotalColumnIndex() {
        return projectColumnIndex(projectColumns);
    }
}

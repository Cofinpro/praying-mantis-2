package pt.cofinpro.prayingmantis.absences;

/**
 * Which part of the first or last day is taken. A multi-day request starts FULL or AFTERNOON and ends
 * FULL or MORNING; a single day has the same part at both ends (DB check ck_absence_requests_day_parts).
 */
public enum DayPart {
    FULL,
    MORNING,
    AFTERNOON
}

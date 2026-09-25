package pt.cofinpro.prayingmantis.notifications;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Small helpers for the notification sentences, so every feature words days and dates the same way. */
public final class NotificationTexts {

    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d", Locale.ENGLISH);

    private NotificationTexts() {
    }

    /** "1 day", "0.5 days", "5 days". */
    public static String days(BigDecimal days) {
        String number = days.stripTrailingZeros().toPlainString();
        return number + (days.compareTo(BigDecimal.ONE) == 0 ? " day" : " days");
    }

    /** "2 Nov", "2–6 Nov", "30 Oct–3 Nov" (an en dash, as in the T-4.1 example). */
    public static String range(LocalDate start, LocalDate end) {
        if (start.equals(end)) {
            return start.format(DAY_MONTH);
        }
        if (start.getMonth() == end.getMonth() && start.getYear() == end.getYear()) {
            return start.format(DAY) + "–" + end.format(DAY_MONTH);
        }
        return start.format(DAY_MONTH) + "–" + end.format(DAY_MONTH);
    }
}

package pt.cofinpro.prayingmantis.notifications;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class NotificationTextsTest {

    @Test
    void days() {
        assertThat(NotificationTexts.days(new BigDecimal("1.0"))).isEqualTo("1 day");
        assertThat(NotificationTexts.days(new BigDecimal("0.5"))).isEqualTo("0.5 days");
        assertThat(NotificationTexts.days(new BigDecimal("5.0"))).isEqualTo("5 days");
    }

    @Test
    void ranges() {
        assertThat(NotificationTexts.range(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 2))).isEqualTo("2 Nov");
        assertThat(NotificationTexts.range(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 6))).isEqualTo("2–6 Nov");
        assertThat(NotificationTexts.range(LocalDate.of(2026, 10, 30), LocalDate.of(2026, 11, 3))).isEqualTo("30 Oct–3 Nov");
        assertThat(NotificationTexts.range(LocalDate.of(2026, 12, 30), LocalDate.of(2027, 1, 4))).isEqualTo("30 Dec–4 Jan");
    }
}

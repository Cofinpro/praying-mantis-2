package pt.cofinpro.prayingmantis.holidays;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicHolidayService {

    private final PublicHolidayRepository holidays;
    private final Clock clock;

    public PublicHolidayService(PublicHolidayRepository holidays, Clock clock) {
        this.holidays = holidays;
        this.clock = clock;
    }

    /** The year's holidays by date. Without a year, the current one in Lisbon (T-2.1). */
    @Transactional(readOnly = true)
    public List<PublicHoliday> forYear(Integer year) {
        int y = year != null ? year : LocalDate.now(clock).getYear();
        return holidays.findByDateBetweenOrderByDate(LocalDate.of(y, 1, 1), LocalDate.of(y, 12, 31));
    }
}

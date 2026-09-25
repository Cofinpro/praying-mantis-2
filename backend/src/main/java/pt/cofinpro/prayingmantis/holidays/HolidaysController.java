package pt.cofinpro.prayingmantis.holidays;

import java.util.List;
import org.springframework.web.bind.annotation.RestController;
import pt.cofinpro.prayingmantis.api.HolidaysApi;

/** Holidays are the same for everyone, so any logged-in user may read them. */
@RestController
public class HolidaysController implements HolidaysApi {

    private final PublicHolidayService holidayService;

    public HolidaysController(PublicHolidayService holidayService) {
        this.holidayService = holidayService;
    }

    @Override
    public List<pt.cofinpro.prayingmantis.api.model.PublicHoliday> getPublicHolidays(Integer year) {
        return holidayService.forYear(year).stream()
                .map(holiday -> new pt.cofinpro.prayingmantis.api.model.PublicHoliday(holiday.getDate(), holiday.getName()))
                .toList();
    }
}

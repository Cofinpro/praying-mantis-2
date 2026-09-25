package pt.cofinpro.prayingmantis.holidays;

import java.util.List;
import org.springframework.web.bind.annotation.RestController;
import pt.cofinpro.prayingmantis.api.AdminHolidaysApi;
import pt.cofinpro.prayingmantis.api.model.AdminPublicHoliday;

/** Admin, public holidays (T-9.1). The admin check is in AdminHolidayService, against the DB (decision #35). */
@RestController
public class AdminHolidaysController implements AdminHolidaysApi {

    private final AdminHolidayService adminHolidays;

    public AdminHolidaysController(AdminHolidayService adminHolidays) {
        this.adminHolidays = adminHolidays;
    }

    @Override
    public List<AdminPublicHoliday> getAdminPublicHolidays(Integer year) {
        return adminHolidays.forYear(year).stream().map(AdminHolidaysController::toApi).toList();
    }

    @Override
    public AdminPublicHoliday createAdminPublicHoliday(pt.cofinpro.prayingmantis.api.model.PublicHoliday body) {
        return toApi(adminHolidays.create(body.getDate(), body.getName()));
    }

    @Override
    public AdminPublicHoliday updateAdminPublicHoliday(Long id, pt.cofinpro.prayingmantis.api.model.PublicHoliday body) {
        return toApi(adminHolidays.update(id, body.getDate(), body.getName()));
    }

    @Override
    public void deleteAdminPublicHoliday(Long id) {
        adminHolidays.delete(id);
    }

    private static AdminPublicHoliday toApi(PublicHoliday holiday) {
        return new AdminPublicHoliday(holiday.getId(), holiday.getDate(), holiday.getName());
    }
}

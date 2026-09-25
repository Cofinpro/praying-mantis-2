package pt.cofinpro.prayingmantis.holidays;

import java.time.LocalDate;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.cofinpro.prayingmantis.auth.Permissions;
import pt.cofinpro.prayingmantis.common.ConflictException;
import pt.cofinpro.prayingmantis.common.NotFoundException;

/**
 * Managing public holidays (BE-9.4, decision #35), e.g. next year's list. Existing absence requests keep their
 * stored working days (decision #15); only requests created afterwards see the change. Every method checks
 * that the caller is an admin.
 */
@Service
public class AdminHolidayService {

    static final String DATE_TAKEN = "holiday-date-taken";

    private final PublicHolidayRepository holidays;
    private final Permissions permissions;

    public AdminHolidayService(PublicHolidayRepository holidays, Permissions permissions) {
        this.holidays = holidays;
        this.permissions = permissions;
    }

    @Transactional(readOnly = true)
    public List<PublicHoliday> forYear(int year) {
        permissions.requireAdmin();
        return holidays.findByDateBetweenOrderByDate(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
    }

    @Transactional
    public PublicHoliday create(LocalDate date, String name) {
        permissions.requireAdmin();
        if (holidays.existsByDate(date)) {
            throw dateTaken(date);
        }
        return save(new PublicHoliday(date, name.strip()));
    }

    @Transactional
    public PublicHoliday update(Long holidayId, LocalDate date, String name) {
        permissions.requireAdmin();
        PublicHoliday holiday = find(holidayId);
        if (holidays.existsByDateAndIdNot(date, holidayId)) {
            throw dateTaken(date);
        }
        holiday.update(date, name.strip());
        return save(holiday);
    }

    @Transactional
    public void delete(Long holidayId) {
        permissions.requireAdmin();
        holidays.delete(find(holidayId));
    }

    private PublicHoliday find(Long holidayId) {
        return holidays.findById(holidayId).orElseThrow(() -> new NotFoundException("Public holiday not found"));
    }

    /** A concurrent create on the same date gets past the check; uq_public_holidays_date catches it. */
    private PublicHoliday save(PublicHoliday holiday) {
        try {
            return holidays.saveAndFlush(holiday);
        } catch (DataIntegrityViolationException e) {
            if (String.valueOf(e.getMessage()).contains("uq_public_holidays_date")) {
                throw dateTaken(holiday.getDate());
            }
            throw e;
        }
    }

    private static ConflictException dateTaken(LocalDate date) {
        return new ConflictException(DATE_TAKEN, date + " is already a public holiday");
    }
}
